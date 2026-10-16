# Robocode Torneio Runner

Utilitário para executar partidas do Robocode (individuais ou equipes/times) e sincronizar em tempo real com a API do **chaveamento-robos**.

---

## 🚀 Como Executar

### No Linux / macOS:
```bash
./executar_torneio.sh
```

### No Windows:
Basta dar dois cliques em `executar_torneio.bat` ou rodar no CMD / PowerShell:
```cmd
executar_torneio.bat
```


### 1. Modo 100% Automático e Interativo (Mais Fácil)
Se você apenas rodar `./executar_torneio.sh` sem nenhum parâmetro:
1. Ele conecta automaticamente à API com login padrão (`admin@admin.com` / `admin123`).
2. Lista as partidas da chave que estão prontas para jogar.
3. Mapeia automaticamente os nomes dos times cadastrados para seus robôs/arquivos `.team` correspondentes!
4. Basta pressionar **Enter** para disputar a partida recomendada.

### 2. Modo com ID da Partida
Basta passar o ID da partida (o robô A e o robô B são mapeados automaticamente):
```bash
./executar_torneio.sh --partida 14
```

### 3. Controle de Tempo e Velocidade (Requisito do Professor: ~2 minutos)
Para controlar a duração e o ritmo da disputa:
- `--tempo 120`: Define a duração máxima da partida (padrão: 120 segundos / 2 minutos).
- `--tps 30`: Define os *Turns Per Second* (velocidade da simulação). `30` é o padrão para animação fluida e realista em tempo real.
```bash
./executar_torneio.sh --partida 14 --tempo 120 --tps 30
```

---

## ⚙️ Parâmetros Disponíveis

| Parâmetro | Alias | Descrição | Padrão |
| :--- | :--- | :--- | :--- |
| `--partida` | `-p` | ID da partida no backend | *Automático via menu se omitido* |
| `--tempo` | `-d` | Duração máxima da partida em segundos ou minutos (ex: `120`, `2m`) | `120` (2 minutos) |
| `--tps` | | Velocidade da arena em Turns Per Second | `30` |
| `--timeA` | `-a` | Nome do robô ou arquivo `.team` do Time A | *Automático pelo nome do time* |
| `--timeB` | `-b` | Nome do robô ou arquivo `.team` do Time B | *Automático pelo nome do time* |
| `--rounds` | `-r` | Número máximo de rounds na arena | `3` (Melhor de 3) |
| `--vitorias` | `-v` | Vitórias necessárias para vencer | `2` |
| `--duas-seguidas` | | Finaliza antecipadamente se vencer 2 seguidas (2x0) | `true` |
| `--nodisplay` | | Desativa a interface gráfica da arena | `false` |
| `--api` | | URL base da API | `http://localhost:8080` |
