package com.cosmicbreach.mount;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A mount kept in a Stable Crystal: its data is its own copy, it saves and syncs whole, it knows its owner and last safe spot. */
class StowedMountTest {
    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("cosmicbreach", "drift_manta");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000cafe");

    private static CompoundTag data() {
        CompoundTag t = new CompoundTag();
        t.putString("id", "cosmicbreach:drift_manta");
        t.putUUID("UUID", UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
        t.putUUID("Owner", OWNER);
        t.putFloat("Health", 31.0f);
        t.putDouble("CareSafeX", 10.5);
        t.putDouble("CareSafeY", 200.0);
        t.putDouble("CareSafeZ", -4.5);
        t.putBoolean("CareRescue", false);
        return t;
    }

    private static StowedMount mount() {
        return new StowedMount(TYPE, data(), 31.0f, 40.0f, Optional.of("Skye"));
    }

    @Test
    void itKeepsItsOwnCopyOfTheTagAndHandsOutCopies() {
        CompoundTag mine = data();
        StowedMount s = new StowedMount(TYPE, mine, 31.0f, 40.0f, Optional.empty());
        mine.putString("id", "minecraft:pig");
        assertEquals("cosmicbreach:drift_manta", s.data().getString("id"), "the caller's tag is not shared with it");
        CompoundTag out = s.data();
        out.putString("id", "minecraft:pig");
        assertEquals("cosmicbreach:drift_manta", s.data().getString("id"), "nor is the tag it hands out");
        assertNotSame(s.data(), s.data());
        assertEquals(s, new StowedMount(TYPE, data(), 31.0f, 40.0f, Optional.empty()), "equal by what it holds");
    }

    @Test
    void itKnowsItsOwnerAndWhereItLastStoodSafe() {
        assertEquals(OWNER, mount().ownerId());
        assertEquals(new Vec3(10.5, 200.0, -4.5), mount().safeSpot());
    }

    @Test
    void aMountWithNoOwnerOrNoSpotSaysSo() {
        CompoundTag bare = new CompoundTag();
        bare.putString("id", "cosmicbreach:drift_manta");
        StowedMount s = new StowedMount(TYPE, bare, 20f, 40f, Optional.empty());
        assertNull(s.ownerId());
        assertNull(s.safeSpot(), "one that never stood safe (a world from before 1.1) has no spot");
    }

    @Test
    void itSurvivesSavingAndLoading() {
        StowedMount s = mount();
        Tag saved = StowedMount.CODEC.encodeStart(NbtOps.INSTANCE, s).getOrThrow();
        assertEquals(s, StowedMount.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow(), "the crystal comes back from a save whole");
    }

    @Test
    void itSurvivesTheTripToTheClient() {
        StowedMount s = mount();
        ByteBuf buf = Unpooled.buffer();
        StowedMount.STREAM_CODEC.encode(buf, s);
        assertEquals(s, StowedMount.STREAM_CODEC.decode(buf), "the client sees the same mount (creative sends whole stacks back)");
        StowedMount unnamed = new StowedMount(TYPE, data(), 12.0f, 40.0f, Optional.empty());
        ByteBuf buf2 = Unpooled.buffer();
        StowedMount.STREAM_CODEC.encode(buf2, unnamed);
        assertEquals(unnamed, StowedMount.STREAM_CODEC.decode(buf2));
    }
}
