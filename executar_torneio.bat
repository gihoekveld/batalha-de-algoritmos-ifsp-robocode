@echo off
setlocal

cd /d "%~dp0"

echo ===================================================
echo   Robocode Torneio Runner - Chaveamento de Robos
echo ===================================================

rem Compila o Runner se o .class nao existir
if not exist "torneio\RobocodeTorneioRunner.class" (
    echo Compilando RobocodeTorneioRunner...
    javac -cp "libs/*" torneio\RobocodeTorneioRunner.java
    if errorlevel 1 (
        echo [ERRO] Falha na compilacao do runner.
        echo Verifique se o JDK esta instalado e configurado no PATH do Windows.
        pause
        exit /b 1
    )
)

rem Opcao necessaria para Java 12 a 23
set _JAVA_OPTIONS=-Djava.security.manager=allow

rem No Windows, o separador de classpath e ';' em vez de ':'
java -cp ".;libs/*" -Xmx512M -XX:+IgnoreUnrecognizedVMOptions --add-opens=java.base/sun.net.www.protocol.jar=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED torneio.RobocodeTorneioRunner %*

endlocal
