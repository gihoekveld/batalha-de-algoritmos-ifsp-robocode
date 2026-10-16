@echo off
setlocal
cd /d "%~dp0"

rem Opcao necessaria para Java 12 a 23 devido a deprecacao do SecurityManager
set _JAVA_OPTIONS=-Djava.security.manager=allow

rem Executa a interface grafica principal do Robocode
java -Xmx512M -XX:+IgnoreUnrecognizedVMOptions --add-opens=java.base/sun.net.www.protocol.jar=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.desktop/javax.swing.text=ALL-UNNAMED --add-opens=java.desktop/sun.awt=ALL-UNNAMED -cp "libs/*" robocode.Robocode %*

endlocal
