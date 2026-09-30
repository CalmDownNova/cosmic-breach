package com.cosmicbreach.guardian;

import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ledger of rewards held for players who were offline or dead at a kill, and of items to hand back. */
class OwedRewardsTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    private static CompoundTag stack(String id, int count) {
        CompoundTag t = new CompoundTag();
        t.putString("id", id);
        t.putInt("count", count);
        return t;
    }

    @Test
    void killsAndItemsAreKeptPerPlayerOldestFirst() {
        OwedRewards owed = new OwedRewards();
        assertFalse(owed.owes(A));
        owed.oweKill(A, "first");
        owed.oweKill(A, "second");
        owed.oweItem(B, stack("minecraft:stone", 1));
        assertTrue(owed.owes(A));
        assertTrue(owed.owes(B));
        assertEquals(3, owed.size());
        assertEquals(List.of("first", "second"), owed.takeKills(A), "in the order they were owed");
        assertTrue(owed.takeItems(A).isEmpty(), "A was owed no items");
        assertFalse(owed.owes(A), "paid: nothing left for A");
        assertTrue(owed.owes(B), "B still waits");
        assertTrue(owed.takeKills(B).isEmpty());
        assertEquals(1, owed.takeItems(B).size());
        assertEquals(0, owed.size());
    }

    @Test
    void takingTwiceGivesNothingTheSecondTime() {
        OwedRewards owed = new OwedRewards();
        owed.oweKill(A, "first");
        assertEquals(1, owed.takeKills(A).size());
        assertTrue(owed.takeKills(A).isEmpty(), "a kill is paid once");
    }

    @Test
    void anOwedItemIsACopyOfWhatWasGiven() {
        OwedRewards owed = new OwedRewards();
        CompoundTag given = stack("minecraft:stone", 2);
        owed.oweItem(A, given);
        given.putInt("count", 64);
        assertEquals(2, owed.takeItems(A).get(0).getInt("count"), "later changes to the caller's tag don't leak in");
    }

    @Test
    void theLedgerSurvivesARestart() {
        OwedRewards owed = new OwedRewards();
        owed.oweKill(A, "first");
        owed.oweKill(B, "second");
        owed.oweItem(B, stack("minecraft:stone", 3));
        CompoundTag saved = owed.save(new CompoundTag(), null);
        OwedRewards back = OwedRewards.load(saved, null);
        assertEquals(3, back.size());
        assertEquals(List.of("first"), back.takeKills(A));
        assertEquals(List.of("second"), back.takeKills(B));
        List<CompoundTag> items = back.takeItems(B);
        assertEquals(1, items.size());
        assertEquals("minecraft:stone", items.get(0).getString("id"));
        assertEquals(3, items.get(0).getInt("count"));
    }

    @Test
    void anEmptyOrDamagedSaveLoadsAsNothingOwed() {
        assertEquals(0, OwedRewards.load(new CompoundTag(), null).size());
        CompoundTag bad = new CompoundTag();
        net.minecraft.nbt.ListTag players = new net.minecraft.nbt.ListTag();
        players.add(new CompoundTag()); // no player id
        bad.put("players", players);
        assertEquals(0, OwedRewards.load(bad, null).size());
    }
}
