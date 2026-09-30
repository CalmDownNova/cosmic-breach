package com.cosmicbreach.combat.core;

import com.cosmicbreach.combat.core.CombatEvent.ActiveTick;
import com.cosmicbreach.combat.core.CombatEvent.ChargeFull;
import com.cosmicbreach.combat.core.CombatEvent.ChargeStarted;
import com.cosmicbreach.combat.core.CombatEvent.MoveStarted;
import com.cosmicbreach.combat.core.CombatEvent.PlungeLanded;
import com.cosmicbreach.combat.core.CombatStateMachine.Context;
import com.cosmicbreach.combat.core.CombatStateMachine.Phase;
import com.cosmicbreach.combat.data.HitShape;
import com.cosmicbreach.combat.data.MoveDef;
import com.cosmicbreach.combat.data.MoveKind;
import com.cosmicbreach.combat.data.MoveTraits;
import com.cosmicbreach.combat.data.WeaponDef;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The engine rules the Choir Astrolabe needs (GDD 4.1 and 4.2): its aerial is a timed move (the Starfall) that any
 * attack in the air starts, never a plunge; a weapon with both still plunges when aiming down; the Constellation's
 * charge (hold up to 30); the Pocket Star's Supernova as the ability's second press, free within the star's 80 ticks.
 */
class AstrolabeRulesInMachineTest {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cosmicbreach", path);
    }

    private static final ResourceLocation L1 = id("astrolabe/l1");
    private static final ResourceLocation L2 = id("astrolabe/l2");
    private static final ResourceLocation L3 = id("astrolabe/l3");
    private static final ResourceLocation STARFALL = id("astrolabe/starfall");
    private static final ResourceLocation CONSTELLATION = id("astrolabe/constellation");
    private static final ResourceLocation PARALLAX = id("astrolabe/parallax");
    private static final ResourceLocation POCKET_STAR = id("astrolabe/pocket_star");
    private static final ResourceLocation SUPERNOVA = id("astrolabe/supernova");
    private static final ResourceLocation DIVE = id("other/dive");
    private static final Context G = Context.GROUNDED;
    private static final Context AIR = new Context(false, 0f, 70.0);
    private static final Context AIR_DOWN = new Context(false, 70f, 70.0);

    private static MoveDef move(MoveKind kind, int startup, int active, int recovery, ResourceLocation next, double mv,
                                double mvMax, Optional<MoveDef.Charge> charge) {
        return new MoveDef(kind, new MoveDef.Timing(startup, active, recovery), new MoveDef.Hit(mv, mvMax, 0, 4, 3, 0, 1, 0),
                new HitShape.Sphere(0.0, 0.0), Optional.ofNullable(next), id("anim"), Optional.empty(), 0.0, charge,
                Optional.empty(), Optional.empty(), Optional.empty(), MoveTraits.NONE);
    }

    private static final Map<ResourceLocation, MoveDef> MOVES = new HashMap<>();

    static {
        MOVES.put(L1, move(MoveKind.LIGHT, 3, 1, 5, L2, 1.0, 0, Optional.empty()));
        MOVES.put(L2, move(MoveKind.LIGHT, 3, 1, 5, L3, 1.0, 0, Optional.empty()));
        MOVES.put(L3, move(MoveKind.LIGHT, 5, 3, 9, null, 0.7, 0, Optional.empty()));
        MOVES.put(STARFALL, move(MoveKind.LIGHT, 3, 1, 8, null, 1.5, 0, Optional.empty()));
        MOVES.put(CONSTELLATION, move(MoveKind.CHARGED, 4, 4, 12, null, 1.8, 1.8, Optional.of(new MoveDef.Charge(8, 30))));
        MOVES.put(PARALLAX, move(MoveKind.DASH_ATTACK, 1, 1, 5, null, 0.8, 0, Optional.empty()));
        MOVES.put(POCKET_STAR, move(MoveKind.ABILITY, 6, 1, 10, null, 0.6, 0, Optional.empty()));
        MOVES.put(SUPERNOVA, move(MoveKind.ABILITY, 2, 1, 8, null, 1.0, 0, Optional.empty()));
        MOVES.put(DIVE, new MoveDef(MoveKind.PLUNGE, new MoveDef.Timing(2, 0, 10), new MoveDef.Hit(1.4, 2.4, 0.1, 20, 6, 4, 1, 0),
                new HitShape.Sphere(2.5, 0.0), Optional.empty(), id("anim"), 0.0, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty()));
    }

    /** The Astrolabe as the machine sees it: no plunge, a Starfall aerial, the Pocket Star with its Supernova. */
    private static final WeaponDef ASTROLABE = new WeaponDef(4.8, Map.of(Stat.ARCANE, Grade.S, Stat.AGILITY, Grade.D), 24.0,
            List.of(L1, L2, L3), Optional.of(CONSTELLATION), Optional.empty(), Optional.of(PARALLAX),
            Optional.of(new WeaponDef.Ability(POCKET_STAR, 50, 240, Optional.of(new WeaponDef.Recast(SUPERNOVA, 80)))), 3,
            Optional.empty(), Map.of(), Optional.empty(), Optional.empty(), Optional.of(STARFALL));
    /** A weapon with both a plunge and an aerial: the plunge wins when the aim is down. */
    private static final WeaponDef BOTH = new WeaponDef(5.0, Map.of(), 3.0, List.of(L1), Optional.empty(), Optional.of(DIVE),
            Optional.empty(), Optional.empty(), 1, Optional.empty(), Map.of(), Optional.empty(), Optional.empty(),
            Optional.of(STARFALL));

    private CombatStateMachine sm;

    @BeforeEach
    void setUp() {
        sm = new CombatStateMachine(MOVES::get);
        sm.setWeapon(ASTROLABE);
    }

    private List<CombatEvent> run(int ticks, Context ctx) {
        List<CombatEvent> all = new ArrayList<>();
        for (int i = 0; i < ticks; i++) {
            all.addAll(sm.tick(ctx));
        }
        return all;
    }

    private static <T> Optional<T> first(List<CombatEvent> events, Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).findFirst();
    }

    private ResourceLocation tap(Context ctx) {
        sm.pressAttack(ctx);
        sm.releaseAttack(ctx);
        return first(sm.tick(ctx), MoveStarted.class).map(s -> s.move().id()).orElse(null);
    }

    @Test
    void onTheGroundAnAttackStartsTheChain() {
        assertEquals(L1, tap(G));
    }

    @Test
    void inTheAirAnyAttackFiresTheStarfallAsATimedMove() {
        assertEquals(STARFALL, tap(AIR));
        List<CombatEvent> events = run(12, AIR);
        List<ActiveTick> active = events.stream().filter(ActiveTick.class::isInstance).map(ActiveTick.class::cast).toList();
        assertEquals(1, active.size(), "one active tick, like its frame table (3 / 1 / 8)");
        assertFalse(first(events, PlungeLanded.class).isPresent(), "never a plunge");
        assertEquals(Phase.IDLE, sm.phase(), "over after 12 ticks without landing");
    }

    @Test
    void aimingStraightDownWithNoPlungeStillFiresTheStarfall() {
        assertEquals(STARFALL, tap(AIR_DOWN));
    }

    @Test
    void aWeaponWithBothPlungesWhenAimingDownAndFiresItsAerialOtherwise() {
        sm.setWeapon(BOTH);
        assertEquals(DIVE, tap(AIR_DOWN));
        run(20, G);
        assertEquals(STARFALL, tap(AIR));
    }

    @Test
    void theStarfallDoesNotContinueTheChain() {
        assertEquals(STARFALL, tap(AIR));
        run(12, G);
        assertEquals(L1, tap(G), "the chain starts again at L1");
    }

    @Test
    void theConstellationChargesUpToThirtyTicksAndReleasesAtItsFlatValue() {
        sm.pressAttack(G);
        List<CombatEvent> events = run(40, G);
        assertTrue(first(events, ChargeStarted.class).isPresent(), "holding past L1 flows into the charge");
        assertTrue(first(events, ChargeFull.class).isPresent(), "full at 30");
        sm.releaseAttack(G);
        MoveStarted started = first(sm.tick(G), MoveStarted.class).orElseThrow();
        assertEquals(CONSTELLATION, started.move().id());
        assertEquals(1.8, started.move().mv(), 1e-9, "1.8 per target however long it was held");
    }

    @Test
    void theSupernovaIsTheFreeSecondPressWithinTheStarsEightyTicks() {
        sm.syncFromServer(100, sm.dashCharges(), 0);
        sm.pressAbility(G);
        sm.releaseAbility();
        run(7, G); // startup 6: the first active tick opens the second press
        assertEquals(80, sm.recastWindow(), "80 ticks from the placing, the star's life");
        run(20, G);
        double before = sm.resonance();
        sm.pressAbility(G);
        assertEquals(before, sm.resonance(), 1e-9, "free");
        sm.releaseAbility();
        MoveStarted nova = first(sm.tick(G), MoveStarted.class).orElseThrow();
        assertEquals(SUPERNOVA, nova.move().id());
    }

    @Test
    void afterTheStarsEightyTicksTheAbilityIsTheStarAgain() {
        sm.syncFromServer(100, sm.dashCharges(), 0);
        sm.pressAbility(G);
        sm.releaseAbility();
        run(7 + 80, G);
        assertEquals(0, sm.recastWindow());
        sm.pressAbility(G);
        List<CombatEvent> events = sm.tick(G);
        assertNotEquals(SUPERNOVA, first(events, MoveStarted.class).map(s -> s.move().id()).orElse(null),
                "no Supernova once the star has burned out");
    }
}
