package com.cosmicbreach.structure.crypt;

import com.cosmicbreach.guardian.unsung.NaveLayout;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;

/**
 * What the Silent Nave leaves of a Hollow Crypt that shares its pillar (pure). Both stand on the same platform, the
 * nave is built after the crypt and overwrites what it touches, and its keel (a solid of basalt under the nave) and
 * floor used to bury the crypt's rooms and the top of its way down. The crypt's gatehouse always stands in one of the
 * four cells round the pillar's middle, which is where the nave's door and landing are, so it cannot stay. The rule:
 *
 * <ul>
 *   <li>The nave never writes into the crypt's own volume: everything of the crypt's box under its roof layer, and the
 *       roof layer over the entrance cell (the opening of the stair, whose top step lies in it).</li>
 *   <li>The gatehouse is not built: over the entrance cell the nave's own blocks stand where it has any, and the rest
 *       is cleared to air.</li>
 *   <li>From the top step of the stair a lane three blocks wide and four high is cut straight out, in whichever of the
 *       four directions comes soonest into the open (a cross-section the nave leaves clear), through whatever the nave
 *       built there. Its floor is the crypt's roof, flush with the platform and the nave's floor; past the roof's edge
 *       {@link #laneFloor} says which columns need one.</li>
 * </ul>
 */
public final class CryptNaveLink {
    /** The lane's clear height over the roof layer. */
    public static final int HEAD = 4;
    /** Half the lane's width: 3 wide. */
    public static final int HALF_WIDTH = 1;
    /** The longest lane (the nave and its towers are no deeper than this). */
    public static final int MAX_LANE = 64;
    /** How far over the roof layer the gatehouse reaches. */
    public static final int GATEHOUSE_HEIGHT = 8;

    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private final int minY;
    private final int roofY;
    private final int gateMinX;
    private final int gateMinZ;
    private final int topX;
    private final int topZ;
    private final int direction;
    private final List<int[]> laneColumns = new ArrayList<>();
    private final Set<Long> lane = new HashSet<>();
    private boolean open;

    /** The link for a crypt at {@code origin} with layout {@code plan} sharing a platform with {@code nave}. */
    public static CryptNaveLink of(NaveLayout nave, BlockPos origin, CryptLayout plan) {
        return new CryptNaveLink(nave, origin, plan);
    }

    private CryptNaveLink(NaveLayout nave, BlockPos origin, CryptLayout plan) {
        int c = CryptLayout.CELL;
        this.minX = origin.getX();
        this.maxX = origin.getX() + CryptLayout.SIZE - 1;
        this.minZ = origin.getZ();
        this.maxZ = origin.getZ() + CryptLayout.SIZE - 1;
        this.minY = origin.getY() - CryptLayout.STOREY * (plan.levels - 1) - 4;
        this.roofY = origin.getY() + 8;
        this.gateMinX = origin.getX() + c * plan.entranceX;
        this.gateMinZ = origin.getZ() + c * plan.entranceZ;
        int[] strip = CryptPiece.strip(plan.side(0, plan.entranceX, plan.entranceZ), 1);
        this.topX = gateMinX + strip[0];
        this.topZ = gateMinZ + strip[1];
        int best = -1;
        int bestLength = Integer.MAX_VALUE;
        for (int d = 0; d < 4; d++) {
            int length = laneLength(nave, d);
            if (length < bestLength) {
                bestLength = length;
                best = d;
            }
        }
        this.direction = best;
        this.open = bestLength <= MAX_LANE;
        int dx = CryptLayout.DX[best];
        int dz = CryptLayout.DZ[best];
        for (int k = 0; k <= Math.min(bestLength, MAX_LANE); k++) {
            for (int t = -HALF_WIDTH; t <= HALF_WIDTH; t++) {
                int x = topX + dx * k + (dx == 0 ? t : 0);
                int z = topZ + dz * k + (dz == 0 ? t : 0);
                laneColumns.add(new int[] {x, z});
                lane.add(key(x, z));
            }
        }
    }

    /** Steps from the stair's top to the first cross-section the nave leaves clear in direction {@code d}, or beyond MAX_LANE. */
    private int laneLength(NaveLayout nave, int d) {
        int dx = CryptLayout.DX[d];
        int dz = CryptLayout.DZ[d];
        for (int k = 0; k <= MAX_LANE; k++) {
            boolean clear = true;
            for (int t = -HALF_WIDTH; t <= HALF_WIDTH && clear; t++) {
                int x = topX + dx * k + (dx == 0 ? t : 0);
                int z = topZ + dz * k + (dz == 0 ? t : 0);
                for (int y = roofY + 1; y <= roofY + HEAD && clear; y++) {
                    NaveLayout.Kind kind = nave.kind(x, y, z);
                    clear = kind == NaveLayout.Kind.KEEP || kind == NaveLayout.Kind.AIR;
                }
            }
            if (clear) {
                return k;
            }
        }
        return MAX_LANE + 1;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private boolean inFootprint(int x, int z) {
        return x >= gateMinX && x <= gateMinX + CryptLayout.CELL && z >= gateMinZ && z <= gateMinZ + CryptLayout.CELL;
    }

    /** True if the nave must leave block (x, y, z) as it is: the crypt's own volume. */
    public boolean spares(int x, int y, int z) {
        boolean inBox = x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        if (inBox && y >= minY && y < roofY) {
            return true;
        }
        return y == roofY && inFootprint(x, z);
    }

    /** True if block (x, y, z) is part of the lane cut from the stair's top, and so is air. */
    public boolean carves(int x, int y, int z) {
        return y > roofY && y <= roofY + HEAD && lane.contains(key(x, z));
    }

    /** {minX, minY, minZ, maxX, maxY, maxZ} of the gatehouse's volume over the roof layer, and the lichen outside its door. */
    public int[] gatehouseBox() {
        return new int[] {gateMinX - 1, roofY + 1, gateMinZ - 1, gateMinX + CryptLayout.CELL + 1, roofY + GATEHOUSE_HEIGHT, gateMinZ + CryptLayout.CELL + 1};
    }

    /** True if the lane came into the open within {@link #MAX_LANE} blocks. */
    public boolean reachesOpen() {
        return open;
    }

    /** The lane's columns, {x, z}, from the stair's top outward (three to a step). */
    public List<int[]> laneColumns() {
        return laneColumns;
    }

    /** True if (x, z) is a lane column that lies past the crypt's roof, so its floor at the roof layer may need laying. */
    public boolean laneFloor(int x, int z) {
        boolean inBox = x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        return !inBox && lane.contains(key(x, z));
    }

    public int roofY() {
        return roofY;
    }

    /** Where one stands at the top of the stair: the column, and Y over the roof layer. */
    public BlockPos top() {
        return new BlockPos(topX, roofY + 1, topZ);
    }

    /** The direction the lane runs from the stair's top (0 east, 1 south, 2 west, 3 north). */
    public int direction() {
        return direction;
    }

    /** True if the crypt's box meets the nave's. */
    public static boolean overlaps(NaveLayout nave, BlockPos origin) {
        int[] b = nave.bounds();
        return origin.getX() <= b[3] && origin.getX() + CryptLayout.SIZE - 1 >= b[0]
                && origin.getZ() <= b[5] && origin.getZ() + CryptLayout.SIZE - 1 >= b[2];
    }
}
