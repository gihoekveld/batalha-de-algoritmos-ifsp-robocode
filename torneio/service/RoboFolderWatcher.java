package torneio.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class RoboFolderWatcher {
    private final File folder;
    private final RobotManager robotManager;
    private final Consumer<String> logger;
    private final Runnable onRobotsUpdated;
    private final AtomicBoolean executando = new AtomicBoolean(false);
    private final Map<String, Long> ultimasModificacoes = new HashMap<>();

    public RoboFolderWatcher(File folder, RobotManager robotManager, Consumer<String> logger, Runnable onRobotsUpdated) {
        this.folder = folder;
        this.robotManager = robotManager;
        this.logger = logger != null ? logger : System.out::println;
        this.onRobotsUpdated = onRobotsUpdated;
    }

    public synchronized void iniciar() {
        if (executando.getAndSet(true)) return;

        // Guarda estado inicial
        escanearEstadoInicial();

        Thread watcherThread = new Thread(this::loopMonitoramento, "RoboFolderWatcher-Thread");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    public synchronized void parar() {
        executando.set(false);
    }

    private void escanearEstadoInicial() {
        if (!folder.exists() || !folder.isDirectory()) return;
        try (Stream<Path> stream = Files.walk(folder.toPath(), 1)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.getFileName().toString().endsWith(".java"))
                  .forEach(p -> ultimasModificacoes.put(p.getFileName().toString(), p.toFile().lastModified()));
        } catch (Exception ignored) {}
    }

    private void loopMonitoramento() {
        while (executando.get()) {
            try {
                Thread.sleep(1500);
                if (!folder.exists() || !folder.isDirectory()) continue;

                boolean houveAlteracao = false;
                String arquivoAlterado = null;

                Map<String, Long> estadoAtual = new HashMap<>();
                try (Stream<Path> stream = Files.walk(folder.toPath(), 1)) {
                    for (Path p : (Iterable<Path>) stream.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().endsWith(".java"))::iterator) {
                        String name = p.getFileName().toString();
                        long mod = p.toFile().lastModified();
                        estadoAtual.put(name, mod);

                        if (!ultimasModificacoes.containsKey(name)) {
                            houveAlteracao = true;
                            arquivoAlterado = name + " (Novo robô adicionado)";
                        } else if (ultimasModificacoes.get(name) != mod) {
                            houveAlteracao = true;
                            arquivoAlterado = name;
                        }
                    }
                }

                if (houveAlteracao) {
                    ultimasModificacoes.clear();
                    ultimasModificacoes.putAll(estadoAtual);

                    // Breve pausa para garantir que o editor terminou de salvar
                    Thread.sleep(400);

                    logger.accept("[AUTO-DETECT] Código modificado detectado: " + arquivoAlterado);
                    logger.accept("[Compilador] Recompilando automaticamente em segundo plano...");

                    RobotManager.CompilacaoResult res = robotManager.compilarTodosOsRobos("auto_watcher");
                    if (res.sucesso) {
                        logger.accept("[OK] Código recompilado com sucesso! Alterações prontas para o próximo round.");
                        if (onRobotsUpdated != null) {
                            onRobotsUpdated.run();
                        }
                    } else {
                        logger.accept("[ATENÇÃO] O código modificado tem ERROS de compilação:\n" + res.detalhes);
                    }
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                // Silencioso para não poluir log em caso de erro temporário de IO
            }
        }
    }
}
