package com.cosmicbreach.client.leviathan;

import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.guardian.leviathan.LeviathanEffects;
import com.cosmicbreach.guardian.leviathan.LeviathanMoves;
import com.cosmicbreach.guardian.leviathan.LeviathanPathPayload;
import com.cosmicbreach.guardian.leviathan.LeviathanRegistry;
import com.cosmicbreach.guardian.leviathan.LeviathanTactics;
import com.cosmicbreach.guardian.leviathan.SongPull;
import com.cosmicbreach.guardian.leviathan.ThalassineLeviathan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * The Leviathan on the client: its renderer, effects and telegraphs ({@link LeviathanFx}), the dive paths it hears of,
 * and the Song of Pulling on this client's own player (each client moves its own player, as the Drift's currents do):
 * {@link SongPull#step} each tick while a Leviathan pulls, unless a block stands between the mouth and the player or the
 * player dashed within the last {@value LeviathanMoves#DASH_BREAK} ticks.
 */
public final class LeviathanClient {
    private static long exemptUntil = Long.MIN_VALUE;
    /** Blocks this client's player was pulled this session, for tests. */
    private static double pulled;
    private static long lastPulledTick = Long.MIN_VALUE;
    private static boolean lastBlocked;

    private LeviathanClient() {
    }

    public static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(LeviathanRegistry.THALASSINE_LEVIATHAN.get(), LeviathanRenderer::new);
            event.registerEntityRenderer(LeviathanRegistry.SHED_SCALE.get(), NoopRenderer::new);
        });
        LeviathanEffects.install(new LeviathanFx());
        gameBus.addListener(RenderLevelStageEvent.class, LeviathanFx::render);
        gameBus.addListener(EntityTickEvent.Pre.class, LeviathanClient::onEntityTick);
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> LeviathanFx.clear());
    }

    /** A dive's path from the server (main thread). */
    public static void path(LeviathanPathPayload payload) {
        LeviathanFx.path(payload);
    }

    /** How strongly the Breach Dive's wake is drawn for {@code l} at {@code time} (0 to 1; scenarios read it). */
    public static float wakeStrength(ThalassineLeviathan l, double time) {
        return LeviathanFx.wakeStrength(l, time);
    }

    private static void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || !player.level().isClientSide() || player.isSpectator()
                || player.getAbilities().flying) {
            return;
        }
        long now = player.level().getGameTime();
        PlayerCombat combat = PlayerCombat.existing(player);
        if (combat != null && combat.machine().isDashing()) {
            exemptUntil = now + LeviathanMoves.DASH_BREAK;
        }
        for (ThalassineLeviathan l : player.level().getEntitiesOfClass(ThalassineLeviathan.class, player.getBoundingBox().inflate(48.0))) {
            if (l.action() != LeviathanTactics.Attack.SONG || !SongPull.pulling(now - l.actionStart())) {
                continue;
            }
            SongPull.Cone cone = l.songCone();
            Vec3 middle = player.getBoundingBox().getCenter();
            // an asteroid blocks the song only when it hides both the player's eyes and middle from the mouth
            boolean blocked = hidden(player, cone.mouth(), middle) && hidden(player, cone.mouth(), player.getEyePosition());
            lastBlocked = blocked;
            int pullTick = (int) (now - l.actionStart() - LeviathanMoves.SONG_TELL);
            Vec3 step = SongPull.step(cone, middle, SongPull.strength(pullTick), player.isFallFlying(), now < exemptUntil, blocked);
            if (step.lengthSqr() > 0) {
                move(player, step);
                pulled += step.length();
                lastPulledTick = now;
            }
        }
    }

    private static boolean hidden(LocalPlayer player, Vec3 from, Vec3 to) {
        return player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType()
                == HitResult.Type.BLOCK;
    }

    /** Moves the player by {@code d} where the way is clear, axis by axis where it isn't. */
    private static void move(LocalPlayer player, Vec3 d) {
        AABB box = player.getBoundingBox();
        if (player.level().noCollision(player, box.move(d))) {
            player.setPos(player.getX() + d.x, player.getY() + d.y, player.getZ() + d.z);
            return;
        }
        for (Vec3 axis : new Vec3[] {new Vec3(d.x, 0, 0), new Vec3(0, d.y, 0), new Vec3(0, 0, d.z)}) {
            if (axis.lengthSqr() > 0 && player.level().noCollision(player, player.getBoundingBox().move(axis))) {
                player.setPos(player.getX() + axis.x, player.getY() + axis.y, player.getZ() + axis.z);
            }
        }
    }

    /** For tests: how far the song has pulled this client's player, and when last. */
    public static double pulled() {
        return pulled;
    }

    public static long lastPulledTick() {
        return lastPulledTick;
    }

    /** For tests: whether the last pull this player was in reach of was blocked by rock. */
    public static boolean lastBlocked() {
        return lastBlocked;
    }

    public static boolean exempt(long now) {
        return now < exemptUntil;
    }

    public static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
