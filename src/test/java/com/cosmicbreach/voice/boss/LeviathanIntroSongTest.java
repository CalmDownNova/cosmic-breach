package com.cosmicbreach.voice.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.guardian.leviathan.LeviathanMoves;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Leviathan's intro song plays in full and her lines wait for it, never the other way round (A5, from the A4 review): the
 * song is a watched sound, and a line raised while it plays starts its words after it is over. (Her opening line is no longer said
 * in the intro: it comes at her first Moorage, see {@link LeviathanOpenerWindowTest}.)
 */
class LeviathanIntroSongTest {
    static final Context SOLO = Context.of(1, false, 3);

    @Test
    void theSongIsAWatchedSoundAsLongAsItsFile() {
        assertEquals(84, BossVoiceSounds.clearTicks("leviathan/song"), "4.2 s: song_1 to song_3 are all 84 ticks");
        assertEquals(LeviathanMoves.INTRO_SONG + 84, LeviathanMoves.introSongOver());
    }

    @Test
    void aFreeLineRaisedDuringTheSongWaitsForItToEnd() {
        VoiceDirector d = VoiceDirectorEffectsTest.director(VoiceDirectorEffectsTest.line(0, "bow", "weapon:bow", 50, 60, 3, 40));
        d.trigger(Trigger.parse("weapon:bow"), SOLO, 160, false);
        long over = LeviathanMoves.introSongOver() + VoiceDirector.QUIET_MARGIN;
        List<Object[]> said = VoiceDirectorEffectsTest.run(d, 160, 400, VoiceDirectorEffectsTest.gate(over, Integer.MAX_VALUE));
        // the director's own freshness is 240 ticks in this fixture: it starts as soon as the words may
        assertEquals(1, said.size());
        assertTrue((Long) said.get(0)[1] + 3 >= LeviathanMoves.introSongOver(), "its first word comes after the song");
    }
}
