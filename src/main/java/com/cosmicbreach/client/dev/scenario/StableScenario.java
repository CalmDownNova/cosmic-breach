package com.cosmicbreach.client.dev.scenario;

import com.cosmicbreach.client.dev.Scenario;
import com.cosmicbreach.client.dev.Steps;
import com.cosmicbreach.guardian.GuardianPayouts;
import com.cosmicbreach.guardian.OwedRewards;
import com.cosmicbreach.mount.DriftManta;
import com.cosmicbreach.mount.LumenStag;
import com.cosmicbreach.mount.MountCareRules;
import com.cosmicbreach.mount.Mounts;
import com.cosmicbreach.mount.Stable;
import com.cosmicbreach.mount.StableCrystalItem;
import com.cosmicbreach.mount.StagRules;
import com.cosmicbreach.mount.StowedMount;
import com.cosmicbreach.registry.ModBlocks;
import com.cosmicbreach.world.AetheriaWorld;
import com.cosmicbreach.world.ShearBands;
import com.cosmicbreach.world.weather.MeteorShower;
import com.cosmicbreach.world.weather.SolarFlare;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.jetbrains.annotations.Nullable;

/**
 * Mount care (1.1 design section 4), part {@code stable}, in the Drift on a 13 by 13 test rock built in open air, with
 * real inputs: a tamed stingray swims to within reach; named, hurt and harnessed, it goes into a Stable Crystal and comes
 * back out beside the player with its health, name, harness and owner (and so does a stag); its owner's iron sword and a
 * meteor strike leave it unharmed; a Gravity Tide carries a dropped item away but not the parked stingray; dropped below
 * the Drift, up a shaft cleared beside the rock, it climbs back to its last safe spot, over the rock's edge; with no safe
 * spot remembered (as in a world saved before 1.1) it climbs to its owner's side; stowed with an old safe spot far away
 * and a climb under way, it is set down where the player puts it and stays there; ridden down into the gap below by a
 * rider who may not pass, it is thrown back with its rider still on it; a wild one comes to the rock's edge, within
 * reach, while the player holds a Resonance Chime, and goes round a wall that stands in its way. The quality review's
 * fixes: a friend's blow is turned away from the parked and the ridden stingray with PvP off and is not with PvP on, and the
 * gear's scorch is not turned away; a crystal holding a mount cannot be hurt (not even in its first moment), does not
 * despawn, goes back to its owner when it falls out of the world, is killed or is destroyed by a creative player's blow, is
 * owed to its owner when they are not about (paid when they next log in or respawn), and sets the mount down when it is
 * killed with no owner about; a stingray a crystal sets down below the Drift is left there; another player's stingray, a
 * dying one and a crystal whose data names a pig are refused.
 *
 * <p>The sections after the first climb back note an expectation that fails and go on, so one run shows every one that
 * does; the run fails on them at the end.
 */
public final class StableScenario implements Scenario {
    private final Minecraft mc = Minecraft.getInstance();
    private final List<String> results = new ArrayList<>();
    /** The block players stand in is {@code rock}; the rock itself is one below. */
    private BlockPos rock = BlockPos.ZERO;
    private int manta = -1;
    private int wild = -1;
    private int stag = -1;
    private int item = -1;
    private final double[] mark = new double[4];
    /** The health the crystal took with the stingray (it regenerates a little now and then, so it is read, not assumed). */
    private final float[] stowedHealth = {0f};
    /** Expectations of the later sections that did not hold: noted so one run shows them all, and failed on at the end. */
    private final List<String> failures = new ArrayList<>();
    /** A player who is not about: owns the mounts that must not go back to anyone. */
    private static final UUID STRANGER = UUID.nameUUIDFromBytes("stable-stranger".getBytes(StandardCharsets.UTF_8));

    @Override
    public int timeBudgetSeconds() {
        return 900;
    }

    private static @Nullable DriftManta server(ServerPlayer p, int id) {
        return p.serverLevel().getEntity(id) instanceof DriftManta m ? m : null;
    }

    private static @Nullable LumenStag serverStag(ServerPlayer p, int id) {
        return p.serverLevel().getEntity(id) instanceof LumenStag s ? s : null;
    }

    private @Nullable Entity client(int id) {
        return mc.level == null ? null : mc.level.getEntity(id);
    }

    /** Distance from the player's eye to the near edge of entity {@code id}'s box (vanilla reach is 3). */
    private double reach(int id) {
        Entity e = client(id);
        if (e == null) {
            return Double.MAX_VALUE;
        }
        Vec3 eye = mc.player.getEyePosition();
        AABB b = e.getBoundingBox();
        double dx = Math.max(0, Math.max(b.minX - eye.x, eye.x - b.maxX));
        double dy = Math.max(0, Math.max(b.minY - eye.y, eye.y - b.maxY));
        double dz = Math.max(0, Math.max(b.minZ - eye.z, eye.z - b.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void select(@Nullable Item item) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (item == null ? s.isEmpty() : s.is(item)) {
                mc.player.getInventory().selected = i;
                return;
            }
        }
        throw new Steps.Failure("no " + (item == null ? "empty slot" : item) + " in the hotbar");
    }

    private void aimAt(int id) {
        Entity e = client(id);
        if (e == null) {
            throw new Steps.Failure("entity " + id + " is not here");
        }
        CryptKit.aim(mc, e.getBoundingBox().getCenter());
    }

    /** A command with numbers worked out when the step runs, sent as the player. */
    private void command(String format, Object... args) {
        mc.player.connection.sendCommand(String.format(Locale.ROOT, format, args));
    }

    /** Notes {@code what} as failed unless {@code ok}; the run goes on and fails on the notes at the end. */
    private void expect(String what, boolean ok) {
        if (!ok) {
            failures.add(what);
        }
    }

    /** Waits up to {@code ticks} for {@code condition}; running out of time is noted as a failure and the run goes on. */
    private void softWait(Steps steps, String what, int ticks, BooleanSupplier condition) {
        int[] waited = {0};
        steps.waitUntil(what, ticks + 20, () -> {
            if (condition.getAsBoolean()) {
                return true;
            }
            if (++waited[0] > ticks) {
                failures.add("timed out after " + ticks + " ticks waiting until " + what);
                return true;
            }
            return false;
        });
    }

    @Override
    public void steps(Steps steps) {
        setUp(steps);
        stowAndRelease(steps);
        stagToo(steps);
        safeWhenParked(steps);
        rescue(steps);
        ownerFallback(steps);
        releaseKeepsPlace(steps);
        crystalSafety(steps);
        gapBounce(steps);
        lure(steps);
        lureAround(steps);
        steps.log("results", () -> String.join(" | ", results));
        steps.log("expectations that failed", () -> failures.isEmpty() ? "none" : String.join(" | ", failures));
        steps.check("every expectation of the later sections held", failures::isEmpty);
    }

    private void setUp(Steps steps) {
        steps.command("gamemode creative")
                .command("difficulty normal")
                .command("time set noon")
                .command("gamerule doDaylightCycle false")
                .command("gamerule doMobSpawning false")
                .command("advancement grant @s only cosmicbreach:attunement/drift")
                .command("advancement revoke @s only cosmicbreach:attunement/deep")
                .command("cosmicbreach debug goto drift")
                .waitUntil("in Aetheria", 600, () -> mc.level != null && AetheriaWorld.is(mc.level))
                .waitUntil("the view is drawn", 1600, LeviathanScenario.settled(mc, 1200))
                .command("cosmicbreach weather clear")
                // the rock hangs 20 above wherever the player arrived, kept inside the Drift (166 to 294) with room to drop
                // below it, and has open air over it, a way out to the east and a shaft down beside it (the climb back)
                .run("build the rock, the air round it and the shaft", () -> {
                    BlockPos at = ServerQuery.ask(p -> p.blockPosition());
                    rock = new BlockPos(at.getX(), Mth.clamp(at.getY() + 20, 190, 262), at.getZ());
                    int x = rock.getX();
                    int y = rock.getY();
                    int z = rock.getZ();
                    command("fill %d %d %d %d %d %d minecraft:air", x - 8, y - 1, z - 8, x + 8, y + 12, z + 8);
                    command("fill %d %d %d %d %d %d minecraft:air", x + 7, y - 4, z - 5, x + 26, y + 8, z + 5);
                    command("fill %d %d %d %d %d %d minecraft:air", x + 8, 138, z - 2, x + 12, y - 5, z + 2);
                    command("fill %d %d %d %d %d %d cosmicbreach:driftstone", x - 6, y - 1, z - 6, x + 6, y - 1, z + 6);
                })
                .waitUntil("the rock stands", 100, () -> mc.level.getBlockState(rock.below()).is(ModBlocks.DRIFTSTONE.get()))
                .run("onto the rock", () -> ColossusScenario.tp(mc, rock.getX() + 0.5, rock.getY(), rock.getZ() + 0.5,
                        rock.getX() + 0.5, rock.getY() + 1, rock.getZ() - 5.0))
                .waitTicks(20)
                .command("clear @s");
    }

    private void stowAndRelease(Steps steps) {
        steps.command("cosmicbreach debug mount spawn manta")
                .waitTicks(5)
                .run("find it", () -> manta = ServerQuery.ask(p -> p.serverLevel().getEntitiesOfClass(DriftManta.class,
                        p.getBoundingBox().inflate(20), m -> !m.isTamed()).stream().findFirst().map(Entity::getId).orElse(-1)))
                .check("a stingray is here", () -> manta >= 0)
                .check("wild, it hangs out of reach", () -> reach(manta) > 3.0)
                .command("cosmicbreach debug mount tame")
                .waitUntil("just tamed, it comes within reach", 300, () -> reach(manta) <= 3.0)
                .run("name it, hurt it a little, harness it", () -> ServerQuery.ask(p -> {
                    DriftManta m = server(p, manta);
                    m.setCustomName(Component.literal("Skye"));
                    m.setHealth(31f);
                    m.equipSaddle(new ItemStack(Mounts.DRIFT_HARNESS.get()), SoundSource.NEUTRAL);
                    return true;
                }))
                .command("give @s cosmicbreach:stable_crystal")
                .waitTicks(5)
                .run("hold the crystal", () -> select(Stable.STABLE_CRYSTAL.get()))
                .run("aim at the stingray", () -> aimAt(manta))
                .press(mc.options.keyUse)
                .waitUntil("it went into the crystal", 40, () -> ServerQuery.ask(p -> server(p, manta) == null)
                        && StableCrystalItem.stowed(mc.player.getMainHandItem()) != null)
                .check("the crystal holds all of it", () -> {
                    StowedMount s = StableCrystalItem.stowed(mc.player.getMainHandItem());
                    stowedHealth[0] = s.health();
                    boolean ok = s.health() >= 31f && s.health() <= 33f && s.name().orElse("").equals("Skye") && s.data().contains("SaddleItem")
                            && s.data().hasUUID("Owner");
                    results.add("stowed " + s.type() + " health " + s.health() + " name " + s.name().orElse("-"));
                    return ok;
                })
                .run("aim at the rock three blocks ahead", () -> CryptKit.aim(mc, Vec3.atCenterOf(rock.below().relative(mc.player.getDirection(), 3))))
                .press(mc.options.keyUse)
                .waitUntil("it came back out beside the player", 40, () -> ServerQuery.ask(p -> p.serverLevel()
                        .getEntitiesOfClass(DriftManta.class, p.getBoundingBox().inflate(8), m -> m.hasCustomName()).size() == 1))
                .run("remember it", () -> manta = ServerQuery.ask(p -> p.serverLevel().getEntitiesOfClass(DriftManta.class,
                        p.getBoundingBox().inflate(8), m -> m.hasCustomName()).get(0).getId()))
                .check("health, name, harness and owner came back", () -> ServerQuery.ask(p -> {
                    DriftManta m = server(p, manta);
                    return m != null && m.getHealth() >= stowedHealth[0] && m.getHealth() <= stowedHealth[0] + 2f
                            && "Skye".equals(m.getCustomName().getString()) && m.isSaddled()
                            && p.getUUID().equals(m.getOwnerUUID()) && m.isTamed();
                }))
                // the client hears of the emptied crystal with the next slot update, a tick after the stingray is back
                .waitUntil("the crystal is empty again", 20, () -> StableCrystalItem.stowed(mc.player.getMainHandItem()) == null)
                .check("and empty on the server too", () -> ServerQuery.ask(p -> p.getMainHandItem().get(Stable.STOWED.get()) == null));
    }

    /** The crystal works for every mod mount: a stag goes in and comes out the same way. */
    private void stagToo(Steps steps) {
        float[] health = {0f};
        steps.command("cosmicbreach debug mount spawn stag 1")
                .waitTicks(5)
                .run("bring the stag onto the rock and tame it", () -> stag = ServerQuery.ask(p -> {
                    LumenStag s = p.serverLevel().getEntitiesOfClass(LumenStag.class, p.getBoundingBox().inflate(24), m -> !m.isTamed())
                            .stream().findFirst().orElse(null);
                    if (s == null) {
                        return -1;
                    }
                    s.teleportTo(rock.getX() + 0.5, rock.getY(), rock.getZ() + 3.5);
                    s.setDeltaMovement(Vec3.ZERO);
                    s.setTrust(StagRules.TAME_AT);
                    s.tameTo(p);
                    s.setNoAi(true); // it stands where it is put: a stag that strolls can move out from under the crosshair before the click (once in nine runs)
                    s.setCustomName(Component.literal("Dawn"));
                    s.setHealth(20f);
                    return s.getId();
                }))
                .check("a stag is here", () -> stag >= 0)
                .waitUntil("the client sees it", 40, () -> client(stag) != null)
                .waitTicks(10)
                .run("hold the crystal", () -> select(Stable.STABLE_CRYSTAL.get()))
                .run("aim at the stag", () -> aimAt(stag))
                .press(mc.options.keyUse)
                .waitUntil("it went into the crystal", 40, () -> ServerQuery.ask(p -> serverStag(p, stag) == null)
                        && StableCrystalItem.stowed(mc.player.getMainHandItem()) != null)
                .check("the crystal holds the stag, named and hurt, with its owner", () -> {
                    StowedMount s = StableCrystalItem.stowed(mc.player.getMainHandItem());
                    health[0] = s.health();
                    results.add("stowed " + s.type() + " health " + s.health() + " name " + s.name().orElse("-"));
                    return s.type().getPath().equals("lumen_stag") && s.health() >= 20f && s.health() <= 22f
                            && s.name().orElse("").equals("Dawn") && s.data().hasUUID("Owner");
                })
                .run("aim at the rock two blocks to the side", () -> CryptKit.aim(mc, Vec3.atCenterOf(rock.below().relative(mc.player.getDirection(), 2))))
                .press(mc.options.keyUse)
                .waitUntil("it came back out", 40, () -> ServerQuery.ask(p -> p.serverLevel()
                        .getEntitiesOfClass(LumenStag.class, p.getBoundingBox().inflate(8), m -> m.hasCustomName()).size() == 1))
                .check("health, name and owner came back", () -> ServerQuery.ask(p -> {
                    LumenStag s = p.serverLevel().getEntitiesOfClass(LumenStag.class, p.getBoundingBox().inflate(8), m -> m.hasCustomName())
                            .get(0);
                    return s.getHealth() >= health[0] && s.getHealth() <= health[0] + 2f && "Dawn".equals(s.getCustomName().getString())
                            && s.isTamed() && p.getUUID().equals(s.getOwnerUUID());
                }))
                .run("send it away (the rock is needed)", () -> ServerQuery.ask(p -> {
                    p.serverLevel().getEntitiesOfClass(LumenStag.class, p.getBoundingBox().inflate(24)).forEach(Entity::discard);
                    return true;
                }));
    }

    private void safeWhenParked(Steps steps) {
        float[] before = {0f};
        double[] tide = {0, 0}; // how far the Tide carried the item and the stingray
        // straight at the damage rules first, so the swings and the meteor below are not the only proof: its owner's hit and
        // the weather's are turned away, and an ordinary hit still hurts it (it is only those two it shrugs off)
        steps.check("its owner's hit and the weather's are turned away, an ordinary hit is not", () -> ServerQuery.ask(p -> {
                    DriftManta m = server(p, manta);
                    float start = m.getHealth();
                    boolean owner = m.hurt(p.damageSources().playerAttack(p), 5.0f);
                    DamageSource meteor = new DamageSource(p.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                            .getHolderOrThrow(MeteorShower.METEOR), m.position());
                    boolean weather = m.hurt(meteor, 8.0f);
                    float untouched = m.getHealth();
                    boolean plain = m.hurt(p.damageSources().generic(), 3.0f);
                    results.add(String.format(Locale.ROOT, "hits: owner %s, weather %s, plain %s, health %.1f to %.1f to %.1f", owner, weather, plain,
                            start, untouched, m.getHealth()));
                    return !owner && !weather && untouched >= start && plain && m.getHealth() < untouched;
                }))
                .waitTicks(25)
                .run("with PvP off a friend's blow is turned away from the parked stingray, the gear's scorch is not", () -> {
                    float[] h = ServerQuery.ask(p -> {
                        DriftManta m = server(p, manta);
                        boolean pvp = p.server.isPvpAllowed();
                        p.server.setPvpAllowed(false);
                        float start = m.getHealth();
                        boolean blow = m.hurt(p.damageSources().playerAttack(friend(p)), 5.0f);
                        float afterBlow = m.getHealth();
                        boolean scorch = m.hurt(SolarFlare.scorch(p.serverLevel()), 1.0f);
                        p.server.setPvpAllowed(pvp);
                        return new float[] {start, afterBlow, m.getHealth(), blow ? 1 : 0, scorch ? 1 : 0};
                    });
                    String line = String.format(Locale.ROOT, "PvP off: friend's blow hurt it %s (health %.1f to %.1f), the gear's scorch hurt it %s (to %.1f)",
                            h[3] > 0, h[0], h[1], h[4] > 0, h[2]);
                    results.add(line);
                    expect(line, h[3] == 0 && h[1] >= h[0] && h[4] > 0 && h[2] < h[1]);
                })
                .waitTicks(25)
                // quality re-review, Minor 1: the mod's own weapons follow the server's PvP setting, so do vanilla's hits
                .run("with PvP on a friend's blow is fair game, even on a parked stingray", () -> {
                    float[] h = ServerQuery.ask(p -> {
                        DriftManta m = server(p, manta);
                        boolean pvp = p.server.isPvpAllowed();
                        p.server.setPvpAllowed(true);
                        float start = m.getHealth();
                        boolean blow = m.hurt(p.damageSources().playerAttack(friend(p)), 5.0f);
                        float after = m.getHealth();
                        p.server.setPvpAllowed(pvp);
                        return new float[] {start, after, blow ? 1 : 0};
                    });
                    String line = String.format(Locale.ROOT, "PvP on: friend's blow hurt it %s (health %.1f to %.1f)", h[2] > 0, h[0], h[1]);
                    results.add(line);
                    expect(line, h[2] > 0 && h[1] < h[0]);
                })
                .waitTicks(25)
                .command("give @s minecraft:iron_sword")
                .waitTicks(5)
                .run("hold the sword", () -> select(Items.IRON_SWORD))
                .run("its health now", () -> before[0] = ServerQuery.ask(p -> server(p, manta).getHealth()))
                .run("aim at it", () -> aimAt(manta))
                .press(mc.options.keyAttack)
                .waitTicks(10)
                .check("its owner's sword did nothing", () -> ServerQuery.ask(p -> server(p, manta).getHealth()) >= before[0])
                .run("a meteor lands right under it", () -> ServerQuery.ask(p -> {
                    DriftManta m = server(p, manta);
                    BlockPos ground = BlockPos.containing(m.getX(), rock.getY() - 1, m.getZ());
                    MeteorShower.strike(p.serverLevel(), ground, 0, 0f);
                    return true;
                }))
                .waitTicks(40)
                .check("the meteor did nothing to it", () -> ServerQuery.ask(p -> server(p, manta).getHealth()) >= before[0])
                .run("an item on the rock and the stingray's place", () -> {
                    item = ServerQuery.ask(p -> {
                        ItemEntity e = new ItemEntity(p.serverLevel(), rock.getX() + 0.5, rock.getY(), rock.getZ() + 3.5, new ItemStack(Items.STONE));
                        e.setPickUpDelay(32767);
                        p.serverLevel().addFreshEntity(e);
                        return e.getId();
                    });
                    Vec3 m = ServerQuery.ask(p -> server(p, manta).position());
                    Vec3 i = ServerQuery.ask(p -> p.serverLevel().getEntity(item).position());
                    mark[0] = m.x;
                    mark[1] = m.z;
                    mark[2] = i.x;
                    mark[3] = i.z;
                })
                // a Tide carries whatever stands in the Drift, a player included (one flying is left alone): so the player hovers
                // above the rock for it, or they would be swept off the rock, fall out of the world and take the level with them
                .run("up above the rock", () -> ColossusScenario.tp(mc, rock.getX() + 0.5, rock.getY() + 4.0, rock.getZ() + 0.5,
                        rock.getX() + 0.5, rock.getY() + 1, rock.getZ() - 5.0))
                .waitTicks(3)
                // flying is set now that the client is in the air: the teleport sets it before the move, and the client drops
                // it again for a player it still has on the ground, which let the Tide carry the player about (and down)
                .run("and hover there", () -> {
                    mc.player.getAbilities().flying = true;
                    mc.player.onUpdateAbilities();
                })
                .waitTicks(10)
                .check("it hovers above the rock", () -> mc.player.getAbilities().flying && !mc.player.onGround())
                .command("cosmicbreach weather tide drift")
                .command("cosmicbreach weather skip drift")
                .waitTicks(300)
                .run("measure what the Tide did", () -> ServerQuery.ask(p -> {
                    Vec3 m = server(p, manta).position();
                    Entity i = p.serverLevel().getEntity(item);
                    tide[0] = i == null ? 99 : Math.hypot(i.getX() - mark[2], i.getZ() - mark[3]);
                    tide[1] = Math.hypot(m.x - mark[0], m.z - mark[1]);
                    results.add(String.format(Locale.ROOT, "tide: item moved %.2f, stingray %.2f", tide[0], tide[1]));
                    return true;
                }))
                .log("the Tide's numbers", () -> ServerQuery.ask(p -> {
                    Entity i = p.serverLevel().getEntity(item);
                    return String.format(Locale.ROOT, "tide: item moved %.2f from (%.1f, %.1f) to %s, stingray moved %.2f from (%.1f, %.1f) to %s, heading %.2f, "
                                    + "player at %s flying %s, stingray rescuing %s remembers %s", tide[0], mark[2], mark[3],
                            i == null ? "nowhere (gone)" : i.position().toString(), tide[1], mark[0], mark[1], server(p, manta).position(),
                            com.cosmicbreach.world.weather.GravityTide.heading(p.serverLevel()), p.position(), p.getAbilities().flying,
                            server(p, manta).isRescuing(), server(p, manta).lastSafe());
                }))
                .check("the Tide carried the item but not the parked stingray", () -> tide[0] > 1.0 && tide[1] < 0.3)
                .command("cosmicbreach weather clear")
                .command("kill @e[type=minecraft:item]")
                .waitTicks(5)
                .run("back onto the rock", () -> ColossusScenario.tp(mc, rock.getX() + 0.5, rock.getY(), rock.getZ() + 0.5,
                        rock.getX() + 0.5, rock.getY() + 1, rock.getZ() - 5.0))
                .waitTicks(20);
    }

    /** Dropped to 150, below the Drift's floor, in the cleared shaft beside the rock: it climbs the shaft and crosses back. */
    private void rescue(Steps steps) {
        int[] ticks = {0};
        double[] top = {0};
        steps.run("drop it below the Drift, in the shaft", () -> ServerQuery.ask(p -> {
                    DriftManta m = server(p, manta);
                    m.teleportTo(rock.getX() + 10.5, 150.0, rock.getZ() + 0.5);
                    return true;
                }))
                .waitUntil("the rescue starts", 40, () -> ServerQuery.ask(p -> {
                    DriftManta m = server(p, manta);
                    return m != null && m.isRescuing();
                }))
                .waitUntil("it climbs back to where it was safe", 1800, () -> ServerQuery.ask(p -> {
                    DriftManta m = server(p, manta);
                    if (m == null) {
                        throw new Steps.Failure("the stingray is gone (it fell out of the world, or the level unloaded)");
                    }
                    ticks[0]++;
                    top[0] = Math.max(top[0], m.getY());
                    return !m.isRescuing() && m.getY() >= 166.0 && m.lastSafe() != null
                            && m.position().distanceTo(m.lastSafe()) <= 1.5;
                }))
                .log("rescued", () -> {
                    String line = String.format(Locale.ROOT, "rescued to %s after %d ticks, highest %.1f",
                            ServerQuery.ask(p -> server(p, manta).position()), ticks[0], top[0]);
                    results.add(line);
                    return line;
                })
                .run("it came in over the rock's edge, not by the 3 s set down", () -> expect(
                        "the climb back ended by the stuck rule (a set down at the spot) and not by arriving",
                        !ServerQuery.ask(p -> server(p, manta).rescueEndedStuck())))
                .check("a real climb at the rescue's own speed: not a set down after being stuck for 3 s, not slowed by the AI level of detail", () -> {
                    double blocks = rock.getY() - 150.0;
                    double flat = blocks / MountCareRules.RESCUE_SPEED;
                    return ticks[0] > 0.8 * flat && ticks[0] < 1.5 * flat + 100 && top[0] >= rock.getY() - 1.0;
                });
    }

    private void gapBounce(Steps steps) {
        int[] caught = {0};
        steps.run("bring it back beside the player", () -> ServerQuery.ask(p -> {
                    DriftManta m = server(p, manta);
                    m.teleportTo(p.getX() + 2.0, p.getY() + 0.5, p.getZ());
                    return true;
                }))
                .waitTicks(10)
                .command("gamemode survival")
                .waitUntil("in survival", 40, () -> !mc.player.isCreative())
                .run("an empty hand", () -> select(null))
                .run("aim at it", () -> aimAt(manta))
                .press(mc.options.keyUse)
                .waitUntil("riding it", 40, () -> mc.player.getVehicle() instanceof DriftManta)
                .run("with PvP off a friend's blow is turned away from the ridden stingray too", () -> {
                    float[] h = ServerQuery.ask(p -> {
                        DriftManta m = server(p, manta);
                        boolean pvp = p.server.isPvpAllowed();
                        p.server.setPvpAllowed(false);
                        float start = m.getHealth();
                        boolean blow = m.hurt(p.damageSources().playerAttack(friend(p)), 5.0f);
                        float after = m.getHealth();
                        p.server.setPvpAllowed(pvp);
                        return new float[] {start, after, blow ? 1 : 0};
                    });
                    String line = String.format(Locale.ROOT, "PvP off, ridden: friend's blow hurt it %s (health %.1f to %.1f)", h[2] > 0, h[0], h[1]);
                    results.add(line);
                    expect(line, h[2] == 0 && h[1] >= h[0]);
                })
                .run("count the catches so far", () -> caught[0] = Integer.parseInt(ShearBands.counters().split(" ")[0]))
                // the drop itself is placed by the server (diving there through unknown asteroids would make the test flaky);
                // what is under test is the catch: the rider, still mounted, sinks into the gap they may not pass
                .run("mount and rider sink into the gap below the Drift", () -> ServerQuery.ask(p -> {
                    DriftManta m = server(p, manta);
                    m.teleportTo(m.getX(), 158.5, m.getZ());
                    p.connection.send(new ClientboundMoveVehiclePacket(m));
                    return true;
                }))
                .waitUntil("the gap threw them back up together", 100, () -> Integer.parseInt(ShearBands.counters().split(" ")[0]) > caught[0]
                        && mc.player.getY() > 165.0 && mc.player.getVehicle() instanceof DriftManta)
                .check("still riding its own stingray", () -> mc.player.getVehicle() != null && mc.player.getVehicle().getId() == manta)
                .log("bounce", () -> {
                    String line = String.format(Locale.ROOT, "thrown back to %.1f, riding %s", mc.player.getY(), mc.player.isPassenger());
                    results.add(line);
                    return line;
                })
                // held a few ticks: a one tick press can be gone again before the server looks at it (it timed out once in three runs)
                .press(mc.options.keyShift, 5)
                .waitUntil("off it", 40, () -> !mc.player.isPassenger())
                .command("gamemode creative");
    }

    private void lure(Steps steps) {
        steps.run("a wild stingray out in the open air beyond the edge", () -> wild = ServerQuery.ask(p -> {
                    DriftManta m = Mounts.DRIFT_MANTA.get().create(p.serverLevel());
                    m.moveTo(rock.getX() + 18.5, rock.getY() + 1.0, rock.getZ() + 0.5, 90f, 0f);
                    m.finalizeSpawn(p.serverLevel(), p.serverLevel().getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.COMMAND, null);
                    m.setPersistenceRequired();
                    p.serverLevel().addFreshEntity(m);
                    return m.getId();
                }))
                .run("to the rock's east edge", () -> ColossusScenario.tp(mc, rock.getX() + 5.5, rock.getY(), rock.getZ() + 0.5,
                        rock.getX() + 12.0, rock.getY() + 1, rock.getZ() + 0.5))
                .command("give @s cosmicbreach:resonance_chime")
                .waitTicks(5)
                .run("hold the Chime", () -> select(Mounts.RESONANCE_CHIME.get()))
                .waitUntil("the wild one comes to the edge within reach", 600, () -> reach(wild) <= 3.0)
                .check("it waits past the edge, over open air", () -> ServerQuery.ask(p -> server(p, wild).getX()) > rock.getX() + 6.5)
                .log("lure", () -> {
                    String line = String.format(Locale.ROOT, "lure reach %.2f", reach(wild));
                    results.add(line);
                    return line;
                });
    }

    /**
     * A stingray that never recorded a safe spot (a world saved before 1.1 did not) and sits below the Drift takes its
     * owner's side as one: it climbs the shaft and crosses to the rock its owner stands on.
     */
    private void ownerFallback(Steps steps) {
        int[] ticks = {0};
        steps.run("it forgets where it was safe, and is dropped below the Drift in the shaft", () -> ServerQuery.ask(p -> {
            DriftManta m = server(p, manta);
            m.forgetSafe();
            m.teleportTo(rock.getX() + 10.5, 150.0, rock.getZ() + 0.5);
            return true;
        }));
        softWait(steps, "the climb starts though it remembers no spot", 40, () -> ServerQuery.ask(p -> server(p, manta).isRescuing()));
        softWait(steps, "it climbs back up to its owner's side", 900, () -> {
            ticks[0]++;
            return ServerQuery.ask(p -> {
                DriftManta m = server(p, manta);
                return !m.isRescuing() && m.getY() >= rock.getY() - 0.5;
            });
        });
        steps.run("it ended beside its owner, by arriving", () -> {
            double[] end = ServerQuery.ask(p -> {
                DriftManta m = server(p, manta);
                return new double[] {m.getX() - p.getX(), m.getY() - p.getY(), m.getZ() - p.getZ(), m.rescueEndedStuck() ? 1 : 0,
                        m.isRescuing() ? 1 : 0};
            });
            String line = String.format(Locale.ROOT, "no remembered spot: ended %.1f east, %.1f up, %.1f south of its owner after %d ticks%s",
                    end[0], end[1], end[2], ticks[0], end[3] > 0 ? ", by the stuck rule" : "");
            results.add(line);
            expect(line, Math.hypot(end[0], end[2]) <= 3.5 && end[1] >= -0.5 && end[1] <= 3.0 && end[3] == 0 && end[4] == 0);
        });
    }

    /**
     * Stowed with an old safe spot far away and a climb under way, it is set down where the player puts it and stays there:
     * it does not fly off to where it once stood, nor take up the climb it had begun.
     */
    private void releaseKeepsPlace(Steps steps) {
        Vec3[] put = {null};
        steps.run("hold the crystal", () -> select(Stable.STABLE_CRYSTAL.get()))
                .waitUntil("the server has it in hand", 40, () -> ServerQuery.ask(p -> p.getMainHandItem().is(Stable.STABLE_CRYSTAL.get())))
                // the old spot is planted in the mount's data first, so it goes into the crystal by the item's own click handler,
                // called directly (the earlier sections stowed it with a real click, and the release below is a real click too)
                .run("give it an old safe spot far to the east and a climb under way, then put it in the crystal", () -> {
                    boolean stowed = ServerQuery.ask(p -> {
                        DriftManta m = server(p, manta);
                        CompoundTag data = m.saveWithoutId(new CompoundTag());
                        data.putDouble("CareSafeX", rock.getX() + 24.5);
                        data.putDouble("CareSafeY", rock.getY() + 1.0);
                        data.putDouble("CareSafeZ", rock.getZ() + 0.5);
                        data.putBoolean("CareRescue", true);
                        m.load(data);
                        ItemStack crystal = p.getMainHandItem();
                        return crystal.getItem().interactLivingEntity(crystal, p, m, InteractionHand.MAIN_HAND).consumesAction();
                    });
                    expect("the crystal took the stingray", stowed);
                })
                .waitUntil("it is in the crystal", 40, () -> StableCrystalItem.stowed(mc.player.getMainHandItem()) != null)
                .run("aim at the rock three blocks ahead", () -> CryptKit.aim(mc, Vec3.atCenterOf(rock.below().relative(mc.player.getDirection(), 3))))
                .press(mc.options.keyUse)
                .waitUntil("it came back out", 40, () -> ServerQuery.ask(p -> p.serverLevel()
                        .getEntitiesOfClass(DriftManta.class, p.getBoundingBox().inflate(8), m -> m.hasCustomName()).size() == 1))
                .run("remember it and where it was set down", () -> put[0] = ServerQuery.ask(p -> {
                    DriftManta m = p.serverLevel().getEntitiesOfClass(DriftManta.class, p.getBoundingBox().inflate(8), e -> e.hasCustomName()).get(0);
                    manta = m.getId();
                    return m.position();
                }))
                .waitTicks(100)
                .run("it stayed where it was set down", () -> {
                    double away = ServerQuery.ask(p -> server(p, manta).position().distanceTo(put[0]));
                    boolean climbing = ServerQuery.ask(p -> server(p, manta).isRescuing());
                    Vec3 spot = ServerQuery.ask(p -> server(p, manta).lastSafe());
                    String line = String.format(Locale.ROOT, "set down with an old spot, left alone for 100 ticks: moved %.1f, climbing %s, remembers %s",
                            away, climbing, spot);
                    results.add(line);
                    expect(line, away < 3.0 && !climbing && (spot == null || spot.distanceTo(put[0]) < 6.0));
                });
    }

    /** A full crystal: a copy of the stingray under {@code mountId}, owned by {@code owner}, last safe at {@code safe} (null: never). */
    private ItemStack fullCrystal(ServerPlayer p, UUID mountId, UUID owner, @Nullable Vec3 safe) {
        DriftManta m = server(p, manta);
        CompoundTag tag = new CompoundTag();
        m.saveAsPassenger(tag);
        tag.putUUID("UUID", mountId);
        tag.putUUID("Owner", owner);
        tag.remove("CareSafeX");
        tag.remove("CareSafeY");
        tag.remove("CareSafeZ");
        if (safe != null) {
            tag.putDouble("CareSafeX", safe.x);
            tag.putDouble("CareSafeY", safe.y);
            tag.putDouble("CareSafeZ", safe.z);
        }
        tag.putBoolean("CareRescue", false);
        ItemStack stack = new ItemStack(Stable.STABLE_CRYSTAL.get());
        stack.set(Stable.STOWED.get(),
                new StowedMount(BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()), tag, m.getHealth(), m.getMaxHealth(), Optional.of("Copy")));
        return stack;
    }

    /** {@code crystal} as an item entity at (x, y, z) that nobody can pick up; returns its id. */
    private static int dropCrystal(ServerPlayer p, double x, double y, double z, ItemStack crystal) {
        ItemEntity e = new ItemEntity(p.serverLevel(), x, y, z, crystal, 0, 0, 0);
        e.setPickUpDelay(32767);
        p.serverLevel().addFreshEntity(e);
        return e.getId();
    }

    /**
     * Another player, who is not the owner: a fake one (the server needs no second client for a blow) that, unlike NeoForge's own
     * fake player (which can never hurt another player), may hurt other players where the server's PvP setting allows it.
     */
    private static final class Friend extends FakePlayer {
        Friend(ServerLevel level) {
            super(level, new GameProfile(UUID.nameUUIDFromBytes("stable-friend".getBytes(StandardCharsets.UTF_8)), "Friend"));
        }

        @Override
        public boolean canHarmPlayer(Player other) {
            return server.isPvpAllowed();
        }
    }

    private static ServerPlayer friend(ServerPlayer p) {
        return new Friend(p.serverLevel());
    }

    private static int fullCrystals(ServerPlayer p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (StableCrystalItem.stowed(p.getInventory().getItem(i)) != null) {
                n++;
            }
        }
        return n;
    }

    private static void emptyFullCrystals(ServerPlayer p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (StableCrystalItem.stowed(p.getInventory().getItem(i)) != null) {
                p.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private static void discard(ServerPlayer p, @Nullable UUID id, int entityId) {
        Entity e = id != null ? p.serverLevel().getEntity(id) : p.serverLevel().getEntity(entityId);
        if (e != null) {
            e.discard();
        }
    }

    /**
     * A crystal holding a mount is as safe as the mount (quality review, Important 1): nothing that hurts an item hurts it,
     * it does not despawn, it goes back to its owner when it falls out of the world or is killed, with no owner about it
     * hangs where the mount last stood safe (falling) or sets the mount down (killed). And the refusals: another player's
     * stingray, a dying one, and a crystal whose data names another kind of entity. The crystal in hand is empty here.
     */
    private void crystalSafety(Steps steps) {
        int[] id = {-1};
        UUID[] mount = {null};
        Vec3[] home = {null}; // where the mount last stood safe: on the rock, worked out when the rock is built
        steps.run("a full crystal lies on the rock", () -> {
                    home[0] = new Vec3(rock.getX() + 0.5, rock.getY() + 0.5, rock.getZ() - 3.5);
                    id[0] = ServerQuery.ask(p -> dropCrystal(p, rock.getX() + 3.5, rock.getY(), rock.getZ() - 2.5,
                            fullCrystal(p, UUID.randomUUID(), p.getUUID(), null)));
                })
                .waitTicks(5)
                .run("fire, lava, a cactus and an explosion cannot hurt it, and it will not despawn", () -> {
                    int[] seen = ServerQuery.ask(p -> {
                        ItemEntity e = (ItemEntity) p.serverLevel().getEntity(id[0]);
                        var d = p.damageSources();
                        int hurt = 0;
                        for (DamageSource s : new DamageSource[] {d.lava(), d.onFire(), d.cactus(), d.explosion(null, null)}) {
                            if (e.hurt(s, 50f)) {
                                hurt++;
                            }
                        }
                        return new int[] {hurt, e.isAlive() ? 1 : 0, e.getAge()};
                    });
                    String line = String.format(Locale.ROOT, "crystal item: %d of 4 kinds of damage hurt it, alive %d, age %d", seen[0], seen[1], seen[2]);
                    results.add(line);
                    expect(line, seen[0] == 0 && seen[1] == 1 && seen[2] == Short.MIN_VALUE);
                    ServerQuery.ask(p -> {
                        discard(p, null, id[0]);
                        return true;
                    });
                })
                .run("a full crystal falls out of the world with its owner about", () -> id[0] = ServerQuery.ask(p -> dropCrystal(p, rock.getX() + 0.5,
                        p.serverLevel().getMinBuildHeight() - 12.0, rock.getZ() + 0.5, fullCrystal(p, UUID.randomUUID(), p.getUUID(), null))))
                .waitTicks(10)
                .run("it is back in its owner's inventory", () -> {
                    int[] seen = ServerQuery.ask(p -> new int[] {fullCrystals(p), p.serverLevel().getEntity(id[0]) == null ? 0 : 1});
                    String line = String.format(Locale.ROOT, "out of the world, owner about: %d full crystal in hand, the item still there %d", seen[0], seen[1]);
                    results.add(line);
                    expect(line, seen[0] == 1 && seen[1] == 0);
                    ServerQuery.ask(p -> {
                        emptyFullCrystals(p);
                        discard(p, null, id[0]);
                        return true;
                    });
                })
                .run("another full crystal, its mount's owner not about, falls out of the world", () -> id[0] = ServerQuery.ask(p -> dropCrystal(p,
                        rock.getX() + 6.5, p.serverLevel().getMinBuildHeight() - 12.0, rock.getZ() + 0.5,
                        fullCrystal(p, UUID.randomUUID(), STRANGER, home[0]))))
                .waitTicks(10)
                // quality re-review, Important 1: it is taken out of the world and owed to its owner, who has it when they next log in
                // or respawn (logging in is GuardianPayouts.settle, called here for a fake player who is that owner)
                .run("it is kept for its owner, and paid out when they are about", () -> {
                    int[] kept = ServerQuery.ask(p -> new int[] {p.serverLevel().getEntity(id[0]) == null ? 1 : 0,
                            OwedRewards.get(p.server).owes(STRANGER) ? 1 : 0});
                    int[] paid = ServerQuery.ask(p -> {
                        ServerPlayer owner = FakePlayerFactory.get(p.serverLevel(), new GameProfile(STRANGER, "Stranger"));
                        GuardianPayouts.settle(owner);
                        int n = fullCrystals(owner);
                        boolean still = OwedRewards.get(p.server).owes(STRANGER);
                        emptyFullCrystals(owner);
                        return new int[] {n, still ? 1 : 0};
                    });
                    String line = String.format(Locale.ROOT, "out of the world, no owner: the item gone %d and owed %d; the owner about, %d full crystal in their pack, still owed %d",
                            kept[0], kept[1], paid[0], paid[1]);
                    results.add(line);
                    expect(line, kept[0] == 1 && kept[1] == 1 && paid[0] == 1 && paid[1] == 0);
                    ServerQuery.ask(p -> {
                        discard(p, null, id[0]);
                        return true;
                    });
                })
                .run("a full crystal, its mount's owner not about, lies on the rock", () -> {
                    mount[0] = UUID.randomUUID();
                    id[0] = ServerQuery.ask(p -> dropCrystal(p, rock.getX() + 4.5, rock.getY(), rock.getZ() + 4.5, fullCrystal(p, mount[0], STRANGER, null)));
                })
                .waitTicks(5)
                .run("it is killed", () -> ServerQuery.ask(p -> {
                    Entity e = p.serverLevel().getEntity(id[0]);
                    if (e != null) {
                        e.kill();
                    }
                    return true;
                }))
                .waitTicks(5)
                .run("its mount stands where it was, still its owner's", () -> {
                    double[] seen = ServerQuery.ask(p -> {
                        Entity m = p.serverLevel().getEntity(mount[0]);
                        if (m == null) {
                            return new double[] {-1, 0};
                        }
                        return new double[] {Math.hypot(m.getX() - (rock.getX() + 4.5), m.getZ() - (rock.getZ() + 4.5)),
                                m instanceof DriftManta d && STRANGER.equals(d.getOwnerUUID()) ? 1 : 0};
                    });
                    String line = String.format(Locale.ROOT, "killed, no owner: its mount %s, %.1f from the item, keeps its owner %s",
                            seen[0] >= 0 ? "stands" : "is gone", Math.max(seen[0], 0), seen[1] > 0);
                    results.add(line);
                    expect(line, seen[0] >= 0 && seen[0] < 4.0 && seen[1] > 0);
                    ServerQuery.ask(p -> {
                        discard(p, mount[0], -1);
                        return true;
                    });
                })
                .run("a full crystal of the player's own mount lies on the rock", () -> id[0] = ServerQuery.ask(p -> dropCrystal(p, rock.getX() + 4.5,
                        rock.getY(), rock.getZ() - 4.5, fullCrystal(p, UUID.randomUUID(), p.getUUID(), null))))
                .waitTicks(5)
                .run("it is killed", () -> ServerQuery.ask(p -> {
                    Entity e = p.serverLevel().getEntity(id[0]);
                    if (e != null) {
                        e.kill();
                    }
                    return true;
                }))
                .waitTicks(5)
                .run("it went back to its owner", () -> {
                    int n = ServerQuery.ask(p -> fullCrystals(p));
                    String line = "killed, owner about: " + n + " full crystal in hand";
                    results.add(line);
                    expect(line, n == 1);
                    ServerQuery.ask(p -> {
                        emptyFullCrystals(p);
                        return true;
                    });
                })
                // quality re-review, Minor 2: a creative player's blow bypasses invulnerability, and an item is unprotected until it
                // has had a tick of its own: neither may cost a mount
                .run("a full crystal of the player's own mount lies on the rock and a creative player's blow destroys it", () -> {
                    id[0] = ServerQuery.ask(p -> dropCrystal(p, rock.getX() + 3.5, rock.getY(), rock.getZ() + 4.5,
                            fullCrystal(p, UUID.randomUUID(), p.getUUID(), null)));
                })
                .waitTicks(5)
                .run("the blow", () -> ServerQuery.ask(p -> {
                    Entity e = p.serverLevel().getEntity(id[0]);
                    if (e != null) {
                        e.hurt(p.damageSources().playerAttack(p), 50f);
                    }
                    return true;
                }))
                .waitTicks(5)
                .run("the crystal went back to its owner", () -> {
                    int n = ServerQuery.ask(p -> fullCrystals(p));
                    String line = "destroyed by a creative player's blow, owner about: " + n + " full crystal in hand";
                    results.add(line);
                    expect(line, n == 1);
                    ServerQuery.ask(p -> {
                        emptyFullCrystals(p);
                        discard(p, null, id[0]);
                        return true;
                    });
                })
                .run("a full crystal spilled into the world is safe from its very first moment", () -> {
                    int[] seen = ServerQuery.ask(p -> {
                        ItemEntity e = new ItemEntity(p.serverLevel(), rock.getX() + 2.5, rock.getY(), rock.getZ() + 4.5,
                                fullCrystal(p, UUID.randomUUID(), p.getUUID(), null), 0, 0, 0);
                        e.setPickUpDelay(32767);
                        p.serverLevel().addFreshEntity(e);
                        boolean hurt = e.hurt(p.damageSources().explosion(null, null), 50f); // before it has had a tick of its own
                        int[] out = {hurt ? 1 : 0, e.isAlive() ? 1 : 0};
                        e.discard();
                        return out;
                    });
                    String line = String.format(Locale.ROOT, "a crystal spilled into the world: hurt in the same tick %d, alive %d", seen[0], seen[1]);
                    results.add(line);
                    expect(line, seen[0] == 0 && seen[1] == 1);
                })
                // quality re-review, Minor 3: a mount a crystal sets down below its layer stays where the player put it
                .run("a crystal sets its stingray down below the Drift, in the shaft", () -> {
                    UUID made = UUID.randomUUID();
                    mount[0] = made;
                    boolean ok = ServerQuery.ask(p -> {
                        ItemStack crystal = fullCrystal(p, made, p.getUUID(), null);
                        BlockPos below = new BlockPos(rock.getX() + 10, 149, rock.getZ());
                        InteractionResult result = crystal.getItem().useOn(new UseOnContext(p.serverLevel(), p, InteractionHand.MAIN_HAND, crystal,
                                new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false)));
                        return result.consumesAction();
                    });
                    expect("the crystal did not set its stingray down in the shaft", ok);
                })
                .waitTicks(100)
                .run("it is left there: a mount a crystal set down does not climb to its owner", () -> {
                    double[] seen = ServerQuery.ask(p -> {
                        if (!(p.serverLevel().getEntity(mount[0]) instanceof DriftManta d)) {
                            return new double[] {-1, 0, 0};
                        }
                        return new double[] {d.getY(), d.isRescuing() ? 1 : 0, d.saveWithoutId(new CompoundTag()).getBoolean("CarePlaced") ? 1 : 0};
                    });
                    String line = String.format(Locale.ROOT, "set down below the Drift by a crystal, left alone for 100 ticks: at height %.1f, climbing %s, saved as set down by a crystal %s",
                            seen[0], seen[1] > 0, seen[2] > 0);
                    results.add(line);
                    expect(line, seen[0] >= 0 && seen[0] < 160.0 && seen[1] == 0 && seen[2] > 0);
                    ServerQuery.ask(p -> {
                        discard(p, mount[0], -1);
                        return true;
                    });
                })
                .run("a second stingray, tamed", () -> id[0] = ServerQuery.ask(p -> {
                    DriftManta m = Mounts.DRIFT_MANTA.get().create(p.serverLevel());
                    m.moveTo(rock.getX() - 2.5, rock.getY() + 1.0, rock.getZ() + 3.5, 0f, 0f);
                    m.finalizeSpawn(p.serverLevel(), p.serverLevel().getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.COMMAND, null);
                    m.setPersistenceRequired();
                    p.serverLevel().addFreshEntity(m);
                    m.tameTo(p);
                    return m.getId();
                }))
                .waitTicks(3)
                // the crystal is in hand (the release section held it) and empty; the direct call is the click's own handler
                .run("another player's stingray cannot be put in the crystal", () -> {
                    boolean kept = ServerQuery.ask(p -> {
                        DriftManta m = (DriftManta) p.serverLevel().getEntity(id[0]);
                        m.setOwnerUUID(STRANGER);
                        ItemStack crystal = p.getMainHandItem();
                        crystal.getItem().interactLivingEntity(crystal, p, m, InteractionHand.MAIN_HAND);
                        boolean ok = StableCrystalItem.stowed(p.getMainHandItem()) == null && !m.isRemoved();
                        m.setOwnerUUID(p.getUUID());
                        return ok;
                    });
                    expect("another player's stingray went into the crystal", kept);
                })
                .run("a dying stingray cannot be put in the crystal either", () -> {
                    boolean kept = ServerQuery.ask(p -> {
                        Entity e = p.serverLevel().getEntity(id[0]);
                        if (!(e instanceof DriftManta m)) {
                            return false;
                        }
                        float health = m.getHealth();
                        m.setHealth(0f);
                        ItemStack crystal = p.getMainHandItem();
                        crystal.getItem().interactLivingEntity(crystal, p, m, InteractionHand.MAIN_HAND);
                        boolean ok = StableCrystalItem.stowed(p.getMainHandItem()) == null && !m.isRemoved();
                        m.setHealth(health);
                        return ok;
                    });
                    expect("a dying stingray went into the crystal", kept);
                })
                .run("a crystal whose data names another kind of entity sets nothing down", () -> {
                    int pigs = ServerQuery.ask(p -> {
                        ItemStack forged = fullCrystal(p, UUID.randomUUID(), p.getUUID(), null);
                        StowedMount s = StableCrystalItem.stowed(forged);
                        CompoundTag tag = s.data();
                        tag.putString("id", "minecraft:pig");
                        forged.set(Stable.STOWED.get(), new StowedMount(s.type(), tag, s.health(), s.maxHealth(), s.name()));
                        BlockPos top = rock.below();
                        InteractionResult result = forged.getItem().useOn(new UseOnContext(p.serverLevel(), p, InteractionHand.MAIN_HAND, forged,
                                new BlockHitResult(Vec3.atCenterOf(top).add(0, 0.5, 0), Direction.UP, top, false)));
                        List<Pig> found = p.serverLevel().getEntitiesOfClass(Pig.class, new AABB(rock).inflate(8));
                        found.forEach(Entity::discard);
                        return found.size() + (result.consumesAction() ? 100 : 0);
                    });
                    expect("a crystal whose data names a pig acted on it (" + pigs + ")", pigs == 0);
                })
                .run("send the second one away", () -> ServerQuery.ask(p -> {
                    discard(p, null, id[0]);
                    return true;
                }));
    }

    /**
     * A wild stingray behind a small wall, in line with the nearest edge to the player, goes round it (headed straight at
     * the edge, it would press against the wall for good).
     */
    private void lureAround(Steps steps) {
        steps.run("a wall in the corridor, in line with the player's nearest edge", () -> command("fill %d %d %d %d %d %d cosmicbreach:driftstone",
                        rock.getX() + 14, rock.getY(), rock.getZ() - 1, rock.getX() + 14, rock.getY() + 2, rock.getZ() + 1))
                .waitUntil("the wall stands", 100, () -> mc.level.getBlockState(rock.east(14)).is(ModBlocks.DRIFTSTONE.get()))
                .run("the wild stingray behind it", () -> ServerQuery.ask(p -> {
                    DriftManta m = server(p, wild);
                    m.teleportTo(rock.getX() + 18.5, rock.getY() + 1.0, rock.getZ() + 0.5);
                    m.setDeltaMovement(Vec3.ZERO);
                    return true;
                }))
                .waitUntil("the client sees it out there", 40, () -> reach(wild) > 6.0);
        softWait(steps, "it goes round the wall to the edge, within reach", 600, () -> reach(wild) <= 3.0);
        steps.run("where it is", () -> {
            String line = String.format(Locale.ROOT, "lure round a wall: reach %.2f, at %s", reach(wild), ServerQuery.ask(p -> server(p, wild).position()));
            results.add(line);
        });
    }
}
