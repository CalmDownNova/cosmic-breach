package com.cosmicbreach.structure.sanctum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Sanctum floor for the Hollow Heliarch's fight (GDD 7.3): a disc of radius 28 at the world's origin, players
 * standing at Y 64 (its top blocks at Y 63), over the Breach. Always here, whichever side the halls lie on.
 *
 * <ul>
 *   <li><b>Rings</b> ({@link Ring}): the throne dais to 8, the mid ring to 16, the outer ring to 24, the rim to 28,
 *       measured from the throne's column (block (0, 0)) to block centres. An ember line glows along the outer edge of
 *       the dais, the mid and the outer ring.</li>
 *   <li><b>Segments</b>: each ring is cut into eight, numbered 0 to 7 clockwise from north (segment 0 spans north to
 *       north-east, 2 east to south-east), gold seams between them. {@link #segmentBlocks} lists every block of one
 *       (its floor and the keel under it: each ring's keel is one step deeper toward the middle, so a segment is a
 *       clean wedge) for the Collapse to crack and drop; {@link #standing} says whether one is still there.</li>
 *   <li><b>Pillars</b>: eight Choir Pillars at radius 22, pillar {@code i} in the middle of the outer ring's segment
 *       {@code i}, 5 across (13 blocks), Y 64 to 78 with an ember capital; unbreakable, and they block line of sight.
 *       They are not part of any segment ({@link #pillarBlocks}).</li>
 *   <li><b>The throne</b> at {@link #THRONE}, facing the Throne Stair; the summon hook is {@link SanctumThrone}.</li>
 *   <li><b>Falling</b> off is always survivable ({@link FallRescue}): thrown back onto the rim for half the health
 *       and 60 ticks of Voidsick ({@link SanctumRegistry#VOIDSICK}, no weapon abilities).</li>
 * </ul>
 */
public final class SanctumArena {
    public static final int FLOOR_Y = SanctumLayout.ARENA_Y;
    public static final BlockPos THRONE = new BlockPos(0, SanctumLayout.ARENA_Y, 0);
    public static final Vec3 CENTRE = new Vec3(0.5, SanctumLayout.ARENA_Y, 0.5);
    public static final double RADIUS = 28.0;
    public static final int SEGMENTS = SanctumLayout.SEGMENTS;
    public static final int PILLARS = SanctumLayout.SEGMENTS;

    /** The four rings, inside out. */
    public enum Ring {
        DAIS(0.0, SanctumLayout.DAIS_R),
        MID(SanctumLayout.DAIS_R, SanctumLayout.MID_R),
        OUTER(SanctumLayout.MID_R, SanctumLayout.OUTER_R),
        RIM(SanctumLayout.OUTER_R, SanctumLayout.RIM_R);

        /** Block centres farther than this from the throne's column belong to the ring... */
        public final double inner;
        /** ...up to this. */
        public final double outer;

        Ring(double inner, double outer) {
            this.inner = inner;
            this.outer = outer;
        }
    }

    private static final List<List<List<BlockPos>>> SEGMENT_BLOCKS = new ArrayList<>();
    private static final List<List<BlockPos>> PILLAR_BLOCKS = new ArrayList<>();

    static {
        for (int r = 0; r < 4; r++) {
            List<List<BlockPos>> ring = new ArrayList<>();
            for (int s = 0; s < SEGMENTS; s++) {
                ring.add(new ArrayList<>());
            }
            SEGMENT_BLOCKS.add(ring);
        }
        for (int y = SanctumLayout.FLOOR_Y; y >= SanctumLayout.keelBottom(0); y--) {
            for (int x = -29; x <= 29; x++) {
                for (int z = -29; z <= 29; z++) {
                    if (SanctumLayout.discBlock(x, y, z)) {
                        SEGMENT_BLOCKS.get(SanctumLayout.ring(x, z)).get(SanctumLayout.segment(x, z)).add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        for (int i = 0; i < PILLARS; i++) {
            List<BlockPos> p = new ArrayList<>();
            for (int y = SanctumLayout.ARENA_Y; y <= SanctumLayout.PILLAR_TOP; y++) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        int x = SanctumLayout.PILLAR_X[i] + dx;
                        int z = SanctumLayout.PILLAR_Z[i] + dz;
                        if (SanctumLayout.pillarAt(x, z) == i) {
                            p.add(new BlockPos(x, y, z));
                        }
                    }
                }
            }
            PILLAR_BLOCKS.add(Collections.unmodifiableList(p));
        }
    }

    private SanctumArena() {
    }

    /** The ring of column (x, z), or null off the disc. */
    public static @Nullable Ring ringAt(int x, int z) {
        int r = SanctumLayout.ring(x, z);
        return r < 0 ? null : Ring.values()[r];
    }

    /** The segment of column (x, z): 0 to 7 clockwise from north. */
    public static int segmentAt(int x, int z) {
        return SanctumLayout.segment(x, z);
    }

    /** Every block of one segment of one ring: the floor first (Y 63), then the keel under it, layer by layer. */
    public static List<BlockPos> segmentBlocks(Ring ring, int segment) {
        return Collections.unmodifiableList(SEGMENT_BLOCKS.get(ring.ordinal()).get(Math.floorMod(segment, SEGMENTS)));
    }

    /** True while any floor block of that segment is still there. */
    public static boolean standing(Level level, Ring ring, int segment) {
        for (BlockPos p : segmentBlocks(ring, segment)) {
            if (p.getY() == SanctumLayout.FLOOR_Y && !level.getBlockState(p).isAir()) {
                return true;
            }
        }
        return false;
    }

    /** Pillar {@code i}'s centre column at floor level. */
    public static BlockPos pillarBase(int i) {
        return new BlockPos(SanctumLayout.PILLAR_X[i], SanctumLayout.ARENA_Y, SanctumLayout.PILLAR_Z[i]);
    }

    /** Every block of pillar {@code i}. */
    public static List<BlockPos> pillarBlocks(int i) {
        return PILLAR_BLOCKS.get(i);
    }

    /** True if (x, y, z) is over the disc, from its floor up to the pillars' capitals. */
    public static boolean over(double x, double y, double z) {
        double dx = x - 0.5;
        double dz = z - 0.5;
        return dx * dx + dz * dz <= SanctumLayout.RIM_R * SanctumLayout.RIM_R && y >= SanctumLayout.ARENA_Y - 1 && y <= SanctumLayout.PILLAR_TOP + 2;
    }
}
