package torneio_sample_team;

import robocode.ScannedRobotEvent;
import robocode.TeamRobot;

/**
 * Template básico para um robô de equipe do torneio.
 */
public class TorneioSampleTeam_Explicito extends TeamRobot {

    @Override
    public void run() {
        while (true) {
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
