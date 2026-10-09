package com.cosmicbreach.codex;

import com.cosmicbreach.world.Layer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;

/**
 * The rules of the Codex's second target (playtest 3: "it's easier to find a boss than the puzzles"): which puzzle
 * room the mote points at, and what a sneak-use of the book does. Pure; the world side is {@link PuzzleFinder}, the
 * memory of who opened which vault is {@link PuzzleRoomLog}.
 *
 * <p>A puzzle room is a structure whose point is its puzzle: the Spire Reliquary and the Gyre Observatory (a Lens
 * Array each) and the Hollow Crypt (a Choir Floor). The Breach Sanctum's two puzzle wings are not counted: the
 * Sanctum is the boss target's last stop already. A room is done for a player once they have opened its vault (the
 * reward each player takes once), so a room someone else solved still counts until this player claims their share.
 */
public final class PuzzleRooms {
    /** A kind of puzzle room: its structure's id and the layer it stands in. */
    public enum Kind {
        RELIQUARY("spire_reliquary", Layer.REACH),
        OBSERVATORY("gyre_observatory", Layer.DRIFT),
        CRYPT("hollow_crypt", Layer.DEEP);

        public final String structure;
        public final Layer layer;

        Kind(String structure, Layer layer) {
            this.structure = structure;
            this.layer = layer;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public static Optional<Kind> byKey(String key) {
            for (Kind k : values()) {
                if (k.key().equals(key)) {
                    return Optional.of(k);
                }
            }
            return Optional.empty();
        }
    }

    /** A room of {@code kind} at {@code at}: where worldgen puts its entrance, or a vault the log knows. */
    public record Room(Kind kind, BlockPos at) {
    }

    /**
     * How far (horizontally) a vault may lie from its room's entrance and still be that room's: every puzzle room is
     * one compact piece (a Crypt is 41 blocks square around its gate), and two rooms of a kind start at least a
     * separation of 8 or more chunks apart, so a vault always lies nearest its own entrance.
     */
    public static final int MATCH = 64;

    /** What the book points at. */
    public enum Target {
        BOSS, PUZZLE;

        public Target next() {
            return this == BOSS ? PUZZLE : BOSS;
        }
    }

    /** What one sneak-use of the book does. */
    public enum Press {
        /** The use button is still held from the last press (a held button repeats every 4 ticks), or too soon. */
        IGNORE,
        /** Throw a mote at the current target. */
        THROW,
        /** The player's mote is still flying: turn the book to the other target and throw at that. */
        SWITCH
    }

    /** A held use button repeats every 4 ticks; a gap longer than this is a fresh press. */
    public static final int HOLD_GAP = 6;

    private PuzzleRooms() {
    }

    /**
     * What a sneak-use at game time {@code now} does: {@code lastUse} is the previous use call (pressed or held, -1 for
     * none), {@code lastThrow} the previous throw (-1 for none), {@code moteFlying} whether this player's mote is still
     * in the air, and {@code cooldown} the ticks between throws. Pure.
     */
    public static Press press(long now, long lastUse, long lastThrow, boolean moteFlying, int cooldown) {
        if (lastUse >= 0 && now - lastUse <= HOLD_GAP) {
            return Press.IGNORE;
        }
        if (moteFlying) {
            return Press.SWITCH;
        }
        if (lastThrow >= 0 && now - lastThrow < cooldown) {
            return Press.IGNORE;
        }
        return Press.THROW;
    }

    /**
     * The room to point at from {@code from}, standing in {@code here}: the nearest (horizontally) room of this layer
     * that the player has not done, or empty. {@code sites} are the rooms worldgen places (built or not yet),
     * {@code open} the vaults the log knows that this player has not opened, {@code done} the vaults this player has
     * opened. A site is done when it is the nearest site of its kind, within {@link #MATCH}, to a vault in
     * {@code done}; a known vault within {@link #MATCH} of a site is that site's room, not a second one. Pure.
     */
    public static Optional<Room> choose(Layer here, BlockPos from, List<Room> sites, List<Room> open, List<Room> done) {
        List<Room> layerSites = new ArrayList<>();
        for (Room s : sites) {
            if (s.kind().layer == here) {
                layerSites.add(s);
            }
        }
        List<Room> doneSites = new ArrayList<>();
        for (Room vault : done) {
            nearestSite(layerSites, vault).ifPresent(doneSites::add);
        }
        List<Room> pool = new ArrayList<>();
        for (Room s : layerSites) {
            if (!doneSites.contains(s)) {
                pool.add(s);
            }
        }
        for (Room vault : open) {
            if (vault.kind().layer == here && nearestSite(layerSites, vault).isEmpty()) {
                pool.add(vault);
            }
        }
        Room best = null;
        double bestD = Double.MAX_VALUE;
        for (Room r : pool) {
            double d = horizontalDistSqr(r.at(), from);
            if (d < bestD) {
                bestD = d;
                best = r;
            }
        }
        return Optional.ofNullable(best);
    }

    /** The site of {@code vault}'s kind nearest to it, if within {@link #MATCH}. */
    static Optional<Room> nearestSite(List<Room> sites, Room vault) {
        Room best = null;
        double bestD = (double) MATCH * MATCH;
        for (Room s : sites) {
            if (s.kind() != vault.kind()) {
                continue;
            }
            double d = horizontalDistSqr(s.at(), vault.at());
            if (d <= bestD) {
                bestD = d;
                best = s;
            }
        }
        return Optional.ofNullable(best);
    }

    public static double horizontalDistSqr(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
