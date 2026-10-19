# 🤖 Robocode Torneio — Guia do Operador

Painel visual e oficial para conduzir as batalhas de **Robocode** na Batalha de Algoritmos, integrado em tempo real com a API do **chaveamento-robos**.

---

## 🚀 Como Iniciar o Painel

### No macOS:
- Basta dar **duplo clique** em: `iniciar_painel.command`
- Ou pelo terminal:
  ```bash
  ./iniciar_painel.sh
  ```

### No Windows:
- **Opção 1 (Recomendada - Sem restrição de segurança)**:
  - Duplo clique em `criar_atalho_desktop.vbs` para gerar o atalho oficial **"Robocode Torneio"** na sua Área de Trabalho.
  - Depois basta abrir diretamente por esse atalho (ele funciona exatamente como o Robocode oficial e contorna bloqueios de arquivos `.bat` em redes escolares).
- **Opção 2**: Duplo clique em `iniciar_painel.vbs` (não abre tela preta).
- **Opção 3**: Duplo clique em `iniciar_painel.bat`.
  - *Dica*: Se o `.bat` não abrir ou fechar rápido, clique com o botão direito nele -> **Propriedades** -> marque **"Desbloquear"** -> OK. Ou abra pelo Prompt de Comando (CMD) para ler eventuais mensagens de erro.
---

## 🎯 Como Operar no Dia do Evento (Passo a Passo)

### 1. Quando os Alunos/Participantes Entregarem os Robôs
1. Salve o arquivo `.java` ou o arquivo de equipe `.team` dentro da pasta:
   📂 `robots/torneio_teams/`
   - Exemplos:
     - Equipe montada com robô: `robots/torneio_teams/TimeAlfa.team`
     - Código de robô: `robots/torneio_teams/MeuTanque.java` (com `package torneio_teams;`)
2. Na barra superior do painel, clique no botão **`🛠️ Compilar Robôs`**.
3. O painel compila o código automaticamente e atualiza a lista exibindo **exclusivamente os robôs dessa pasta**.

---

### 2. Para Iniciar uma Partida do Torneio
1. **Selecione a Partida** no menu suspenso do topo (ex: `[#1] OITAVAS: Equipe Alpha vs Equipe Beta`).
   - O painel carrega automaticamente a fase, o status e o placar atual do backend.
2. **Escalação das Equipes**:
   - **Time A (Azul)**: Marque na lista qual robô vai competir.
   - **Time B (Vermelho)**: Marque na lista qual robô vai competir.
   - 💡 **Quer que um time use mais de um robô (2v2, 3v3)?**
     Basta marcar as caixas de seleção de mais de um robô no mesmo time! O painel detecta automaticamente e gera o arquivo de equipe (`.team`) nos bastidores.
3. **Clique no botão verde**:
   # `▶️ INICIAR BATALHA NO ROBOCODE`
4. A arena oficial do Robocode abre na tela.
5. A cada round vencido, o placar é atualizado automaticamente na API do chaveamento (`POST /partidas/{partidaId}/rounds`) como `TIME_A` ou `TIME_B`.
6. Quando o vencedor é definido (ex: 2 vitórias ou 2x0), a partida finaliza no chaveamento e a próxima partida avança automaticamente!

---

## ⚙️ Configurações Rápidas do Painel

- **Rounds Máx**: Quantidade de rounds (Padrão: `3`).
- **Vitórias p/ Ganhar**: Vitórias necessárias para avançar (Padrão: `2` — Melhor de 3).
- **Encerrar com 2x0**: Se marcado, encerra antes do 3º round caso um time abra 2 a 0.
- **Tempo Máx (s)**: Duração máxima da disputa (Padrão: `120s` / 2 minutos).
- **Velocidade (TPS)**: Padrão `30` Turns Per Second para animação fluida na tela.
- **⚙️ Configurações (Barra Superior)**: Permite alterar a URL da API, email e senha de login e o ID do torneio.
- **🔄 Resetar Partida**: Zera o placar no backend para 0 x 0 caso ocorra algum problema técnico.
