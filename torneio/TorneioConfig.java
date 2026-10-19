package torneio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public class TorneioConfig {
    private static final String CONFIG_FILE = "torneio.properties";

    public String apiBaseUrl = "http://localhost:8080";
    public String email = "admin@admin.com";
    public String senha = "admin123";
    public String token = null;

    public Long torneioId = 1L;
    public int maxRounds = 3;
    public int vitoriasParaGanhar = 2;
    public boolean finalizarComDuasSeguidas = true;
    public boolean disputaRoundARound = true; // Executa 1 round por vez com intervalo para ajuste de código
    public int duracaoSegundos = 120; // 2 minutos padrão
    public boolean semLimiteTempo = false; // Se true, batalha roda até o fim dos rounds sem interrupção por timer
    public int tps = 30;              // 30 TPS para animação fluida
    public boolean exibirJanela = true;
    public int campoLargura = 800;    // Largura do campo de batalha
    public int campoAltura = 600;     // Altura do campo de batalha

    public void carregar() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            salvar();
            return;
        }

        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            p.load(in);
            if (p.containsKey("api.url")) apiBaseUrl = p.getProperty("api.url").trim();
            if (p.containsKey("api.email")) email = p.getProperty("api.email").trim();
            if (p.containsKey("api.senha")) senha = p.getProperty("api.senha").trim();
            if (p.containsKey("api.token")) token = p.getProperty("api.token").trim();
            if (p.containsKey("torneio.id")) torneioId = Long.parseLong(p.getProperty("torneio.id").trim());
            if (p.containsKey("partida.maxRounds")) maxRounds = Integer.parseInt(p.getProperty("partida.maxRounds").trim());
            if (p.containsKey("partida.vitoriasParaGanhar")) vitoriasParaGanhar = Integer.parseInt(p.getProperty("partida.vitoriasParaGanhar").trim());
            if (p.containsKey("partida.duasSeguidas")) finalizarComDuasSeguidas = Boolean.parseBoolean(p.getProperty("partida.duasSeguidas").trim());
            if (p.containsKey("partida.disputaRoundARound")) disputaRoundARound = Boolean.parseBoolean(p.getProperty("partida.disputaRoundARound").trim());
            if (p.containsKey("partida.duracaoSegundos")) duracaoSegundos = Integer.parseInt(p.getProperty("partida.duracaoSegundos").trim());
            if (p.containsKey("partida.semLimiteTempo")) semLimiteTempo = Boolean.parseBoolean(p.getProperty("partida.semLimiteTempo").trim());
            if (p.containsKey("robocode.tps")) tps = Integer.parseInt(p.getProperty("robocode.tps").trim());
            if (p.containsKey("robocode.exibirJanela")) exibirJanela = Boolean.parseBoolean(p.getProperty("robocode.exibirJanela").trim());
            if (p.containsKey("robocode.campoLargura")) campoLargura = Integer.parseInt(p.getProperty("robocode.campoLargura").trim());
            if (p.containsKey("robocode.campoAltura")) campoAltura = Integer.parseInt(p.getProperty("robocode.campoAltura").trim());
        } catch (Exception e) {
            System.err.println("Erro ao ler " + CONFIG_FILE + ": " + e.getMessage());
        }
    }

    public void salvar() {
        Properties p = new Properties();
        p.setProperty("api.url", apiBaseUrl != null ? apiBaseUrl : "http://localhost:8080");
        p.setProperty("api.email", email != null ? email : "admin@admin.com");
        p.setProperty("api.senha", senha != null ? senha : "admin123");
        p.setProperty("api.token", token != null ? token : "");
        p.setProperty("torneio.id", String.valueOf(torneioId != null ? torneioId : 1L));
        p.setProperty("partida.maxRounds", String.valueOf(maxRounds));
        p.setProperty("partida.vitoriasParaGanhar", String.valueOf(vitoriasParaGanhar));
        p.setProperty("partida.duasSeguidas", String.valueOf(finalizarComDuasSeguidas));
        p.setProperty("partida.disputaRoundARound", String.valueOf(disputaRoundARound));
        p.setProperty("partida.duracaoSegundos", String.valueOf(duracaoSegundos));
        p.setProperty("partida.semLimiteTempo", String.valueOf(semLimiteTempo));
        p.setProperty("robocode.tps", String.valueOf(tps));
        p.setProperty("robocode.exibirJanela", String.valueOf(exibirJanela));
        p.setProperty("robocode.campoLargura", String.valueOf(campoLargura));
        p.setProperty("robocode.campoAltura", String.valueOf(campoAltura));

        try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
            p.store(out, "Configuracoes do Robocode Torneio Launcher");
        } catch (Exception e) {
            System.err.println("Erro ao salvar " + CONFIG_FILE + ": " + e.getMessage());
        }
    }
}
