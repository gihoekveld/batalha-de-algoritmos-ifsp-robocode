@echo off
REM Script de inicialização do Robocode Torneio Launcher para Windows
cd /d "%~dp0"

echo ===================================================
echo   Iniciando Painel do Robocode Torneio
echo ===================================================

REM 1. Verifica se o Java esta instalado e acessivel
where java >nul 2>nul
if %errorlevel% neq 0 (
    echo.
    echo [ERRO] O comando 'java' nao foi encontrado no Windows!
    echo Certifique-se de que o Java esta instalado e configurado no PATH.
    echo.
    pause
    exit /b 1
)

REM 2. Se nao encontrar a classe nem o jar compilado, tenta compilar se o javac existir
if not exist "torneio\TorneioMain.class" if not exist "libs\robocode-torneio.jar" (
    echo Compilando Painel do Robocode Torneio...
    where javac >nul 2>nul
    if %errorlevel% neq 0 (
        echo [AVISO] 'javac' nao encontrado. A maquina possui apenas o JRE (nao possui JDK).
    ) else (
        javac --release 11 -encoding UTF-8 -cp "libs\*;." torneio\TorneioConfig.java torneio\model\*.java torneio\service\*.java torneio\gui\*.java torneio\TorneioMain.java
    )
)

REM 3. Executa o Painel do Torneio
echo Abrindo o painel...
java -cp "libs\*;.;torneio" -Xmx512M -XX:+IgnoreUnrecognizedVMOptions "--add-opens=java.base/sun.net.www.protocol.jar=ALL-UNNAMED" "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED" "--add-opens=java.desktop/javax.swing.text=ALL-UNNAMED" "--add-opens=java.desktop/sun.awt=ALL-UNNAMED" torneio.TorneioMain %*

if %errorlevel% neq 0 (
    echo.
    echo [ERRO] Ocorreu uma falha ao abrir o Painel (codigo %errorlevel%).
    echo Verifique as mensagens de erro acima.
    echo.
    pause
)
