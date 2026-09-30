package com.cosmicbreach.guardian.leviathan;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.guardian.RewardTable;
import com.cosmicbreach.guardian.RewardTable.Line;
import com.cosmicbreach.progression.XpSource;
import java.util.List;

/**
 * The Thalassine Leviathan's rewards, per participant (Thalassine Leviathan design v1, "Rules and anti-cheese"): a first
 * kill gives the Leviathan Pearl (Forge III), the Halo of Nine, 3 Leviathan Scales, 9,000 Attunement XP and a stat point
 * (and the "Moored" advancement with the Deep's attunement and the Starfall's line, through the guardian framework);
 * repeat kills give 1,800 XP, the Pearl 35% of the time, the Halo 10%, and 1 to 3 Scales.
 */
public final class LeviathanLoot {
    public static final RewardTable TABLE = new RewardTable(List.of(
            Line.firstThenChance(CosmicBreach.id("leviathan_pearl"), 1, 0.35),
            Line.firstThenChance(CosmicBreach.id("halo_of_nine"), 1, 0.10),
            new Line(CosmicBreach.id("leviathan_scale"), 3, 1.0, 1, 3)),
            XpSource.GUARDIAN_FIRST_KILL.at(XpSource.LayerTier.DRIFT),
            XpSource.GUARDIAN_REPEAT_KILL.at(XpSource.LayerTier.DRIFT),
            XpSource.GUARDIAN_FIRST_KILL_POINTS);

    private LeviathanLoot() {
    }
}
