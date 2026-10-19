#!/bin/bash
#
# Script de inicialização do Robocode Torneio Launcher
#

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "$DIR" || exit 1

# Compila as classes do torneio se necessário ou se houver alterações
if [ ! -f "torneio/TorneioMain.class" ] || [ $(find torneio -name "*.java" -newer torneio/TorneioMain.class 2>/dev/null | wc -l) -gt 0 ]; then
    echo "Compilando Painel do Robocode Torneio..."
    javac -encoding UTF-8 -cp "libs/*:." torneio/TorneioConfig.java torneio/model/*.java torneio/service/*.java torneio/gui/*.java torneio/TorneioMain.java
    if [ $? -ne 0 ]; then
        echo "Erro na compilação do painel!"
        exit 1
    fi
fi

# Executa a interface gráfica com suporte completo a módulos no Java 17+
java \
  -cp ".:libs/*" \
  -Xmx512M \
  -Xdock:name="Robocode Torneio" \
  -Xdock:icon=robocode.ico \
  -XX:+IgnoreUnrecognizedVMOptions \
  "--add-opens=java.base/sun.net.www.protocol.jar=ALL-UNNAMED" \
  "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED" \
  "--add-opens=java.desktop/javax.swing.text=ALL-UNNAMED" \
  "--add-opens=java.desktop/sun.awt=ALL-UNNAMED" \
  torneio.TorneioMain "$@"
