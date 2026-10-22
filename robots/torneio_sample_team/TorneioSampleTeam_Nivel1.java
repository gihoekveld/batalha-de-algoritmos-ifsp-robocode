package torneio_sample_team;

import robocode.*;
import robocode.TeamRobot;

/**
 * Nível 1: patrulha simples e tiro de baixa potência contra adversários.
 */
public class TorneioSampleTeam_Nivel1 extends TeamRobot {

    @Override
    public void run() {
        while (true) {
            ahead(100);
            turnRight(90);
            turnRadarRight(360);
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        if (isTeammate(e.getName())) {
            return;
        }

        turnGunRight(e.getBearing());
        fire(1);
    }
}
