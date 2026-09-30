package com.cosmicbreach.world.weather;

import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.weather.WeatherSchedule.Phase;
import com.cosmicbreach.world.weather.WeatherSchedule.Slot;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The weather timetable (GDD 2.5): one event per layer, warning then event, countdowns, gaps, the Heliarch. */
class WeatherScheduleTest {
    private static final RandomSource RANDOM = RandomSource.create(42);

    /** Fixed intervals per slot, doubled for the Surge after the Heliarch (like the config's). */
    private static WeatherSchedule.Intervals fixed(Map<Slot, Integer> ticks) {
        return (slot, fallen, random) -> ticks.getOrDefault(slot, 1_000_000) * (slot.kind == WeatherKind.SURGE && fallen ? 2 : 1);
    }

    private static Map<Slot, Integer> intervals(int flare, int reachShower, int driftShower, int tide, int surge) {
        Map<Slot, Integer> m = new EnumMap<>(Slot.class);
        m.put(Slot.REACH_FLARE, flare);
        m.put(Slot.REACH_SHOWER, reachShower);
        m.put(Slot.DRIFT_SHOWER, driftShower);
        m.put(Slot.DRIFT_TIDE, tide);
        m.put(Slot.DEEP_SURGE, surge);
        return m;
    }

    private static int run(WeatherSchedule s, WeatherSchedule.Intervals in, int ticks) {
        int changed = 0;
        for (int i = 0; i < ticks; i++) {
            changed |= s.tick(in, RANDOM, true);
        }
        return changed;
    }

    @Test
    void theEventsAreWhereAndHowLongTheDesignSays() {
        assertTrue(WeatherKind.FLARE.canHappenIn(Layer.REACH));
        assertFalse(WeatherKind.FLARE.canHappenIn(Layer.DRIFT));
        assertTrue(WeatherKind.SHOWER.canHappenIn(Layer.REACH) && WeatherKind.SHOWER.canHappenIn(Layer.DRIFT));
        assertFalse(WeatherKind.SHOWER.canHappenIn(Layer.DEEP));
        assertTrue(WeatherKind.TIDE.canHappenIn(Layer.DRIFT));
        assertTrue(WeatherKind.SURGE.canHappenIn(Layer.DEEP));
        assertEquals(15 * 20, WeatherKind.FLARE.warningTicks);
        assertEquals(90 * 20, WeatherKind.FLARE.activeTicks);
        assertEquals(10 * 20, WeatherKind.SHOWER.warningTicks);
        assertEquals(60 * 20, WeatherKind.SHOWER.activeTicks);
        assertEquals(10 * 20, WeatherKind.TIDE.warningTicks);
        assertEquals(45 * 20, WeatherKind.TIDE.activeTicks);
        assertEquals(20 * 20, WeatherKind.SURGE.warningTicks);
        assertEquals(120 * 20, WeatherKind.SURGE.activeTicks);
        assertEquals(Layer.DRIFT, WeatherKind.SHOWER.homeLayer(Layer.DRIFT));
        assertEquals(Layer.REACH, WeatherKind.SHOWER.homeLayer(Layer.DEEP));
        assertEquals(Layer.DEEP, WeatherKind.SURGE.homeLayer(null));
    }

    @Test
    void aSlotCountsDownThenWarnsThenRunsThenGoesCalm() {
        WeatherSchedule s = new WeatherSchedule();
        WeatherSchedule.Intervals in = fixed(intervals(100, 10_000, 10_000, 10_000, 10_000));
        assertEquals(0, run(s, in, 99));
        assertEquals(Phase.IDLE, s.now(Layer.REACH).phase());
        assertEquals(1 << Layer.REACH.ordinal(), run(s, in, 1), "due on the 100th tick");
        assertTrue(s.now(Layer.REACH).is(WeatherKind.FLARE, Phase.WARNING));
        assertEquals(0, run(s, in, 299));
        assertEquals(1 << Layer.REACH.ordinal(), run(s, in, 1), "15 s of warning");
        assertTrue(s.now(Layer.REACH).is(WeatherKind.FLARE, Phase.ACTIVE));
        run(s, in, 1799);
        assertTrue(s.now(Layer.REACH).is(WeatherKind.FLARE, Phase.ACTIVE));
        run(s, in, 1);
        assertEquals(Phase.IDLE, s.now(Layer.REACH).phase(), "90 s of Flare");
        assertNull(s.now(Layer.REACH).kind());
    }

    @Test
    void oneEventPerLayerAndCountdownsWaitWhileTheLayerIsBusy() {
        WeatherSchedule s = new WeatherSchedule();
        WeatherSchedule.Intervals in = fixed(intervals(5000, 150, 10_000, 10_000, 10_000));
        run(s, in, 100);
        assertEquals(50, s.countdown(Slot.REACH_SHOWER));
        s.start(Layer.REACH, WeatherKind.FLARE, 0f, in, RANDOM);
        assertEquals(5000, s.countdown(Slot.REACH_FLARE), "the started event's slot is drawn again");
        run(s, in, 1000);
        assertEquals(50, s.countdown(Slot.REACH_SHOWER), "the shower's countdown holds while the Flare runs");
        run(s, in, 1100);
        assertEquals(Phase.IDLE, s.now(Layer.REACH).phase(), "15 s of warning and 90 s of Flare");
        assertEquals(WeatherSchedule.GAP_TICKS, s.countdown(Slot.REACH_SHOWER), "at least the gap after an event");
        assertEquals(5000, s.countdown(Slot.REACH_FLARE));
        run(s, in, WeatherSchedule.GAP_TICKS - 1);
        assertEquals(Phase.IDLE, s.now(Layer.REACH).phase());
        run(s, in, 1);
        assertTrue(s.now(Layer.REACH).is(WeatherKind.SHOWER, Phase.WARNING));
    }

    @Test
    void layersRunTheirOwnWeatherSideBySide() {
        WeatherSchedule s = new WeatherSchedule();
        WeatherSchedule.Intervals in = fixed(intervals(50, 10_000, 60, 70, 80));
        int changed = run(s, in, 80);
        assertEquals(7, changed, "all three layers changed");
        assertTrue(s.now(Layer.REACH).is(WeatherKind.FLARE, Phase.WARNING));
        assertTrue(s.now(Layer.DRIFT).is(WeatherKind.SHOWER, Phase.WARNING), "the Drift's shower came first");
        assertTrue(s.now(Layer.DEEP).is(WeatherKind.SURGE, Phase.WARNING));
        assertEquals(10, s.countdown(Slot.DRIFT_TIDE), "the Tide waits its turn");
    }

    @Test
    void withoutNaturalWeatherOnlyStartedEventsRun() {
        WeatherSchedule s = new WeatherSchedule();
        WeatherSchedule.Intervals in = fixed(intervals(5, 5, 5, 5, 5));
        for (int i = 0; i < 100; i++) {
            s.tick(in, RANDOM, false);
        }
        for (Layer layer : Layer.values()) {
            assertEquals(Phase.IDLE, s.now(layer).phase());
        }
        s.start(Layer.DRIFT, WeatherKind.TIDE, 1.5f, in, RANDOM);
        for (int i = 0; i < 200; i++) {
            s.tick(in, RANDOM, false);
        }
        assertTrue(s.now(Layer.DRIFT).is(WeatherKind.TIDE, Phase.ACTIVE));
        assertEquals(1.5f, s.now(Layer.DRIFT).heading());
    }

    @Test
    void startingReplacesAndRedrawsItsSlot() {
        WeatherSchedule s = new WeatherSchedule();
        WeatherSchedule.Intervals in = fixed(intervals(10_000, 10_000, 10_000, 30, 10_000));
        run(s, in, 1);
        s.start(Layer.DRIFT, WeatherKind.SHOWER, 0f, in, RANDOM);
        assertTrue(s.now(Layer.DRIFT).is(WeatherKind.SHOWER, Phase.WARNING));
        s.start(Layer.DRIFT, WeatherKind.TIDE, 0f, in, RANDOM);
        assertTrue(s.now(Layer.DRIFT).is(WeatherKind.TIDE, Phase.WARNING), "a new start replaces the old event");
        assertEquals(30, s.countdown(Slot.DRIFT_TIDE), "its countdown is drawn again");
        assertThrows(IllegalArgumentException.class, () -> s.start(Layer.DEEP, WeatherKind.FLARE, 0f, in, RANDOM));
    }

    @Test
    void skippingAndClearing() {
        WeatherSchedule s = new WeatherSchedule();
        WeatherSchedule.Intervals in = fixed(intervals(10_000, 10_000, 10_000, 10_000, 10_000));
        assertFalse(s.skipWarning(Layer.DEEP));
        s.start(Layer.DEEP, WeatherKind.SURGE, 0f, in, RANDOM);
        assertTrue(s.skipWarning(Layer.DEEP));
        run(s, in, 1);
        assertTrue(s.now(Layer.DEEP).is(WeatherKind.SURGE, Phase.ACTIVE), "the event starts on the next tick");
        assertTrue(s.clear(Layer.DEEP));
        assertEquals(Phase.IDLE, s.now(Layer.DEEP).phase());
        assertFalse(s.clear(Layer.DEEP));
    }

    @Test
    void theHeliarchHalvesHowOftenSurgesCome() {
        WeatherSchedule s = new WeatherSchedule();
        WeatherSchedule.Intervals in = fixed(intervals(10_000, 10_000, 10_000, 10_000, 1000));
        run(s, in, 400);
        assertEquals(600, s.countdown(Slot.DEEP_SURGE));
        s.heliarchFell();
        assertTrue(s.heliarchFallen());
        assertEquals(1200, s.countdown(Slot.DEEP_SURGE), "the running countdown doubles");
        s.heliarchFell();
        assertEquals(1200, s.countdown(Slot.DEEP_SURGE), "only the first death counts");
        s.start(Layer.DEEP, WeatherKind.SURGE, 0f, in, RANDOM);
        assertEquals(2000, s.countdown(Slot.DEEP_SURGE), "later draws are doubled too");
        assertEquals(9600, s.countdown(Slot.REACH_FLARE), "other events keep their pace");
    }

    @Test
    void theConfigIntervalsSpanTheirMinutes() {
        RandomSource r = RandomSource.create(7);
        int lo = Integer.MAX_VALUE;
        int hi = 0;
        for (int i = 0; i < 2000; i++) {
            int t = WeatherConfig.intervalTicks(Slot.REACH_FLARE, false, r);
            lo = Math.min(lo, t);
            hi = Math.max(hi, t);
        }
        assertTrue(lo >= 25 * 1200 && lo < 26 * 1200, "Flares at least 25 minutes apart: " + lo);
        assertTrue(hi <= 35 * 1200 && hi > 34 * 1200, "and at most 35: " + hi);
        int surge = WeatherConfig.intervalTicks(Slot.DEEP_SURGE, true, r);
        assertTrue(surge >= 120 * 1200 && surge <= 180 * 1200, "Surges every 120 to 180 minutes after the Heliarch: " + surge);
    }
}
