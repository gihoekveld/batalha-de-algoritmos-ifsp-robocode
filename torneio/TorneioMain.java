package torneio;

import torneio.gui.TorneioLauncherFrame;

import javax.swing.SwingUtilities;

public class TorneioMain {
    public static void main(String[] args) {
        // Inicializa a interface gráfica do Launcher no Event Dispatch Thread do Swing
        SwingUtilities.invokeLater(() -> {
            try {
                TorneioLauncherFrame frame = new TorneioLauncherFrame();
                frame.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
