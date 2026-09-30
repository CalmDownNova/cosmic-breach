package com.cosmicbreach.guardian;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;
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
 *
 * <p>A client keeps a boss bar until the server says it is gone, so two things take it away without the owner:
 * a player who respawns, changes dimension or logs out is taken off every bar at once ({@link #forget}; the next
 * update puts them back if they still should see it), and a bar whose owner stopped updating it (its chunk
 * unloaded or stopped ticking, which ends the fight without a word) is taken from everyone after
 * {@value #STALE_TICKS} ticks ({@link #sweep}).
 */
public final class GuardianBossBar {
    /** A bar not updated for this many of its level's ticks has lost its owner. */
    public static final int STALE_TICKS = 40;
    /** Every bar shown to someone right now (weak: a bar whose owner is gone may simply be collected). */
    private static final Set<GuardianBossBar> LIVE = Collections.newSetFromMap(new WeakHashMap<>());

    private final ServerBossEvent event;
    private @Nullable ServerLevel level;
    private long lastUpdate;
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
        this.level = level;
        this.lastUpdate = level.getGameTime();
        synchronized (LIVE) {
            LIVE.add(this);
        }
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
        synchronized (LIVE) {
            LIVE.remove(this);
        }
    }

    /** True while {@code player} is shown this bar (for checks). */
    public boolean shownTo(ServerPlayer player) {
        return event.getPlayers().contains(player);
    }

    /**
     * Takes {@code player} off every guardian bar (they respawned, changed dimension or left). Players are equal by
     * entity id, and a respawned player keeps the id of the body it replaces, so this also drops the dead body the
     * bar still lists. The owner's next update shows the bar again if the player should still see it.
     */
    public static void forget(ServerPlayer player) {
        for (GuardianBossBar bar : live()) {
            if (bar.event.getPlayers().contains(player)) {
                bar.event.removePlayer(player);
                PacketDistributor.sendToPlayer(player, bar.gonePayload());
            }
        }
    }

    /** Once a server tick: a bar its owner stopped updating is taken from everyone. */
    public static void sweep() {
        for (GuardianBossBar bar : live()) {
            if (bar.level != null && bar.level.getGameTime() - bar.lastUpdate > STALE_TICKS) {
                bar.remove();
            }
        }
    }

    /** How many bars are shown to someone right now (for checks). */
    public static int liveCount() {
        return live().size();
    }

    private static List<GuardianBossBar> live() {
        synchronized (LIVE) {
            return new ArrayList<>(LIVE);
        }
    }

    private GuardianBarPayload gonePayload() {
        return new GuardianBarPayload(id(), 0f, false, 0, 0, "", true);
    }
}
