package com.cosmicbreach.voice.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The debug commands' hooks (A5.3): the global gap can be declared run without making a said line sayable again. */
class VoiceDirectorDebugTest {
    static VoiceLine line(int order, String id, int priority) {
        VoiceLine.Variant take = new VoiceLine.Variant(ResourceLocation.fromNamespaceAndPath("cosmicbreach", "bossvoice/test_" + id),
                "subtitles.test." + id, 60, 3, 40, List.of());
        return new VoiceLine("heliarch", id, Trigger.parse("player_death"), List.of(), priority, 0, "Words.", Map.of(VoiceLine.ALL, take), order);
    }

    @Test
    void equalLinesTakeTurnsAndTheGapCanBeDeclaredRun() {
        VoiceDirector d = new VoiceDirector(new BossCatalog("heliarch", 240, 80, 0xFFFFFF, new GearCheck.Reference(1, 15, 4, 8, 10, 14, 16),
                List.of(line(0, "first", 70), line(1, "second", 70))), new HashMap<>());
        Context solo = Context.of(1, false, 0);
        d.trigger(Trigger.parse("player_death"), solo, 100, false);
        assertEquals("first", d.next(100, VoiceDirector.Gate.OPEN).line().id(), "equal priorities: the script's order");
        // the second death comes while the global gap (12 s from the end of the first line at 160) still runs
        d.trigger(Trigger.parse("player_death"), solo, 200, false);
        assertNull(d.next(200, VoiceDirector.Gate.OPEN), "held by the gap");
        d.endGap();
        VoiceDirector.Start s = d.next(201, VoiceDirector.Gate.OPEN);
        assertNotNull(s);
        assertEquals("second", s.line().id(), "the first line stays said: the second takes its turn");
    }
}
