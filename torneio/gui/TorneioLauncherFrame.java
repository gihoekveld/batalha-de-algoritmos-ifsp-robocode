package torneio.gui;

import torneio.TorneioConfig;
import torneio.model.Partida;
import torneio.model.ResultadoBatalha;
import torneio.model.Time;
import torneio.service.ApiClient;
import torneio.service.BattleRunner;
import torneio.service.RoboFolderWatcher;
import torneio.service.RobotManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class TorneioLauncherFrame extends JFrame {
    private final TorneioConfig config;
    private final ApiClient apiClient;
    private final RobotManager robotManager;

    private Map<Long, Time> timesCadastrados = new LinkedHashMap<>();
    private List<Partida> partidasCarregadas = new ArrayList<>();
    private List<String> todosRobosDisponiveis = new ArrayList<>();

    // Navegação Principal por Abas
    private JTabbedPane tabbedPane;

    // Header Superior
    private JLabel lblStatusConexaoHeader;
    private JButton btnCompilarHeader;
    private JButton btnAtualizarHeader;

    // =========================================================================
    // Componentes da Aba 1: Conexão & Torneio
    // =========================================================================
    private JTextField txtApiUrl;
    private JTextField txtApiEmail;
    private JPasswordField txtApiSenha;
    private JSpinner spinTorneioId;
    private JLabel lblStatusConexaoDetalhe;
    private JLabel lblResumoTorneio;
    private JLabel lblResumoEquipes;
    private JLabel lblResumoPartidas;

    // =========================================================================
    // Componentes da Aba 2: Configurações da Partida & Regras
    // =========================================================================
    private JSpinner spinRounds;
    private JSpinner spinVitorias;
    private JCheckBox chkDuasSeguidas;
    private JCheckBox chkRoundARound;
    private JSpinner spinDuracao;
    private JCheckBox chkSemLimiteTempo;
    private JSpinner spinTps;
    private JCheckBox chkExibirJanela;
    private JSpinner spinCampoLargura;
    private JSpinner spinCampoAltura;
    private JLabel lblStatusCompilacao;

    // =========================================================================
    // Componentes da Aba 3: Robôs & Gerenciador de Times
    // =========================================================================
    private DefaultTableModel modelRobosDisponiveis;
    private JTable tabelaRobosDisponiveis;
    private DefaultTableModel modelTimesExistentes;
    private JTable tabelaTimesExistentes;
    private JTextField txtNovoTimeNome;
    private JTextField txtNovoTimeAutor;
    private JTextField txtNovoTimeDescricao;
    private JLabel lblContadorSelecaoNovoTime;

    // =========================================================================
    // Componentes da Aba 4: Arena & Execução da Partida
    // =========================================================================
    private JComboBox<Partida> cbPartidas;
    private JLabel lblInfoPartida;
    private JLabel lblVersusTimeA;
    private JLabel lblVersusPlacar;
    private JLabel lblVersusTimeB;
    private JLabel lblVersusFaseStatus;

    private JLabel lblNomeTimeA;
    private JLabel lblNomeTimeB;
    private DefaultTableModel modelTimesA;
    private DefaultTableModel modelTimesB;
    private JTable tabelaTimesA;
    private JTable tabelaTimesB;
    private JLabel lblContadorA;
    private JLabel lblContadorB;
    private boolean isUpdatingTableSelection = false;

    private JButton btnIniciarBatalha;
    private JButton btnAbortarBatalha;
    private JButton btnResetarPartida;
    private JLabel lblModoBatalhaDesc;

    // =========================================================================
    // Componentes da Aba 5: Arena de Testes & Treino
    // =========================================================================
    private JRadioButton rbModoTesteIndividual;
    private JRadioButton rbModoTesteEquipe;
    private DefaultTableModel modelCombatenteTeste;
    private JTable tabelaCombatenteTeste;
    private JTextField txtFiltroCombatenteTeste;
    private JLabel lblCombatenteSelecionadoTeste;

    private JRadioButton rbOponenteSample;
    private JRadioButton rbOponenteTurma;
    private DefaultTableModel modelOponentesTeste;
    private JTable tabelaOponentesTeste;
    private JLabel lblOponentesSelecionadosTeste;

    private JComboBox<String> cbRoundsTeste;
    private JComboBox<String> cbTpsTeste;
    private JCheckBox chkAutoCompilarTeste;
    private JButton btnIniciarTeste;
    private JButton btnAbortarTeste;
    private JLabel lblStatusTeste;
    private BattleRunner battleRunnerTeste;
    private boolean isUpdatingTestCombatenteSelection = false;

    // =========================================================================
    // Rodapé Global: Console de Logs
    // =========================================================================
    private JTextArea txtLogs;
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss");

    private BattleRunner battleRunnerAtual;
    private RoboFolderWatcher folderWatcher;

    public TorneioLauncherFrame() {
        this.config = new TorneioConfig();
        this.config.carregar();
        this.apiClient = new ApiClient(config);
        this.robotManager = new RobotManager();

        configurarAparencia();
        inicializarComponentes();
        carregarDadosIniciaisAssincrono();

        // Inicia o monitor de código em segundo plano na pasta de robôs
        this.folderWatcher = new RoboFolderWatcher(
                this.robotManager.getRobosDir(),
                this.robotManager,
                this::log,
                () -> SwingUtilities.invokeLater(this::atualizarRobosLocais));
        this.folderWatcher.iniciar();
    }

    private void configurarAparencia() {
        setTitle("Robocode Torneio — Painel Oficial da Batalha de Algoritmos");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1260, 880);
        setMinimumSize(new Dimension(1100, 740));
        setLocationRelativeTo(null);

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }

    private void inicializarComponentes() {
        JPanel painelPrincipal = new JPanel(new BorderLayout(8, 8));
        painelPrincipal.setBorder(new EmptyBorder(10, 12, 10, 12));
        setContentPane(painelPrincipal);

        // 1. Barra Superior (Header & Status Global)
        painelPrincipal.add(criarPainelHeader(), BorderLayout.NORTH);

        // 2. Centro: JTabbedPane com as 4 Abas Solicitadas
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));

        tabbedPane.addTab("  1. Conexão & Torneio  ", criarAbaConexaoTorneio());
        tabbedPane.addTab("  2. Configurações da Partida  ", criarAbaConfiguracoesPartida());
        tabbedPane.addTab("  3. Robôs & Gerenciador de Times  ", criarAbaRobosETimes());
        tabbedPane.addTab("  4. Arena & Execução da Partida  ", criarAbaExecucaoPartida());
        tabbedPane.addTab("  5. Arena de Testes & Treino  ", criarAbaArenaTestes());

        // Inicia selecionando a Arena se já houver torneio configurado
        tabbedPane.setSelectedIndex(3);

        painelPrincipal.add(tabbedPane, BorderLayout.CENTER);

        // 3. Rodapé Global (Console de Logs Visível em Todas as Abas)
        painelPrincipal.add(criarPainelLogsGlobal(), BorderLayout.SOUTH);
    }

    // =========================================================================
    // BARRA SUPERIOR (HEADER)
    // =========================================================================

    private JPanel criarPainelHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(15, 23, 42)); // Slate escuro premium
        header.setBorder(new EmptyBorder(10, 16, 10, 16));

        JPanel ladoEsquerdo = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        ladoEsquerdo.setOpaque(false);

        JLabel titulo = new JLabel("ROBOCODE TORNEIO");
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titulo.setForeground(Color.WHITE);

        JLabel subtitulo = new JLabel("— Painel de Chaveamento & Batalha");
        subtitulo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitulo.setForeground(new Color(148, 163, 184));

        ladoEsquerdo.add(titulo);
        ladoEsquerdo.add(subtitulo);

        JPanel ladoDireito = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        ladoDireito.setOpaque(false);

        lblStatusConexaoHeader = new JLabel("[Conectando...]");
        lblStatusConexaoHeader.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblStatusConexaoHeader.setForeground(Color.YELLOW);

        btnAtualizarHeader = new JButton("Atualizar Dados");
        btnAtualizarHeader.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnAtualizarHeader.setFocusPainted(false);
        btnAtualizarHeader.addActionListener(e -> recarregarDados());

        ladoDireito.add(lblStatusConexaoHeader);
        ladoDireito.add(btnAtualizarHeader);

        header.add(ladoEsquerdo, BorderLayout.WEST);
        header.add(ladoDireito, BorderLayout.EAST);
        return header;
    }

    // =========================================================================
    // ABA 1: CONEXÃO & TORNEIO
    // =========================================================================

    private JPanel criarAbaConexaoTorneio() {
        JPanel painel = new JPanel(new BorderLayout(15, 15));
        painel.setBorder(new EmptyBorder(20, 25, 20, 25));

        // Painel Superior de Descrição
        JPanel desc = new JPanel(new BorderLayout());
        desc.setBackground(new Color(241, 245, 249));
        desc.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1),
                new EmptyBorder(12, 16, 12, 16)));
        JLabel lblDesc = new JLabel("<html><b>Autenticação e Conexão com o Backend do Chaveamento</b><br>" +
                "Configure o endereço da API REST, as credenciais de acesso de administrador e o identificador (ID) do Torneio.</html>");
        lblDesc.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblDesc.setForeground(new Color(51, 65, 85));
        desc.add(lblDesc, BorderLayout.CENTER);

        // Formulário Central (2 Colunas: Configuração à Esquerda, Status do Torneio à
        // Direita)
        JPanel centro = new JPanel(new GridLayout(1, 2, 20, 0));

        // Cartão 1: Credenciais da API
        JPanel cardCred = new JPanel(new GridBagLayout());
        cardCred.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), " Credenciais de Acesso à API ",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 14), new Color(15, 23, 42)));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8, 12, 8, 12);
        g.fill = GridBagConstraints.HORIZONTAL;

        txtApiUrl = new JTextField(config.apiBaseUrl, 20);
        txtApiEmail = new JTextField(config.email, 20);
        txtApiSenha = new JPasswordField(config.senha, 20);
        spinTorneioId = new JSpinner(
                new SpinnerNumberModel((int) (config.torneioId != null ? config.torneioId : 1L), 1, 999999, 1));

        g.gridx = 0;
        g.gridy = 0;
        g.weightx = 0.3;
        cardCred.add(new JLabel("URL Base da API:"), g);
        g.gridx = 1;
        g.gridy = 0;
        g.weightx = 0.7;
        cardCred.add(txtApiUrl, g);

        g.gridx = 0;
        g.gridy = 1;
        g.weightx = 0.3;
        cardCred.add(new JLabel("E-mail de Login:"), g);
        g.gridx = 1;
        g.gridy = 1;
        g.weightx = 0.7;
        cardCred.add(txtApiEmail, g);

        g.gridx = 0;
        g.gridy = 2;
        g.weightx = 0.3;
        cardCred.add(new JLabel("Senha do Administrador:"), g);
        g.gridx = 1;
        g.gridy = 2;
        g.weightx = 0.7;
        cardCred.add(txtApiSenha, g);

        g.gridx = 0;
        g.gridy = 3;
        g.weightx = 0.3;
        cardCred.add(new JLabel("ID do Torneio Ativo:"), g);
        g.gridx = 1;
        g.gridy = 3;
        g.weightx = 0.7;
        cardCred.add(spinTorneioId, g);

        JPanel botoesCred = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));

        CustomColoredButton btnConectar = new CustomColoredButton("Conectar", new Color(37, 99, 235),
                new Color(59, 130, 246));
        btnConectar.setPreferredSize(new Dimension(190, 34));
        btnConectar.addActionListener(e -> {
            salvarCredenciaisApi();
            autenticarEConectar();
            recarregarDados();
        });

        botoesCred.add(btnConectar);

        g.gridx = 0;
        g.gridy = 4;
        g.gridwidth = 2;
        cardCred.add(botoesCred, g);

        // Cartão 2: Status do Torneio Carregado
        JPanel cardStatus = new JPanel(new BorderLayout(10, 15));
        cardStatus.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), " Informações do Torneio Carregado ",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 14), new Color(15, 23, 42)));
        cardStatus.setBackground(Color.WHITE);

        JPanel painelInfoGrid = new JPanel(new GridLayout(4, 1, 10, 10));
        painelInfoGrid.setBorder(new EmptyBorder(15, 20, 15, 20));
        painelInfoGrid.setOpaque(false);

        lblStatusConexaoDetalhe = new JLabel("Status: Verificando...");
        lblStatusConexaoDetalhe.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblStatusConexaoDetalhe.setForeground(Color.DARK_GRAY);

        lblResumoTorneio = new JLabel("Torneio Selecionado: #" + config.torneioId);
        lblResumoTorneio.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        lblResumoEquipes = new JLabel("Equipes Registradas: Carregando...");
        lblResumoEquipes.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        lblResumoPartidas = new JLabel("Partidas no Chaveamento: Carregando...");
        lblResumoPartidas.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        painelInfoGrid.add(lblStatusConexaoDetalhe);
        painelInfoGrid.add(lblResumoTorneio);
        painelInfoGrid.add(lblResumoEquipes);
        painelInfoGrid.add(lblResumoPartidas);

        JPanel rodapeStatus = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        rodapeStatus.setOpaque(false);

        cardStatus.add(painelInfoGrid, BorderLayout.CENTER);
        cardStatus.add(rodapeStatus, BorderLayout.SOUTH);

        centro.add(cardCred);
        centro.add(cardStatus);

        painel.add(desc, BorderLayout.NORTH);
        painel.add(centro, BorderLayout.CENTER);
        return painel;
    }

    private void salvarCredenciaisApi() {
        config.apiBaseUrl = txtApiUrl.getText().trim();
        config.email = txtApiEmail.getText().trim();
        config.senha = new String(txtApiSenha.getPassword()).trim();
        config.torneioId = ((Number) spinTorneioId.getValue()).longValue();
        config.salvar();
        log("[Config] Credenciais da API salvas com sucesso no arquivo torneio.properties.");
    }

    // =========================================================================
    // ABA 2: CONFIGURAÇÕES DA PARTIDA & REGRAS
    // =========================================================================

    private JPanel criarAbaConfiguracoesPartida() {
        JPanel painel = new JPanel(new BorderLayout(15, 15));
        painel.setBorder(new EmptyBorder(15, 20, 15, 20));

        JPanel gridConfigs = new JPanel(new GridLayout(2, 2, 16, 16));

        // 1. Card Regras da Partida (Melhor de X)
        JPanel cardRegras = new JPanel(new GridBagLayout());
        cardRegras.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), " 1. Regras de Disputa e Vitória ",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 13), new Color(15, 23, 42)));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 10, 6, 10);
        g.fill = GridBagConstraints.HORIZONTAL;

        spinRounds = new JSpinner(new SpinnerNumberModel(config.maxRounds, 1, 10, 1));
        spinVitorias = new JSpinner(new SpinnerNumberModel(config.vitoriasParaGanhar, 1, 10, 1));
        chkDuasSeguidas = new JCheckBox("Encerrar imediatamente com 2x0 (duas seguidas)",
                config.finalizarComDuasSeguidas);
        chkRoundARound = new JCheckBox("Disputa Round a Round (1 round por vez com intervalo)",
                config.disputaRoundARound);
        chkRoundARound
                .setToolTipText("Permite que os alunos alterem o código dos robôs entre os rounds da mesma partida!");

        g.gridx = 0;
        g.gridy = 0;
        g.weightx = 0.5;
        cardRegras.add(new JLabel("Limite Máximo de Rounds:"), g);
        g.gridx = 1;
        g.gridy = 0;
        g.weightx = 0.5;
        cardRegras.add(spinRounds, g);

        g.gridx = 0;
        g.gridy = 1;
        cardRegras.add(new JLabel("Vitórias para Vencer a Partida:"), g);
        g.gridx = 1;
        g.gridy = 1;
        cardRegras.add(spinVitorias, g);

        g.gridx = 0;
        g.gridy = 2;
        g.gridwidth = 2;
        cardRegras.add(chkDuasSeguidas, g);
        g.gridx = 0;
        g.gridy = 3;
        g.gridwidth = 2;
        cardRegras.add(chkRoundARound, g);

        // 2. Card Tempo e Execução
        JPanel cardTempo = new JPanel(new GridBagLayout());
        cardTempo.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), " 2. Tempo de Execução & Animação ",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 13), new Color(15, 23, 42)));

        spinDuracao = new JSpinner(new SpinnerNumberModel(config.duracaoSegundos, 30, 600, 10));
        chkSemLimiteTempo = new JCheckBox("Sem Limite de Tempo (luta contínua até o fim)", config.semLimiteTempo);
        spinDuracao.setEnabled(!config.semLimiteTempo);
        chkSemLimiteTempo.addActionListener(e -> spinDuracao.setEnabled(!chkSemLimiteTempo.isSelected()));

        spinTps = new JSpinner(new SpinnerNumberModel(config.tps, 10, 120, 5));
        chkExibirJanela = new JCheckBox("Exibir Janela Gráfica do Robocode", config.exibirJanela);

        g.gridx = 0;
        g.gridy = 0;
        g.gridwidth = 1;
        g.weightx = 0.5;
        cardTempo.add(new JLabel("Duração Máxima (segundos):"), g);
        g.gridx = 1;
        g.gridy = 0;
        g.weightx = 0.5;
        cardTempo.add(spinDuracao, g);

        g.gridx = 0;
        g.gridy = 1;
        g.gridwidth = 2;
        cardTempo.add(chkSemLimiteTempo, g);

        g.gridx = 0;
        g.gridy = 2;
        g.gridwidth = 1;
        cardTempo.add(new JLabel("Velocidade do Jogo (TPS):"), g);
        g.gridx = 1;
        g.gridy = 2;
        cardTempo.add(spinTps, g);

        g.gridx = 0;
        g.gridy = 3;
        g.gridwidth = 2;
        cardTempo.add(chkExibirJanela, g);

        // 3. Card Área de Jogo (Battlefield)
        JPanel cardCampo = new JPanel(new GridBagLayout());
        cardCampo.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), " 3. Tamanho da Área do Jogo (Battlefield) ",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 13), new Color(15, 23, 42)));

        spinCampoLargura = new JSpinner(new SpinnerNumberModel(config.campoLargura, 600, 4000, 50));
        spinCampoAltura = new JSpinner(new SpinnerNumberModel(config.campoAltura, 400, 3000, 50));

        g.gridx = 0;
        g.gridy = 0;
        g.gridwidth = 1;
        g.weightx = 0.5;
        cardCampo.add(new JLabel("Largura do Campo (pixels):"), g);
        g.gridx = 1;
        g.gridy = 0;
        g.weightx = 0.5;
        cardCampo.add(spinCampoLargura, g);

        g.gridx = 0;
        g.gridy = 1;
        cardCampo.add(new JLabel("Altura do Campo (pixels):"), g);
        g.gridx = 1;
        g.gridy = 1;
        cardCampo.add(spinCampoAltura, g);

        JPanel presets = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        presets.setOpaque(false);
        JButton btnP1 = new JButton("800x600 (Padrão)");
        btnP1.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnP1.addActionListener(e -> {
            spinCampoLargura.setValue(800);
            spinCampoAltura.setValue(600);
        });

        JButton btnP2 = new JButton("1000x800 (Amplo)");
        btnP2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnP2.addActionListener(e -> {
            spinCampoLargura.setValue(1000);
            spinCampoAltura.setValue(800);
        });

        JButton btnP3 = new JButton("1200x1000 (Arena 2v2)");
        btnP3.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnP3.addActionListener(e -> {
            spinCampoLargura.setValue(1200);
            spinCampoAltura.setValue(1000);
        });

        presets.add(btnP1);
        presets.add(btnP2);
        presets.add(btnP3);

        g.gridx = 0;
        g.gridy = 2;
        g.gridwidth = 2;
        cardCampo.add(new JLabel("Presets rápidos recomendados:"), g);
        g.gridx = 0;
        g.gridy = 3;
        g.gridwidth = 2;
        cardCampo.add(presets, g);

        // 4. Card Compilação & Ferramentas
        JPanel cardTools = new JPanel(new BorderLayout(10, 10));
        cardTools.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), " 4. Compilação e Manutenção de Robôs ",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 13), new Color(15, 23, 42)));

        JPanel toolsCentro = new JPanel(new GridLayout(3, 1, 8, 8));
        toolsCentro.setBorder(new EmptyBorder(10, 15, 10, 15));

        CustomColoredButton btnCompilarGrande = new CustomColoredButton("Compilar Todos os Robôs Agora",
                new Color(14, 116, 144), new Color(6, 182, 212));
        btnCompilarGrande.setPreferredSize(new Dimension(280, 38));
        btnCompilarGrande.addActionListener(e -> executarCompilacaoRobos());

        lblStatusCompilacao = new JLabel("Última compilação: Não executada nesta sessão");
        lblStatusCompilacao.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblStatusCompilacao.setForeground(new Color(71, 85, 105));

        JLabel lblDicaComp = new JLabel(
                "<html><i>O monitor em tempo real recompila preventivamente códigos alterados pelos alunos.</i></html>");
        lblDicaComp.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        toolsCentro.add(btnCompilarGrande);
        toolsCentro.add(lblStatusCompilacao);
        toolsCentro.add(lblDicaComp);

        cardTools.add(toolsCentro, BorderLayout.CENTER);

        gridConfigs.add(cardRegras);
        gridConfigs.add(cardTempo);
        gridConfigs.add(cardCampo);
        gridConfigs.add(cardTools);

        // Barra de Ação Inferior da Aba de Configurações
        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        CustomColoredButton btnSalvarTudo = new CustomColoredButton("Salvar Todas as Configurações",
                new Color(22, 163, 74), new Color(34, 197, 94));
        btnSalvarTudo.setPreferredSize(new Dimension(260, 36));
        btnSalvarTudo.addActionListener(e -> {
            salvarTodasConfiguracoes();
            JOptionPane.showMessageDialog(this, "Configurações da partida salvas com sucesso!", "Sucesso",
                    JOptionPane.INFORMATION_MESSAGE);
        });

        rodape.add(btnSalvarTudo);

        painel.add(gridConfigs, BorderLayout.CENTER);
        painel.add(rodape, BorderLayout.SOUTH);
        return painel;
    }

    private void salvarTodasConfiguracoes() {
        config.maxRounds = (Integer) spinRounds.getValue();
        config.vitoriasParaGanhar = (Integer) spinVitorias.getValue();
        config.finalizarComDuasSeguidas = chkDuasSeguidas.isSelected();
        config.disputaRoundARound = chkRoundARound.isSelected();
        config.duracaoSegundos = (Integer) spinDuracao.getValue();
        config.semLimiteTempo = chkSemLimiteTempo.isSelected();
        config.tps = (Integer) spinTps.getValue();
        config.exibirJanela = chkExibirJanela.isSelected();
        config.campoLargura = (Integer) spinCampoLargura.getValue();
        config.campoAltura = (Integer) spinCampoAltura.getValue();
        config.salvar();
        log(String.format("[Config] Configurações atualizadas: MD%d (Vitórias: %d, Round a Round: %b, Campo: %dx%d)",
                config.maxRounds, config.vitoriasParaGanhar, config.disputaRoundARound, config.campoLargura,
                config.campoAltura));
    }

    // =========================================================================
    // ABA 3: ROBÔS & GERENCIADOR DE TIMES (.team)
    // =========================================================================

    private JPanel criarAbaRobosETimes() {
        JPanel painel = new JPanel(new BorderLayout(10, 10));
        painel.setBorder(new EmptyBorder(12, 15, 12, 15));

        // Topo: Diretório dos Robôs e Ações
        JPanel topo = new JPanel(new BorderLayout(10, 0));
        topo.setBackground(new Color(241, 245, 249));
        topo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1),
                new EmptyBorder(10, 14, 10, 14)));

        JPanel botoesTopo = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        botoesTopo.setOpaque(false);

        JButton btnAbrirPasta = new JButton("Abrir Pasta dos Robôs");
        btnAbrirPasta.setFocusPainted(false);
        btnAbrirPasta.addActionListener(e -> abrirPastaRobosTorneio());

        JButton btnRecarregarAba3 = new JButton("Recarregar Lista");
        btnRecarregarAba3.setFocusPainted(false);
        btnRecarregarAba3.addActionListener(e -> atualizarRobosLocais());

        botoesTopo.add(btnAbrirPasta);
        botoesTopo.add(btnRecarregarAba3);

        topo.add(botoesTopo, BorderLayout.EAST);

        // Centro: 2 Colunas (Esquerda: Robôs Individuais Compilados / Direita: Criador
        // de Times e Times Existentes)
        JPanel centro = new JPanel(new GridLayout(1, 2, 15, 0));

        // Coluna 1: Robôs Individuais Compilados (.class)
        JPanel colRobos = new JPanel(new BorderLayout(6, 6));
        colRobos.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), " 1. Robôs Compilados Disponíveis (.class) ",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 13), new Color(15, 23, 42)));

        JPanel filtroBox = new JPanel(new BorderLayout(6, 0));
        filtroBox.setBorder(new EmptyBorder(4, 6, 4, 6));
        JTextField txtFiltroRobos = new JTextField();
        txtFiltroRobos.setToolTipText("Digite para filtrar robôs...");

        modelRobosDisponiveis = new DefaultTableModel(new Object[] { "Selecionar", "Robô Compilado" }, 0) {
            @Override
            public Class<?> getColumnClass(int col) {
                return col == 0 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int col) {
                return col == 0;
            }
        };
        tabelaRobosDisponiveis = new JTable(modelRobosDisponiveis);
        tabelaRobosDisponiveis.setRowHeight(24);
        tabelaRobosDisponiveis.getColumnModel().getColumn(0).setMaxWidth(75);
        tabelaRobosDisponiveis.getColumnModel().getColumn(0).setMinWidth(75);

        TableRowSorter<DefaultTableModel> sorterRobos = new TableRowSorter<>(modelRobosDisponiveis);
        tabelaRobosDisponiveis.setRowSorter(sorterRobos);
        txtFiltroRobos.addCaretListener(e -> {
            String t = txtFiltroRobos.getText().trim();
            sorterRobos.setRowFilter(t.isEmpty() ? null : RowFilter.regexFilter("(?i)" + t, 1));
        });

        modelRobosDisponiveis.addTableModelListener(e -> {
            if (e.getColumn() == 0)
                atualizarContadorNovoTime();
        });

        filtroBox.add(new JLabel("Filtrar:"), BorderLayout.WEST);
        filtroBox.add(txtFiltroRobos, BorderLayout.CENTER);

        JPanel rodapeRobos = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        lblContadorSelecaoNovoTime = new JLabel("0 robô(s) marcado(s) para compor nova equipe");
        lblContadorSelecaoNovoTime.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblContadorSelecaoNovoTime.setForeground(new Color(30, 64, 175));
        rodapeRobos.add(lblContadorSelecaoNovoTime);

        colRobos.add(filtroBox, BorderLayout.NORTH);
        colRobos.add(new JScrollPane(tabelaRobosDisponiveis), BorderLayout.CENTER);
        colRobos.add(rodapeRobos, BorderLayout.SOUTH);

        // Coluna 2: Criador de Times (.team) e Times Existentes
        JPanel colTimes = new JPanel(new BorderLayout(10, 10));

        // Subcard: Criar Novo Time
        JPanel cardCriarTeam = new JPanel(new GridBagLayout());
        cardCriarTeam.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), " 2. Montar Nova Equipe (.team) ",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 13), new Color(15, 23, 42)));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 8, 4, 8);
        gc.fill = GridBagConstraints.HORIZONTAL;

        txtNovoTimeNome = new JTextField(15);
        txtNovoTimeAutor = new JTextField(15);
        txtNovoTimeDescricao = new JTextField(15);

        gc.gridx = 0;
        gc.gridy = 0;
        gc.weightx = 0.35;
        cardCriarTeam.add(new JLabel("Nome do Time:"), gc);
        gc.gridx = 1;
        gc.gridy = 0;
        gc.weightx = 0.65;
        cardCriarTeam.add(txtNovoTimeNome, gc);

        gc.gridx = 0;
        gc.gridy = 1;
        cardCriarTeam.add(new JLabel("Autor / Integrantes:"), gc);
        gc.gridx = 1;
        gc.gridy = 1;
        cardCriarTeam.add(txtNovoTimeAutor, gc);

        gc.gridx = 0;
        gc.gridy = 2;
        cardCriarTeam.add(new JLabel("Descrição do Time:"), gc);
        gc.gridx = 1;
        gc.gridy = 2;
        cardCriarTeam.add(txtNovoTimeDescricao, gc);

        CustomColoredButton btnSalvarTime = new CustomColoredButton("Salvar Arquivo .team", new Color(22, 163, 74),
                new Color(34, 197, 94));
        btnSalvarTime.setPreferredSize(new Dimension(200, 32));
        btnSalvarTime.addActionListener(e -> criarNovoTimeManual());

        gc.gridx = 0;
        gc.gridy = 3;
        gc.gridwidth = 2;
        cardCriarTeam.add(btnSalvarTime, gc);

        // Subcard: Times Já Criados
        JPanel cardTimesExistentes = new JPanel(new BorderLayout(6, 6));
        cardTimesExistentes.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), " 3. Equipes Cadastradas (.team) ",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 13), new Color(15, 23, 42)));

        modelTimesExistentes = new DefaultTableModel(new Object[] { "Arquivo de Equipe (.team)", "Localização" }, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        tabelaTimesExistentes = new JTable(modelTimesExistentes);
        tabelaTimesExistentes.setRowHeight(24);

        cardTimesExistentes.add(new JScrollPane(tabelaTimesExistentes), BorderLayout.CENTER);

        colTimes.add(cardCriarTeam, BorderLayout.NORTH);
        colTimes.add(cardTimesExistentes, BorderLayout.CENTER);

        centro.add(colRobos);
        centro.add(colTimes);

        painel.add(topo, BorderLayout.NORTH);
        painel.add(centro, BorderLayout.CENTER);
        return painel;
    }

    private void atualizarContadorNovoTime() {
        int count = 0;
        for (int i = 0; i < modelRobosDisponiveis.getRowCount(); i++) {
            if (Boolean.TRUE.equals(modelRobosDisponiveis.getValueAt(i, 0)))
                count++;
        }
        lblContadorSelecaoNovoTime.setText(count + " robô(s) marcado(s) para compor a nova equipe");
    }

    private void criarNovoTimeManual() {
        String nome = txtNovoTimeNome.getText().trim();
        String autor = txtNovoTimeAutor.getText().trim();
        String desc = txtNovoTimeDescricao.getText().trim();

        if (nome.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Informe o nome da equipe antes de salvar.", "Aviso",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<String> membros = new ArrayList<>();
        for (int i = 0; i < modelRobosDisponiveis.getRowCount(); i++) {
            if (Boolean.TRUE.equals(modelRobosDisponiveis.getValueAt(i, 0))) {
                membros.add((String) modelRobosDisponiveis.getValueAt(i, 1));
            }
        }

        if (membros.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Selecione pelo menos 1 robô na tabela à esquerda para compor o time.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean ok = robotManager.salvarTimeManual(nome, membros, autor, desc);
        if (ok) {
            log(String.format("[OK] Equipe '%s' (.team) salva com sucesso com %d robôs: %s", nome, membros.size(),
                    membros));
            JOptionPane.showMessageDialog(this, "Equipe '" + nome + ".team' criada e registrada com sucesso!",
                    "Equipe Criada", JOptionPane.INFORMATION_MESSAGE);
            txtNovoTimeNome.setText("");
            txtNovoTimeAutor.setText("");
            txtNovoTimeDescricao.setText("");
            atualizarRobosLocais();
        } else {
            JOptionPane.showMessageDialog(this, "Erro ao criar o arquivo .team para a equipe.", "Erro",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void abrirPastaRobosTorneio() {
        try {
            File dir = robotManager.getRobosDir();
            if (Desktop.isDesktopSupported() && dir.exists()) {
                Desktop.getDesktop().open(dir);
            } else {
                JOptionPane.showMessageDialog(this, "Caminho da pasta dos robôs: " + dir.getAbsolutePath(),
                        "Pasta de Robôs",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Não foi possível abrir a pasta automaticamente: " + ex.getMessage(),
                    "Aviso", JOptionPane.WARNING_MESSAGE);
        }
    }

    // =========================================================================
    // ABA 4: ARENA & EXECUÇÃO DA PARTIDA
    // =========================================================================

    private JPanel criarAbaExecucaoPartida() {
        JPanel painel = new JPanel(new BorderLayout(10, 10));
        painel.setBorder(new EmptyBorder(12, 14, 12, 14));

        // 1. Barra de Seleção de Partida
        JPanel painelPartida = new JPanel(new BorderLayout(10, 5));
        painelPartida.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), " 1. Partida do Chaveamento em Disputa ",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 13), new Color(15, 23, 42)));

        JPanel linhaPartida = new JPanel(new BorderLayout(10, 0));
        linhaPartida.setBorder(new EmptyBorder(4, 10, 4, 10));

        cbPartidas = new JComboBox<>();
        cbPartidas.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        cbPartidas.addActionListener(e -> onPartidaSelecionada());

        JPanel botoesPartida = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnResetarPartida = new JButton("Resetar Placar (0x0)");
        btnResetarPartida.setToolTipText("Zera o placar no backend e reinicia o status da partida");
        btnResetarPartida.addActionListener(e -> resetarPartidaAtual());

        JButton btnRecarregarChav = new JButton("Atualizar Chaveamento");
        btnRecarregarChav.addActionListener(e -> recarregarDados());

        botoesPartida.add(btnResetarPartida);
        botoesPartida.add(btnRecarregarChav);

        linhaPartida.add(new JLabel("Selecione a Partida: "), BorderLayout.WEST);
        linhaPartida.add(cbPartidas, BorderLayout.CENTER);
        linhaPartida.add(botoesPartida, BorderLayout.EAST);

        lblInfoPartida = new JLabel("Carregando chaveamento...");
        lblInfoPartida.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblInfoPartida.setForeground(new Color(71, 85, 105));
        lblInfoPartida.setBorder(new EmptyBorder(0, 10, 4, 10));

        painelPartida.add(linhaPartida, BorderLayout.CENTER);
        painelPartida.add(lblInfoPartida, BorderLayout.SOUTH);

        // 2. Banner de Duelo (Versus Banner)
        JPanel bannerVersus = criarBannerVersus();

        // 3. Cartões de Escalação Lado a Lado (Time A vs Time B)
        JPanel painelTimes = criarPainelTimes();

        // 4. Barra Inferior de Ação da Batalha
        JPanel barraAcaoBatalha = criarBarraAcaoBatalha();

        // Agrupamento central
        JPanel centro = new JPanel(new BorderLayout(8, 8));
        centro.add(bannerVersus, BorderLayout.NORTH);
        centro.add(painelTimes, BorderLayout.CENTER);
        centro.add(barraAcaoBatalha, BorderLayout.SOUTH);

        painel.add(painelPartida, BorderLayout.NORTH);
        painel.add(centro, BorderLayout.CENTER);
        return painel;
    }

    private JPanel criarBannerVersus() {
        JPanel banner = new JPanel(new BorderLayout(10, 0));
        banner.setBackground(new Color(248, 250, 252));
        banner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
                new EmptyBorder(8, 16, 8, 16)));

        lblVersusTimeA = new JLabel("TIME A", SwingConstants.CENTER);
        lblVersusTimeA.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblVersusTimeA.setForeground(new Color(2, 132, 199)); // Azul

        lblVersusPlacar = new JLabel("0  x  0", SwingConstants.CENTER);
        lblVersusPlacar.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblVersusPlacar.setForeground(new Color(15, 23, 42));

        lblVersusTimeB = new JLabel("TIME B", SwingConstants.CENTER);
        lblVersusTimeB.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblVersusTimeB.setForeground(new Color(220, 38, 38)); // Vermelho

        JPanel pCentro = new JPanel(new GridLayout(2, 1, 0, 2));
        pCentro.setOpaque(false);
        pCentro.add(lblVersusPlacar);

        lblVersusFaseStatus = new JLabel("Fase: - | Status: AGUARDANDO", SwingConstants.CENTER);
        lblVersusFaseStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblVersusFaseStatus.setForeground(new Color(100, 116, 139));
        pCentro.add(lblVersusFaseStatus);

        banner.add(lblVersusTimeA, BorderLayout.WEST);
        banner.add(pCentro, BorderLayout.CENTER);
        banner.add(lblVersusTimeB, BorderLayout.EAST);
        return banner;
    }

    private JPanel criarPainelTimes() {
        JPanel painel = new JPanel(new GridLayout(1, 2, 14, 0));

        // Time A (Esquerda)
        modelTimesA = criarModeloTabelaTimes();
        lblContadorA = new JLabel("[!] Nenhuma equipe selecionada");
        lblContadorA.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblContadorA.setForeground(new Color(185, 28, 28));
        tabelaTimesA = criarTabelaTimes(modelTimesA, lblContadorA);
        lblNomeTimeA = new JLabel("TIME A");
        lblNomeTimeA.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblNomeTimeA.setForeground(new Color(2, 132, 199)); // Azul

        JPanel cardA = criarCardEquipe("TIME A", lblNomeTimeA, tabelaTimesA, modelTimesA, lblContadorA,
                new Color(224, 242, 254));

        // Time B (Direita)
        modelTimesB = criarModeloTabelaTimes();
        lblContadorB = new JLabel("[!] Nenhuma equipe selecionada");
        lblContadorB.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblContadorB.setForeground(new Color(185, 28, 28));
        tabelaTimesB = criarTabelaTimes(modelTimesB, lblContadorB);
        lblNomeTimeB = new JLabel("TIME B");
        lblNomeTimeB.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblNomeTimeB.setForeground(new Color(220, 38, 38)); // Vermelho

        JPanel cardB = criarCardEquipe("TIME B", lblNomeTimeB, tabelaTimesB, modelTimesB, lblContadorB,
                new Color(254, 226, 226));

        painel.add(cardA);
        painel.add(cardB);
        return painel;
    }

    private JPanel criarCardEquipe(String tituloTag, JLabel lblNomeTime, JTable tabela, DefaultTableModel model,
            JLabel lblContador, Color bgHeader) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBorder(BorderFactory.createLineBorder(new Color(203, 213, 225), 1));

        JPanel topo = new JPanel(new BorderLayout());
        topo.setBackground(bgHeader);
        topo.setBorder(new EmptyBorder(8, 10, 8, 10));
        topo.add(lblNomeTime, BorderLayout.WEST);

        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        filtros.setOpaque(false);

        JTextField txtFiltro = new JTextField(10);
        txtFiltro.setToolTipText("Filtrar equipes (.team) na lista...");
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        tabela.setRowSorter(sorter);
        txtFiltro.addCaretListener(e -> {
            String text = txtFiltro.getText().trim();
            sorter.setRowFilter(text.isEmpty() ? null : RowFilter.regexFilter("(?i)" + text, 1, 2));
        });

        JButton btnLimpar = new JButton("Limpar");
        btnLimpar.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnLimpar.addActionListener(e -> {
            isUpdatingTableSelection = true;
            try {
                for (int i = 0; i < model.getRowCount(); i++)
                    model.setValueAt(false, i, 0);
            } finally {
                isUpdatingTableSelection = false;
            }
            atualizarStatusSelecaoTime(model, lblContador);
        });

        JButton btnSugerir = new JButton("Auto-Sugerir");
        btnSugerir.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnSugerir.addActionListener(e -> autoSugerirTime(lblNomeTime.getText(), model, lblContador));

        filtros.add(new JLabel("Buscar:"));
        filtros.add(txtFiltro);
        filtros.add(btnSugerir);
        filtros.add(btnLimpar);
        topo.add(filtros, BorderLayout.EAST);

        JScrollPane scroll = new JScrollPane(tabela);
        scroll.setPreferredSize(new Dimension(300, 160));

        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        rodape.setBackground(new Color(248, 250, 252));
        rodape.add(lblContador);

        card.add(topo, BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        card.add(rodape, BorderLayout.SOUTH);
        return card;
    }

    private DefaultTableModel criarModeloTabelaTimes() {
        return new DefaultTableModel(new Object[] { "Escalar", "Equipe (.team)", "Integrantes" }, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };
    }

    private JTable criarTabelaTimes(DefaultTableModel model, JLabel lblContador) {
        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(0).setMinWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(210);
        table.getColumnModel().getColumn(2).setPreferredWidth(260);
        table.getTableHeader().setReorderingAllowed(false);

        model.addTableModelListener(e -> {
            if (e.getColumn() == 0 && !isUpdatingTableSelection) {
                int row = e.getFirstRow();
                if (row >= 0 && row < model.getRowCount()) {
                    Boolean checked = (Boolean) model.getValueAt(row, 0);
                    if (Boolean.TRUE.equals(checked)) {
                        isUpdatingTableSelection = true;
                        try {
                            for (int i = 0; i < model.getRowCount(); i++) {
                                if (i != row && Boolean.TRUE.equals(model.getValueAt(i, 0))) {
                                    model.setValueAt(false, i, 0);
                                }
                            }
                        } finally {
                            isUpdatingTableSelection = false;
                        }
                    }
                }
                atualizarStatusSelecaoTime(model, lblContador);
            }
        });
        return table;
    }

    private void atualizarStatusSelecaoTime(DefaultTableModel model, JLabel lblContador) {
        if (model == null || lblContador == null)
            return;
        String timeSelecionado = null;
        String membrosStr = null;
        for (int i = 0; i < model.getRowCount(); i++) {
            Boolean checked = (Boolean) model.getValueAt(i, 0);
            if (Boolean.TRUE.equals(checked)) {
                timeSelecionado = (String) model.getValueAt(i, 1);
                if (model.getColumnCount() > 2) {
                    membrosStr = (String) model.getValueAt(i, 2);
                }
                break;
            }
        }

        if (timeSelecionado != null) {
            String nomeCurto = timeSelecionado;
            if (nomeCurto.startsWith("torneio_teams.")) {
                nomeCurto = nomeCurto.substring("torneio_teams.".length());
            }
            if (nomeCurto.endsWith(".team")) {
                nomeCurto = nomeCurto.substring(0, nomeCurto.length() - 5);
            }
            if (membrosStr != null && !membrosStr.isBlank()) {
                lblContador.setText("[OK] Equipe: " + nomeCurto + " (" + membrosStr + ")");
            } else {
                lblContador.setText("[OK] Equipe: " + nomeCurto);
            }
            lblContador.setForeground(new Color(22, 101, 52));
        } else {
            lblContador.setText("[!] Nenhuma equipe selecionada");
            lblContador.setForeground(new Color(185, 28, 28));
        }
    }

    private JPanel criarBarraAcaoBatalha() {
        JPanel painel = new JPanel(new BorderLayout(10, 4));
        painel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1),
                new EmptyBorder(8, 14, 8, 14)));
        painel.setBackground(new Color(248, 250, 252));

        lblModoBatalhaDesc = new JLabel("Modo: Disputa Round a Round (1 round com intervalo)");
        lblModoBatalhaDesc.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblModoBatalhaDesc.setForeground(new Color(30, 41, 59));

        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        botoes.setOpaque(false);

        btnIniciarBatalha = new CustomColoredButton(
                "Iniciar Batalha",
                new Color(22, 163, 74), // Verde normal
                new Color(34, 197, 94) // Verde hover
        );
        btnIniciarBatalha.setPreferredSize(new Dimension(320, 38));
        btnIniciarBatalha.addActionListener(e -> iniciarBatalhaSelecionada());

        btnAbortarBatalha = new CustomColoredButton(
                "Abortar Partida",
                new Color(220, 38, 38), // Vermelho normal
                new Color(239, 68, 68) // Vermelho hover
        );
        btnAbortarBatalha.setPreferredSize(new Dimension(170, 38));
        btnAbortarBatalha.setEnabled(false);
        btnAbortarBatalha.addActionListener(e -> abortarBatalhaAtual());

        botoes.add(btnIniciarBatalha);
        botoes.add(btnAbortarBatalha);

        painel.add(lblModoBatalhaDesc, BorderLayout.WEST);
        painel.add(botoes, BorderLayout.EAST);
        return painel;
    }

    // =========================================================================
    // RODAPÉ GLOBAL: CONSOLE DE LOGS
    // =========================================================================

    private JPanel criarPainelLogsGlobal() {
        JPanel painel = new JPanel(new BorderLayout(0, 4));
        painel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), " Console de Eventos e Logs da Arena ",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12)));

        txtLogs = new JTextArea(6, 80);
        txtLogs.setEditable(false);
        txtLogs.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtLogs.setBackground(new Color(15, 23, 42)); // Fundo Slate Escuro
        txtLogs.setForeground(new Color(241, 245, 249));
        txtLogs.setCaretColor(Color.WHITE);

        JScrollPane scroll = new JScrollPane(txtLogs);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        JPanel barraLogs = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton btnLimparLogs = new JButton("Limpar");
        btnLimparLogs.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnLimparLogs.addActionListener(e -> txtLogs.setText(""));
        barraLogs.add(btnLimparLogs);

        painel.add(scroll, BorderLayout.CENTER);
        painel.add(barraLogs, BorderLayout.SOUTH);
        return painel;
    }

    // =========================================================================
    // LÓGICA DE NEGÓCIO, CONEXÃO E PARTIDAS
    // =========================================================================

    public void log(String mensagem) {
        SwingUtilities.invokeLater(() -> {
            String hora = timeFmt.format(new Date());
            txtLogs.append(String.format("[%s] %s\n", hora, mensagem));
            txtLogs.setCaretPosition(txtLogs.getDocument().getLength());
        });
    }

    private void carregarDadosIniciaisAssincrono() {
        log("Iniciando Robocode Torneio Launcher...");
        atualizarRobosLocais();

        new Thread(() -> {
            autenticarEConectar();
            recarregarDados();
        }).start();
    }

    private void autenticarEConectar() {
        log("Conectando à API de Chaveamento em: " + config.apiBaseUrl);
        boolean ok = apiClient.autenticar(config.email, config.senha);
        SwingUtilities.invokeLater(() -> {
            if (ok) {
                lblStatusConexaoHeader.setText("[Conectado] " + config.apiBaseUrl);
                lblStatusConexaoHeader.setForeground(new Color(34, 197, 94));
                lblStatusConexaoDetalhe.setText("Conectado com Sucesso (" + config.apiBaseUrl + ")");
                lblStatusConexaoDetalhe.setForeground(new Color(22, 101, 52));
                log("[OK] Autenticação realizada com sucesso no backend!");
            } else {
                lblStatusConexaoHeader.setText("[Desconectado / Erro]");
                lblStatusConexaoHeader.setForeground(new Color(239, 68, 68));
                lblStatusConexaoDetalhe.setText("Desconectado / Falha de Autenticação");
                lblStatusConexaoDetalhe.setForeground(new Color(220, 38, 38));
                log("[Aviso] Não foi possível autenticar na API. Verifique credenciais na aba 1.");
            }
        });
    }

    private int prioridadeStatusPartida(String status) {
        if (status == null)
            return 2;
        String s = status.trim().toUpperCase();
        if ("EM_ANDAMENTO".equals(s)) {
            return 1; // 1. No começo: em andamento
        }
        if ("FINALIZADA".equals(s)) {
            return 3; // 3. No final: finalizadas
        }
        return 2; // 2. No meio: aguardando
    }

    private void ordenarPartidas(List<Partida> lista) {
        if (lista == null || lista.isEmpty())
            return;
        lista.sort((p1, p2) -> {
            int prio1 = prioridadeStatusPartida(p1.status);
            int prio2 = prioridadeStatusPartida(p2.status);
            if (prio1 != prio2) {
                return Integer.compare(prio1, prio2);
            }
            long id1 = p1.id != null ? p1.id : 0L;
            long id2 = p2.id != null ? p2.id : 0L;
            return Long.compare(id1, id2);
        });
    }

    private void recarregarDados() {
        log("Buscando equipes e chaveamento no servidor para o Torneio #" + config.torneioId + "...");
        new Thread(() -> {
            Map<Long, Time> times = apiClient.carregarTimes();
            List<Partida> partidas = apiClient.carregarPartidas(config.torneioId, times);

            SwingUtilities.invokeLater(() -> {
                ordenarPartidas(partidas);
                this.timesCadastrados = times;
                this.partidasCarregadas = partidas;

                lblResumoTorneio.setText("Torneio Selecionado: #" + config.torneioId);
                lblResumoEquipes.setText("Equipes Registradas: " + times.size() + " equipe(s)");
                lblResumoPartidas.setText("Partidas no Chaveamento: " + partidas.size() + " partida(s)");

                Long idSelecionado = null;
                Object itemAtual = cbPartidas.getSelectedItem();
                if (itemAtual instanceof Partida && ((Partida) itemAtual).id != null) {
                    idSelecionado = ((Partida) itemAtual).id;
                }

                cbPartidas.removeAllItems();
                Partida partidaParaSelecionar = null;

                if (partidas.isEmpty()) {
                    cbPartidas.addItem(new Partida() {
                        @Override
                        public String toString() {
                            return "-- Nenhuma partida encontrada no torneio #" + config.torneioId + " --";
                        }
                    });
                    lblInfoPartida.setText(
                            "Nenhuma partida listada. Gere o chaveamento no backend ou verifique o Torneio ID na aba 1.");
                    lblVersusTimeA.setText("NENHUM TIME");
                    lblVersusTimeB.setText("NENHUM TIME");
                    lblVersusPlacar.setText("0  x  0");
                    lblVersusFaseStatus.setText("Nenhuma partida ativa");
                } else {
                    for (Partida p : partidas) {
                        cbPartidas.addItem(p);
                        if (idSelecionado != null && idSelecionado.equals(p.id)) {
                            partidaParaSelecionar = p;
                        }
                    }
                    if (partidaParaSelecionar != null) {
                        cbPartidas.setSelectedItem(partidaParaSelecionar);
                    } else if (cbPartidas.getItemCount() > 0) {
                        cbPartidas.setSelectedIndex(0);
                    }
                    log("[Chaveamento] " + partidas.size() + " partida(s) carregada(s) do chaveamento!");
                }

                Object sel = cbPartidas.getSelectedItem();
                if (sel instanceof Partida && ((Partida) sel).id != null) {
                    Partida pSel = (Partida) sel;
                    atualizarBotaoBatalha(pSel);
                    autoSugerirTime(pSel.nomeTimeA, modelTimesA, lblContadorA);
                    autoSugerirTime(pSel.nomeTimeB, modelTimesB, lblContadorB);
                } else if (sel instanceof Partida) {
                    atualizarBotaoBatalha((Partida) sel);
                }
            });
        }).start();
    }

    private void atualizarRobosLocais() {
        todosRobosDisponiveis = robotManager.descobrirRobos();
        List<String> apenasRobos = robotManager.listarApenasRobos();
        List<String> apenasTimes = robotManager.listarApenasTimes();

        if (todosRobosDisponiveis.isEmpty()) {
            log("[Info] Nenhum robô ou equipe encontrado na pasta 'robots/torneio_teams'.");
        } else {
            log(String.format(
                    "[Info] Encontrados em 'robots/torneio_teams': %d robô(s) compilados e %d equipe(s) .team",
                    apenasRobos.size(), apenasTimes.size()));
        }

        // Atualiza tabelas da Arena (Aba 4) - APENAS OS TIMES (.team)
        preencherTabelaTimes(modelTimesA, apenasTimes);
        preencherTabelaTimes(modelTimesB, apenasTimes);
        atualizarStatusSelecaoTime(modelTimesA, lblContadorA);
        atualizarStatusSelecaoTime(modelTimesB, lblContadorB);

        // Atualiza tabelas da Aba 3 (Robôs & Times) - APENAS OS ROBÔS INDIVIDUAIS
        // (.class) na esquerda
        if (modelRobosDisponiveis != null) {
            modelRobosDisponiveis.setRowCount(0);
            for (String r : apenasRobos) {
                modelRobosDisponiveis.addRow(new Object[] { false, r });
            }
            atualizarContadorNovoTime();
        }

        if (modelTimesExistentes != null) {
            modelTimesExistentes.setRowCount(0);
            for (String t : apenasTimes) {
                modelTimesExistentes.addRow(new Object[] { t, "robots/torneio_teams/" + t });
            }
        }

        // Atualiza tabelas da Arena de Testes & Treino (Aba 5)
        atualizarListaCombatentesTeste();
        atualizarListaOponentesTeste();

        // Se uma partida estiver selecionada na Arena, reaplica auto-sugestão
        Object sel = cbPartidas.getSelectedItem();
        if (sel instanceof Partida && ((Partida) sel).id != null) {
            Partida p = (Partida) sel;
            autoSugerirTime(p.nomeTimeA, modelTimesA, lblContadorA);
            autoSugerirTime(p.nomeTimeB, modelTimesB, lblContadorB);
        }
    }

    private void preencherTabelaTimes(DefaultTableModel model, List<String> times) {
        if (model == null)
            return;
        String timeAnteriormenteMarcado = obterTimeMarcado(model);
        isUpdatingTableSelection = true;
        try {
            model.setRowCount(0);
            for (String t : times) {
                List<String> membros = robotManager.obterMembrosDoTime(t);
                String membrosStr = membros.isEmpty() ? "Sem integrantes" : String.join(", ", membros);
                boolean marcado = (timeAnteriormenteMarcado != null && timeAnteriormenteMarcado.equalsIgnoreCase(t));
                model.addRow(new Object[] { marcado, t, membrosStr });
            }
        } finally {
            isUpdatingTableSelection = false;
        }
    }

    private void onPartidaSelecionada() {
        Object item = cbPartidas.getSelectedItem();
        if (!(item instanceof Partida))
            return;
        Partida p = (Partida) item;
        if (p.id == null)
            return;

        String nomeA = p.nomeTimeA != null ? p.nomeTimeA : "TIME A";
        String nomeB = p.nomeTimeB != null ? p.nomeTimeB : "TIME B";

        lblNomeTimeA.setText(nomeA);
        lblNomeTimeB.setText(nomeB);
        lblVersusTimeA.setText(nomeA);
        lblVersusTimeB.setText(nomeB);

        String pA = p.placarTimeA != null ? p.placarTimeA.toString() : "0";
        String pB = p.placarTimeB != null ? p.placarTimeB.toString() : "0";
        String status = p.status != null ? p.status : "AGUARDANDO";
        String fase = p.fase != null ? p.fase : "Fase Regular";

        lblVersusPlacar.setText(String.format("%s  x  %s", pA, pB));
        lblVersusFaseStatus.setText(String.format("Fase: %s | Status: %s | Partida #%d", fase, status, p.id));
        lblInfoPartida.setText(String.format("Partida #%d | %s | Status: %s | Placar Atual: %s (%s) x (%s) %s",
                p.id, fase, status, nomeA, pA, pB, nomeB));

        // Auto-sugere as equipes (.team) correspondentes a cada time da partida
        autoSugerirTime(nomeA, modelTimesA, lblContadorA);
        autoSugerirTime(nomeB, modelTimesB, lblContadorB);
        atualizarBotaoBatalha(p);
    }

    private void atualizarBotaoBatalha(Partida p) {
        if (p == null || p.id == null) {
            btnIniciarBatalha.setText("Iniciar Batalha");
            btnIniciarBatalha.setEnabled(false);
            return;
        }

        if ("FINALIZADA".equals(p.status)) {
            int pA = p.placarTimeA != null ? p.placarTimeA : 0;
            int pB = p.placarTimeB != null ? p.placarTimeB : 0;
            btnIniciarBatalha.setText(String.format("Partida Finalizada (%d x %d)", pA, pB));
            btnIniciarBatalha.setEnabled(false);
            return;
        }

        btnIniciarBatalha.setEnabled(true);
        int pA = p.placarTimeA != null ? p.placarTimeA : 0;
        int pB = p.placarTimeB != null ? p.placarTimeB : 0;
        int total = pA + pB;

        if (chkRoundARound != null && chkRoundARound.isSelected()) {
            lblModoBatalhaDesc.setText("Modo: Disputa Round a Round (1 round com intervalo técnico)");
            if (total == 0) {
                btnIniciarBatalha.setText("Iniciar Round 1 (0 x 0)");
            } else if (total == 1) {
                btnIniciarBatalha.setText(String.format("Iniciar Round 2 (%d x %d)", pA, pB));
            } else if (total == 2) {
                btnIniciarBatalha.setText(String.format("Iniciar Round 3 - Decisivo (%d x %d)", pA, pB));
            } else {
                btnIniciarBatalha.setText(String.format("Iniciar Próximo Round (%d x %d)", pA, pB));
            }
        } else {
            lblModoBatalhaDesc.setText("Modo: Série Contínua (" + config.maxRounds + " rounds)");
            btnIniciarBatalha.setText("Iniciar Batalha Completa");
        }
    }

    private void autoSugerirTime(String nomeTime, DefaultTableModel model, JLabel lblContador) {
        if (nomeTime == null || nomeTime.isBlank() || model == null)
            return;
        List<String> todosTimes = robotManager.listarApenasTimes();
        String timeSugerido = robotManager.sugerirTimeParaNome(nomeTime, todosTimes);

        isUpdatingTableSelection = true;
        try {
            for (int i = 0; i < model.getRowCount(); i++) {
                String t = (String) model.getValueAt(i, 1);
                boolean match = (timeSugerido != null && timeSugerido.equalsIgnoreCase(t));
                model.setValueAt(match, i, 0);
            }
        } finally {
            isUpdatingTableSelection = false;
        }

        if (timeSugerido != null) {
            log("[Escalação] Equipe sugerida para '" + nomeTime + "': " + timeSugerido);
        } else {
            log("[Aviso] Não foi encontrada equipe (.team) correspondente ao nome '" + nomeTime + "'.");
        }
        atualizarStatusSelecaoTime(model, lblContador);
    }

    private String obterTimeMarcado(DefaultTableModel model) {
        if (model == null)
            return null;
        for (int i = 0; i < model.getRowCount(); i++) {
            Boolean checked = (Boolean) model.getValueAt(i, 0);
            if (Boolean.TRUE.equals(checked)) {
                return (String) model.getValueAt(i, 1);
            }
        }
        return null;
    }

    private void executarCompilacaoRobos() {
        if (btnCompilarHeader != null)
            btnCompilarHeader.setEnabled(false);
        log("[Compilador] Compilando arquivos .java em 'robots/torneio_teams'...");
        new Thread(() -> {
            RobotManager.CompilacaoResult res = robotManager.compilarTodosOsRobos();
            SwingUtilities.invokeLater(() -> {
                if (btnCompilarHeader != null)
                    btnCompilarHeader.setEnabled(true);
                String hora = timeFmt.format(new Date());
                if (res.sucesso) {
                    lblStatusCompilacao.setText("Última compilação: Sucesso às " + hora);
                    lblStatusCompilacao.setForeground(new Color(22, 101, 52));
                    log("[OK] " + res.mensagem);
                    JOptionPane.showMessageDialog(this, res.mensagem, "Compilação Concluída",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    lblStatusCompilacao.setText("Última compilação: Erro às " + hora);
                    lblStatusCompilacao.setForeground(new Color(220, 38, 38));
                    log("[Erro] " + res.mensagem + "\n" + res.detalhes);
                    JOptionPane.showMessageDialog(this, res.mensagem + "\n\nDetalhes:\n" + res.detalhes,
                            "Erro na Compilação", JOptionPane.ERROR_MESSAGE);
                }
                atualizarRobosLocais();
            });
        }).start();
    }

    private void resetarPartidaAtual() {
        Object item = cbPartidas.getSelectedItem();
        if (!(item instanceof Partida))
            return;
        Partida p = (Partida) item;
        if (p.id == null)
            return;

        int opt = JOptionPane.showConfirmDialog(this,
                "Deseja resetar o placar da Partida #" + p.id + " para 0 x 0 no backend?",
                "Confirmar Reset", JOptionPane.YES_NO_OPTION);
        if (opt == JOptionPane.YES_OPTION) {
            new Thread(() -> {
                boolean ok = apiClient.resetarPartida(p.id);
                if (ok) {
                    log("[Reset] Partida #" + p.id + " resetada com sucesso para 0 x 0.");
                    recarregarDados();
                } else {
                    log("[Aviso] Erro ao tentar resetar partida #" + p.id);
                }
            }).start();
        }
    }

    private void iniciarBatalhaSelecionada() {
        Object item = cbPartidas.getSelectedItem();
        if (!(item instanceof Partida)) {
            JOptionPane.showMessageDialog(this, "Selecione uma partida válida antes de iniciar.", "Aviso",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        Partida partida = (Partida) item;
        String specA = obterTimeMarcado(modelTimesA);
        String specB = obterTimeMarcado(modelTimesB);

        // Auto-recuperação resiliente se o checkbox não estiver marcado no momento
        if (specA == null && partida.nomeTimeA != null) {
            autoSugerirTime(partida.nomeTimeA, modelTimesA, lblContadorA);
            specA = obterTimeMarcado(modelTimesA);
            if (specA == null) {
                List<String> todos = robotManager.listarApenasTimes();
                specA = robotManager.sugerirTimeParaNome(partida.nomeTimeA, todos);
            }
        }
        if (specB == null && partida.nomeTimeB != null) {
            autoSugerirTime(partida.nomeTimeB, modelTimesB, lblContadorB);
            specB = obterTimeMarcado(modelTimesB);
            if (specB == null) {
                List<String> todos = robotManager.listarApenasTimes();
                specB = robotManager.sugerirTimeParaNome(partida.nomeTimeB, todos);
            }
        }

        if (specA == null) {
            JOptionPane.showMessageDialog(this,
                    "Selecione a equipe (.team) para o TIME A (" + lblNomeTimeA.getText() + ").",
                    "Escalação Incompleta", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (specB == null) {
            JOptionPane.showMessageDialog(this,
                    "Selecione a equipe (.team) para o TIME B (" + lblNomeTimeB.getText() + ").",
                    "Escalação Incompleta", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 0. Auto-compilação preventiva com backup dos códigos
        log("[Compilador] Verificando e compilando códigos dos robôs antes de iniciar...");
        RobotManager.CompilacaoResult compResult = robotManager.compilarTodosOsRobos("partida_" + partida.id);
        if (!compResult.sucesso) {
            log("[ERRO DE COMPILAÇÃO]\n" + compResult.detalhes);
            JOptionPane.showMessageDialog(this,
                    "Não foi possível iniciar o round porque há erro de compilação no código dos robôs!\n\n"
                            + "Detalhes:\n" + compResult.detalhes + "\n"
                            + "O aluno deve corrigir o erro de sintaxe antes de iniciar.",
                    "Erro de Compilação",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        log("[OK] Códigos compilados e validados com sucesso!");
        todosRobosDisponiveis = robotManager.descobrirRobos();

        // Salva configurações atuais
        salvarTodasConfiguracoes();

        int duracaoEfetiva = config.semLimiteTempo ? 0 : config.duracaoSegundos;

        List<String> membrosA = robotManager.obterMembrosDoTime(specA);
        List<String> membrosB = robotManager.obterMembrosDoTime(specB);

        log(String.format("[Combate] Escalação pronta: Time A (%s: %s) vs Time B (%s: %s)",
                specA, membrosA, specB, membrosB));

        btnIniciarBatalha.setEnabled(false);
        btnAbortarBatalha.setEnabled(true);

        battleRunnerAtual = new BattleRunner(
                partida,
                specA,
                specB,
                duracaoEfetiva,
                config.tps,
                config.maxRounds,
                config.vitoriasParaGanhar,
                config.finalizarComDuasSeguidas,
                config.disputaRoundARound,
                config.exibirJanela,
                config.campoLargura,
                config.campoAltura,
                apiClient,
                this::log,
                this::exibirModalResultado,
                () -> SwingUtilities.invokeLater(() -> {
                    btnIniciarBatalha.setEnabled(true);
                    btnAbortarBatalha.setEnabled(false);
                    recarregarDados();
                }));

        battleRunnerAtual.iniciarBatalhaAssincrona();
    }

    private void exibirModalResultado(ResultadoBatalha res) {
        SwingUtilities.invokeLater(() -> {
            ResultadoBatalhaDialog dialog = new ResultadoBatalhaDialog(this, res);
            dialog.setVisible(true);
        });
    }

    private void abortarBatalhaAtual() {
        if (battleRunnerAtual != null) {
            BattleRunner runner = battleRunnerAtual;
            battleRunnerAtual = null;
            new Thread(() -> {
                try {
                    runner.abortar();
                } catch (Throwable ignored) {
                }
            }, "Abort-Atual-Thread").start();
            btnAbortarBatalha.setEnabled(false);
            btnIniciarBatalha.setEnabled(true);
        }
    }

    // =========================================================================
    // ABA 5: ARENA DE TESTES & TREINO LIVRE (INDIVIDUAL / EQUIPES)
    // =========================================================================

    private JPanel criarAbaArenaTestes() {
        JPanel painel = new JPanel(new BorderLayout(10, 10));
        painel.setBorder(new EmptyBorder(12, 12, 12, 12));

        // 1. Banner Superior Informativo de Treino
        JPanel banner = criarBannerTreino();

        // 2. Painel Central: Combatente (esquerda) vs Oponentes (direita)
        JPanel centro = new JPanel(new GridLayout(1, 2, 14, 0));
        centro.add(criarCardCombatenteTeste());
        centro.add(criarCardOponentesTeste());

        // 3. Barra Inferior: Controles de Batalha de Teste
        JPanel rodapeControles = criarBarraAcaoTeste();

        JPanel meio = new JPanel(new BorderLayout(10, 10));
        meio.add(banner, BorderLayout.NORTH);
        meio.add(centro, BorderLayout.CENTER);

        painel.add(meio, BorderLayout.CENTER);
        painel.add(rodapeControles, BorderLayout.SOUTH);

        return painel;
    }

    private JPanel criarBannerTreino() {
        JPanel banner = new JPanel(new BorderLayout(12, 4));
        banner.setBackground(new Color(248, 250, 252));
        banner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
                new EmptyBorder(10, 16, 10, 16)));

        JLabel lblTitulo = new JLabel("ARENA DE TESTES E TREINO LIVRE");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblTitulo.setForeground(new Color(15, 23, 42));

        JLabel lblSub = new JLabel(
                "Ambiente de experimentação isolado: teste robôs individuais ou equipes completas sem impactar o chaveamento oficial.");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(new Color(71, 85, 105));

        JPanel pTexto = new JPanel(new GridLayout(2, 1, 0, 2));
        pTexto.setOpaque(false);
        pTexto.add(lblTitulo);
        pTexto.add(lblSub);

        banner.add(pTexto, BorderLayout.WEST);
        return banner;
    }

    private JPanel criarCardCombatenteTeste() {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1),
                " 1. Combatente em Teste (Seu Robô ou Equipe) ",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 13), new Color(15, 23, 42)));
        card.setBackground(Color.WHITE);

        // Topo: Linha 1 (Checkboxes / Modos) e Linha 2 (Filtro de Busca) separadas
        JPanel topo = new JPanel();
        topo.setLayout(new BoxLayout(topo, BoxLayout.Y_AXIS));
        topo.setOpaque(false);
        topo.setBorder(new EmptyBorder(6, 10, 6, 10));

        JPanel linha1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
        linha1.setOpaque(false);

        rbModoTesteIndividual = new JRadioButton("Robô Individual (.class)", true);
        rbModoTesteIndividual.setFont(new Font("Segoe UI", Font.BOLD, 12));
        rbModoTesteIndividual.setOpaque(false);

        rbModoTesteEquipe = new JRadioButton("Equipe Completa (.team)", false);
        rbModoTesteEquipe.setFont(new Font("Segoe UI", Font.BOLD, 12));
        rbModoTesteEquipe.setOpaque(false);

        ButtonGroup bg = new ButtonGroup();
        bg.add(rbModoTesteIndividual);
        bg.add(rbModoTesteEquipe);

        rbModoTesteIndividual.addActionListener(e -> {
            atualizarListaCombatentesTeste();
            if (rbOponenteTurma != null && rbOponenteTurma.isSelected()) {
                atualizarListaOponentesTeste();
            }
        });
        rbModoTesteEquipe.addActionListener(e -> {
            atualizarListaCombatentesTeste();
            if (rbOponenteTurma != null && rbOponenteTurma.isSelected()) {
                atualizarListaOponentesTeste();
            }
        });

        linha1.add(rbModoTesteIndividual);
        linha1.add(rbModoTesteEquipe);

        JPanel linha2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        linha2.setOpaque(false);

        JLabel lblBuscar = new JLabel("Buscar:");
        lblBuscar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        txtFiltroCombatenteTeste = new JTextField(18);
        txtFiltroCombatenteTeste.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        txtFiltroCombatenteTeste.setToolTipText("Filtrar robô ou equipe...");

        JButton btnLimparFiltro = new JButton("Limpar");
        btnLimparFiltro.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnLimparFiltro.addActionListener(e -> {
            txtFiltroCombatenteTeste.setText("");
        });

        linha2.add(lblBuscar);
        linha2.add(txtFiltroCombatenteTeste);
        linha2.add(btnLimparFiltro);

        topo.add(linha1);
        topo.add(Box.createVerticalStrut(4));
        topo.add(linha2);

        // Centro: Tabela com seleção única
        modelCombatenteTeste = criarModeloTabelaCombatenteTeste();
        lblCombatenteSelecionadoTeste = new JLabel("[!] Nenhum combatente selecionado");
        lblCombatenteSelecionadoTeste.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblCombatenteSelecionadoTeste.setForeground(new Color(185, 28, 28));

        tabelaCombatenteTeste = criarTabelaCombatenteTeste(modelCombatenteTeste);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(modelCombatenteTeste);
        tabelaCombatenteTeste.setRowSorter(sorter);
        txtFiltroCombatenteTeste.addCaretListener(e -> {
            String text = txtFiltroCombatenteTeste.getText().trim();
            sorter.setRowFilter(text.isEmpty() ? null : RowFilter.regexFilter("(?i)" + text, 1, 2));
        });

        JScrollPane scroll = new JScrollPane(tabelaCombatenteTeste);

        // Rodapé do Card
        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        rodape.setBackground(new Color(248, 250, 252));
        rodape.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(226, 232, 240)));
        rodape.add(lblCombatenteSelecionadoTeste);

        card.add(topo, BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        card.add(rodape, BorderLayout.SOUTH);
        return card;
    }

    private JPanel criarCardOponentesTeste() {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1),
                " 2. Adversários para o Treino (Samples / Turma) ",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 13), new Color(15, 23, 42)));
        card.setBackground(Color.WHITE);

        // Topo: Linha 1 (Checkboxes / Modos) e Linha 2 (Botões de Ação) separadas
        JPanel topo = new JPanel();
        topo.setLayout(new BoxLayout(topo, BoxLayout.Y_AXIS));
        topo.setOpaque(false);
        topo.setBorder(new EmptyBorder(6, 10, 6, 10));

        JPanel linha1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        linha1.setOpaque(false);

        rbOponenteSample = new JRadioButton("Amostras Robocode (Samples)", true);
        rbOponenteSample.setFont(new Font("Segoe UI", Font.BOLD, 12));
        rbOponenteSample.setOpaque(false);

        rbOponenteTurma = new JRadioButton("Robôs do Torneio (inclui Sample Team)", false);
        rbOponenteTurma.setFont(new Font("Segoe UI", Font.BOLD, 12));
        rbOponenteTurma.setOpaque(false);

        ButtonGroup bg = new ButtonGroup();
        bg.add(rbOponenteSample);
        bg.add(rbOponenteTurma);

        rbOponenteSample.addActionListener(e -> atualizarListaOponentesTeste());
        rbOponenteTurma.addActionListener(e -> atualizarListaOponentesTeste());

        linha1.add(rbOponenteSample);
        linha1.add(rbOponenteTurma);

        JPanel linha2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        linha2.setOpaque(false);

        JLabel lblAtalhos = new JLabel("Atalhos:");
        lblAtalhos.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JButton btnSelInteractive = new JButton("sample.Interactive");
        btnSelInteractive.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnSelInteractive.setForeground(new Color(2, 132, 199));
        btnSelInteractive.setToolTipText("Selecionar sample.Interactive (permite pilotar manualmente com o teclado)");
        btnSelInteractive.addActionListener(e -> selecionarOponenteRapido("sample.Interactive"));

        JButton btnSelSittingDuck = new JButton("SittingDuck");
        btnSelSittingDuck.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnSelSittingDuck.setToolTipText("Selecionar sample.SittingDuck (alvo imóvel)");
        btnSelSittingDuck.addActionListener(e -> selecionarOponenteRapido("sample.SittingDuck"));

        JButton btnLimparOponentes = new JButton("Limpar");
        btnLimparOponentes.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnLimparOponentes.addActionListener(e -> limparSelecaoOponentes());

        linha2.add(lblAtalhos);
        linha2.add(btnSelInteractive);
        linha2.add(btnSelSittingDuck);
        linha2.add(btnLimparOponentes);

        topo.add(linha1);
        topo.add(Box.createVerticalStrut(4));
        topo.add(linha2);

        // Centro: Tabela com seleção múltipla de adversários
        modelOponentesTeste = criarModeloTabelaOponentesTeste();
        lblOponentesSelecionadosTeste = new JLabel("[!] Nenhum adversário selecionado");
        lblOponentesSelecionadosTeste.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblOponentesSelecionadosTeste.setForeground(new Color(185, 28, 28));

        tabelaOponentesTeste = criarTabelaOponentesTeste(modelOponentesTeste);
        JScrollPane scroll = new JScrollPane(tabelaOponentesTeste);

        // Rodapé do Card
        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        rodape.setBackground(new Color(248, 250, 252));
        rodape.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(226, 232, 240)));
        rodape.add(lblOponentesSelecionadosTeste);

        card.add(topo, BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        card.add(rodape, BorderLayout.SOUTH);
        return card;
    }

    private JPanel criarBarraAcaoTeste() {
        JPanel painel = new JPanel();
        painel.setLayout(new BoxLayout(painel, BoxLayout.Y_AXIS));
        painel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1),
                new EmptyBorder(8, 14, 8, 14)));
        painel.setBackground(new Color(248, 250, 252));

        // Linha 1: Campos de Configuração e Parâmetros
        JPanel linha1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
        linha1.setOpaque(false);

        JLabel lblRounds = new JLabel("Rounds:");
        lblRounds.setFont(new Font("Segoe UI", Font.BOLD, 12));
        cbRoundsTeste = new JComboBox<>(new String[] {
                "1 Round (Rápido)",
                "3 Rounds (Padrão)",
                "5 Rounds",
                "10 Rounds"
        });
        cbRoundsTeste.setSelectedIndex(0); // 1 Round default para testes rápidos
        cbRoundsTeste.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        JLabel lblTps = new JLabel("Velocidade (TPS):");
        lblTps.setFont(new Font("Segoe UI", Font.BOLD, 12));
        cbTpsTeste = new JComboBox<>(new String[] {
                "30 TPS (Recomendado p/ Controle Manual)",
                "45 TPS (Moderado)",
                "60 TPS (Padrão Robocode)",
                "120 TPS (Acelerado)",
                "Máxima (Sem Limite)"
        });
        cbTpsTeste.setSelectedIndex(0); // 30 TPS ideal para controle manual com o teclado!
        cbTpsTeste.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        chkAutoCompilarTeste = new JCheckBox("Auto-compilar antes", true);
        chkAutoCompilarTeste.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chkAutoCompilarTeste.setOpaque(false);

        linha1.add(lblRounds);
        linha1.add(cbRoundsTeste);
        linha1.add(Box.createHorizontalStrut(10));
        linha1.add(lblTps);
        linha1.add(cbTpsTeste);
        linha1.add(Box.createHorizontalStrut(10));
        linha1.add(chkAutoCompilarTeste);

        // Linha 2: Linha própria para Status e Botões de Ação
        JPanel linha2 = new JPanel(new BorderLayout(14, 0));
        linha2.setOpaque(false);
        linha2.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(226, 232, 240)),
                new EmptyBorder(8, 0, 2, 0)));

        lblStatusTeste = new JLabel("Pronto para iniciar teste.");
        lblStatusTeste.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblStatusTeste.setForeground(new Color(71, 85, 105));

        JPanel pBotoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        pBotoes.setOpaque(false);

        btnIniciarTeste = new CustomColoredButton(
                "Iniciar Batalha de Teste",
                new Color(22, 163, 74),
                new Color(34, 197, 94));
        btnIniciarTeste.setPreferredSize(new Dimension(260, 38));
        btnIniciarTeste.addActionListener(e -> iniciarBatalhaTeste());

        btnAbortarTeste = new CustomColoredButton(
                "Abortar Teste",
                new Color(220, 38, 38),
                new Color(239, 68, 68));
        btnAbortarTeste.setPreferredSize(new Dimension(140, 38));
        btnAbortarTeste.setEnabled(false);
        btnAbortarTeste.addActionListener(e -> abortarBatalhaTeste());

        pBotoes.add(btnIniciarTeste);
        pBotoes.add(btnAbortarTeste);

        linha2.add(lblStatusTeste, BorderLayout.WEST);
        linha2.add(pBotoes, BorderLayout.EAST);

        painel.add(linha1);
        painel.add(Box.createVerticalStrut(4));
        painel.add(linha2);
        return painel;
    }

    private DefaultTableModel criarModeloTabelaCombatenteTeste() {
        return new DefaultTableModel(new Object[] { "Testar", "Robô / Equipe", "Detalhes / Integrantes" }, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };
    }

    private JTable criarTabelaCombatenteTeste(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setMaxWidth(55);
        table.getColumnModel().getColumn(0).setMinWidth(55);
        table.getColumnModel().getColumn(1).setPreferredWidth(210);
        table.getColumnModel().getColumn(2).setPreferredWidth(250);
        table.getTableHeader().setReorderingAllowed(false);

        model.addTableModelListener(e -> {
            if (e.getColumn() == 0 && !isUpdatingTestCombatenteSelection) {
                int row = e.getFirstRow();
                if (row >= 0 && row < model.getRowCount()) {
                    Boolean checked = (Boolean) model.getValueAt(row, 0);
                    if (Boolean.TRUE.equals(checked)) {
                        isUpdatingTestCombatenteSelection = true;
                        try {
                            for (int i = 0; i < model.getRowCount(); i++) {
                                if (i != row && Boolean.TRUE.equals(model.getValueAt(i, 0))) {
                                    model.setValueAt(false, i, 0);
                                }
                            }
                        } finally {
                            isUpdatingTestCombatenteSelection = false;
                        }
                    }
                }
                atualizarStatusSelecaoCombatenteTeste();
            }
        });

        return table;
    }

    private DefaultTableModel criarModeloTabelaOponentesTeste() {
        return new DefaultTableModel(new Object[] { "Lutar", "Adversário", "Estilo / Categoria", "Descrição / Dica" },
                0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };
    }

    private JTable criarTabelaOponentesTeste(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setMaxWidth(55);
        table.getColumnModel().getColumn(0).setMinWidth(55);
        table.getColumnModel().getColumn(1).setPreferredWidth(160);
        table.getColumnModel().getColumn(2).setPreferredWidth(170);
        table.getColumnModel().getColumn(3).setPreferredWidth(250);
        table.getTableHeader().setReorderingAllowed(false);

        model.addTableModelListener(e -> {
            if (e.getColumn() == 0) {
                atualizarStatusSelecaoOponentesTeste();
            }
        });

        return table;
    }

    private void atualizarStatusSelecaoCombatenteTeste() {
        if (modelCombatenteTeste == null || lblCombatenteSelecionadoTeste == null)
            return;
        String sel = null;
        for (int i = 0; i < modelCombatenteTeste.getRowCount(); i++) {
            if (Boolean.TRUE.equals(modelCombatenteTeste.getValueAt(i, 0))) {
                sel = (String) modelCombatenteTeste.getValueAt(i, 1);
                break;
            }
        }
        if (sel != null && !sel.isBlank()) {
            lblCombatenteSelecionadoTeste.setText("Combatente: " + sel + " [Pronto para Teste]");
            lblCombatenteSelecionadoTeste.setForeground(new Color(22, 101, 52));
        } else {
            lblCombatenteSelecionadoTeste.setText("[!] Nenhum combatente selecionado");
            lblCombatenteSelecionadoTeste.setForeground(new Color(185, 28, 28));
        }
    }

    private void atualizarStatusSelecaoOponentesTeste() {
        if (modelOponentesTeste == null || lblOponentesSelecionadosTeste == null)
            return;
        List<String> sels = new ArrayList<>();
        for (int i = 0; i < modelOponentesTeste.getRowCount(); i++) {
            if (Boolean.TRUE.equals(modelOponentesTeste.getValueAt(i, 0))) {
                sels.add((String) modelOponentesTeste.getValueAt(i, 1));
            }
        }
        if (sels.isEmpty()) {
            lblOponentesSelecionadosTeste.setText("[!] Nenhum adversário selecionado");
            lblOponentesSelecionadosTeste.setForeground(new Color(185, 28, 28));
        } else if (sels.size() == 1) {
            lblOponentesSelecionadosTeste.setText("Adversário: " + sels.get(0) + " (1 selecionado)");
            lblOponentesSelecionadosTeste.setForeground(new Color(22, 101, 52));
        } else {
            lblOponentesSelecionadosTeste
                    .setText(String.format("Adversários: %d selecionados (%s)", sels.size(), String.join(", ", sels)));
            lblOponentesSelecionadosTeste.setForeground(new Color(22, 101, 52));
        }
    }

    private void selecionarOponenteRapido(String className) {
        if (rbOponenteSample != null && !rbOponenteSample.isSelected()) {
            rbOponenteSample.setSelected(true);
            atualizarListaOponentesTeste();
        }
        if (modelOponentesTeste == null)
            return;
        for (int i = 0; i < modelOponentesTeste.getRowCount(); i++) {
            String nome = (String) modelOponentesTeste.getValueAt(i, 1);
            boolean match = (nome != null && nome.equalsIgnoreCase(className));
            modelOponentesTeste.setValueAt(match, i, 0);
        }
        atualizarStatusSelecaoOponentesTeste();
    }

    private void limparSelecaoOponentes() {
        if (modelOponentesTeste == null)
            return;
        for (int i = 0; i < modelOponentesTeste.getRowCount(); i++) {
            modelOponentesTeste.setValueAt(false, i, 0);
        }
        atualizarStatusSelecaoOponentesTeste();
    }

    private void atualizarListaCombatentesTeste() {
        if (modelCombatenteTeste == null || rbModoTesteIndividual == null)
            return;

        String prevSelected = obterCombatenteTesteMarcado();
        isUpdatingTestCombatenteSelection = true;
        try {
            modelCombatenteTeste.setRowCount(0);
            if (rbModoTesteIndividual.isSelected()) {
                List<String> robos = robotManager.listarApenasRobos();
                for (String r : robos) {
                    boolean marcado = (prevSelected != null && prevSelected.equalsIgnoreCase(r));
                    modelCombatenteTeste.addRow(new Object[] { marcado, r, detalhesRoboTeste(r) });
                }
            } else {
                List<String> times = robotManager.listarApenasTimes();
                for (String t : times) {
                    List<String> membros = robotManager.obterMembrosDoTime(t);
                    String membrosStr = membros.isEmpty() ? "Sem integrantes" : String.join(", ", membros);
                    boolean marcado = (prevSelected != null && prevSelected.equalsIgnoreCase(t));
                    modelCombatenteTeste.addRow(new Object[] { marcado, t, membrosStr });
                }
            }
        } finally {
            isUpdatingTestCombatenteSelection = false;
        }

        atualizarStatusSelecaoCombatenteTeste();
    }

    private void atualizarListaOponentesTeste() {
        if (modelOponentesTeste == null || rbOponenteSample == null)
            return;

        Set<String> prevSelected = new HashSet<>(obterOponentesTesteMarcados());
        modelOponentesTeste.setRowCount(0);

        if (rbOponenteSample.isSelected()) {
            List<RobotManager.SampleRobotItem> samples = robotManager.listarRobosSampleDetalhados();
            boolean hadPrev = !prevSelected.isEmpty();
            for (RobotManager.SampleRobotItem item : samples) {
                boolean marcado;
                if (hadPrev) {
                    marcado = prevSelected.contains(item.className);
                } else {
                    // Seleciona sample.Interactive como padrão para testes manuais imediatos!
                    marcado = "sample.Interactive".equalsIgnoreCase(item.className);
                }
                modelOponentesTeste.addRow(new Object[] { marcado, item.className, item.estilo, item.descricao });
            }
        } else {
            // Adversários da turma
            if (rbModoTesteIndividual != null && rbModoTesteIndividual.isSelected()) {
                List<String> robos = robotManager.listarApenasRobos();
                for (String r : robos) {
                    boolean marcado = prevSelected.contains(r);
                    modelOponentesTeste.addRow(new Object[] { marcado, r, "Robô do Torneio",
                            detalhesRoboTeste(r) });
                }
            } else {
                List<String> times = robotManager.listarApenasTimes();
                for (String t : times) {
                    List<String> membros = robotManager.obterMembrosDoTime(t);
                    String membrosStr = membros.isEmpty() ? "Sem integrantes" : String.join(", ", membros);
                    boolean marcado = prevSelected.contains(t);
                    modelOponentesTeste.addRow(new Object[] { marcado, t, "Equipe da Turma", membrosStr });
                }
            }
        }

        atualizarStatusSelecaoOponentesTeste();
    }

    private String detalhesRoboTeste(String className) {
        if (className != null && className.startsWith("torneio_sample_team.")) {
            return "Sample Team — robots/torneio_sample_team";
        }
        return "Robô da turma — robots/torneio_teams/robos";
    }

    private String obterCombatenteTesteMarcado() {
        if (modelCombatenteTeste == null)
            return null;
        for (int i = 0; i < modelCombatenteTeste.getRowCount(); i++) {
            if (Boolean.TRUE.equals(modelCombatenteTeste.getValueAt(i, 0))) {
                return (String) modelCombatenteTeste.getValueAt(i, 1);
            }
        }
        return null;
    }

    private List<String> obterOponentesTesteMarcados() {
        List<String> selecionados = new ArrayList<>();
        if (modelOponentesTeste == null)
            return selecionados;
        for (int i = 0; i < modelOponentesTeste.getRowCount(); i++) {
            if (Boolean.TRUE.equals(modelOponentesTeste.getValueAt(i, 0))) {
                selecionados.add((String) modelOponentesTeste.getValueAt(i, 1));
            }
        }
        return selecionados;
    }

    private void iniciarBatalhaTeste() {
        String combatente = obterCombatenteTesteMarcado();
        if (combatente == null || combatente.isBlank()) {
            JOptionPane.showMessageDialog(this,
                    "Por favor, selecione um robô ou equipe para testar.",
                    "Combatente Não Selecionado",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<String> oponentes = obterOponentesTesteMarcados();
        if (oponentes == null || oponentes.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Por favor, selecione pelo menos um robô ou equipe adversária para o teste.",
                    "Adversário Não Selecionado",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (battleRunnerAtual != null) {
            if (battleRunnerAtual.isExecutando()) {
                JOptionPane.showMessageDialog(this,
                        "Uma batalha oficial do torneio já está em andamento. Aguarde seu encerramento.",
                        "Batalha em Execução",
                        JOptionPane.WARNING_MESSAGE);
                return;
            } else {
                battleRunnerAtual = null;
            }
        }

        if (battleRunnerTeste != null) {
            if (battleRunnerTeste.isExecutando()) {
                JOptionPane.showMessageDialog(this,
                        "Uma batalha de teste já está em andamento. Clique em 'Abortar Teste' se desejar reiniciar.",
                        "Teste em Execução",
                        JOptionPane.WARNING_MESSAGE);
                return;
            } else {
                battleRunnerTeste = null;
            }
        }

        btnIniciarTeste.setEnabled(false);
        btnAbortarTeste.setEnabled(true);
        lblStatusTeste.setText("Compilando e preparando arena de teste...");
        lblStatusTeste.setForeground(new Color(2, 132, 199));

        new Thread(() -> {
            try {
                if (chkAutoCompilarTeste.isSelected()) {
                    log("[Arena de Testes] Auto-compilando robôs antes do combate...");
                    RobotManager.CompilacaoResult cr = robotManager.compilarTodosOsRobos("teste");
                    if (!cr.sucesso) {
                        log("[Arena de Testes] Aviso de compilação: " + cr.mensagem);
                    } else {
                        log("[Arena de Testes] Robôs compilados com sucesso!");
                    }
                }

                int rounds = 1;
                String selRounds = (String) cbRoundsTeste.getSelectedItem();
                if (selRounds != null) {
                    if (selRounds.startsWith("1 "))
                        rounds = 1;
                    else if (selRounds.startsWith("3 "))
                        rounds = 3;
                    else if (selRounds.startsWith("5 "))
                        rounds = 5;
                    else if (selRounds.startsWith("10 "))
                        rounds = 10;
                }

                int tps = 30;
                String selTps = (String) cbTpsTeste.getSelectedItem();
                if (selTps != null) {
                    if (selTps.startsWith("30"))
                        tps = 30;
                    else if (selTps.startsWith("45"))
                        tps = 45;
                    else if (selTps.startsWith("60"))
                        tps = 60;
                    else if (selTps.startsWith("120"))
                        tps = 120;
                    else if (selTps.contains("Sem Limite") || selTps.contains("Máxima"))
                        tps = 10000;
                }

                String specA = combatente;
                String specB = String.join(",", oponentes);

                Partida partidaDummy = new Partida();
                partidaDummy.id = null;
                partidaDummy.nomeTimeA = combatente;
                partidaDummy.nomeTimeB = (oponentes.size() == 1) ? oponentes.get(0)
                        : (oponentes.size() + " Adversários");
                partidaDummy.status = "TREINO";

                log("=================================================");
                log("[ARENA DE TESTES] Iniciando Batalha de Treino");
                log("   Combatente: " + specA);
                log("   Adversário(s): " + specB);
                log("   Rounds: " + rounds + " | TPS: " + tps);
                if (specB.contains("sample.Interactive")) {
                    log("   [CONTROLE MANUAL ATIVO] Clique na janela da arena do Robocode para controlar o sample.Interactive com o teclado (setas e espaço)!");
                }
                log("=================================================");

                SwingUtilities.invokeLater(() -> {
                    lblStatusTeste.setText("Arena aberta: " + specA + " vs " + specB);
                    lblStatusTeste.setForeground(new Color(22, 101, 52));
                });

                battleRunnerTeste = new BattleRunner(
                        partidaDummy,
                        specA,
                        specB,
                        0, // Sem limite de tempo no teste
                        tps,
                        rounds,
                        (rounds / 2) + 1,
                        false,
                        false, // Série contínua
                        true, // Janela sempre visível
                        800,
                        600,
                        null, // Sem apiClient: não envia para o backend
                        this::log,
                        this::exibirModalResultado,
                        () -> SwingUtilities.invokeLater(() -> {
                            battleRunnerTeste = null;
                            btnIniciarTeste.setEnabled(true);
                            btnAbortarTeste.setEnabled(false);
                            lblStatusTeste.setText("Teste finalizado.");
                            lblStatusTeste.setForeground(new Color(71, 85, 105));
                        }));

                battleRunnerTeste.iniciarBatalhaAssincrona();
            } catch (Throwable ex) {
                log("[ERRO TESTE] Falha ao iniciar teste: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    battleRunnerTeste = null;
                    btnIniciarTeste.setEnabled(true);
                    btnAbortarTeste.setEnabled(false);
                    lblStatusTeste.setText("Erro ao iniciar teste: " + ex.getMessage());
                    lblStatusTeste.setForeground(new Color(220, 38, 38));
                });
            }
        }).start();
    }

    private void abortarBatalhaTeste() {
        if (battleRunnerTeste != null) {
            log("[Arena de Testes] Abortando teste em andamento...");
            BattleRunner runner = battleRunnerTeste;
            battleRunnerTeste = null;
            new Thread(() -> {
                try {
                    runner.abortar();
                } catch (Throwable ignored) {
                }
            }, "Abort-Teste-Thread").start();
            btnAbortarTeste.setEnabled(false);
            btnIniciarTeste.setEnabled(true);
            lblStatusTeste.setText("Teste abortado pelo usuário.");
            lblStatusTeste.setForeground(new Color(220, 38, 38));
        }
    }

    // =========================================================================
    // BOTÃO ESTILIZADO COM FEEDBACK VISUAL
    // =========================================================================

    public static class CustomColoredButton extends JButton {
        private final Color normalColor;
        private final Color hoverColor;

        public CustomColoredButton(String text, Color normalColor, Color hoverColor) {
            super(text);
            this.normalColor = normalColor;
            this.hoverColor = hoverColor;
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setForeground(Color.WHITE);
            setFont(new Font("Segoe UI", Font.BOLD, 13));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        public void setEnabled(boolean b) {
            super.setEnabled(b);
            if (b) {
                setForeground(Color.WHITE);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            } else {
                setForeground(new Color(148, 163, 184));
                setCursor(Cursor.getDefaultCursor());
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (!isEnabled()) {
                g2.setColor(new Color(226, 232, 240));
            } else if (getModel().isPressed()) {
                g2.setColor(normalColor.darker());
            } else if (getModel().isRollover()) {
                g2.setColor(hoverColor);
            } else {
                g2.setColor(normalColor);
            }

            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
