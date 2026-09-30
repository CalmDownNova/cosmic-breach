package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Steps;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * Helpers for the multiplayer scenarios (join mode, {@code -Pjoin=<host:port>}): everything from the client's side, since
 * a real server can't be reached into. Commands go through the player's connection like typed ones (the test account
 * must be an operator), positions come from the chat feedback of the debug commands, and checks read what the client
 * sees (entities, blocks, inventory, screens, chat).
 */
final class MpKit {
    private static final List<String> CHAT = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean LISTENING = new AtomicBoolean();

    private MpKit() {
    }

    /** Starts keeping every chat line this client receives (once per game). */
    static void listen() {
        if (LISTENING.compareAndSet(false, true)) {
            NeoForge.EVENT_BUS.addListener(ClientChatReceivedEvent.class, e -> CHAT.add(e.getMessage().getString()));
        }
    }

    static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /** True when this client plays on a server of its own (no built-in server behind it). */
    static boolean remote() {
        Minecraft mc = mc();
        return mc.getSingleplayerServer() == null && mc.getConnection() != null && mc.player != null;
    }

    /** Sends {@code command} (no slash) as the player. */
    static void send(String command) {
        mc().player.connection.sendCommand(command);
    }

    /** Sends a formatted command (Locale.ROOT, so decimals use a dot). */
    static void send(String format, Object... args) {
        send(String.format(Locale.ROOT, format, args));
    }

    /** The chat lines received so far. */
    static int chatSize() {
        return CHAT.size();
    }

    /** The groups of the last chat line from index {@code from} on that matches {@code pattern}, or null. */
    static @Nullable String[] lastMatch(Pattern pattern, int from) {
        for (int i = CHAT.size() - 1; i >= Math.max(0, from); i--) {
            Matcher m = pattern.matcher(CHAT.get(i));
            if (m.find()) {
                String[] groups = new String[m.groupCount()];
                for (int g = 0; g < groups.length; g++) {
                    groups[g] = m.group(g + 1);
                }
                return groups;
            }
        }
        return null;
    }

    /** True if a chat line from index {@code from} on contains {@code text}. */
    static boolean chatSince(int from, String text) {
        for (int i = Math.max(0, from); i < CHAT.size(); i++) {
            if (CHAT.get(i).contains(text)) {
                return true;
            }
        }
        return false;
    }

    /** Turns the player to look at {@code target} (client side; the server hears it with the next move packet). */
    static void aim(Vec3 target) {
        Minecraft mc = mc();
        Vec3 eye = mc.player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)));
        float pitch = Mth.clamp((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), -90f, 90f);
        var p = mc.player;
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
        p.setYHeadRot(yaw);
        p.yHeadRotO = yaw;
        p.yBodyRot = yaw;
        p.yBodyRotO = yaw;
    }

    /** Selects the hotbar slot holding {@code item} the way a number key does. */
    static void select(Item item) {
        Minecraft mc = mc();
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(item)) {
                KeyMapping.click(mc.options.keyHotbarSlots[i].getKey());
                return;
            }
        }
        throw new Steps.Failure(item + " is not in the hotbar");
    }

    /** How many of {@code item} the player carries (main inventory and hotbar). */
    static int count(Item item) {
        int n = 0;
        for (ItemStack stack : mc().player.getInventory().items) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /** The key mapping registered under {@code name} (another mod's), or a failure. */
    static KeyMapping key(String name) {
        for (KeyMapping k : mc().options.keyMappings) {
            if (k.getName().equals(name)) {
                return k;
            }
        }
        throw new Steps.Failure("no key mapping " + name);
    }

    /** Every {@code type} this client knows within {@code range} of the player that {@code filter} accepts. */
    static <T extends Entity> List<T> near(Class<T> type, double range, Predicate<T> filter) {
        Minecraft mc = mc();
        if (mc.level == null || mc.player == null) {
            return new ArrayList<>();
        }
        return mc.level.getEntitiesOfClass(type, new AABB(mc.player.position(), mc.player.position()).inflate(range), filter);
    }

    /** The nearest {@code type} within {@code range}, or null. */
    static <T extends Entity> @Nullable T nearest(Class<T> type, double range) {
        T best = null;
        double bestD = Double.MAX_VALUE;
        for (T e : near(type, range, e -> e.isAlive())) {
            double d = e.distanceToSqr(mc().player);
            if (d < bestD) {
                best = e;
                bestD = d;
            }
        }
        return best;
    }

    /** Holds (and clicks) or lets go of a key, the way real input does. */
    static void key(KeyMapping key, boolean down) {
        KeyMapping.set(key.getKey(), down);
        if (down) {
            KeyMapping.click(key.getKey());
        }
    }

    /**
     * A step body that chases the nearest {@code type} and swings at it every 9 ticks (hopping close with a /tp when it
     * is out of reach), until {@code done} holds. For {@link Steps#waitUntil}.
     */
    static <T extends Entity> java.util.function.BooleanSupplier swingAt(Class<T> type, double range, java.util.function.BooleanSupplier done) {
        int[] tick = {0};
        return () -> {
            Minecraft mc = mc();
            KeyMapping attack = mc.options.keyAttack;
            if (done.getAsBoolean()) {
                KeyMapping.set(attack.getKey(), false);
                return true;
            }
            T target = nearest(type, range);
            if (target == null) {
                return false;
            }
            int t = tick[0]++;
            Vec3 at = target.position();
            Vec3 me = mc.player.position();
            if (me.distanceTo(at) > 2.6 && t % 9 == 2) {
                Vec3 flat = new Vec3(me.x - at.x, 0, me.z - at.z);
                Vec3 from = at.add(flat.lengthSqr() < 1e-4 ? new Vec3(1.6, 0, 0) : flat.normalize().scale(1.6));
                send("tp @s %.2f %.2f %.2f facing %.2f %.2f %.2f", from.x, at.y, from.z, at.x, at.y + 0.5, at.z);
            }
            aim(at.add(0, target.getBbHeight() * 0.5, 0));
            if (t % 9 == 0) {
                key(attack, true);
            } else if (t % 9 == 1) {
                KeyMapping.set(attack.getKey(), false);
            }
            return false;
        };
    }
}
