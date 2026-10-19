package torneio.service;

import robocode.BattleResults;
import robocode.control.*;
import robocode.control.events.*;
import robocode.control.snapshot.IRobotSnapshot;
import robocode.control.snapshot.IScoreSnapshot;
import robocode.control.snapshot.ITurnSnapshot;
import robocode.control.snapshot.RobotState;
import torneio.model.Partida;
import torneio.model.ResultadoBatalha;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class BattleRunner {
    private final Partida partida;
    private final String specA;
    private final String specB;
    private final int duracaoSegundos;
    private final int tps;
    private final int maxRounds;
    private final int vitoriasParaGanhar;
    private final boolean duasSeguidas;
    private final boolean disputaRoundARound;
    private final boolean exibirJanela;
    private final ApiClient apiClient;
    private final Consumer<String> logger;
    private final Consumer<ResultadoBatalha> onResultado;
    private final Runnable onFinalizado;

    private final int campoLargura;
    private final int campoAltura;

    private RobocodeEngine engine;
    private final AtomicBoolean executando = new AtomicBoolean(false);
    private final AtomicBoolean abortado = new AtomicBoolean(false);

    public BattleRunner(
            Partida partida,
            String specA,
            String specB,
            int duracaoSegundos,
            int tps,
            int maxRounds,
            int vitoriasParaGanhar,
            boolean duasSeguidas,
            boolean disputaRoundARound,
            boolean exibirJanela,
            int campoLargura,
            int campoAltura,
            ApiClient apiClient,
            Consumer<String> logger,
            Consumer<ResultadoBatalha> onResultado,
            Runnable onFinalizado
    ) {
        this.partida = partida;
        this.specA = specA;
        this.specB = specB;
        this.duracaoSegundos = duracaoSegundos;
        this.tps = tps;
        this.maxRounds = maxRounds;
        this.vitoriasParaGanhar = vitoriasParaGanhar;
        this.duasSeguidas = duasSeguidas;
        this.disputaRoundARound = disputaRoundARound;
        this.exibirJanela = exibirJanela;
        this.campoLargura = campoLargura > 0 ? campoLargura : 800;
        this.campoAltura = campoAltura > 0 ? campoAltura : 600;
        this.apiClient = apiClient;
        this.logger = logger != null ? logger : System.out::println;
        this.onResultado = onResultado;
        this.onFinalizado = onFinalizado;
    }

    public BattleRunner(
            Partida partida,
            String specA,
            String specB,
            int duracaoSegundos,
            int tps,
            int maxRounds,
            int vitoriasParaGanhar,
            boolean duasSeguidas,
            boolean disputaRoundARound,
            boolean exibirJanela,
            ApiClient apiClient,
            Consumer<String> logger,
            Consumer<ResultadoBatalha> onResultado,
            Runnable onFinalizado
    ) {
        this(partida, specA, specB, duracaoSegundos, tps, maxRounds, vitoriasParaGanhar, duasSeguidas, disputaRoundARound, exibirJanela, 800, 600, apiClient, logger, onResultado, onFinalizado);
    }

    public void iniciarBatalhaAssincrona() {
        new Thread(this::executar, "Robocode-Battle-Thread").start();
    }

    public boolean isExecutando() {
        return executando.get() && !abortado.get();
    }

    public void abortar() {
        if (!abortado.compareAndSet(false, true)) {
            return;
        }
        executando.set(false);
        logger.accept("[Abortar] Encerrando arena do Robocode...");
        if (engine != null) {
            try {
                engine.abortCurrentBattle();
            } catch (Throwable ignored) {}
        }
        fecharArenaRobocode();
    }

    public void fecharArenaRobocode() {
        try {
            if (engine != null) {
                engine.setVisible(false);
                engine.close();
            }
        } catch (Throwable ignored) {}
        engine = null;

        Runnable fecharJanelas = () -> {
            try {
                for (java.awt.Window w : java.awt.Window.getWindows()) {
                    if (w != null && w.isDisplayable()) {
                        String className = w.getClass().getName();
                        if (className.startsWith("torneio.")) {
                            continue;
                        }
                        if (className.startsWith("net.sf.robocode") ||
                            className.startsWith("robocode.") ||
                            className.contains("Robocode")) {
                            w.setVisible(false);
                            w.dispose();
                        }
                    }
                }
            } catch (Throwable ignored) {}
        };

        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            fecharJanelas.run();
        } else {
            try {
                javax.swing.SwingUtilities.invokeAndWait(fecharJanelas);
            } catch (Throwable t) {
                javax.swing.SwingUtilities.invokeLater(fecharJanelas);
            }
        }
    }

    private void executar() {
        executando.set(true);
        try {
            // Breve intervalo para garantir destruição completa de peers de janelas do Robocode anterior
            try {
                Thread.sleep(400);
            } catch (InterruptedException ignored) {}

            boolean isTeste = (partida == null || partida.id == null);
            String nomeA = (partida != null && partida.nomeTimeA != null) ? partida.nomeTimeA : specA;
            String nomeB = (partida != null && partida.nomeTimeB != null) ? partida.nomeTimeB : specB;

            logger.accept("=================================================");
            if (isTeste) {
                logger.accept(String.format("[ARENA DE TESTES] Batalha de Treino: %s vs %s", nomeA, nomeB));
            } else {
                logger.accept(String.format("[Partida #%d] Iniciando: %s vs %s",
                        partida.id, partida.nomeTimeA, partida.nomeTimeB));
            }
            logger.accept(String.format("Competidor A: %s", specA));
            logger.accept(String.format("Competidor B: %s", specB));
            String duracaoTxt = duracaoSegundos > 0 ? (duracaoSegundos + "s") : "Sem limite (até o fim)";
            logger.accept(String.format("Tempo: %s | Velocidade: %d TPS | Rounds: %d",
                    duracaoTxt, tps, maxRounds));
            logger.accept("=================================================");

            // 1. Avisa a API de chaveamento que a partida iniciou (apenas partidas oficiais)
            if (partida != null && partida.id != null && apiClient != null) {
                boolean iniOk = apiClient.iniciarPartida(partida.id);
                if (iniOk) {
                    logger.accept("[OK] Status da partida atualizado no Chaveamento: EM_ANDAMENTO");
                } else {
                    logger.accept("[Aviso] Não foi possível atualizar status para EM_ANDAMENTO no servidor.");
                }
            }

            // 2. Configura opções do Robocode
            System.setProperty("robocode.options.common.showResults", "false");
            System.setProperty("robocode.options.battle.desiredTPS", String.valueOf(tps));

            // 3. Inicializa o RobocodeEngine
            File robocodeHome = new File(System.getProperty("user.dir"));
            engine = new RobocodeEngine(robocodeHome);
            engine.setVisible(exibirJanela);

            // 3.5 Invalida cache do repositório para carregar novo bytecode e desativa popup de resultados
            try {
                Class<?> containerClass = Class.forName("net.sf.robocode.core.Container");
                Class<?> repoClass = Class.forName("net.sf.robocode.repository.IRepositoryManager");
                Object repo = containerClass.getMethod("getComponent", Class.class).invoke(null, repoClass);
                if (repo != null) {
                    repoClass.getMethod("refresh", boolean.class).invoke(repo, true);
                }
                Class<?> settingsClass = Class.forName("net.sf.robocode.settings.ISettingsManager");
                Object settings = containerClass.getMethod("getComponent", Class.class).invoke(null, settingsClass);
                if (settings != null) {
                    settingsClass.getMethod("setOptionsCommonShowResults", boolean.class).invoke(settings, false);
                }
            } catch (Throwable ignored) {}

            // 4. Carrega robôs
            String resolvedA = resolverNome(engine, specA);
            String resolvedB = resolverNome(engine, specB);
            logger.accept("[Robocode] Localizando competidores: " + resolvedA + " vs " + resolvedB);

            RobotSpecification[] selectedRobots = engine.getLocalRepository(resolvedA + "," + resolvedB);
            if (selectedRobots == null || selectedRobots.length < 2) {
                logger.accept("[ERRO] Robôs ou equipes não localizados no repositório do Robocode.");
                logger.accept("   Verifique se as classes estão compiladas na pasta 'robots'.");
                fecharArenaRobocode();
                return;
            }

            // 5. Adiciona Listener da batalha
            TorneioBattleListener listener = new TorneioBattleListener();
            engine.addBattleListener(listener);

            // 6. Define especificação da batalha
            int inactivityTime = 450;
            double gunCoolingRate = 0.1;
            BattlefieldSpecification battlefield = new BattlefieldSpecification(campoLargura, campoAltura);
            int roundsParaExecutar = disputaRoundARound ? 1 : maxRounds;
            BattleSpecification battleSpec = new BattleSpecification(roundsParaExecutar, inactivityTime, gunCoolingRate, battlefield, selectedRobots);

            String modoTxt = disputaRoundARound ? "Disputa Round a Round (1 round com intervalo técnico)" : ("Série contínua (" + maxRounds + " rounds)");
            logger.accept("[Arena] Arena aberta! Modo: " + modoTxt);
            engine.runBattle(battleSpec, true); // aguarda encerramento
            logger.accept("[Fim] Batalha encerrada no Robocode!");
        } catch (Throwable e) {
            logger.accept("[Erro] Erro durante a execução da batalha: " + e.getMessage());
            e.printStackTrace();
        } finally {
            fecharArenaRobocode();
            executando.set(false);
            if (onFinalizado != null) {
                onFinalizado.run();
            }
        }
    }

    private class TorneioBattleListener extends BattleAdaptor {
        private int roundAtual = 0;
        private int ultimoContestantVivo = -1;
        private int previousFirstsA = 0;
        private int previousFirstsB = 0;
        private boolean finalizado = false;
        private long inicioBatalhaMs = 0;
        private int ultimoSegundoAvisado = 0;

        private int roundsGanhosA = 0;
        private int roundsGanhosB = 0;
        private double lastHpA = 0;
        private double lastHpB = 0;
        private double lastDanoA = 0;
        private double lastDanoB = 0;
        private double lastScoreA = 0;
        private double lastScoreB = 0;
        private final AtomicBoolean resultadoEntregue = new AtomicBoolean(false);

        private boolean emProrrogacao = false;
        private int fimProrrogacaoSegundos = 0;
        private boolean emMorteSubita = false;

        @Override
        public void onBattleStarted(BattleStartedEvent event) {
            inicioBatalhaMs = System.currentTimeMillis();
            logger.accept("[Simulador] Batalha iniciada no simulador!");
        }

        @Override
        public void onRoundStarted(RoundStartedEvent event) {
            roundAtual = event.getRound() + 1;
            ultimoContestantVivo = -1;
            int decorrido = (int) ((System.currentTimeMillis() - inicioBatalhaMs) / 1000);
            logger.accept(String.format("[ROUND %d / %d] Iniciado! (%02d:%02d decorridos)",
                    roundAtual, maxRounds, decorrido / 60, decorrido % 60));
        }

        @Override
        public void onTurnEnded(TurnEndedEvent event) {
            if (finalizado || abortado.get()) return;

            ITurnSnapshot snapshot = event.getTurnSnapshot();
            if (snapshot == null) return;

            // Monitora vida (HP) dos robôs vivos em cada equipe
            double hpA = 0;
            double hpB = 0;
            int vivosTimeA = 0;
            int vivosTimeB = 0;

            IRobotSnapshot[] robots = snapshot.getRobots();
            if (robots != null) {
                for (IRobotSnapshot r : robots) {
                    if (r.getState() != RobotState.DEAD && r.getEnergy() > 0) {
                        if (r.getContestantIndex() == 0) {
                            vivosTimeA++;
                            hpA += r.getEnergy();
                        } else if (r.getContestantIndex() == 1) {
                            vivosTimeB++;
                            hpB += r.getEnergy();
                        }
                    }
                }
            }
            lastHpA = hpA;
            lastHpB = hpB;

            // Monitora pontuações e dano acumulado
            IScoreSnapshot[] scores = snapshot.getIndexedTeamScores();
            if (scores != null && scores.length >= 2) {
                lastDanoA = scores[0].getTotalBulletDamageScore() + scores[0].getTotalRammingDamageScore();
                lastDanoB = scores[1].getTotalBulletDamageScore() + scores[1].getTotalRammingDamageScore();
                lastScoreA = scores[0].getTotalScore();
                lastScoreB = scores[1].getTotalScore();
            }

            if (vivosTimeA > 0 && vivosTimeB == 0) {
                ultimoContestantVivo = 0;
            } else if (vivosTimeB > 0 && vivosTimeA == 0) {
                ultimoContestantVivo = 1;
            }

            // Confirmação complementar por vitórias no snapshot
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

            // Checagem de cronômetro (somente se duracaoSegundos > 0)
            long agora = System.currentTimeMillis();
            int decorrido = (int) ((agora - inicioBatalhaMs) / 1000);

            if (duracaoSegundos > 0) {
                if (decorrido > 0 && decorrido % 20 == 0 && decorrido != ultimoSegundoAvisado) {
                    ultimoSegundoAvisado = decorrido;
                    String statusFase = emMorteSubita ? "MORTE SÚBITA" : (emProrrogacao ? "PRORROGAÇÃO" : "REGULAMENTAR");
                    String rotA = (partida != null && partida.nomeTimeA != null) ? partida.nomeTimeA : specA;
                    String rotB = (partida != null && partida.nomeTimeB != null) ? partida.nomeTimeB : specB;
                    logger.accept(String.format("[%s] %02d:%02d decorridos (HP: %s=%.1f | %s=%.1f)",
                            statusFase, decorrido / 60, decorrido % 60,
                            rotA, lastHpA, rotB, lastHpB));
                }

                // 1. Se estiver em MORTE SÚBITA: o primeiro dano sofrido por qualquer equipe define o vencedor!
                if (emMorteSubita) {
                    double diff = lastHpA - lastHpB;
                    if (Math.abs(diff) >= 0.2) {
                        finalizado = true;
                        tratarVitoriaMorteSubita(decorrido, diff > 0);
                        return;
                    }
                }

                // 2. Se o tempo regulamentar estiver ativo e não estiver em morte súbita
                if (!emMorteSubita) {
                    if (!emProrrogacao) {
                        if (decorrido >= duracaoSegundos) {
                            if (roundsGanhosA != roundsGanhosB) {
                                // Placar diferente: encerra normalmente no tempo regulamentar
                                finalizado = true;
                                tratarTempoEsgotado(decorrido);
                            } else {
                                // EMPATE: Entra na Prorrogação (+1 minuto de tolerância acelerada)
                                emProrrogacao = true;
                                fimProrrogacaoSegundos = decorrido + 60; // +1 minuto de tolerância
                                int tpsAcelerado = Math.min(90, Math.max(50, (int) (tps * 1.6)));
                                ajustarVelocidade(tpsAcelerado);

                                logger.accept("=================================================");
                                logger.accept(String.format("[PRORROGAÇÃO ATIVADA] Tempo regulamentar esgotado em EMPATE (%d x %d)!",
                                        roundsGanhosA, roundsGanhosB));
                                logger.accept(String.format("+1 MINUTO (60s) de tolerância concedido em velocidade acelerada (%d TPS)!",
                                        tpsAcelerado));
                                logger.accept("As equipes têm 1 minuto extra para buscar o K.O.!");
                                logger.accept("=================================================");
                            }
                        }
                    } else {
                        // Já está em PRORROGAÇÃO!
                        if (decorrido >= fimProrrogacaoSegundos) {
                            double diffHp = lastHpA - lastHpB;
                            if (Math.abs(diffHp) >= 0.5) {
                                // Desempate por HP na Prorrogação
                                finalizado = true;
                                tratarVitoriaProrrogacaoHp(decorrido, diffHp > 0);
                            } else {
                                // HPs continuam rigorosamente empatados: entra em MORTE SÚBITA!
                                emMorteSubita = true;
                                logger.accept("=================================================");
                                logger.accept(String.format("[MORTE SÚBITA ATIVADA] Prorrogação esgotou com HPs rigorosamente iguais (%.1f HP x %.1f HP)!",
                                        lastHpA, lastHpB));
                                logger.accept("A partida continuará rolando: o primeiro time que sofrer dano (perder HP) perde imediatamente!");
                                logger.accept("=================================================");
                            }
                        }
                    }
                }
            }
        }

        private void ajustarVelocidade(int novoTps) {
            try {
                Class<?> containerClass = Class.forName("net.sf.robocode.core.Container");
                Class<?> settingsClass = Class.forName("net.sf.robocode.settings.ISettingsManager");
                Object settings = containerClass.getMethod("getComponent", Class.class).invoke(null, settingsClass);
                if (settings != null) {
                    settingsClass.getMethod("setOptionsBattleDesiredTPS", int.class).invoke(settings, novoTps);
                    logger.accept(String.format("[Velocidade] Arena acelerada dinamicamente para %d TPS!", novoTps));
                }
            } catch (Throwable t) {
                System.setProperty("robocode.options.battle.desiredTPS", String.valueOf(novoTps));
            }
        }

        private ResultadoBatalha criarBaseResultado(int decorrido) {
            ResultadoBatalha res = new ResultadoBatalha();
            res.partida = partida;
            res.nomeTimeA = (partida != null && partida.nomeTimeA != null) ? partida.nomeTimeA : specA;
            res.nomeTimeB = (partida != null && partida.nomeTimeB != null) ? partida.nomeTimeB : specB;
            res.roundsDisputados = roundAtual;
            res.duracaoRealSegundos = decorrido;
            res.hpFinalA = lastHpA;
            res.hpFinalB = lastHpB;
            res.danoA = lastDanoA;
            res.danoB = lastDanoB;
            res.scoreRobocodeA = lastScoreA;
            res.scoreRobocodeB = lastScoreB;
            return res;
        }

        private void tratarVitoriaProrrogacaoHp(int decorrido, boolean venceuA) {
            ResultadoBatalha res = criarBaseResultado(decorrido);
            res.vencedorNome = venceuA ? res.nomeTimeA : res.nomeTimeB;
            res.vencedorTag = venceuA ? "TIME_A" : "TIME_B";
            res.placarFinalA = venceuA ? roundsGanhosA + 1 : roundsGanhosA;
            res.placarFinalB = venceuA ? roundsGanhosB : roundsGanhosB + 1;
            res.tipoVitoria = ResultadoBatalha.TipoVitoria.PRORROGACAO_HP;
            res.tituloMotivo = "Vitória na Prorrogação por Energia Restante (HP)";
            double maiorHp = Math.max(lastHpA, lastHpB);
            double menorHp = Math.min(lastHpA, lastHpB);
            res.explicacaoDetalhada = String.format(
                    "Após 1 minuto de prorrogação acelerada (%d x %d), o %s venceu por ter maior energia restante: %.1f HP contra %.1f HP.",
                    roundsGanhosA, roundsGanhosB, res.vencedorNome, maiorHp, menorHp
            );
            concluirResultado(res);
        }

        private void tratarVitoriaMorteSubita(int decorrido, boolean venceuA) {
            ResultadoBatalha res = criarBaseResultado(decorrido);
            res.vencedorNome = venceuA ? res.nomeTimeA : res.nomeTimeB;
            res.vencedorTag = venceuA ? "TIME_A" : "TIME_B";
            res.placarFinalA = venceuA ? roundsGanhosA + 1 : roundsGanhosA;
            res.placarFinalB = venceuA ? roundsGanhosB : roundsGanhosB + 1;
            res.tipoVitoria = ResultadoBatalha.TipoVitoria.MORTE_SUBITA_HP;
            res.tituloMotivo = "Vitória na Morte Súbita (Primeiro Dano Sofrido)";
            double maiorHp = Math.max(lastHpA, lastHpB);
            double menorHp = Math.min(lastHpA, lastHpB);
            res.explicacaoDetalhada = String.format(
                    "Na morte súbita com prorrogação empatada, o %s venceu no primeiro dano sofrido pelo oponente (%.1f HP vs %.1f HP).",
                    res.vencedorNome, maiorHp, menorHp
            );
            concluirResultado(res);
        }

        private void tratarTempoEsgotado(int decorrido) {
            logger.accept(String.format("[TEMPO ESGOTADO] Limite de %ds atingido!", duracaoSegundos));
            ResultadoBatalha res = criarBaseResultado(decorrido);

            if (roundsGanhosA != roundsGanhosB) {
                boolean venceuA = roundsGanhosA > roundsGanhosB;
                res.placarFinalA = roundsGanhosA;
                res.placarFinalB = roundsGanhosB;
                res.vencedorNome = venceuA ? res.nomeTimeA : res.nomeTimeB;
                res.vencedorTag = venceuA ? "TIME_A" : "TIME_B";
                res.tipoVitoria = ResultadoBatalha.TipoVitoria.PLACAR_REGULAMENTAR;
                res.tituloMotivo = "Vitória por Placar no Tempo Regulamentar";
                res.explicacaoDetalhada = String.format(
                        "O tempo regulamentar de %ds esgotou. O %s venceu mantendo a vantagem de %d x %d no placar.",
                        duracaoSegundos, res.vencedorNome, res.placarFinalA, res.placarFinalB
                );
            } else {
                // Fallback de segurança se chamado diretamente
                boolean venceuA = lastHpA > lastHpB;
                res.vencedorNome = venceuA ? res.nomeTimeA : res.nomeTimeB;
                res.vencedorTag = venceuA ? "TIME_A" : "TIME_B";
                res.placarFinalA = venceuA ? roundsGanhosA + 1 : roundsGanhosA;
                res.placarFinalB = venceuA ? roundsGanhosB : roundsGanhosB + 1;
                res.tipoVitoria = ResultadoBatalha.TipoVitoria.TEMPO_HP;
                res.tituloMotivo = "Desempate Técnico por Energia Restante (HP)";
                res.explicacaoDetalhada = String.format(
                        "Tempo esgotado com placar empatado. O %s venceu por ter mais energia: %.1f HP contra %.1f HP.",
                        res.vencedorNome, Math.max(lastHpA, lastHpB), Math.min(lastHpA, lastHpB)
                );
            }

            concluirResultado(res);
        }

        private void concluirResultado(ResultadoBatalha res) {
            logger.accept("=================================================");
            logger.accept("[DESFECHO DA BATALHA DEFINIDO]");
            logger.accept("   " + res.tituloMotivo);
            logger.accept("   " + res.explicacaoDetalhada);
            logger.accept(String.format("[VENCEDOR DEFINIDO] %s (Placar: %d x %d)",
                    res.vencedorNome, res.placarFinalA, res.placarFinalB));
            logger.accept("=================================================");

            resultadoEntregue.set(true);
            finalizado = true;

            if (partida != null && partida.id != null) {
                if (disputaRoundARound) {
                    ApiClient.RoundResult rRes = apiClient.registrarRound(
                            partida.id, res.vencedorTag, vitoriasParaGanhar, maxRounds, duasSeguidas
                    );
                    if (rRes.sucesso) {
                        res.placarFinalA = Integer.parseInt(rRes.placarA);
                        res.placarFinalB = Integer.parseInt(rRes.placarB);
                        if (rRes.partidaFinalizada) {
                            String observacao = res.tituloMotivo + " — " + res.explicacaoDetalhada;
                            apiClient.registrarResultado(partida.id, res.placarFinalA, res.placarFinalB, observacao);
                            res.sincronizadoApi = true;
                            if (onResultado != null) {
                                onResultado.accept(res);
                            }
                        } else {
                            logger.accept("=================================================");
                            logger.accept(String.format("[ROUND CONCLUÍDO] Placar da Série: %s [%s] x [%s] %s",
                                    partida.nomeTimeA, rRes.placarA, rRes.placarB, partida.nomeTimeB));
                            logger.accept("[INTERVALO TÉCNICO] Partida continua! Alunos podem ajustar seus códigos.");
                            logger.accept("Clique em 'Iniciar Próximo Round' no painel assim que estiverem prontos.");
                            logger.accept("=================================================");
                        }
                    }
                } else {
                    String observacao = res.tituloMotivo + " — " + res.explicacaoDetalhada;
                    boolean ok = apiClient.registrarResultado(partida.id, res.placarFinalA, res.placarFinalB, observacao);
                    res.sincronizadoApi = ok;
                    if (onResultado != null) {
                        onResultado.accept(res);
                    }
                }
            } else {
                if (onResultado != null) {
                    onResultado.accept(res);
                }
            }

            abortar();
        }

        @Override
        public void onRoundEnded(RoundEndedEvent event) {
            if (finalizado || abortado.get()) return;

            String vencedor = null;
            String nomeVencedor = "Nenhum (Empate)";

            String rotuloA = (partida != null && partida.nomeTimeA != null) ? partida.nomeTimeA : specA;
            String rotuloB = (partida != null && partida.nomeTimeB != null) ? partida.nomeTimeB : specB;

            if (ultimoContestantVivo == 0) {
                vencedor = "TIME_A";
                nomeVencedor = rotuloA;
                roundsGanhosA++;
            } else if (ultimoContestantVivo == 1) {
                vencedor = "TIME_B";
                nomeVencedor = rotuloB;
                roundsGanhosB++;
            } else {
                // Desempate técnico por HP ou pontuação se não houve sobrevivente único
                if (lastHpA > lastHpB) {
                    vencedor = "TIME_A";
                    nomeVencedor = rotuloA;
                    roundsGanhosA++;
                } else if (lastHpB > lastHpA) {
                    vencedor = "TIME_B";
                    nomeVencedor = rotuloB;
                    roundsGanhosB++;
                } else if (lastScoreA >= lastScoreB) {
                    vencedor = "TIME_A";
                    nomeVencedor = rotuloA;
                    roundsGanhosA++;
                } else {
                    vencedor = "TIME_B";
                    nomeVencedor = rotuloB;
                    roundsGanhosB++;
                }
            }

            logger.accept(String.format("[FIM DO ROUND %d] Vencedor: %s (%s)",
                    roundAtual, nomeVencedor, vencedor != null ? vencedor : "EMPATE"));

            if (vencedor != null && partida != null && partida.id != null && apiClient != null) {
                ApiClient.RoundResult res = apiClient.registrarRound(
                        partida.id, vencedor, vitoriasParaGanhar, maxRounds, duasSeguidas
                );

                if (res.sucesso) {
                    logger.accept(String.format("[PLACAR ATUALIZADO] %s [%s] x [%s] %s | Status: %s",
                            partida.nomeTimeA, res.placarA, res.placarB, partida.nomeTimeB, res.status));

                    // Marca resultado entregue para nunca duplicar no onBattleCompleted
                    resultadoEntregue.set(true);

                    if (res.partidaFinalizada) {
                        finalizado = true;
                        logger.accept("[PARABÉNS] Condição de vitória atingida! Partida FINALIZADA no Chaveamento!");

                        ResultadoBatalha finalRes = new ResultadoBatalha();
                        finalRes.partida = partida;
                        finalRes.nomeTimeA = partida.nomeTimeA;
                        finalRes.nomeTimeB = partida.nomeTimeB;
                        finalRes.placarFinalA = Integer.parseInt(res.placarA);
                        finalRes.placarFinalB = Integer.parseInt(res.placarB);
                        finalRes.vencedorNome = (finalRes.placarFinalA > finalRes.placarFinalB) ? partida.nomeTimeA : partida.nomeTimeB;
                        finalRes.vencedorTag = (finalRes.placarFinalA > finalRes.placarFinalB) ? "TIME_A" : "TIME_B";
                        finalRes.tipoVitoria = ResultadoBatalha.TipoVitoria.DESTRUICAO_KO;
                        finalRes.tituloMotivo = "Vitória por Destruição Total (K.O.)";
                        finalRes.explicacaoDetalhada = String.format(
                                "O %s destruiu os robôs adversários e atingiu a meta de vitória com o placar consolidado de %s x %s.",
                                finalRes.vencedorNome, res.placarA, res.placarB
                        );
                        finalRes.hpFinalA = lastHpA;
                        finalRes.hpFinalB = lastHpB;
                        finalRes.danoA = lastDanoA;
                        finalRes.danoB = lastDanoB;
                        finalRes.scoreRobocodeA = lastScoreA;
                        finalRes.scoreRobocodeB = lastScoreB;
                        finalRes.sincronizadoApi = true;
                        finalRes.roundsDisputados = roundAtual;
                        finalRes.duracaoRealSegundos = (int) ((System.currentTimeMillis() - inicioBatalhaMs) / 1000);

                        // Registra a observação explicativa na API
                        if (partida.id != null) {
                            String obs = String.format("Vitória por Destruição Total (K.O.) — Placar %d x %d",
                                    finalRes.placarFinalA, finalRes.placarFinalB);
                            apiClient.registrarResultado(partida.id, finalRes.placarFinalA, finalRes.placarFinalB, obs);
                        }

                        if (onResultado != null) {
                            onResultado.accept(finalRes);
                        }

                        if (!disputaRoundARound) {
                            new Thread(() -> {
                                try { Thread.sleep(800); } catch (Exception ignored) {}
                                abortar();
                            }).start();
                        }
                    } else if (disputaRoundARound) {
                        finalizado = true;
                        logger.accept("=================================================");
                        logger.accept(String.format("[ROUND CONCLUÍDO] Placar da Série: %s [%s] x [%s] %s",
                                partida.nomeTimeA, res.placarA, res.placarB, partida.nomeTimeB));
                        logger.accept("[INTERVALO TÉCNICO] Ajustes de código liberados para os alunos!");
                        logger.accept("Quando estiver pronto, clique em 'Iniciar Próximo Round' no painel.");
                        logger.accept("=================================================");
                    }
                } else {
                    logger.accept("[API] Falha ao registrar round: " + res.erro);
                }
            }
        }

        @Override
        public void onBattleCompleted(BattleCompletedEvent event) {
            logger.accept("[Fim] Batalha finalizada completamente!");
            BattleResults[] results = event.getIndexedResults();
            if (results != null) {
                String rotuloA = (partida != null && partida.nomeTimeA != null) ? partida.nomeTimeA : specA;
                String rotuloB = (partida != null && partida.nomeTimeB != null) ? partida.nomeTimeB : specB;
                for (int i = 0; i < results.length; i++) {
                    String timeLabel = (i == 0) ? rotuloA : (i == 1 ? rotuloB : ("Oponente " + (i + 1)));
                    logger.accept(String.format("   %s: %d pontos (%d vitórias de round)",
                            timeLabel, results[i].getScore(), results[i].getFirsts()));
                }
            }

            // Se por acaso a batalha terminou sem disparar o resultado ainda
            // NUNCA disparar tratarTempoEsgotado em disputaRoundARound!
            if (!disputaRoundARound && resultadoEntregue.compareAndSet(false, true)) {
                int decorrido = (int) ((System.currentTimeMillis() - inicioBatalhaMs) / 1000);
                tratarTempoEsgotado(decorrido);
            }
        }

        @Override
        public void onBattleError(BattleErrorEvent event) {
            logger.accept("[Erro] Erro no Robocode: " + event.getError());
        }
    }

    private String resolverNome(RobocodeEngine engine, String input) {
        if (input == null || input.isBlank()) return null;

        if (input.contains(",")) {
            String[] parts = input.split(",");
            List<String> resList = new ArrayList<>();
            for (String p : parts) {
                String res = resolverNome(engine, p.trim());
                if (res != null && !res.isBlank()) {
                    resList.add(res);
                }
            }
            return String.join(",", resList);
        }

        String limpo = input.trim();
        boolean isTeam = limpo.endsWith(".team") || limpo.contains("torneio_teams");
        if (limpo.endsWith(".team")) {
            limpo = limpo.substring(0, limpo.length() - 5);
        }

        try {
            RobotSpecification[] todos = engine.getLocalRepository();
            if (todos != null) {
                for (RobotSpecification s : todos) {
                    String sName = s.getName();
                    String sClass = s.getClassName();
                    if (sName.equalsIgnoreCase(input) ||
                        sName.equalsIgnoreCase(limpo) ||
                        sName.equalsIgnoreCase(limpo + "*") ||
                        sName.equalsIgnoreCase(input + "*") ||
                        (sClass != null && (sClass.equalsIgnoreCase(input) || sClass.equalsIgnoreCase(limpo) || (sClass + "*").equalsIgnoreCase(input)))) {
                        return s.getName();
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Se for time (.team) e não achou na lista direta, o padrão do Robocode requer '*' no final
        if (isTeam) {
            return limpo.endsWith("*") ? limpo : (limpo + "*");
        }
        return input;
    }
}
