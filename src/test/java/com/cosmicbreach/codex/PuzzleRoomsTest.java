package com.cosmicbreach.codex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.codex.PuzzleRooms.Kind;
import com.cosmicbreach.codex.PuzzleRooms.Press;
import com.cosmicbreach.codex.PuzzleRooms.Room;
import com.cosmicbreach.codex.PuzzleRooms.Target;
import com.cosmicbreach.world.Layer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class PuzzleRoomsTest {
    private static final BlockPos REACH = new BlockPos(0, 360, 0);
    private static final BlockPos DRIFT = new BlockPos(0, 230, 0);
    private static final BlockPos DEEP = new BlockPos(0, 100, 0);

    private static Room reliquary(int x, int z) {
        return new Room(Kind.RELIQUARY, new BlockPos(x, 360, z));
    }

    private static Room observatory(int x, int z) {
        return new Room(Kind.OBSERVATORY, new BlockPos(x, 230, z));
    }

    private static Room crypt(int x, int z) {
        return new Room(Kind.CRYPT, new BlockPos(x, 100, z));
    }

    // ------------------------------------------------------------------ target selection

    @Test
    void theNearestRoomWins() {
        List<Room> sites = List.of(reliquary(900, 0), reliquary(-300, 200), reliquary(0, -500));
        assertEquals(reliquary(-300, 200), PuzzleRooms.choose(Layer.REACH, REACH, sites, List.of(), List.of()).orElseThrow());
    }

    @Test
    void distanceIsHorizontal() {
        // a room far below but right under the player is still the nearest
        Room under = new Room(Kind.CRYPT, new BlockPos(5, 10, 5));
        Room level = new Room(Kind.CRYPT, new BlockPos(60, 100, 0));
        assertEquals(under, PuzzleRooms.choose(Layer.DEEP, DEEP, List.of(level, under), List.of(), List.of()).orElseThrow());
    }

    // ------------------------------------------------------------------ layer filtering

    @Test
    void onlyRoomsOfThePlayersLayerCount() {
        List<Room> sites = List.of(reliquary(10, 0), observatory(2000, 0), crypt(30, 0));
        assertEquals(Kind.OBSERVATORY, PuzzleRooms.choose(Layer.DRIFT, DRIFT, sites, List.of(), List.of()).orElseThrow().kind());
        assertEquals(Kind.RELIQUARY, PuzzleRooms.choose(Layer.REACH, REACH, sites, List.of(), List.of()).orElseThrow().kind());
        assertEquals(Kind.CRYPT, PuzzleRooms.choose(Layer.DEEP, DEEP, sites, List.of(), List.of()).orElseThrow().kind());
    }

    @Test
    void everyKindHasItsOwnLayerAndEveryLayerAKind() {
        for (Layer layer : Layer.values()) {
            assertTrue(Set.of(Kind.values()).stream().anyMatch(k -> k.layer == layer), layer + " has a puzzle room");
        }
        assertEquals(Layer.REACH, Kind.RELIQUARY.layer);
        assertEquals(Layer.DRIFT, Kind.OBSERVATORY.layer);
        assertEquals(Layer.DEEP, Kind.CRYPT.layer);
    }

    // ------------------------------------------------------------------ completion filtering

    @Test
    void aRoomWhoseVaultThePlayerOpenedIsSkipped() {
        List<Room> sites = List.of(reliquary(100, 0), reliquary(700, 0));
        // the vault sits a little way inside the spire from its entrance
        List<Room> done = List.of(reliquary(112, 9));
        assertEquals(reliquary(700, 0), PuzzleRooms.choose(Layer.REACH, REACH, sites, List.of(), done).orElseThrow());
    }

    @Test
    void anOpenedVaultMarksOnlyItsNearestSite() {
        // two rooms of a kind close together: the vault belongs to the nearer one only
        List<Room> sites = List.of(reliquary(100, 0), reliquary(150, 0));
        List<Room> done = List.of(reliquary(108, 0));
        assertEquals(reliquary(150, 0), PuzzleRooms.choose(Layer.REACH, REACH, sites, List.of(), done).orElseThrow());
    }

    @Test
    void aVaultOfAnotherKindOrTooFarAwayMarksNothing() {
        List<Room> sites = List.of(crypt(100, 0));
        assertEquals(crypt(100, 0), PuzzleRooms.choose(Layer.DEEP, DEEP, sites, List.of(),
                List.of(new Room(Kind.RELIQUARY, new BlockPos(100, 360, 0)))).orElseThrow());
        assertEquals(crypt(100, 0), PuzzleRooms.choose(Layer.DEEP, DEEP, sites, List.of(),
                List.of(crypt(100 + PuzzleRooms.MATCH + 1, 0))).orElseThrow());
    }

    @Test
    void aKnownVaultIsItsSitesRoomNotASecondOne() {
        // the log knows the vault of the room at 100 (unopened by this player): it is that room, so the target is the
        // entrance, and a vault with no site nearby (a room worldgen did not place) is a room of its own
        List<Room> sites = List.of(crypt(100, 0));
        List<Room> open = List.of(crypt(95, 12), crypt(-60, 0));
        assertEquals(crypt(-60, 0), PuzzleRooms.choose(Layer.DEEP, DEEP, sites, open, List.of()).orElseThrow());
        assertEquals(crypt(100, 0), PuzzleRooms.choose(Layer.DEEP, DEEP, sites, List.of(crypt(95, 12)), List.of()).orElseThrow());
    }

    @Test
    void knownVaultsOfOtherLayersAreIgnored() {
        assertTrue(PuzzleRooms.choose(Layer.DEEP, DEEP, List.of(), List.of(reliquary(5, 5), observatory(5, 5)), List.of()).isEmpty());
    }

    // ------------------------------------------------------------------ none found

    @Test
    void nothingLeftOnTheLayerIsEmpty() {
        assertTrue(PuzzleRooms.choose(Layer.REACH, REACH, List.of(), List.of(), List.of()).isEmpty());
        // every room done
        assertTrue(PuzzleRooms.choose(Layer.REACH, REACH, List.of(reliquary(100, 0)), List.of(), List.of(reliquary(104, 4))).isEmpty());
        // rooms only on other layers
        assertTrue(PuzzleRooms.choose(Layer.DRIFT, DRIFT, List.of(reliquary(10, 0), crypt(10, 0)), List.of(), List.of()).isEmpty());
    }

    // ------------------------------------------------------------------ cycling

    @Test
    void theTargetCyclesBossPuzzleBoss() {
        assertEquals(Target.PUZZLE, Target.BOSS.next());
        assertEquals(Target.BOSS, Target.PUZZLE.next());
        assertEquals(Target.BOSS, Target.BOSS.next().next());
    }

    @Test
    void theFirstPressThrows() {
        assertEquals(Press.THROW, PuzzleRooms.press(1000, -1, -1, false, LairMote.COOLDOWN_TICKS));
    }

    @Test
    void aPressWhileTheMoteFliesSwitches() {
        // thrown at 1000, pressed again 20 ticks later with the mote still in the air
        assertEquals(Press.SWITCH, PuzzleRooms.press(1020, 1000, 1000, true, LairMote.COOLDOWN_TICKS));
    }

    @Test
    void aHeldButtonIsOnePress() {
        // a held use button calls again every 4 ticks: that never switches back and forth
        assertEquals(Press.IGNORE, PuzzleRooms.press(1004, 1000, 1000, true, LairMote.COOLDOWN_TICKS));
        assertEquals(Press.IGNORE, PuzzleRooms.press(1000 + PuzzleRooms.HOLD_GAP, 1000, 1000, true, LairMote.COOLDOWN_TICKS));
        assertEquals(Press.SWITCH, PuzzleRooms.press(1000 + PuzzleRooms.HOLD_GAP + 1, 1000, 1000, true, LairMote.COOLDOWN_TICKS));
    }

    @Test
    void afterTheMoteFadesAPressThrowsAgainAtTheSameTarget() {
        long thrown = 1000;
        long later = thrown + LairMote.LIFE_TICKS + 5;
        assertEquals(Press.THROW, PuzzleRooms.press(later, thrown, thrown, false, LairMote.COOLDOWN_TICKS));
        // a mote gone early (another dimension) still keeps the cooldown
        assertEquals(Press.IGNORE, PuzzleRooms.press(thrown + 20, thrown, thrown, false, LairMote.COOLDOWN_TICKS));
    }

    @Test
    void theMoteOutlivesTheCooldownSoASwitchIsAlwaysPossible() {
        assertTrue(LairMote.LIFE_TICKS > PuzzleRooms.HOLD_GAP + 1);
        assertTrue(LairMote.LIFE_TICKS >= LairMote.COOLDOWN_TICKS);
    }

    @Test
    void aNewPlayersBookPointsAtTheBoss() {
        LairMote.reset();
        UUID me = UUID.randomUUID();
        assertEquals(Target.BOSS, LairMote.target(me));
        LairMote.setTarget(me, Target.PUZZLE);
        assertEquals(Target.PUZZLE, LairMote.target(me));
        LairMote.forget(me);
        assertEquals(Target.BOSS, LairMote.target(me));
    }

    // ------------------------------------------------------------------ the log

    @Test
    void theLogRemembersWhoOpenedWhat() {
        PuzzleRoomLog log = new PuzzleRoomLog();
        UUID me = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        BlockPos a = new BlockPos(100, 360, 0);
        BlockPos b = new BlockPos(-40, 100, 70);
        log.seen(a, Kind.RELIQUARY, List.of());
        log.seen(b, Kind.CRYPT, List.of(friend));
        assertEquals(List.of(new Room(Kind.RELIQUARY, a), new Room(Kind.CRYPT, b)), log.vaults(me, false));
        assertTrue(log.vaults(me, true).isEmpty());
        log.opened(a, Kind.RELIQUARY, me);
        assertEquals(List.of(new Room(Kind.RELIQUARY, a)), log.vaults(me, true));
        assertEquals(List.of(new Room(Kind.CRYPT, b)), log.vaults(friend, true));
        // a vault loading again with an older list forgets nobody
        log.seen(a, Kind.RELIQUARY, List.of());
        assertEquals(List.of(new Room(Kind.RELIQUARY, a)), log.vaults(me, true));
    }

    @Test
    void theLogSurvivesASave() {
        PuzzleRoomLog log = new PuzzleRoomLog();
        UUID me = UUID.randomUUID();
        BlockPos a = new BlockPos(100, 230, 0);
        log.opened(a, Kind.OBSERVATORY, me);
        log.seen(new BlockPos(5, 100, 5), Kind.CRYPT, List.of());
        CompoundTag tag = log.save(new CompoundTag(), null);
        PuzzleRoomLog back = PuzzleRoomLog.fromTag(tag);
        assertEquals(log.vaults(me, true), back.vaults(me, true));
        assertEquals(log.vaults(me, false), back.vaults(me, false));
        assertFalse(back.vaults(me, false).isEmpty());
    }

    // ------------------------------------------------------------------ the search and the words

    @Test
    void theSearchIsBoundedForEveryKind() throws IOException {
        Pattern spacing = Pattern.compile("\"spacing\"\\s*:\\s*(\\d+)");
        for (Kind kind : Kind.values()) {
            Path set = projectFile("src/main/resources/data/cosmicbreach/worldgen/structure_set/" + kind.structure + ".json");
            String json = Files.readString(set, StandardCharsets.UTF_8);
            assertTrue(json.contains("minecraft:random_spread"), kind + " is placed by random spread");
            Matcher m = spacing.matcher(json);
            assertTrue(m.find(), kind + " has a spacing");
            int rings = PuzzleFinder.rings(Integer.parseInt(m.group(1)));
            assertTrue(rings >= 1 && rings <= 8, kind + ": " + rings + " rings");
            // at most (2r+1)^2 regions, each worked out once and cached
            assertTrue((2 * rings + 1) * (2 * rings + 1) <= 289, kind + " looks at a bounded number of regions");
        }
    }

    @Test
    void everyMessageHasItsWords() throws IOException {
        String codex = Files.readString(projectFile("src/main/resources/assets/cosmicbreach_codex/lang/en_us.json"), StandardCharsets.UTF_8);
        for (Kind kind : Kind.values()) {
            assertTrue(codex.contains("\"cosmicbreach.codex.mote.puzzle." + kind.key() + "\""), kind.key());
        }
        for (String key : List.of("track.boss", "track.puzzle", "track.no_puzzle", "mote.none")) {
            assertTrue(codex.contains("\"cosmicbreach.codex." + key + "\""), key);
        }
        String onboarding = Files.readString(projectFile("src/main/resources/assets/cosmicbreach_onboarding/lang/en_us.json"),
                StandardCharsets.UTF_8);
        assertTrue(onboarding.contains("\"item.cosmicbreach.starfall_codex.tooltip.switch\""));
    }

    private static Path projectFile(String path) {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve(path))) {
                return dir.resolve(path);
            }
        }
        throw new AssertionError("no " + path + " above " + Path.of("").toAbsolutePath());
    }
}
