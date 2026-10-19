package torneio.gui;

import torneio.model.ResultadoBatalha;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class ResultadoBatalhaDialog extends JDialog {

    public ResultadoBatalhaDialog(Frame parent, ResultadoBatalha res) {
        super(parent, "Resultado Oficial da Disputa", true);
        setSize(680, 560);
        setLocationRelativeTo(parent);
        setResizable(false);

        JPanel content = new JPanel(new BorderLayout(0, 15));
        content.setBackground(new Color(248, 250, 252));
        content.setBorder(new EmptyBorder(0, 0, 15, 0));

        // 1. Header com Vencedor e Placar
        content.add(criarHeader(res), BorderLayout.NORTH);

        // 2. Centro (Card de Motivo + Tabela de Estatísticas)
        JPanel centro = new JPanel();
        centro.setLayout(new BoxLayout(centro, BoxLayout.Y_AXIS));
        centro.setOpaque(false);
        centro.setBorder(new EmptyBorder(0, 20, 0, 20));

        centro.add(criarCardMotivo(res));
        centro.add(Box.createVerticalStrut(15));
        centro.add(criarTabelaComparativa(res));
        centro.add(Box.createVerticalStrut(12));
        centro.add(criarAvisoChaveamento(res));

        content.add(centro, BorderLayout.CENTER);

        // 3. Rodapé com Botão Fechar
        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.CENTER));
        rodape.setOpaque(false);
        TorneioLauncherFrame.CustomColoredButton btnFechar = new TorneioLauncherFrame.CustomColoredButton(
                "Concluir e Voltar ao Torneio",
                new Color(30, 41, 59),
                new Color(51, 65, 85)
        );
        btnFechar.setPreferredSize(new Dimension(240, 38));
        btnFechar.addActionListener(e -> dispose());
        rodape.add(btnFechar);

        content.add(rodape, BorderLayout.SOUTH);

        setContentPane(content);
    }

    private JPanel criarHeader(ResultadoBatalha res) {
        JPanel header = new JPanel(new BorderLayout(10, 5));
        header.setBackground(new Color(15, 23, 42)); // Azul escuro moderno
        header.setBorder(new EmptyBorder(18, 20, 18, 20));

        JPanel infoVencedor = new JPanel(new GridLayout(2, 1, 0, 4));
        infoVencedor.setOpaque(false);

        JLabel lblTitulo = new JLabel("VENCEDOR: " + res.vencedorNome.toUpperCase());
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblTitulo.setForeground(new Color(250, 204, 21)); // Amarelo Dourado

        String placarStr = String.format("Placar Final: %s [%d]  x  [%d] %s",
                res.nomeTimeA, res.placarFinalA, res.placarFinalB, res.nomeTimeB);
        JLabel lblPlacar = new JLabel(placarStr);
        lblPlacar.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblPlacar.setForeground(Color.WHITE);

        infoVencedor.add(lblTitulo);
        infoVencedor.add(lblPlacar);

        header.add(infoVencedor, BorderLayout.CENTER);
        return header;
    }

    private JPanel criarCardMotivo(ResultadoBatalha res) {
        JPanel card = new JPanel(new BorderLayout(8, 6));
        card.setBackground(new Color(254, 243, 199)); // Amarelo suave
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(245, 158, 11), 1, true),
                new EmptyBorder(12, 14, 12, 14)
        ));

        JLabel lblTipo = new JLabel(res.tituloMotivo);
        lblTipo.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTipo.setForeground(new Color(146, 64, 14));

        JTextArea txtExplicacao = new JTextArea(res.explicacaoDetalhada);
        txtExplicacao.setWrapStyleWord(true);
        txtExplicacao.setLineWrap(true);
        txtExplicacao.setOpaque(false);
        txtExplicacao.setEditable(false);
        txtExplicacao.setFocusable(false);
        txtExplicacao.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtExplicacao.setForeground(new Color(120, 53, 15));

        card.add(lblTipo, BorderLayout.NORTH);
        card.add(txtExplicacao, BorderLayout.CENTER);
        return card;
    }

    private JPanel criarTabelaComparativa(ResultadoBatalha res) {
        JPanel painel = new JPanel(new BorderLayout(0, 5));
        painel.setOpaque(false);

        JLabel lblTitulo = new JLabel("Estatísticas Comparativas da Disputa:");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblTitulo.setForeground(new Color(51, 65, 85));

        String[] colunas = {"Critério de Combate", res.nomeTimeA, res.nomeTimeB};
        Object[][] dados = {
                {"Rounds Vencidos", res.placarFinalA, res.placarFinalB},
                {"Energia Restante (HP)", String.format("%.1f HP", res.hpFinalA), String.format("%.1f HP", res.hpFinalB)},
                {"Dano de Tiros (Bullet Dmg)", String.format("%.1f", res.danoA), String.format("%.1f", res.danoB)},
                {"Pontuação Oficial Robocode", String.format("%.0f pts", res.scoreRobocodeA), String.format("%.0f pts", res.scoreRobocodeB)}
        };

        DefaultTableModel model = new DefaultTableModel(dados, colunas) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable table = new JTable(model);
        table.setRowHeight(26);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getTableHeader().setBackground(new Color(241, 245, 249));

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        table.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(620, 135));

        painel.add(lblTitulo, BorderLayout.NORTH);
        painel.add(scroll, BorderLayout.CENTER);
        return painel;
    }

    private JPanel criarAvisoChaveamento(ResultadoBatalha res) {
        JPanel painel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        painel.setOpaque(false);

        String msg;
        Color cor;
        if (res.sincronizadoApi) {
            msg = "[OK] Placar sincronizado no Chaveamento! O vencedor avançou automaticamente para a próxima fase.";
            cor = new Color(22, 101, 52);
        } else {
            msg = "[Aviso] Placar local definido, mas a API não confirmou a atualização. Verifique o backend.";
            cor = new Color(185, 28, 28);
        }

        JLabel lbl = new JLabel(msg);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lbl.setForeground(cor);

        painel.add(lbl);
        return painel;
    }
}
