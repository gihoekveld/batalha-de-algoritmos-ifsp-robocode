package torneio;

import robocode.BattleResults;
import robocode.control.*;
import robocode.control.events.*;
import robocode.control.snapshot.IRobotSnapshot;
import robocode.control.snapshot.IScoreSnapshot;
import robocode.control.snapshot.ITurnSnapshot;
import robocode.control.snapshot.RobotState;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runner do Robocode integrado com a API chaveamento-robos.
 * Suporta robôs individuais e arquivos .team (equipes).
 * Monitora rounds, controla o tempo da partida (2 minutos),
 * contabiliza vitórias e envia fetch autenticado em tempo real.
 * 
 * Permite configuração via torneio.properties, variáveis de ambiente,
 * flags CLI ou menus e inputs interativos no terminal.
 */
public class RobocodeTorneioRunner {

    // Configurações com valores padrão (sobrescrevíveis via torneio.properties, variáveis de ambiente, flags ou input)
    private String apiBaseUrl = "http://localhost:8080";
    private String token;
    private Long partidaId;
    private String timeAName;
    private String timeBName;
    private String nomeExibicaoA;
    private String nomeExibicaoB;
    private int maxRounds = 3;
    private int vitoriasParaGanhar = 2;
    private boolean finalizarComDuasSeguidas = true;
    private boolean exibirJanela = true;
    private boolean resetar = false;

    // Controle de tempo (requisito: partida durar em torno de 2 minutos)
    private int duracaoSegundos = 120; // 2 minutos (120s) padrão
    private int tps = 30;              // 30 turns por segundo (animação fluida em tempo real)

    private String email = "admin@admin.com";
    private String senha = "admin123";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static void main(String[] args) {
        RobocodeTorneioRunner runner = new RobocodeTorneioRunner();
        runner.executar(args);
    }

    public void executar(String[] args) {
        // 1. Carrega configurações do arquivo externo 'torneio.properties' se existir
        carregarPropriedades();

        // 2. Carrega variáveis de ambiente (se definidas)
        carregarVariaveisAmbiente();

        // 3. Processa argumentos de linha de comando (sobrescrevem arquivos/ambiente)
        boolean abrirConfig = processarArgumentos(args);

        Scanner scanner = new Scanner(System.in);

        // Se o usuário solicitou o menu de configuração via flag (--configurar / -c)
        if (abrirConfig) {
            menuConfiguracoes(scanner);
        }

        // 4. Autenticação na API (se ainda não temos token)
        if (token == null && email != null && senha != null) {
            this.token = autenticar(email, senha);
            if (this.token == null && args.length == 0) {
                System.out.println("\n[AVISO] A autenticação na API (" + apiBaseUrl + ") não foi concluída.");
                System.out.print("Deseja configurar a URL da API ou as credenciais agora? (S/N) [S]: ");
                String resp = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
                if (resp.isEmpty() || resp.equalsIgnoreCase("s") || resp.equalsIgnoreCase("sim")) {
                    menuConfiguracoes(scanner);
                }
            }
        }

        // 5. Seleção interativa da partida e dos robôs (se não foram passados por parâmetro)
        if (partidaId == null) {
            menuPrincipal(scanner);
        } else {
            if (timeAName == null || timeBName == null) {
                resolverTimesDaPartida(partidaId);
            }
        }

        // 6. Inicia a batalha
        iniciar();
    }

    private void carregarPropriedades() {
        File file = new File("torneio.properties");
        if (!file.exists()) {
            file = new File("torneio/torneio.properties");
        }
        if (!file.exists()) {
            file = new File("torneio.properties.example");
        }
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                Properties props = new Properties();
                props.load(in);
                if (props.containsKey("api.url") && !props.getProperty("api.url").isBlank()) {
                    apiBaseUrl = props.getProperty("api.url").trim();
                }
                if (props.containsKey("auth.email") && !props.getProperty("auth.email").isBlank()) {
                    email = props.getProperty("auth.email").trim();
                }
                if (props.containsKey("auth.senha") && !props.getProperty("auth.senha").isBlank()) {
                    senha = props.getProperty("auth.senha").trim();
                }
                if (props.containsKey("auth.token") && !props.getProperty("auth.token").isBlank()) {
                    token = props.getProperty("auth.token").trim();
                }
                if (props.containsKey("partida.rounds") && !props.getProperty("partida.rounds").isBlank()) {
                    maxRounds = Integer.parseInt(props.getProperty("partida.rounds").trim());
                }
                if (props.containsKey("partida.vitorias") && !props.getProperty("partida.vitorias").isBlank()) {
                    vitoriasParaGanhar = Integer.parseInt(props.getProperty("partida.vitorias").trim());
                }
                if (props.containsKey("partida.duasSeguidas") && !props.getProperty("partida.duasSeguidas").isBlank()) {
                    finalizarComDuasSeguidas = Boolean.parseBoolean(props.getProperty("partida.duasSeguidas").trim());
                }
                if (props.containsKey("partida.resetar") && !props.getProperty("partida.resetar").isBlank()) {
                    resetar = Boolean.parseBoolean(props.getProperty("partida.resetar").trim());
                }
                if (props.containsKey("partida.duracaoSegundos") && !props.getProperty("partida.duracaoSegundos").isBlank()) {
                    duracaoSegundos = Integer.parseInt(props.getProperty("partida.duracaoSegundos").trim());
                }
                if (props.containsKey("partida.tps") && !props.getProperty("partida.tps").isBlank()) {
                    tps = Integer.parseInt(props.getProperty("partida.tps").trim());
                }
                if (props.containsKey("robocode.exibirJanela") && !props.getProperty("robocode.exibirJanela").isBlank()) {
                    exibirJanela = Boolean.parseBoolean(props.getProperty("robocode.exibirJanela").trim());
                }
            } catch (Exception e) {
                System.err.println("Aviso: Falha ao ler torneio.properties: " + e.getMessage());
            }
        }
    }

    private void carregarVariaveisAmbiente() {
        String envApi = System.getenv("API_URL");
        if (envApi != null && !envApi.isBlank()) apiBaseUrl = envApi.trim();

        String envToken = System.getenv("AUTH_TOKEN");
        if (envToken != null && !envToken.isBlank()) token = envToken.trim();

        String envEmail = System.getenv("ROBOCODE_EMAIL");
        if (envEmail != null && !envEmail.isBlank()) email = envEmail.trim();

        String envSenha = System.getenv("ROBOCODE_SENHA");
        if (envSenha != null && !envSenha.isBlank()) senha = envSenha.trim();

        String envRounds = System.getenv("ROBOCODE_ROUNDS");
        if (envRounds != null && !envRounds.isBlank()) {
            try { maxRounds = Integer.parseInt(envRounds.trim()); } catch (Exception ignored) {}
        }

        String envTempo = System.getenv("ROBOCODE_TEMPO");
        if (envTempo != null && !envTempo.isBlank()) duracaoSegundos = parseSegundos(envTempo.trim());

        String envTps = System.getenv("ROBOCODE_TPS");
        if (envTps != null && !envTps.isBlank()) {
            try { tps = Integer.parseInt(envTps.trim()); } catch (Exception ignored) {}
        }
    }

    private void salvarPropriedades() {
        File file = new File("torneio.properties");
        try (FileOutputStream out = new FileOutputStream(file)) {
            Properties props = new Properties();
            props.setProperty("api.url", apiBaseUrl != null ? apiBaseUrl : "http://localhost:8080");
            props.setProperty("auth.email", email != null ? email : "admin@admin.com");
            props.setProperty("auth.senha", senha != null ? senha : "");
            if (token != null && !token.isBlank()) props.setProperty("auth.token", token);
            props.setProperty("partida.rounds", String.valueOf(maxRounds));
            props.setProperty("partida.vitorias", String.valueOf(vitoriasParaGanhar));
            props.setProperty("partida.duasSeguidas", String.valueOf(finalizarComDuasSeguidas));
            props.setProperty("partida.resetar", String.valueOf(resetar));
            props.setProperty("partida.duracaoSegundos", String.valueOf(duracaoSegundos));
            props.setProperty("partida.tps", String.valueOf(tps));
            props.setProperty("robocode.exibirJanela", String.valueOf(exibirJanela));
            props.store(out, "Configurações do Torneio Robocode atualizadas via console");
            System.out.println("Configurações salvas com sucesso em 'torneio.properties'!");
        } catch (Exception e) {
            System.err.println("Erro ao salvar torneio.properties: " + e.getMessage());
        }
    }

    private boolean processarArgumentos(String[] args) {
        boolean abrirConfig = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help":
                case "-h":
                    imprimirAjuda();
                    System.exit(0);
                    break;
                case "--configurar":
                case "--config":
                case "-c":
                    abrirConfig = true;
                    break;
                case "--partida":
                case "-p":
                    if (i + 1 < args.length) partidaId = Long.parseLong(args[++i]);
                    break;
                case "--timeA":
                case "-a":
                    if (i + 1 < args.length) timeAName = args[++i];
                    break;
                case "--timeB":
                case "-b":
                    if (i + 1 < args.length) timeBName = args[++i];
                    break;
                case "--tempo":
                case "--duracao":
                case "-d":
                    if (i + 1 < args.length) duracaoSegundos = parseSegundos(args[++i]);
                    break;
                case "--tps":
                    if (i + 1 < args.length) tps = Integer.parseInt(args[++i]);
                    break;
                case "--api":
                    if (i + 1 < args.length) apiBaseUrl = args[++i];
                    break;
                case "--token":
                case "-t":
                    if (i + 1 < args.length) token = args[++i];
                    break;
                case "--email":
                    if (i + 1 < args.length) email = args[++i];
                    break;
                case "--senha":
                    if (i + 1 < args.length) senha = args[++i];
                    break;
                case "--rounds":
                case "-r":
                    if (i + 1 < args.length) maxRounds = Integer.parseInt(args[++i]);
                    break;
                case "--vitorias":
                case "-v":
                    if (i + 1 < args.length) vitoriasParaGanhar = Integer.parseInt(args[++i]);
                    break;
                case "--duas-seguidas":
                    if (i + 1 < args.length) finalizarComDuasSeguidas = Boolean.parseBoolean(args[++i]);
                    break;
                case "--resetar":
                    resetar = true;
                    break;
                case "--nodisplay":
                    exibirJanela = false;
                    break;
            }
        }
        return abrirConfig;
    }

    private static int parseSegundos(String input) {
        try {
            input = input.trim().toLowerCase();
            if (input.endsWith("m") || input.endsWith("min")) {
                int min = Integer.parseInt(input.replaceAll("[^0-9]", ""));
                return min * 60;
            }
            if (input.endsWith("s")) {
                return Integer.parseInt(input.replaceAll("[^0-9]", ""));
            }
            return Integer.parseInt(input);
        } catch (Exception e) {
            return 120;
        }
    }

    private void menuPrincipal(Scanner scanner) {
        while (true) {
            System.out.println("\n=================================================");
            System.out.println("       CHAVEAMENTO ROBOCODE - PAINEL PRINCIPAL   ");
            System.out.println("=================================================");
            System.out.println("  • API Base URL : " + apiBaseUrl);
            System.out.println("  • Autenticação : " + (token != null ? "Token ativo" : email));
            System.out.println("  • Regras       : Máx " + maxRounds + " rounds (vence com " + vitoriasParaGanhar + (finalizarComDuasSeguidas ? ", encerra com 2 seguidas" : "") + ")");
            System.out.println("  • Duração      : " + duracaoSegundos + "s (" + (duracaoSegundos / 60) + "m" + (duracaoSegundos % 60 > 0 ? (duracaoSegundos % 60) + "s" : "") + ") | TPS: " + tps);
            System.out.println("  • Interface    : " + (exibirJanela ? "Arena visual ativada" : "Modo headless (sem janela)"));
            System.out.println("-------------------------------------------------");
            System.out.println("  [P] Escolher partida do chaveamento (Padrão)");
            System.out.println("  [C] Configurar ambiente (URL da API, login, regras, tempo)");
            System.out.println("  [R] Listar robôs instalados na pasta 'robots/'");
            System.out.println("  [S] Sair");
            System.out.println("=================================================");
            System.out.print("Opção [Pressione Enter para escolher partida]: ");

            String opcao = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
            if (opcao.isEmpty() || opcao.equalsIgnoreCase("p") || opcao.equalsIgnoreCase("1")) {
                boolean escolheu = selecionarPartidaInterativamente(scanner);
                if (escolheu) break;
            } else if (opcao.equalsIgnoreCase("c") || opcao.equalsIgnoreCase("config")) {
                menuConfiguracoes(scanner);
            } else if (opcao.equalsIgnoreCase("r") || opcao.equalsIgnoreCase("robos")) {
                List<String> robos = descobrirRobosInstalados();
                System.out.println("\n--- Robôs e Equipes encontrados em 'robots/' (" + robos.size() + ") ---");
                for (int i = 0; i < robos.size(); i++) {
                    System.out.printf("  [%d] %s\n", i + 1, robos.get(i));
                }
            } else if (opcao.equalsIgnoreCase("s") || opcao.equalsIgnoreCase("q") || opcao.equalsIgnoreCase("sair")) {
                System.out.println("Saindo do Robocode Torneio Runner.");
                System.exit(0);
            } else {
                System.out.println("Opção inválida.");
            }
        }
    }

    private void menuConfiguracoes(Scanner scanner) {
        System.out.println("\n-------------------------------------------------");
        System.out.println("           CONFIGURAÇÕES DO AMBIENTE             ");
        System.out.println("  (Pressione Enter em qualquer campo para manter)");
        System.out.println("-------------------------------------------------");

        System.out.print("URL base da API [" + apiBaseUrl + "]: ");
        String novaApi = scanner.nextLine().trim();
        if (!novaApi.isEmpty()) this.apiBaseUrl = novaApi;

        System.out.print("Email de autenticação [" + email + "]: ");
        String novoEmail = scanner.nextLine().trim();
        if (!novoEmail.isEmpty()) this.email = novoEmail;

        System.out.print("Senha de autenticação [" + (senha != null && !senha.isEmpty() ? "******" : "vazio") + "]: ");
        String novaSenha = scanner.nextLine().trim();
        if (!novaSenha.isEmpty()) this.senha = novaSenha;

        System.out.print("Duração da partida em segundos (ex: 120 ou 2m) [" + duracaoSegundos + "s]: ");
        String novoTempo = scanner.nextLine().trim();
        if (!novoTempo.isEmpty()) this.duracaoSegundos = parseSegundos(novoTempo);

        System.out.print("TPS - Turns Por Segundo / Velocidade [" + tps + "]: ");
        String novoTps = scanner.nextLine().trim();
        if (!novoTps.isEmpty()) {
            try { this.tps = Integer.parseInt(novoTps); } catch (Exception ignored) {}
        }

        System.out.print("Número máximo de rounds [" + maxRounds + "]: ");
        String novosRounds = scanner.nextLine().trim();
        if (!novosRounds.isEmpty()) {
            try { this.maxRounds = Integer.parseInt(novosRounds); } catch (Exception ignored) {}
        }

        System.out.print("Vitórias para ganhar a partida [" + vitoriasParaGanhar + "]: ");
        String novasVitorias = scanner.nextLine().trim();
        if (!novasVitorias.isEmpty()) {
            try { this.vitoriasParaGanhar = Integer.parseInt(novasVitorias); } catch (Exception ignored) {}
        }

        System.out.print("Finalizar ao vencer 2 rounds seguidos? (S/N) [" + (finalizarComDuasSeguidas ? "S" : "N") + "]: ");
        String seguidas = scanner.nextLine().trim();
        if (!seguidas.isEmpty()) {
            this.finalizarComDuasSeguidas = seguidas.equalsIgnoreCase("s") || seguidas.equalsIgnoreCase("sim") || seguidas.equalsIgnoreCase("true");
        }

        System.out.print("Exibir arena visual do Robocode? (S/N) [" + (exibirJanela ? "S" : "N") + "]: ");
        String exibir = scanner.nextLine().trim();
        if (!exibir.isEmpty()) {
            this.exibirJanela = exibir.equalsIgnoreCase("s") || exibir.equalsIgnoreCase("sim") || exibir.equalsIgnoreCase("true");
        }

        System.out.print("Deseja salvar estas alterações em 'torneio.properties'? (S/N) [S]: ");
        String salvar = scanner.nextLine().trim();
        if (salvar.isEmpty() || salvar.equalsIgnoreCase("s") || salvar.equalsIgnoreCase("sim")) {
            salvarPropriedades();
        }

        // Tenta reautenticar com as novas credenciais
        this.token = autenticar(this.email, this.senha);
    }

    private boolean selecionarPartidaInterativamente(Scanner scanner) {
        System.out.println("\n=================================================");
        System.out.println("      CHAVEAMENTO ROBOCODE - SELEÇÃO DE PARTIDA  ");
        System.out.println("=================================================");

        List<PartidaInfo> partidas = listarPartidasDoServidor();

        if (partidas.isEmpty()) {
            System.out.println("Nenhuma partida encontrada no servidor.");
            System.out.println("Verifique a URL da API (" + apiBaseUrl + ") ou se o torneio já gerou o chaveamento.");
            System.out.print("Informe o ID da partida manualmente (ou Enter para voltar ao menu): ");
            String input = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
            if (!input.isEmpty()) {
                try {
                    this.partidaId = Long.parseLong(input);
                    resolverTimesDaPartida(partidaId);
                    confirmarCompetidores(scanner);
                    return true;
                } catch (Exception e) {
                    System.err.println("ID inválido: " + e.getMessage());
                }
            }
            return false;
        }

        System.out.println("Partidas encontradas no Torneio:");
        int primeiraDisponivel = -1;
        for (int i = 0; i < partidas.size(); i++) {
            PartidaInfo p = partidas.get(i);
            String status = p.status.equals("EM_ANDAMENTO") ? " [EM ANDAMENTO]" : (p.status.equals("FINALIZADA") ? " [FINALIZADA]" : " [AGUARDANDO]");
            if (primeiraDisponivel == -1 && !p.status.equals("FINALIZADA")) {
                primeiraDisponivel = i;
            }
            System.out.printf("  [%d] Partida #%d: %s vs %s (%s)%s\n",
                    i + 1, p.id, p.nomeTimeA, p.nomeTimeB, p.fase, status);
        }

        int defaultIndex = primeiraDisponivel != -1 ? primeiraDisponivel : 0;
        PartidaInfo defaultPartida = partidas.get(defaultIndex);

        System.out.printf("\nEscolha a partida [1-%d] (Pressione Enter para a #%d): ",
                partidas.size(), defaultPartida.id);

        String escolha = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
        PartidaInfo selecionada;
        if (escolha.isEmpty()) {
            selecionada = defaultPartida;
        } else {
            try {
                int idx = Integer.parseInt(escolha) - 1;
                if (idx >= 0 && idx < partidas.size()) {
                    selecionada = partidas.get(idx);
                } else {
                    selecionada = defaultPartida;
                }
            } catch (Exception e) {
                selecionada = defaultPartida;
            }
        }

        this.partidaId = selecionada.id;
        this.nomeExibicaoA = selecionada.nomeTimeA;
        this.nomeExibicaoB = selecionada.nomeTimeB;

        if ("FINALIZADA".equalsIgnoreCase(selecionada.status)) {
            System.out.print("\n⚠️  Esta partida já foi finalizada. Deseja resetar o placar e disputá-la novamente? (S/N) [S]: ");
            String r = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
            if (r.isEmpty() || r.equalsIgnoreCase("s") || r.equalsIgnoreCase("sim")) {
                this.resetar = true;
            }
        }

        confirmarCompetidores(scanner);
        return true;
    }

    private void confirmarCompetidores(Scanner scanner) {
        List<String> robosDisponiveis = descobrirRobosInstalados();
        String sugestaoA = (this.timeAName != null) ? this.timeAName : mapearTimeParaRobo(this.nomeExibicaoA);
        String sugestaoB = (this.timeBName != null) ? this.timeBName : mapearTimeParaRobo(this.nomeExibicaoB);

        System.out.println("\n-------------------------------------------------");
        System.out.println("VINCULAÇÃO DE ROBÔS AOS TIMES DA PARTIDA #" + partidaId);
        System.out.println("-------------------------------------------------");

        this.timeAName = confirmarRoboCompetidor(scanner, this.nomeExibicaoA, sugestaoA, robosDisponiveis);
        this.timeBName = confirmarRoboCompetidor(scanner, this.nomeExibicaoB, sugestaoB, robosDisponiveis);
    }

    private String confirmarRoboCompetidor(Scanner scanner, String timeDisplay, String roboSugerido, List<String> robosDisponiveis) {
        System.out.printf("\nRobô para [%s] (Padrão sugerido: %s):\n", timeDisplay, roboSugerido);
        System.out.print("  Pressione [Enter] para aceitar, digite o nome do robô ou '?' para listar: ");
        String linha = scanner.hasNextLine() ? scanner.nextLine().trim() : "";

        if (linha.isEmpty()) {
            return roboSugerido;
        }

        if (linha.equals("?") || linha.equalsIgnoreCase("help") || linha.equalsIgnoreCase("listar")) {
            System.out.println("\n--- Robôs e Equipes encontrados em 'robots/' (" + robosDisponiveis.size() + ") ---");
            for (int i = 0; i < robosDisponiveis.size(); i++) {
                System.out.printf("  [%d] %s\n", i + 1, robosDisponiveis.get(i));
            }
            System.out.print("Escolha pelo número ou digite o nome (ou Enter para manter '" + roboSugerido + "'): ");
            String escolha = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
            if (escolha.isEmpty()) return roboSugerido;
            try {
                int idx = Integer.parseInt(escolha) - 1;
                if (idx >= 0 && idx < robosDisponiveis.size()) {
                    return robosDisponiveis.get(idx);
                }
            } catch (Exception ignored) {}
            return escolha;
        }

        try {
            int idx = Integer.parseInt(linha) - 1;
            if (idx >= 0 && idx < robosDisponiveis.size()) {
                return robosDisponiveis.get(idx);
            }
        } catch (Exception ignored) {}

        return linha;
    }

    public static List<String> descobrirRobosInstalados() {
        List<String> robos = new ArrayList<>();
        File robotsDir = new File("robots");
        if (!robotsDir.exists() || !robotsDir.isDirectory()) {
            return robos;
        }
        escanearDiretorioRobos(robotsDir, "", robos);
        Collections.sort(robos);
        return robos;
    }

    private static void escanearDiretorioRobos(File dir, String prefixoPacote, List<String> resultado) {
        File[] arquivos = dir.listFiles();
        if (arquivos == null) return;

        for (File f : arquivos) {
            if (f.isDirectory()) {
                String sub = prefixoPacote.isEmpty() ? f.getName() : prefixoPacote + "." + f.getName();
                escanearDiretorioRobos(f, sub, resultado);
            } else {
                String nome = f.getName();
                if (nome.endsWith(".team")) {
                    String ref = prefixoPacote.isEmpty() ? nome : prefixoPacote + "." + nome;
                    resultado.add(ref);
                } else if (nome.endsWith(".class") && !nome.contains("$")) {
                    String base = nome.substring(0, nome.length() - 6);
                    String ref = prefixoPacote.isEmpty() ? base : prefixoPacote + "." + base;
                    resultado.add(ref);
                } else if (nome.endsWith(".jar")) {
                    resultado.add(nome);
                }
            }
        }
    }

    private void resolverTimesDaPartida(Long pId) {
        try {
            Map<Long, String> timesMap = buscarNomesDosTimes();
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/torneios/1/chaveamento"))
                    .GET();
            if (token != null) req.header("Authorization", "Bearer " + token);
            HttpResponse<String> resp = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                Pattern pat = Pattern.compile("\\{\"id\"\\s*:\\s*" + pId + "[^}]*\"timeAId\"\\s*:\\s*(\\d+)[^}]*\"timeBId\"\\s*:\\s*(\\d+)");
                Matcher mat = pat.matcher(resp.body());
                if (mat.find()) {
                    long aId = Long.parseLong(mat.group(1));
                    long bId = Long.parseLong(mat.group(2));
                    this.nomeExibicaoA = timesMap.getOrDefault(aId, "Time A (#" + aId + ")");
                    this.nomeExibicaoB = timesMap.getOrDefault(bId, "Time B (#" + bId + ")");
                    if (this.timeAName == null) this.timeAName = mapearTimeParaRobo(this.nomeExibicaoA);
                    if (this.timeBName == null) this.timeBName = mapearTimeParaRobo(this.nomeExibicaoB);
                }
            }
        } catch (Exception ignored) {}

        if (this.timeAName == null) this.timeAName = "sample.RamFire";
        if (this.timeBName == null) this.timeBName = "sample.Corners";
    }

    public static String mapearTimeParaRobo(String nomeTime) {
        if (nomeTime == null) return "sampleteam.MyFirstTeam.team";
        String n = nomeTime.toLowerCase();

        // 1. Equipes do IFSP e .team personalizados
        if (n.contains("alpha") || n.contains("ifsp")) return "IFSP_Caragua.TeamA.team";
        if (n.contains("droid") || n.contains("myfirst")) return "sampleteam.MyFirstTeam.team";

        // 2. Robôs clássicos do repositório Robocode
        if (n.contains("ramfire") || n.contains("ram")) return "sample.RamFire";
        if (n.contains("corner")) return "sample.Corners";
        if (n.contains("wall")) return "sample.Walls";
        if (n.contains("crazy")) return "sample.Crazy";
        if (n.contains("tracker") || n.contains("track")) return "sample.Tracker";
        if (n.contains("spin")) return "sample.SpinBot";
        if (n.contains("fire")) return "sample.Fire";

        // Padrão seguro
        return "sampleteam.MyFirstTeam.team";
    }

    private static class PartidaInfo {
        long id;
        String fase;
        String status;
        String nomeTimeA;
        String nomeTimeB;
    }

    private List<PartidaInfo> listarPartidasDoServidor() {
        List<PartidaInfo> lista = new ArrayList<>();
        try {
            Map<Long, String> timesMap = buscarNomesDosTimes();

            HttpRequest.Builder chReq = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/torneios/1/chaveamento"))
                    .GET();
            if (token != null) chReq.header("Authorization", "Bearer " + token);
            HttpResponse<String> chResp = httpClient.send(chReq.build(), HttpResponse.BodyHandlers.ofString());

            if (chResp.statusCode() == 200) {
                // Regex para capturar os campos das partidas
                Pattern partPat = Pattern.compile("\\{\"id\"\\s*:\\s*(\\d+)[^}]*\"fase\"\\s*:\\s*\"([^\"]+)\"[^}]*\"status\"\\s*:\\s*\"([^\"]+)\"[^}]*\"timeAId\"\\s*:\\s*(\\d+)[^}]*\"timeBId\"\\s*:\\s*(\\d+)");
                Matcher partMat = partPat.matcher(chResp.body());
                while (partMat.find()) {
                    long id = Long.parseLong(partMat.group(1));
                    String fase = partMat.group(2);
                    String status = partMat.group(3);
                    long aId = Long.parseLong(partMat.group(4));
                    long bId = Long.parseLong(partMat.group(5));

                    PartidaInfo info = new PartidaInfo();
                    info.id = id;
                    info.fase = fase;
                    info.status = status;
                    info.nomeTimeA = timesMap.getOrDefault(aId, "Time #" + aId);
                    info.nomeTimeB = timesMap.getOrDefault(bId, "Time #" + bId);
                    lista.add(info);
                }
            }
        } catch (Exception ignored) {}
        return lista;
    }

    private Map<Long, String> buscarNomesDosTimes() {
        Map<Long, String> mapa = new HashMap<>();
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/api/times"))
                    .GET();
            if (token != null) req.header("Authorization", "Bearer " + token);
            HttpResponse<String> resp = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                Pattern p = Pattern.compile("\\{\"id\"\\s*:\\s*(\\d+)[^}]*\"nome\"\\s*:\\s*\"([^\"]+)\"");
                Matcher m = p.matcher(resp.body());
                while (m.find()) {
                    mapa.put(Long.parseLong(m.group(1)), m.group(2));
                }
            }
        } catch (Exception ignored) {}
        return mapa;
    }

    private static void imprimirAjuda() {
        System.out.println("Uso: ./executar_torneio.sh [opções]");
        System.out.println("Opções disponíveis:");
        System.out.println("  --configurar, -c            Abre o menu interativo de configurações do ambiente");
        System.out.println("  --partida, -p <id>          ID da partida (opcional, abre menu se omitido)");
        System.out.println("  --tempo, -d <segundos|2m>   Duração máxima da partida (padrão: 120s / 2m)");
        System.out.println("  --tps <turns>               Velocidade da arena em TPS (padrão: 30)");
        System.out.println("  --timeA, -a <nome>          Nome do Robô ou Arquivo .team do Time A");
        System.out.println("  --timeB, -b <nome>          Nome do Robô ou Arquivo .team do Time B");
        System.out.println("  --api <url>                 URL base da API (padrão: http://localhost:8080 ou torneio.properties)");
        System.out.println("  --email <email>             Email do usuário organizador (padrão: admin@admin.com)");
        System.out.println("  --senha <senha>             Senha do usuário organizador");
        System.out.println("  --token, -t <jwt>           Token JWT direto (evita login por email/senha)");
        System.out.println("  --rounds, -r <qtd>          Número máximo de rounds (padrão: 3)");
        System.out.println("  --vitorias, -v <qtd>        Vitórias necessárias para vencer (padrão: 2)");
        System.out.println("  --duas-seguidas <bool>      Finaliza ao vencer 2 seguidas (padrão: true)");
        System.out.println("  --resetar                   Reseta a partida no backend antes de iniciar");
        System.out.println("  --nodisplay                 Executa sem abrir interface gráfica do Robocode (headless)");
        System.out.println("  --help, -h                  Exibe esta mensagem de ajuda");
    }

    private String autenticar(String email, String senha) {
        try {
            System.out.println("Autenticando na API chaveamento-robos (" + apiBaseUrl + ")...");
            String json = String.format("{\"email\":\"%s\",\"senha\":\"%s\"}", email, senha);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String tokenEncontrado = extrairValorJson(response.body(), "token");
                System.out.println("Autenticação realizada com sucesso!");
                return tokenEncontrado;
            } else {
                System.err.println("Erro ao autenticar (HTTP " + response.statusCode() + "): " + response.body());
                return null;
            }
        } catch (Exception e) {
            System.err.println("Falha de conexão com a API: " + e.getMessage());
            return null;
        }
    }

    public void iniciar() {
        if (partidaId == null || timeAName == null || timeBName == null) {
            System.err.println("Parâmetros incompletos. Partida ID, Time A e Time B são obrigatórios.");
            return;
        }

        int minutos = duracaoSegundos / 60;
        int restoSeg = duracaoSegundos % 60;
        String duracaoFmt = String.format("%02d:%02d", minutos, restoSeg);

        System.out.println("\n=================================================");
        System.out.println("       INICIANDO DISPUTA ROBOCODE TORNEIO        ");
        System.out.println("=================================================");
        System.out.println("Partida ID: " + partidaId);
        if (nomeExibicaoA != null) System.out.println("Time A:     " + nomeExibicaoA + " (" + timeAName + ")");
        else System.out.println("Time A:     " + timeAName);
        if (nomeExibicaoB != null) System.out.println("Time B:     " + nomeExibicaoB + " (" + timeBName + ")");
        else System.out.println("Time B:     " + timeBName);
        System.out.println("Tempo Alvo: " + duracaoFmt + " (" + duracaoSegundos + "s)");
        System.out.println("Velocidade: " + tps + " TPS (Turns Per Second)");
        System.out.println("Formato:    Melhor de " + maxRounds + " (vitórias necessárias: " + vitoriasParaGanhar + ")");
        System.out.println("Duas seguidas finaliza: " + finalizarComDuasSeguidas);
        if (resetar) System.out.println("Modo:       Resetar partida antes de iniciar");
        System.out.println("=================================================\n");

        // 1. Notifica o backend para colocar a partida em EM_ANDAMENTO (ou resetar)
        boolean iniciou = notificarInicioPartida();
        if (!iniciou && token != null) {
            System.err.println("\nAbortando execução da batalha pois a partida não pôde ser iniciada.");
            return;
        }

        // 2. Configura a velocidade desejada no Robocode
        System.setProperty("robocode.options.battle.desiredTPS", String.valueOf(tps));

        // 3. Inicializa o RobocodeEngine
        String robocodeHome = System.getProperty("user.dir");
        RobocodeEngine engine = new RobocodeEngine(new File(robocodeHome));
        engine.setVisible(exibirJanela);

        // 4. Adiciona o listener da batalha
        TorneioBattleListener listener = new TorneioBattleListener(engine, duracaoSegundos);
        engine.addBattleListener(listener);

        // 5. Carrega robôs/times de forma inteligente (suporta .team, *, ou nome completo)
        System.out.println("Carregando competidores no Robocode...");
        RobotSpecification[] selectedRobots = carregarCompetidores(engine, timeAName, timeBName);

        if (selectedRobots == null || selectedRobots.length < 2) {
            System.err.println("Erro: Não foi possível carregar os dois competidores (" + timeAName + ", " + timeBName + ").");
            System.err.println("Verifique se os robôs/times estão compilados na pasta 'robots'.");
            engine.close();
            System.exit(1);
            return;
        }

        BattlefieldSpecification battlefield = new BattlefieldSpecification(800, 600);
        long inactivityTime = 450; // tempo de inatividade padrão (450 turns = 15s sem ação)
        double gunCoolingRate = 0.1;
        BattleSpecification battleSpec = new BattleSpecification(maxRounds, inactivityTime, gunCoolingRate, battlefield, selectedRobots);

        // 6. Roda a batalha
        System.out.println("Iniciando batalha na arena!");
        engine.runBattle(battleSpec, true); // aguarda a batalha terminar ou ser abortada
        engine.close();

        System.out.println("\n=================================================");
        System.out.println("       BATALHA CONCLUÍDA COM SUCESSO!            ");
        System.out.println("=================================================");

        // Encerramento limpo sem travar o terminal
        System.exit(0);
    }

    private RobotSpecification[] carregarCompetidores(RobocodeEngine engine, String a, String b) {
        String specA = resolverNome(engine, a);
        String specB = resolverNome(engine, b);

        if (specA == null || specB == null) {
            System.err.println("Erro ao localizar competidores no repositório do Robocode:");
            if (specA == null) System.err.println(" -> Competidor A não encontrado: '" + a + "'");
            if (specB == null) System.err.println(" -> Competidor B não encontrado: '" + b + "'");
            return null;
        }

        System.out.println("Competidor A configurado como: " + specA);
        System.out.println("Competidor B configurado como: " + specB);
        return engine.getLocalRepository(specA + "," + specB);
    }

    private String resolverNome(RobocodeEngine engine, String input) {
        if (input == null || input.isBlank()) return null;

        String limpo = input.trim();
        if (limpo.endsWith(".team")) {
            limpo = limpo.substring(0, limpo.length() - 5);
        }

        RobotSpecification[] todos = engine.getLocalRepository();
        if (todos == null) return null;

        // 1. Procura match exato por nome ou className
        for (RobotSpecification s : todos) {
            if (s.getName().equalsIgnoreCase(input) || s.getClassName().equalsIgnoreCase(input)) {
                return s.getName();
            }
        }

        // 2. Procura com asterisco ou sem extensão .team
        for (RobotSpecification s : todos) {
            String sName = s.getName();
            String sSemEstrela = sName.endsWith("*") ? sName.substring(0, sName.length() - 1) : sName;

            if (sName.equalsIgnoreCase(limpo) || sSemEstrela.equalsIgnoreCase(limpo) || s.getClassName().equalsIgnoreCase(limpo)) {
                return sName;
            }
        }

        // 3. Procura parcial / case-insensitive
        for (RobotSpecification s : todos) {
            String sLower = s.getName().toLowerCase();
            String lLower = limpo.toLowerCase();
            if (sLower.contains(lLower) || s.getClassName().toLowerCase().contains(lLower)) {
                return s.getName();
            }
        }

        return null;
    }

    private boolean notificarInicioPartida() {
        try {
            if (resetar) {
                resetarPartidaNoBackend();
            }

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/partidas/" + partidaId + "/iniciar"))
                    .POST(HttpRequest.BodyPublishers.noBody());

            if (token != null) {
                reqBuilder.header("Authorization", "Bearer " + token);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                System.out.println("[API] Partida #" + partidaId + " iniciada com sucesso (status: EM_ANDAMENTO).");
                return true;
            } else {
                String erro = extrairValorJson(response.body(), "erro");
                if (erro.isEmpty()) erro = response.body();
                System.err.println("\n[API] Aviso ao iniciar partida #" + partidaId + " (HTTP " + response.statusCode() + "): " + erro);
                return false;
            }
        } catch (Exception e) {
            System.err.println("[API] Não foi possível conectar ao backend para iniciar partida: " + e.getMessage());
            return false;
        }
    }

    private void resetarPartidaNoBackend() {
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/partidas/" + partidaId + "/resetar"))
                    .POST(HttpRequest.BodyPublishers.noBody());

            if (token != null) {
                reqBuilder.header("Authorization", "Bearer " + token);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                System.out.println("[API] Partida #" + partidaId + " resetada com sucesso para AGUARDANDO.");
            }
        } catch (Exception e) {
            System.err.println("[API] Falha ao resetar partida: " + e.getMessage());
        }
    }

    private boolean enviarVitoriaRound(String vencedorRound, int roundAtual) {
        try {
            String json = String.format(
                    "{\"vencedorRound\":\"%s\",\"vitoriasParaGanhar\":%d,\"maxRounds\":%d,\"finalizarComDuasSeguidas\":%b}",
                    vencedorRound, vitoriasParaGanhar, maxRounds, finalizarComDuasSeguidas
            );

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/partidas/" + partidaId + "/rounds"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));

            if (token != null) {
                reqBuilder.header("Authorization", "Bearer " + token);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                String placarA = extrairValorJson(body, "placarTimeA");
                String placarB = extrairValorJson(body, "placarTimeB");
                String status = extrairValorJson(body, "status");

                System.out.println("-------------------------------------------------");
                System.out.println(" [ROUND " + roundAtual + "] Vencedor: " + vencedorRound);
                System.out.println(" Placar atualizado no servidor: Time A [" + placarA + "] x [" + placarB + "] Time B");
                System.out.println(" Status da partida: " + status);
                System.out.println("-------------------------------------------------");

                if ("FINALIZADA".equalsIgnoreCase(status)) {
                    System.out.println(">>> [TORNEIO] Condição de vitória atingida! Partida FINALIZADA no chaveamento! <<<");
                    return true; // deve abortar batalha no Robocode
                }
            } else {
                String erro = extrairValorJson(response.body(), "erro");
                if (erro.isEmpty()) erro = response.body();
                System.err.println("[API] Erro ao registrar round #" + roundAtual + " (HTTP " + response.statusCode() + "): " + erro);
            }
        } catch (Exception e) {
            System.err.println("[API] Falha ao enviar resultado do round: " + e.getMessage());
        }
        return false;
    }

    private class TorneioBattleListener extends BattleAdaptor {
        private final RobocodeEngine engine;
        private final int tempoMaxSegundos;
        private int roundAtual = 0;
        private int ultimoContestantVivo = -1;
        private int previousFirstsA = 0;
        private int previousFirstsB = 0;
        private boolean finalizado = false;

        private long inicioBatalhaMs = 0;
        private int ultimoSegundoAvisado = 0;

        public TorneioBattleListener(RobocodeEngine engine, int tempoMaxSegundos) {
            this.engine = engine;
            this.tempoMaxSegundos = tempoMaxSegundos;
            this.inicioBatalhaMs = System.currentTimeMillis();
        }

        @Override
        public void onRoundStarted(RoundStartedEvent event) {
            roundAtual = event.getRound() + 1;
            ultimoContestantVivo = -1;
            int decorrido = (int) ((System.currentTimeMillis() - inicioBatalhaMs) / 1000);
            System.out.printf("\n>>> [ROUND %d / %d] Começou! [⏱️ %02d:%02d decorridos] <<<\n",
                    roundAtual, maxRounds, decorrido / 60, decorrido % 60);
        }

        @Override
        public void onTurnEnded(TurnEndedEvent event) {
            if (finalizado) return;

            // Monitoramento e aviso do cronômetro em tempo real
            long agora = System.currentTimeMillis();
            int decorrido = (int) ((agora - inicioBatalhaMs) / 1000);

            if (decorrido > 0 && decorrido % 15 == 0 && decorrido != ultimoSegundoAvisado) {
                ultimoSegundoAvisado = decorrido;
                int min = decorrido / 60;
                int seg = decorrido % 60;
                int totalMin = tempoMaxSegundos / 60;
                int totalSeg = tempoMaxSegundos % 60;
                System.out.printf("⏱️  [CRONÔMETRO] %02d:%02d / %02d:%02d decorridos da disputa\n",
                        min, seg, totalMin, totalSeg);
            }

            // Checagem de tempo limite (2 minutos)
            if (decorrido >= tempoMaxSegundos) {
                System.out.println("\n⏱️  [TEMPO ESGOTADO] Limite de " + tempoMaxSegundos + "s (2 minutos) atingido!");
                finalizado = true;
                new Thread(() -> {
                    try { Thread.sleep(500); } catch (Exception ignored) {}
                    try { engine.abortCurrentBattle(); } catch (Exception ignored) {}
                    try { Thread.sleep(500); } catch (Exception ignored) {}
                    try { engine.close(); } catch (Exception ignored) {}
                    System.exit(0);
                }).start();
                return;
            }

            ITurnSnapshot snapshot = event.getTurnSnapshot();
            if (snapshot == null) return;

            IRobotSnapshot[] robots = snapshot.getRobots();
            int vivosTimeA = 0;
            int vivosTimeB = 0;

            for (IRobotSnapshot r : robots) {
                if (r.getState() != RobotState.DEAD && r.getEnergy() > 0) {
                    if (r.getContestantIndex() == 0) {
                        vivosTimeA++;
                    } else if (r.getContestantIndex() == 1) {
                        vivosTimeB++;
                    }
                }
            }

            if (vivosTimeA > 0 && vivosTimeB == 0) {
                ultimoContestantVivo = 0; // Time A venceu
            } else if (vivosTimeB > 0 && vivosTimeA == 0) {
                ultimoContestantVivo = 1; // Time B venceu
            }

            IScoreSnapshot[] scores = snapshot.getIndexedTeamScores();
            if (scores != null && scores.length >= 2) {
                int firstsA = scores[0].getTotalFirsts();
                int firstsB = scores[1].getTotalFirsts();
                if (firstsA > previousFirstsA) {
                    ultimoContestantVivo = 0;
                    previousFirstsA = firstsA;
                } else if (firstsB > previousFirstsB) {
                    ultimoContestantVivo = 1;
                    previousFirstsB = firstsB;
                }
            }
        }

        @Override
        public void onRoundEnded(RoundEndedEvent event) {
            if (finalizado) return;

            String vencedor = null;

            if (ultimoContestantVivo == 0) {
                vencedor = "TIME_A";
            } else if (ultimoContestantVivo == 1) {
                vencedor = "TIME_B";
            }

            if (vencedor != null) {
                boolean deveAbortar = enviarVitoriaRound(vencedor, roundAtual);
                if (deveAbortar) {
                    finalizado = true;
                    System.out.println("[ROBOCODE] Interrompendo rounds restantes (campeão definido)...");
                    new Thread(() -> {
                        try { Thread.sleep(500); } catch (Exception ignored) {}
                        try { engine.abortCurrentBattle(); } catch (Exception ignored) {}
                        try { Thread.sleep(500); } catch (Exception ignored) {}
                        try { engine.close(); } catch (Exception ignored) {}
                        System.out.println("\n=================================================");
                        System.out.println("       BATALHA CONCLUÍDA COM SUCESSO!            ");
                        System.out.println("=================================================");
                        System.exit(0);
                    }).start();
                }
            } else {
                System.out.println("[ROUND " + roundAtual + "] Round finalizado sem vencedor claro (empate).");
            }
        }

        @Override
        public void onBattleCompleted(BattleCompletedEvent event) {
            System.out.println("\n=================================================");
            System.out.println("             BATALHA CONCLUÍDA                   ");
            System.out.println("=================================================");
            for (BattleResults res : event.getSortedResults()) {
                System.out.println(" " + res.getRank() + "º Lugar: " + res.getTeamLeaderName()
                        + " | Rounds ganhos: " + res.getFirsts()
                        + " | Pontuação Total: " + res.getScore());
            }
            System.out.println("=================================================\n");
        }
    }

    private static String extrairValorJson(String json, String chave) {
        if (json == null) return "";
        Pattern pattern = Pattern.compile("\"" + chave + "\"\\s*:\\s*\"?([^,\"}]+)\"?");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }
}
