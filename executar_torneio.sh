#!/bin/bash
#
# Script para executar uma batalha no Robocode e sincronizar com o chaveamento-robos
#

# Diretório base do Robocode
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "$DIR" || exit 1

# Compila o Runner se necessário
if [ ! -f "torneio/RobocodeTorneioRunner.class" ] || [ "torneio/RobocodeTorneioRunner.java" -nt "torneio/RobocodeTorneioRunner.class" ]; then
    echo "Compilando RobocodeTorneioRunner..."
    javac -cp "libs/*" torneio/RobocodeTorneioRunner.java
    if [ $? -ne 0 ]; then
        echo "Erro na compilação do runner!"
        exit 1
    fi
fi

# Executa o runner com o classpath do Robocode
java \
  -cp ".:libs/*" \
  -Xmx512M \
  -XX:+IgnoreUnrecognizedVMOptions \
  "--add-opens=java.base/sun.net.www.protocol.jar=ALL-UNNAMED" \
  "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED" \
  torneio.RobocodeTorneioRunner "$@"
