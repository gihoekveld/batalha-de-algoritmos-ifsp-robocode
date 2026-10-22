package torneio_sample_team;

import robocode.*;
import robocode.TeamRobot;

/**
 * Nível 3: acompanha o adversário com o radar e ajusta a mira pela velocidade.
 */
public class TorneioSampleTeam_Nivel3 extends TeamRobot {

    @Override
    public void run() {
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        while (true) {
            setTurnRadarRight(360);
            ahead(140);
            turnRight(45);
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        if (isTeammate(e.getName())) {
            return;
        }

        double gunTurn = getHeading() + e.getBearing() - getGunHeading();
        turnGunRight(gunTurn);

        double power = Math.min(3, Math.max(1, 400 / e.getDistance()));
        fire(power);
    }
}
