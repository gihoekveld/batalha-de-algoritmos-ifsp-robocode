package torneio.service;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Stream;

public class RobotManager {
    private final File robotsDir;
    private final File teamsDir;
    private final File robosDir;

    public RobotManager() {
        this.robotsDir = new File("robots");
        this.teamsDir = new File(robotsDir, "torneio_teams");
        this.robosDir = new File(teamsDir, "robos");

        if (!teamsDir.exists()) {
            teamsDir.mkdirs();
        }
        if (!robosDir.exists()) {
            robosDir.mkdirs();
        }
    }

    public File getTeamsDir() {
        return teamsDir;
    }

    public File getRobosDir() {
        return robosDir;
    }

    public List<String> descobrirRobos() {
        Set<String> resultado = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Path basePath = robotsDir.toPath();

        // 1. Descobre arquivos de equipe (.team) na pasta robots/torneio_teams
        if (teamsDir.exists() && teamsDir.isDirectory()) {
            try (Stream<Path> stream = Files.list(teamsDir.toPath())) {
                stream.filter(Files::isRegularFile).forEach(path -> {
                    String fileName = path.getFileName().toString();
                    if (fileName.startsWith(".")) return;
                    if (fileName.endsWith(".team")) {
                        Path relative = basePath.relativize(path);
                        String relStr = relative.toString().replace(File.separatorChar, '.');
                        resultado.add(relStr);
                    }
                });
            } catch (Exception e) {
                System.err.println("Erro ao listar times da pasta torneio_teams: " + e.getMessage());
            }
        }

        // 2. Descobre robôs individuais compilados (.class) na pasta robots/torneio_teams/robos
        if (robosDir.exists() && robosDir.isDirectory()) {
            try (Stream<Path> stream = Files.list(robosDir.toPath())) {
                stream.filter(Files::isRegularFile).forEach(path -> {
                    String fileName = path.getFileName().toString();
                    if (fileName.startsWith(".")) return;
                    if (fileName.endsWith(".class") && !fileName.contains("$")) {
                        Path relative = basePath.relativize(path);
                        String relStr = relative.toString().replace(File.separatorChar, '.');
                        String className = relStr.substring(0, relStr.length() - 6);
                        resultado.add(className);
                    }
                });
            } catch (Exception e) {
                System.err.println("Erro ao listar robos da pasta torneio_teams/robos: " + e.getMessage());
            }
        }

        return new ArrayList<>(resultado);
    }

    public List<String> listarApenasRobos() {
        List<String> list = new ArrayList<>();
        for (String r : descobrirRobos()) {
            if (!r.endsWith(".team")) {
                list.add(r);
            }
        }
        return list;
    }

    public List<String> listarApenasTimes() {
        List<String> list = new ArrayList<>();
        for (String r : descobrirRobos()) {
            if (r.endsWith(".team")) {
                list.add(r);
            }
        }
        return list;
    }

    public List<String> obterMembrosDoTime(String teamRelPath) {
        if (teamRelPath == null || teamRelPath.isBlank()) return Collections.emptyList();
        String simple = teamRelPath;
        if (simple.endsWith(".team")) {
            simple = simple.substring(0, simple.length() - 5);
        }
        if (simple.startsWith("torneio_teams.")) {
            simple = simple.substring("torneio_teams.".length());
        }

        File f = new File(teamsDir, simple + ".team");
        if (!f.exists()) return Collections.emptyList();

        try {
            Properties p = new Properties();
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                p.load(in);
            }
            String members = p.getProperty("team.members", "");
            if (members.isBlank()) return Collections.emptyList();

            String[] arr = members.split(",");
            List<String> list = new ArrayList<>();
            for (String m : arr) {
                String clean = m.trim();
                if (clean.startsWith("torneio_teams.robos.")) {
                    clean = clean.substring("torneio_teams.robos.".length());
                } else if (clean.startsWith("torneio_teams.")) {
                    clean = clean.substring("torneio_teams.".length());
                }
                list.add(clean);
            }
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public String sugerirTimeParaNome(String nomeTime, List<String> todosTimes) {
        if (nomeTime == null || nomeTime.isBlank() || todosTimes == null) {
            return null;
        }
        String timeClean = nomeTime.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (timeClean.length() < 2) return null;

        for (String t : todosTimes) {
            String path = t;
            if (path.startsWith("torneio_teams.")) {
                path = path.substring("torneio_teams.".length());
            }
            if (path.endsWith(".team")) {
                path = path.substring(0, path.length() - 5);
            }
            String pathClean = path.toLowerCase().replaceAll("[^a-z0-9]", "");
            if (timeClean.equals(pathClean) || timeClean.startsWith(pathClean) || pathClean.startsWith(timeClean)) {
                return t;
            }
        }
        return null;
    }

    public String criarTimeCustomizado(String nomeTime, List<String> robos) {
        if (robos == null || robos.isEmpty()) return null;
        if (robos.size() == 1) {
            return robos.get(0); // se for apenas 1 robo, usa diretamente
        }

        if (!teamsDir.exists()) teamsDir.mkdirs();

        String nomeSanitizado = nomeTime != null ? nomeTime.replaceAll("[^a-zA-Z0-9_-]", "_") : "equipe";
        if (nomeSanitizado.isBlank()) nomeSanitizado = "equipe";

        File teamFile = new File(teamsDir, nomeSanitizado + ".team");
        String members = String.join(",", robos);

        String conteudo = String.format(
                "# Robocode Team auto-gerado pelo Painel do Torneio\n" +
                "team.members=%s\n" +
                "team.author.name=%s\n" +
                "team.description=Equipe composta de %d robos para o torneio\n" +
                "team.version=1.0\n",
                members, nomeTime != null ? nomeTime : "Torneio", robos.size()
        );

        try (FileOutputStream out = new FileOutputStream(teamFile)) {
            out.write(conteudo.getBytes(StandardCharsets.UTF_8));
            return "torneio_teams." + nomeSanitizado + ".team";
        } catch (Exception e) {
            System.err.println("Erro ao criar arquivo .team para " + nomeTime + ": " + e.getMessage());
            return robos.get(0);
        }
    }

    public boolean salvarTimeManual(String nomeTime, List<String> membros, String autor, String descricao) {
        if (membros == null || membros.isEmpty()) return false;
        if (!teamsDir.exists()) teamsDir.mkdirs();

        String nomeSanitizado = nomeTime != null ? nomeTime.replaceAll("[^a-zA-Z0-9_-]", "_") : "equipe";
        if (nomeSanitizado.isBlank()) nomeSanitizado = "equipe";

        File teamFile = new File(teamsDir, nomeSanitizado + ".team");
        String membersStr = String.join(",", membros);

        String conteudo = String.format(
                "# Robocode Team file criado no painel do torneio\n" +
                "team.members=%s\n" +
                "team.author.name=%s\n" +
                "team.description=%s\n" +
                "team.version=1.0\n",
                membersStr,
                autor != null && !autor.isBlank() ? autor : (nomeTime != null ? nomeTime : "Torneio"),
                descricao != null && !descricao.isBlank() ? descricao : ("Equipe oficial de " + membros.size() + " robôs")
        );

        try (FileOutputStream out = new FileOutputStream(teamFile)) {
            out.write(conteudo.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            System.err.println("Erro ao criar arquivo .team para " + nomeTime + ": " + e.getMessage());
            return false;
        }
    }

    public List<String> sugerirRobosParaTime(String nomeTime, List<String> todosRobos) {
        if (nomeTime == null || nomeTime.isBlank() || todosRobos == null) {
            return Collections.emptyList();
        }

        String timeClean = nomeTime.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (timeClean.length() < 2) return Collections.emptyList();

        List<String> sugestoesClass = new ArrayList<>();
        List<String> sugestoesTeam = new ArrayList<>();

        for (String r : todosRobos) {
            boolean isTeamFile = r.endsWith(".team");
            String path = r;
            if (path.startsWith("torneio_teams.robos.")) {
                path = path.substring("torneio_teams.robos.".length());
            } else if (path.startsWith("torneio_teams.")) {
                path = path.substring("torneio_teams.".length());
            }

            if (isTeamFile && path.endsWith(".team")) {
                path = path.substring(0, path.length() - 5);
            }

            // Ex: "TeamAlpha_Striker" -> prefix = "TeamAlpha"
            String prefix = (!isTeamFile && path.contains("_")) ? path.substring(0, path.indexOf('_')) : path;
            String prefixClean = prefix.toLowerCase().replaceAll("[^a-z0-9]", "");
            String pathClean = path.toLowerCase().replaceAll("[^a-z0-9]", "");

            boolean match = false;
            if (!prefixClean.isEmpty() && (timeClean.startsWith(prefixClean) || prefixClean.startsWith(timeClean) || timeClean.equals(prefixClean))) {
                match = true;
            } else if (timeClean.equals(pathClean) || pathClean.startsWith(timeClean) || timeClean.startsWith(pathClean)) {
                match = true;
            }

            if (match) {
                if (isTeamFile) {
                    sugestoesTeam.add(r);
                } else {
                    sugestoesClass.add(r);
                }
            }
        }

        // Se encontrou robôs (.class), sugere os robôs individuais para a equipe;
        // Caso contrário, se encontrou um arquivo .team pré-definido, sugere o .team
        if (!sugestoesClass.isEmpty()) {
            return sugestoesClass;
        }
        return sugestoesTeam;
    }

    public boolean precisaRecompilar() {
        if (!robosDir.exists()) return false;
        try (Stream<Path> stream = Files.list(robosDir.toPath())) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java") && !p.getFileName().toString().startsWith("."))
                    .anyMatch(p -> {
                        File javaFile = p.toFile();
                        File classFile = new File(javaFile.getParentFile(), javaFile.getName().replace(".java", ".class"));
                        return !classFile.exists() || javaFile.lastModified() > classFile.lastModified();
                    });
        } catch (Exception e) {
            return false;
        }
    }

    public void fazerBackupVersao(String motivo) {
        try {
            // Salva na raiz do projeto 'backup_versoes' para nao poluir o classpath do Robocode e javac
            File versoesDir = new File("backup_versoes");
            if (!versoesDir.exists()) versoesDir.mkdirs();

            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String timestamp = sdf.format(new Date());

            if (robosDir.exists()) {
                try (Stream<Path> stream = Files.list(robosDir.toPath())) {
                    stream.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().endsWith(".java") && !p.getFileName().toString().startsWith("."))
                            .forEach(p -> {
                                try {
                                    String baseName = p.getFileName().toString().replace(".java", "");
                                    String tag = motivo != null ? "_" + motivo.replaceAll("[^a-zA-Z0-9_-]", "") : "";
                                    File dest = new File(versoesDir, baseName + "_" + timestamp + tag + ".java");
                                    Files.copy(p, dest.toPath());
                                } catch (Exception ignored) {}
                            });
                }
            }
        } catch (Exception ignored) {}
    }

    public CompilacaoResult compilarTodosOsRobos() {
        return compilarTodosOsRobos(null);
    }

    public CompilacaoResult compilarTodosOsRobos(String contextoBackup) {
        CompilacaoResult res = new CompilacaoResult();
        if (!robosDir.exists()) {
            res.sucesso = true;
            res.mensagem = "Pasta 'robots/torneio_teams/robos' não encontrada.";
            return res;
        }

        fazerBackupVersao(contextoBackup);

        List<String> javaFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.list(robosDir.toPath())) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.getFileName().toString().endsWith(".java") && !p.getFileName().toString().startsWith("."))
                  .forEach(p -> javaFiles.add(p.toAbsolutePath().toString()));
        } catch (Exception e) {
            res.sucesso = false;
            res.mensagem = "Erro ao localizar arquivos .java em 'robots/torneio_teams/robos': " + e.getMessage();
            return res;
        }

        if (javaFiles.isEmpty()) {
            res.sucesso = true;
            res.mensagem = "Nenhum arquivo .java encontrado na pasta 'robots/torneio_teams/robos' para compilar.";
            return res;
        }

        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("javac");
            cmd.add("-encoding");
            cmd.add("UTF-8");
            cmd.add("-cp");
            cmd.add("libs/*:robots:.");
            cmd.addAll(javaFiles);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File("."));
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = proc.waitFor();

            if (exitCode == 0) {
                res.sucesso = true;
                res.mensagem = "Sucesso! " + javaFiles.size() + " robô(s) compilado(s) com sucesso em 'robots/torneio_teams/robos'.";
                res.detalhes = output;
            } else {
                res.sucesso = false;
                res.mensagem = "Falha na compilação dos robôs (código " + exitCode + ").";
                res.detalhes = output;
            }
        } catch (Exception e) {
            res.sucesso = false;
            res.mensagem = "Erro ao executar compilador: " + e.getMessage();
        }

        return res;
    }

    public static class CompilacaoResult {
        public boolean sucesso;
        public String mensagem = "";
        public String detalhes = "";
    }

    public static class SampleRobotItem {
        public final String className;
        public final String titulo;
        public final String estilo;
        public final String descricao;

        public SampleRobotItem(String className, String titulo, String estilo, String descricao) {
            this.className = className;
            this.titulo = titulo;
            this.estilo = estilo;
            this.descricao = descricao;
        }

        @Override
        public String toString() {
            return titulo + " (" + className + ")";
        }
    }

    public List<SampleRobotItem> listarRobosSampleDetalhados() {
        List<SampleRobotItem> lista = new ArrayList<>();

        // Interactive destacado no topo como solicitado
        lista.add(new SampleRobotItem(
                "sample.Interactive",
                "sample.Interactive",
                "Controle Manual (Teclado)",
                "Controle você mesmo com as setas do teclado para testar a mira e esquiva do seu robô!"
        ));
        lista.add(new SampleRobotItem(
                "sample.Interactive_v2",
                "sample.Interactive_v2",
                "Controle Manual v2 (Teclado/Mouse)",
                "Controle manual aprimorado com movimentação e mira direcionais."
        ));
        lista.add(new SampleRobotItem(
                "sample.SittingDuck",
                "sample.SittingDuck",
                "Alvo Estático / Imóvel",
                "Robô que não se move. Perfeito para calibrar precisão de disparos e radar."
        ));
        lista.add(new SampleRobotItem(
                "sample.Target",
                "sample.Target",
                "Alvo em Linha Reta",
                "Alvo que se move em trajetória linear para treinar antecipação de tiro."
        ));
        lista.add(new SampleRobotItem(
                "sample.Corners",
                "sample.Corners",
                "Estratégia de Cantos",
                "Navega rapidamente para um dos quatro cantos e atira protegido nas paredes."
        ));
        lista.add(new SampleRobotItem(
                "sample.Walls",
                "sample.Walls",
                "Patrulheiro de Paredes",
                "Circula colado às quatro paredes do campo de batalha continuamente."
        ));
        lista.add(new SampleRobotItem(
                "sample.RamFire",
                "sample.RamFire",
                "Agressor de Abalroamento",
                "Avança velozmente para colidir de frente com o oponente e atirar à queima-roupa."
        ));
        lista.add(new SampleRobotItem(
                "sample.SpinBot",
                "sample.SpinBot",
                "Giro Circular Constante",
                "Gira em círculos disparando em arco contínuo para saturar o espaço."
        ));
        lista.add(new SampleRobotItem(
                "sample.Crazy",
                "sample.Crazy",
                "Movimento Caótico",
                "Movimenta-se de maneira errática e imprevisível para dificultar a mira."
        ));
        lista.add(new SampleRobotItem(
                "sample.Tracker",
                "sample.Tracker",
                "Perseguidor de Alvo",
                "Mantém radar travado no inimigo e persegue mantendo distância fixa de ataque."
        ));
        lista.add(new SampleRobotItem(
                "sample.TrackFire",
                "sample.TrackFire",
                "Torre Estática de Precisão",
                "Permanece estacionado e ajusta o canhão com mira contínua precisa."
        ));
        lista.add(new SampleRobotItem(
                "sample.Fire",
                "sample.Fire",
                "Torre Básica de Disparo",
                "Atira imediatamente na direção em que o radar detecta o adversário."
        ));
        lista.add(new SampleRobotItem(
                "sampleex.Alien",
                "sampleex.Alien",
                "Robô Alienígena",
                "Robô com comportamento de esquiva inteligente e disparos calculados."
        ));

        // Descobre outros samples em robots/sample se existirem
        File sampleDir = new File(robotsDir, "sample");
        if (sampleDir.exists() && sampleDir.isDirectory()) {
            File[] files = sampleDir.listFiles((dir, name) -> name.endsWith(".class") && !name.contains("$"));
            if (files != null) {
                Set<String> jaAdicionados = new HashSet<>();
                for (SampleRobotItem item : lista) {
                    jaAdicionados.add(item.className.toLowerCase());
                }
                for (File f : files) {
                    String base = f.getName().replace(".class", "");
                    String fullClass = "sample." + base;
                    if (!jaAdicionados.contains(fullClass.toLowerCase())) {
                        lista.add(new SampleRobotItem(fullClass, fullClass, "Sample Robocode", "Robô de demonstração nativo do Robocode"));
                    }
                }
            }
        }

        return lista;
    }
}
