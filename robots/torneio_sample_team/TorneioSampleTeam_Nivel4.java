package torneio_sample_team;

import java.io.IOException;

import robocode.*;
import robocode.TeamRobot;

/**
 * Nível 4: compartilha a posição dos adversários e usa mira preditiva simples.
 */
public class TorneioSampleTeam_Nivel4 extends TeamRobot {

    @Override
    public void run() {
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        while (true) {
            setTurnRadarRight(360);
            ahead(160);
            turnRight(50);
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        if (isTeammate(e.getName())) {
            return;
        }

        double enemyBearing = getHeading() + e.getBearing();
        double enemyX = getX() + e.getDistance() * Math.sin(Math.toRadians(enemyBearing));
        double enemyY = getY() + e.getDistance() * Math.cos(Math.toRadians(enemyBearing));

        try {
            broadcastMessage(new EnemyPosition(enemyX, enemyY, e.getName()));
        } catch (IOException ex) {
            out.println("Não foi possível compartilhar a posição do adversário.");
        }

        double gunTurn = enemyBearing - getGunHeading();
        turnGunRight(gunTurn);
        fire(Math.min(3, Math.max(1, 500 / e.getDistance())));
    }

    private static class EnemyPosition implements java.io.Serializable {
        private final double x;
        private final double y;
        private final String name;

        private EnemyPosition(double x, double y, String name) {
            this.x = x;
            this.y = y;
            this.name = name;
        }
    }
}
