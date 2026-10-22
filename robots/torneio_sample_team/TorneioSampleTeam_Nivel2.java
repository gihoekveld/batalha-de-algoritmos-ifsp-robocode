package torneio_sample_team;

import robocode.ScannedRobotEvent;
import robocode.TeamRobot;

/**
 * Nível 2: mantém o radar em movimento e escolhe a potência pelo alcance.
 */
public class TorneioSampleTeam_Nivel2 extends TeamRobot {

    @Override
    public void run() {
        setAdjustGunForRobotTurn(true);

        while (true) {
            ahead(120);
            turnRight(70);
            turnRadarRight(360);
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        if (isTeammate(e.getName())) {
            return;
        }

        turnGunRight(e.getBearing());
        fire(e.getDistance() < 250 ? 2 : 1);
    }
}
