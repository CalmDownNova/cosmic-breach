package com.cosmicbreach.guardian.unsung;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.guardian.RewardTable;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * The Unsung's rewards per participant (Unsung design v1, "Rules and anti-cheese"): a Silent Sigil on every kill (each
 * summoning of the Heliarch needs a new one, through the Dying Star Heart), the Choir Pendant on a first kill and one
 * time in ten after, 25,000 Attunement XP the first time and 5,000 after, and a stat point the first time. The
 * advancement "Unsung" and the attunement to the Breach Sanctum come with the first kill.
 */
public final class UnsungLoot {
    public static final ResourceLocation SILENT_SIGIL = CosmicBreach.id("silent_sigil");
    public static final ResourceLocation CHOIR_PENDANT = CosmicBreach.id("choir_pendant");
    public static final int FIRST_XP = 25_000;
    public static final int REPEAT_XP = 5_000;
    public static final double PENDANT_REPEAT_CHANCE = 0.10;

    public static final RewardTable TABLE = new RewardTable(List.of(
            RewardTable.Line.firstThenChance(SILENT_SIGIL, 1, 1.0),
            RewardTable.Line.firstThenChance(CHOIR_PENDANT, 1, PENDANT_REPEAT_CHANCE)),
            FIRST_XP, REPEAT_XP, 1);

    private UnsungLoot() {
    }
}
