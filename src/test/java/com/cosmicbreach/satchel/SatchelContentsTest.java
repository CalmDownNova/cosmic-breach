package com.cosmicbreach.satchel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The Satchel's pure rules: caps, conservation of every move, the pickup switch and the stored form. */
class SatchelContentsTest {
    private static final ResourceLocation COBBLE = ResourceLocation.withDefaultNamespace("cobblestone");
    private static final ResourceLocation INGOT = ResourceLocation.fromNamespaceAndPath("cosmicbreach", "starsteel_ingot");

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void depositStopsAtTheCapAndReportsWhatFit() {
        SatchelContents c = SatchelContents.EMPTY.deposit(COBBLE, 4000).contents();
        SatchelContents.Move m = c.deposit(COBBLE, 500);
        assertEquals(96, m.moved(), "only the room left under 4096");
        assertEquals(SatchelContents.CAP, m.contents().count(COBBLE));
        assertEquals(0, m.contents().deposit(COBBLE, 1).moved());
        assertEquals(0, SatchelContents.EMPTY.deposit(COBBLE, -5).moved());
    }

    @Test
    void withdrawGivesAtMostWhatIsThere() {
        SatchelContents c = SatchelContents.EMPTY.deposit(INGOT, 10).contents();
        SatchelContents.Move m = c.withdraw(INGOT, 64);
        assertEquals(10, m.moved());
        assertEquals(0, m.contents().count(INGOT));
        assertFalse(m.contents().materials().containsKey(INGOT), "an emptied type leaves the list");
        assertEquals(0, c.withdraw(COBBLE, 5).moved());
    }

    @Test
    void movesConserveEveryItemAcrossRandomTraffic() {
        // a satchel and a pile of loose items: whatever the sequence, their sum never changes
        Random rnd = new Random(7);
        SatchelContents c = SatchelContents.EMPTY;
        Map<ResourceLocation, Integer> loose = new LinkedHashMap<>(Map.of(COBBLE, 9000, INGOT, 300));
        long total = 9300;
        for (int i = 0; i < 5000; i++) {
            ResourceLocation id = rnd.nextBoolean() ? COBBLE : INGOT;
            int n = 1 + rnd.nextInt(700);
            if (rnd.nextBoolean()) {
                SatchelContents.Move m = c.deposit(id, Math.min(n, loose.get(id)));
                loose.merge(id, -m.moved(), Integer::sum);
                c = m.contents();
            } else {
                SatchelContents.Move m = c.withdraw(id, n);
                loose.merge(id, m.moved(), Integer::sum);
                c = m.contents();
            }
            assertEquals(total, c.totalItems() + loose.values().stream().mapToLong(Integer::longValue).sum(), "after move " + i);
            assertTrue(c.count(COBBLE) <= SatchelContents.CAP && c.count(INGOT) <= SatchelContents.CAP);
        }
    }

    @Test
    void pickupDefaultsOnAndTheSwitchTogglesPerType() {
        SatchelContents c = SatchelContents.EMPTY;
        assertTrue(c.pickupOn(COBBLE));
        c = c.togglePickup(COBBLE);
        assertFalse(c.pickupOn(COBBLE));
        assertTrue(c.pickupOn(INGOT), "other types are untouched");
        assertTrue(c.togglePickup(COBBLE).pickupOn(COBBLE));
    }

    @Test
    void gearHasEighteenSlotsAndKeepsItsPlaces() {
        assertEquals(18, SatchelContents.EMPTY.gear().size());
        SatchelContents c = SatchelContents.EMPTY.withGear(17, new ItemStack(Items.IRON_SWORD));
        assertEquals(Items.IRON_SWORD, c.gear().get(17).getItem());
        assertTrue(c.gear().get(0).isEmpty());
        assertEquals(1, c.totalItems());
        assertEquals(18, c.withGear(List.of(new ItemStack(Items.IRON_AXE))).gear().size(), "a short list is padded with empty slots");
    }

    @Test
    void storedFormRoundTripsMaterialsAndTheSwitches() {
        SatchelContents c = SatchelContents.EMPTY.deposit(COBBLE, 123).contents().deposit(INGOT, 4).contents().togglePickup(INGOT);
        var json = SatchelContents.CODEC.encodeStart(JsonOps.INSTANCE, c).getOrThrow();
        SatchelContents back = SatchelContents.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(c, back);
        assertEquals(c.hashCode(), back.hashCode());
    }

    @Test
    void anOverfullStoredCountIsClampedNotTrusted() {
        SatchelContents c = new SatchelContents(Map.of(COBBLE, 99999), java.util.Set.of(), List.of());
        assertEquals(SatchelContents.CAP, c.count(COBBLE));
    }

    @Test
    void aPickupSwitchRequestIsCheckedAndBounded() {
        ResourceLocation junk = ResourceLocation.fromNamespaceAndPath("evil", "x".repeat(300));
        assertFalse(SatchelMenu.toggleAllowed(SatchelContents.EMPTY, junk, false), "an id that is not a material is refused");
        assertTrue(SatchelMenu.toggleAllowed(SatchelContents.EMPTY, COBBLE, true));
        SatchelContents full = SatchelContents.EMPTY;
        for (int i = 0; i < SatchelContents.MAX_PICKUP_OFF; i++) {
            full = full.togglePickup(ResourceLocation.fromNamespaceAndPath("cosmicbreach", "m" + i));
        }
        assertFalse(SatchelMenu.toggleAllowed(full, COBBLE, true), "the list of switched-off types is capped");
        assertTrue(SatchelMenu.toggleAllowed(full, ResourceLocation.fromNamespaceAndPath("cosmicbreach", "m0"), true), "but a type in it can always be switched back on");
        var many = new java.util.LinkedHashSet<ResourceLocation>();
        for (int i = 0; i < 5000; i++) {
            many.add(ResourceLocation.fromNamespaceAndPath("cosmicbreach", "m" + i));
        }
        assertEquals(SatchelContents.MAX_PICKUP_OFF, new SatchelContents(Map.of(), many, List.of()).pickupOff().size(), "stored data past the cap is cut");
    }
}
