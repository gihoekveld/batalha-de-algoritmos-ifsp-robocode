package torneio.service;

import torneio.TorneioConfig;
import torneio.model.Partida;
import torneio.model.Time;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiClient {
    private final TorneioConfig config;
    private final HttpClient httpClient;

    public ApiClient(TorneioConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean autenticar(String email, String senha) {
        try {
            String json = String.format("{\"email\":\"%s\",\"senha\":\"%s\"}", email, senha);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.apiBaseUrl + "/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String token = extrairValor(response.body(), "token");
                if (!token.isEmpty()) {
                    config.token = token;
                    config.email = email;
                    config.senha = senha;
                    config.salvar();
                    return true;
                }
            }
        } catch (Exception e) {
            System.err.println("Erro na autenticacao: " + e.getMessage());
        }
        return false;
    }

    public Map<Long, Time> carregarTimes() {
        Map<Long, Time> mapa = new LinkedHashMap<>();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(config.apiBaseUrl + "/api/times"))
                    .GET();
            if (config.token != null && !config.token.isEmpty()) {
                builder.header("Authorization", "Bearer " + config.token);
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                // Parse cada objeto {} no array
                Pattern p = Pattern.compile("\\{([^{}]+)\\}");
                Matcher m = p.matcher(response.body());
                while (m.find()) {
                    String obj = m.group(1);
                    String idStr = extrairValor(obj, "id");
                    String nome = extrairValor(obj, "nome");
                    String tipo = extrairValor(obj, "tipo");
                    String status = extrairValor(obj, "status");
                    if (!idStr.isEmpty()) {
                        Time t = new Time();
                        t.id = Long.parseLong(idStr);
                        t.nome = nome.isEmpty() ? "Time #" + t.id : nome;
                        t.tipo = tipo;
                        t.status = status;
                        mapa.put(t.id, t);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao carregar times: " + e.getMessage());
        }
        return mapa;
    }

    public List<Partida> carregarPartidas(Long torneioId, Map<Long, Time> times) {
        List<Partida> lista = new ArrayList<>();
        try {
            long tId = torneioId != null ? torneioId : 1L;
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(config.apiBaseUrl + "/torneios/" + tId + "/chaveamento"))
                    .GET();
            if (config.token != null && !config.token.isEmpty()) {
                builder.header("Authorization", "Bearer " + config.token);
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Pattern p = Pattern.compile("\\{([^{}]+)\\}");
                Matcher m = p.matcher(response.body());
                while (m.find()) {
                    String obj = m.group(1);
                    String idStr = extrairValor(obj, "id");
                    if (!idStr.isEmpty()) {
                        Partida part = new Partida();
                        part.id = Long.parseLong(idStr);
                        part.fase = extrairValor(obj, "fase");
                        part.status = extrairValor(obj, "status");

                        String timeAIdStr = extrairValor(obj, "timeAId");
                        if (!timeAIdStr.isEmpty() && !timeAIdStr.equals("null")) {
                            part.timeAId = Long.parseLong(timeAIdStr);
                            if (times.containsKey(part.timeAId)) {
                                part.nomeTimeA = times.get(part.timeAId).nome;
                            }
                        }

                        String timeBIdStr = extrairValor(obj, "timeBId");
                        if (!timeBIdStr.isEmpty() && !timeBIdStr.equals("null")) {
                            part.timeBId = Long.parseLong(timeBIdStr);
                            if (times.containsKey(part.timeBId)) {
                                part.nomeTimeB = times.get(part.timeBId).nome;
                            }
                        }

                        String placarAStr = extrairValor(obj, "placarTimeA");
                        if (!placarAStr.isEmpty() && !placarAStr.equals("null")) {
                            part.placarTimeA = Integer.parseInt(placarAStr);
                        }

                        String placarBStr = extrairValor(obj, "placarTimeB");
                        if (!placarBStr.isEmpty() && !placarBStr.equals("null")) {
                            part.placarTimeB = Integer.parseInt(placarBStr);
                        }

                        lista.add(part);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao carregar partidas: " + e.getMessage());
        }
        return lista;
    }

    public boolean iniciarPartida(Long partidaId) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(config.apiBaseUrl + "/partidas/" + partidaId + "/iniciar"))
                    .POST(HttpRequest.BodyPublishers.noBody());
            if (config.token != null) builder.header("Authorization", "Bearer " + config.token);

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 || response.statusCode() == 204;
        } catch (Exception e) {
            System.err.println("Erro ao iniciar partida #" + partidaId + ": " + e.getMessage());
            return false;
        }
    }

    public RoundResult registrarRound(Long partidaId, String vencedorRound, int vitoriasParaGanhar, int maxRounds, boolean duasSeguidas) {
        RoundResult result = new RoundResult();
        try {
            String json = String.format(
                    "{\"vencedorRound\":\"%s\",\"vitoriasParaGanhar\":%d,\"maxRounds\":%d,\"finalizarComDuasSeguidas\":%b}",
                    vencedorRound, vitoriasParaGanhar, maxRounds, duasSeguidas
            );

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(config.apiBaseUrl + "/partidas/" + partidaId + "/rounds"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            if (config.token != null) builder.header("Authorization", "Bearer " + config.token);

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            result.statusCode = response.statusCode();
            result.responseBody = response.body();

            if (response.statusCode() == 200) {
                result.sucesso = true;
                result.placarA = extrairValor(response.body(), "placarTimeA");
                result.placarB = extrairValor(response.body(), "placarTimeB");
                result.status = extrairValor(response.body(), "status");
                if ("FINALIZADA".equalsIgnoreCase(result.status)) {
                    result.partidaFinalizada = true;
                }
            } else {
                result.sucesso = false;
                result.erro = extrairValor(response.body(), "erro");
                if (result.erro.isEmpty()) result.erro = response.body();
            }
        } catch (Exception e) {
            result.sucesso = false;
            result.erro = e.getMessage();
        }
        return result;
    }

    public boolean resetarPartida(Long partidaId) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(config.apiBaseUrl + "/partidas/" + partidaId + "/resetar"))
                    .POST(HttpRequest.BodyPublishers.noBody());
            if (config.token != null) builder.header("Authorization", "Bearer " + config.token);

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            System.err.println("Erro ao resetar partida: " + e.getMessage());
            return false;
        }
    }

    public boolean registrarResultado(Long partidaId, int placarA, int placarB) {
        return registrarResultado(partidaId, placarA, placarB, null);
    }

    public boolean registrarResultado(Long partidaId, int placarA, int placarB, String observacao) {
        try {
            String json;
            if (observacao != null && !observacao.isBlank()) {
                String obsEscapada = observacao.replace("\"", "\\\"").replace("\n", " ");
                json = String.format("{\"placarTimeA\":%d,\"placarTimeB\":%d,\"observacao\":\"%s\"}",
                        placarA, placarB, obsEscapada);
            } else {
                json = String.format("{\"placarTimeA\":%d,\"placarTimeB\":%d}", placarA, placarB);
            }

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(config.apiBaseUrl + "/partidas/" + partidaId + "/resultado"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            if (config.token != null) builder.header("Authorization", "Bearer " + config.token);

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 || response.statusCode() == 204;
        } catch (Exception e) {
            System.err.println("Erro ao registrar resultado final da partida: " + e.getMessage());
            return false;
        }
    }

    public static class RoundResult {
        public boolean sucesso;
        public int statusCode;
        public String placarA = "0";
        public String placarB = "0";
        public String status = "";
        public boolean partidaFinalizada = false;
        public String erro = "";
        public String responseBody = "";
    }

    private static String extrairValor(String json, String chave) {
        if (json == null) return "";
        Pattern pattern = Pattern.compile("\"" + chave + "\"\\s*:\\s*\"?([^,\"}]+)\"?");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }
}
