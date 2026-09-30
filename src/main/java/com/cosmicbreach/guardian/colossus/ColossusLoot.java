package com.cosmicbreach.guardian.colossus;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.guardian.RewardTable;
import com.cosmicbreach.guardian.RewardTable.Line;
import com.cosmicbreach.progression.XpSource;
import java.util.List;

/**
 * The Prism Colossus's rewards, per participant (Prism Colossus design v1, "Rules and anti-cheese"): a first kill
 * gives the Prism Heart (Forge II), the Heart of a Dying Star, 3,000 Attunement XP and a stat point (and the
 * "Refracted" advancement with the Drift attunement, granted by the Colossus); repeat kills give 600 XP, a Prism
 * Heart 35% of the time, the Heart 10%, 4 to 8 Spire Quartz and a Heartstone 25% of the time.
 */
public final class ColossusLoot {
    public static final RewardTable TABLE = new RewardTable(List.of(
            Line.firstThenChance(CosmicBreach.id("prism_heart"), 1, 0.35),
            Line.firstThenChance(CosmicBreach.id("heart_of_a_dying_star"), 1, 0.10),
            Line.repeatOnly(CosmicBreach.id("spire_quartz"), 1.0, 4, 8),
            Line.repeatOnly(CosmicBreach.id("heartstone"), 0.25, 1, 1)),
            XpSource.GUARDIAN_FIRST_KILL.at(XpSource.LayerTier.REACH),
            XpSource.GUARDIAN_REPEAT_KILL.at(XpSource.LayerTier.REACH),
            XpSource.GUARDIAN_FIRST_KILL_POINTS);

    private ColossusLoot() {
    }
}
