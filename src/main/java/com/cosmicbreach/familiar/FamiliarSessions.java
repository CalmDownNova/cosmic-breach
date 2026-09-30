package com.cosmicbreach.familiar;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Which familiar each player has out (one at a time) and everything that comes and goes with it, server side: summon
 * and dismiss (from the lantern's use or the familiar key), the mode cycle, the death that darkens the lantern, and the
 * owner's recent fights ("an enemy you hit recently", what hurt them lately) that the familiars read. The familiar
 * lives only while its session does; the lantern keeps the rest ({@link FamiliarBond}). A familiar lost to an unloaded
 * chunk or left behind by a teleport or a dimension change comes back at its owner's side on their next tick.
 */
public final class FamiliarSessions {
    /** One player's familiar out now. */
    static final class Session {
        final UUID bond;
        @Nullable FamiliarEntity entity;
        final RecentTargets targets = new RecentTargets();
        final RecentTargets attackers = new RecentTargets();
        /** Its health share when last seen (for bringing it back after an unload). */
        float lastHealth = 1f;

        Session(UUID bond) {
            this.bond = bond;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private FamiliarSessions() {
    }

    // ------------------------------------------------------------------ questions

    /** The familiar {@code player} has out, or null. */
    public static @Nullable FamiliarEntity active(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? null : s.entity;
    }

    /** The kind of familiar {@code player} has out, or null. */
    public static @Nullable FamiliarKind activeKind(ServerPlayer player) {
        FamiliarEntity e = active(player);
        return e == null || e.isRemoved() ? null : e.kind();
    }

    /** True if {@code e} is the familiar {@code player} has out now. */
    static boolean owns(ServerPlayer player, FamiliarEntity e) {
        Session s = SESSIONS.get(player.getUUID());
        return s != null && s.entity == e;
    }

    /** The lantern in {@code player}'s inventory bound to {@code bond}, or empty. */
    public static ItemStack lantern(ServerPlayer player, UUID bond) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            FamiliarBond b = FamiliarLanternItem.bond(stack);
            if (b != null && b.id().equals(bond)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * The creature {@code familiar} should fight for {@code player} in {@code mode}: the owner's newest recent target,
     * else what hurt the owner (or a familiar of theirs) lately, else in Attack the nearest monster near the owner.
     */
    static @Nullable LivingEntity pickTarget(ServerPlayer player, FamiliarEntity familiar, FamiliarMode mode, long now) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || !mode.fights()) {
            return null;
        }
        ServerLevel level = player.serverLevel();
        OptionalInt id = s.targets.latest(now, FamiliarRules.RECENT_TICKS, i -> familiar.valid(player, level.getEntity(i), mode));
        if (id.isEmpty()) {
            id = s.attackers.latest(now, FamiliarRules.RECENT_TICKS, i -> familiar.valid(player, level.getEntity(i), mode));
        }
        if (id.isPresent()) {
            return (LivingEntity) level.getEntity(id.getAsInt());
        }
        if (mode != FamiliarMode.ATTACK) {
            return null;
        }
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(FamiliarRules.SEEK_RADIUS),
                m -> m instanceof Enemy && familiar.valid(player, m, mode))) {
            double d = m.distanceToSqr(player);
            if (d < bestD && d <= FamiliarRules.SEEK_RADIUS * FamiliarRules.SEEK_RADIUS) {
                best = m;
                bestD = d;
            }
        }
        return best;
    }

    /** The creature {@code player} hit most recently (within 5 s) that {@code familiar} may touch: its specials' target. */
    static @Nullable LivingEntity ownerTarget(ServerPlayer player, FamiliarEntity familiar, long now) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) {
            return null;
        }
        ServerLevel level = player.serverLevel();
        OptionalInt id = s.targets.latest(now, FamiliarRules.RECENT_TICKS, i -> familiar.valid(player, level.getEntity(i), FamiliarMode.ATTACK));
        return id.isPresent() ? (LivingEntity) level.getEntity(id.getAsInt()) : null;
    }

    /** True if {@code mob} hurt {@code player} or one of their familiars lately. */
    static boolean attackedLately(ServerPlayer player, Entity mob, long now) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) {
            return false;
        }
        long at = s.attackers.lastHit(mob.getId());
        return at != Long.MIN_VALUE && now - at <= FamiliarRules.RECENT_TICKS;
    }

    // ------------------------------------------------------------------ summon, dismiss, cycle

    /**
     * The familiar key held, or a lantern used ({@code preferred}): dismisses the familiar that is out if it is that lantern's
     * (or if no lantern was named); otherwise summons {@code preferred}'s familiar, or the first lit lantern's (hands,
     * then hotbar, then the rest), dismissing any other first.
     */
    public static void toggle(ServerPlayer player, @Nullable ItemStack preferred) {
        Session s = SESSIONS.get(player.getUUID());
        FamiliarBond wanted = preferred == null ? null : FamiliarLanternItem.bond(preferred);
        if (s != null && (wanted == null || wanted.id().equals(s.bond))) {
            dismiss(player);
            return;
        }
        ItemStack stack = preferred != null && wanted != null ? preferred : firstLantern(player);
        if (stack.isEmpty()) {
            player.displayClientMessage(Component.translatable("cosmicbreach.familiar.no_lantern"), true);
            return;
        }
        summon(player, stack);
    }

    /** Summons the familiar of {@code stack} at {@code player}'s side (dismissing any other). False if the lantern is dark. */
    public static boolean summon(ServerPlayer player, ItemStack stack) {
        FamiliarBond bond = FamiliarLanternItem.bond(stack);
        if (bond == null) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (bond.state(false, now) == LanternState.DARK) {
            player.displayClientMessage(Component.translatable("cosmicbreach.familiar.dark",
                    Component.translatable(bond.kind().nameKey()), LanternState.secondsLeft(bond.darkUntil(), now)), true);
            return false;
        }
        if (SESSIONS.containsKey(player.getUUID())) {
            dismiss(player);
        }
        FamiliarEntity e = spawn(player, stack, bond, now);
        if (e == null) {
            return false;
        }
        Session s = new Session(bond.id());
        s.entity = e;
        s.lastHealth = e.getHealth() / e.getMaxHealth();
        SESSIONS.put(player.getUUID(), s);
        player.setData(FamiliarRegistry.ACTIVE, bond.id());
        level.playSound(null, e.getX(), e.getY(), e.getZ(), FamiliarRegistry.SUMMON.get(), SoundSource.PLAYERS, 0.9f, 1.0f);
        FamiliarNet.fx(e, FamiliarFxPayload.SUMMON, e.getId(), player.getId(), e.position(), bond.kind().ordinal());
        return true;
    }

    private static @Nullable FamiliarEntity spawn(ServerPlayer player, ItemStack stack, FamiliarBond bond, long now) {
        ServerLevel level = player.serverLevel();
        FamiliarEntity e = FamiliarRegistry.type(bond.kind()).create(level);
        if (e == null) {
            return null;
        }
        e.bind(player, bond, now);
        Vec3 at = e.home(player, now + e.phase);
        e.moveTo(at.x, at.y, at.z, player.getYRot(), 0f);
        e.setYHeadRot(player.getYRot());
        e.setYBodyRot(player.getYRot());
        if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            e.setCustomName(stack.getHoverName());
        }
        level.addFreshEntity(e);
        return e;
    }

    /** Sends the familiar {@code player} has out back into its lantern, keeping its health there. */
    public static void dismiss(ServerPlayer player) {
        Session s = SESSIONS.remove(player.getUUID());
        player.setData(FamiliarRegistry.ACTIVE, FamiliarRegistry.NONE);
        if (s == null) {
            return;
        }
        long now = player.level().getGameTime();
        FamiliarEntity e = s.entity;
        float health = e != null && !e.isRemoved() && e.getMaxHealth() > 0 ? e.getHealth() / e.getMaxHealth() : s.lastHealth;
        ItemStack stack = lantern(player, s.bond);
        FamiliarBond bond = FamiliarLanternItem.bond(stack);
        if (bond != null) {
            stack.set(FamiliarRegistry.BOND.get(), bond.rested(health, now));
        }
        if (e != null && !e.isRemoved()) {
            ServerLevel level = (ServerLevel) e.level();
            level.playSound(null, e.getX(), e.getY(), e.getZ(), FamiliarRegistry.DISMISS.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
            FamiliarNet.fxAt(level, e.position().add(0, e.getBbHeight() * 0.5, 0), FamiliarFxPayload.DISMISS, -1, e.kind().ordinal());
            e.discard();
        }
    }

    /** A tap of the familiar key: the familiar that is out (or the lantern in hand, or the first) turns to its next mode. */
    public static void cycle(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        ItemStack stack = s != null ? lantern(player, s.bond) : ItemStack.EMPTY;
        if (stack.isEmpty()) {
            stack = firstLantern(player, true);
        }
        FamiliarBond bond = FamiliarLanternItem.bond(stack);
        if (bond == null) {
            player.displayClientMessage(Component.translatable("cosmicbreach.familiar.no_lantern"), true);
            return;
        }
        FamiliarMode next = bond.mode().next();
        stack.set(FamiliarRegistry.BOND.get(), bond.withMode(next));
        if (s != null && s.entity != null && bond.id().equals(s.bond)) {
            s.entity.setMode(next);
        }
        player.displayClientMessage(Component.translatable("cosmicbreach.familiar.mode", Component.translatable(bond.kind().nameKey()),
                Component.translatable(next.nameKey())), true);
        player.level().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), FamiliarRegistry.MODE.get(), SoundSource.PLAYERS,
                0.6f, next == FamiliarMode.ATTACK ? 1.2f : next == FamiliarMode.GUARD ? 1.0f : 0.8f);
    }

    /** The first lit lantern {@code player} carries: main hand, off hand, hotbar, the rest (a dark one if none is lit). */
    private static ItemStack firstLantern(ServerPlayer player) {
        return firstLantern(player, false);
    }

    private static ItemStack firstLantern(ServerPlayer player, boolean darkToo) {
        long now = player.level().getGameTime();
        ItemStack dark = ItemStack.EMPTY;
        Inventory inv = player.getInventory();
        ItemStack[] order = new ItemStack[inv.getContainerSize() + 2];
        order[0] = player.getItemInHand(InteractionHand.MAIN_HAND);
        order[1] = player.getItemInHand(InteractionHand.OFF_HAND);
        for (int i = 0; i < inv.getContainerSize(); i++) {
            order[i + 2] = inv.getItem(i);
        }
        for (ItemStack stack : order) {
            FamiliarBond b = FamiliarLanternItem.bond(stack);
            if (b == null) {
                continue;
            }
            if (darkToo || b.state(false, now) != LanternState.DARK) {
                return stack;
            }
            if (dark.isEmpty()) {
                dark = stack;
            }
        }
        return dark;
    }

    /** {@code e} died: its lantern goes dark for 60 s, and its session ends. */
    static void died(FamiliarEntity e) {
        ServerPlayer player = e.ownerPlayer();
        if (player == null) {
            return;
        }
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.entity != e) {
            return;
        }
        SESSIONS.remove(player.getUUID());
        player.setData(FamiliarRegistry.ACTIVE, FamiliarRegistry.NONE);
        long now = player.level().getGameTime();
        ItemStack stack = lantern(player, s.bond);
        FamiliarBond bond = FamiliarLanternItem.bond(stack);
        if (bond != null) {
            stack.set(FamiliarRegistry.BOND.get(), bond.died(now));
        }
        FamiliarNet.fxAt((ServerLevel) e.level(), e.position().add(0, e.getBbHeight() * 0.5, 0), FamiliarFxPayload.DEATH, -1, e.kind().ordinal());
        player.displayClientMessage(Component.translatable("cosmicbreach.familiar.died", Component.translatable(e.kind().nameKey())), true);
    }

    // ------------------------------------------------------------------ the owner's life

    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) {
            return;
        }
        FamiliarEntity e = s.entity;
        if (e != null && !e.isRemoved() && e.getMaxHealth() > 0) {
            s.lastHealth = e.getHealth() / e.getMaxHealth();
        }
        if (player.tickCount % 10 != 0 && e != null && !e.isRemoved() && e.level() == player.level()) {
            return;
        }
        ItemStack stack = lantern(player, s.bond);
        if (stack.isEmpty() || !player.isAlive() || player.isSpectator()) {
            dismiss(player); // the lantern left them: the familiar goes with it
            return;
        }
        if (e == null || e.isRemoved() || e.level() != player.level()) {
            // lost to an unloaded chunk, a dimension change or a long teleport: back at its owner's side
            FamiliarBond bond = FamiliarLanternItem.bond(stack);
            if (bond == null) {
                dismiss(player);
                return;
            }
            if (e != null && !e.isRemoved()) {
                e.discard();
            }
            long now = player.level().getGameTime();
            FamiliarEntity again = spawn(player, stack, bond.rested(s.lastHealth, now), now);
            s.entity = again;
            if (again == null) {
                dismiss(player);
            } else {
                FamiliarNet.fx(again, FamiliarFxPayload.SUMMON, again.getId(), player.getId(), again.position(), bond.kind().ordinal());
            }
        }
    }

    static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            dismiss(player);
        }
    }

    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isCanceled()) {
            dismiss(player);
        }
    }

    static void onServerStopping(ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            dismiss(player);
        }
        SESSIONS.clear();
    }

    /** The owner's fights: what they hit (by their own hand) and what hit them or their familiar. */
    static void onDamaged(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || event.getNewDamage() <= 0) {
            return;
        }
        long now = victim.level().getGameTime();
        Entity by = event.getSource().getEntity();
        Entity direct = event.getSource().getDirectEntity();
        if (by instanceof ServerPlayer owner && victim != owner && !(victim instanceof FamiliarEntity) && !(direct instanceof FamiliarEntity)) {
            Session s = SESSIONS.get(owner.getUUID());
            if (s != null) {
                s.targets.hit(victim.getId(), now);
            }
        }
        if (by instanceof LivingEntity attacker && !(attacker instanceof FamiliarEntity)) {
            ServerPlayer owner = victim instanceof ServerPlayer p ? p : victim instanceof FamiliarEntity f ? f.ownerPlayer() : null;
            if (owner != null && attacker != owner) {
                Session s = SESSIONS.get(owner.getUUID());
                if (s != null) {
                    s.attackers.hit(attacker.getId(), now);
                }
            }
        }
    }

    /** Test and debug: every session ends (no lantern is touched). */
    static void clear() {
        SESSIONS.clear();
    }
}
