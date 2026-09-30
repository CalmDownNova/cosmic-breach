package com.cosmicbreach.guardian;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A guardian's boss bar: vanilla's bar (name, health, colour) for the players in or near its arena, plus what
 * {@link GuardianBarPayload} adds on the client: the Break gauge drawn under the bar, a countdown, and the boss
 * music while it is awake. Server side; the guardian owns one for its fight and calls {@link #update} each tick
 * with who should see it, then {@link #remove} when the fight ends.
 */
public final class GuardianBossBar {
    private final ServerBossEvent event;
    private float gauge;
    private boolean broken;
    private int countdown;
    private int countdownMax;
    private String music = "";
    private GuardianBarPayload sent;
    /** The Codex chapter a player opens by seeing this bar (meeting the guardian), or null. */
    private com.cosmicbreach.codex.CodexChapter meets;

    public GuardianBossBar(Component name, BossEvent.BossBarColor color) {
        this.event = new ServerBossEvent(name, color, BossEvent.BossBarOverlay.PROGRESS);
    }

    /** Seeing this bar opens {@code chapter} in the viewer's Codex (a guardian's page stays sealed until then). */
    public GuardianBossBar meets(com.cosmicbreach.codex.CodexChapter chapter) {
        this.meets = chapter;
        return this;
    }

    public UUID id() {
        return event.getId();
    }

    public void setName(Component name) {
        if (!name.equals(event.getName())) {
            event.setName(name);
        }
    }

    public void setColor(BossEvent.BossBarColor color) {
        if (event.getColor() != color) {
            event.setColor(color);
        }
    }

    public void setProgress(float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        if (Math.abs(p - event.getProgress()) > 1e-4f) {
            event.setProgress(p);
        }
    }

    /** The Break gauge, 0 to 1, and whether the guardian is Broken right now (the gauge flashes full). */
    public void setGauge(float fraction, boolean broken) {
        this.gauge = Math.max(0f, Math.min(1f, fraction));
        this.broken = broken;
    }

    /** A countdown on the bar: {@code ticksLeft} of {@code ticksMax}; a max of 0 hides it. */
    public void setCountdown(int ticksLeft, int ticksMax) {
        this.countdown = Math.max(0, ticksLeft);
        this.countdownMax = Math.max(0, ticksMax);
    }

    /** The boss loop the players who see the bar hear (8 bars at 100 BPM, on the Vesper clock), or null for none. */
    public void setMusic(@org.jetbrains.annotations.Nullable net.minecraft.resources.ResourceLocation music) {
        this.music = music == null ? "" : music.toString();
    }

    public List<ServerPlayer> viewers() {
        return new ArrayList<>(event.getPlayers());
    }

    /** Shows the bar to exactly the players in {@code level} that {@code sees} accepts, and syncs the extras. */
    public void update(ServerLevel level, Predicate<ServerPlayer> sees) {
        for (ServerPlayer player : new ArrayList<>(event.getPlayers())) {
            if (player.isRemoved() || player.level() != level || !sees.test(player)) {
                event.removePlayer(player);
                PacketDistributor.sendToPlayer(player, gonePayload());
            }
        }
        boolean added = false;
        for (ServerPlayer player : level.players()) {
            if (!event.getPlayers().contains(player) && sees.test(player)) {
                event.addPlayer(player);
                added = true;
                if (meets != null) {
                    com.cosmicbreach.codex.Codices.met(player, meets);
                }
            }
        }
        GuardianBarPayload now = new GuardianBarPayload(id(), Math.round(gauge * 100f) / 100f, broken, countdown, countdownMax, music, false);
        if (added || !now.equals(sent)) {
            sent = now;
            for (ServerPlayer player : event.getPlayers()) {
                PacketDistributor.sendToPlayer(player, now);
            }
        }
    }

    /** Takes the bar away from everyone. */
    public void remove() {
        for (ServerPlayer player : new ArrayList<>(event.getPlayers())) {
            PacketDistributor.sendToPlayer(player, gonePayload());
        }
        event.removeAllPlayers();
        sent = null;
    }

    private GuardianBarPayload gonePayload() {
        return new GuardianBarPayload(id(), 0f, false, 0, 0, "", true);
    }
}
