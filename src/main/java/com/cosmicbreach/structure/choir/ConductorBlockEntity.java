package com.cosmicbreach.structure.choir;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.progression.AttunementXp;
import com.cosmicbreach.progression.XpSource;
import com.cosmicbreach.structure.crypt.CryptConfig;
import com.cosmicbreach.structure.crypt.CryptRegistry;
import com.cosmicbreach.structure.vault.VaultBlockEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A Choir Floor (GDD 6.3), run from its Conductor. The structure writes the room with the floor asleep; the first
 * player to step onto the ring wakes it, and its song is drawn from the room's seed then (co-op, with chords, if two
 * or more players are in the room). The play itself is {@link ChoirSession}; this block entity feeds it the game
 * time and the players' steps, and turns what it says into the world: the Conductor's notes (sounds at their pads,
 * each with its subtitle), the metronome, the ring's turn, Discords (4 damage and a push away from the Conductor),
 * the rest, and on the third round the vault, Puzzle Solved XP for everyone in the room, and
 * {@link ChoirFloors#onSolved} listeners.
 *
 * <p>Steps are judged here on the server's tick: a player's feet enter a pad when, on the ground, the point under
 * them moves onto it ({@link ChoirRules#padAt}, the ring's turn applied): walking in, or landing from a hop. The
 * client draws the floor from the synced state and the game time ({@code client.crypt.ConductorRenderer}), so the
 * pads light with their colours and glyphs as the Conductor plays them, sound or no sound.
 */
public class ConductorBlockEntity extends BlockEntity {
    /** How far from the Conductor's axis the room reaches: players inside count for co-op and XP. */
    public static final double ROOM_RADIUS = 9.5;

    // set by the structure
    private long seed;
    private String difficulty = ChoirDifficulty.CRYPT.name();
    private int tier = 3;
    private @Nullable BlockPos vault;

    // saved progress
    private boolean coop;
    private boolean sung;
    private int savedRound;
    private int savedRotation;
    private boolean solved;
    private final Set<UUID> solvedFor = new HashSet<>();

    // server, not saved
    private @Nullable ChoirSession session;
    private final Map<UUID, Integer> lastPad = new HashMap<>();
    /** Who has stepped on a pad since the floor woke: a missed beat hurts only them. */
    private final ChoirPlayers players = new ChoirPlayers();
    /** Fell idle because a whole phrase passed with nobody stepping: wakes again only for a step on a pad (or an empty ring). */
    private boolean hushed;
    private int idles;
    private @Nullable ServerPlayer stepper;
    /** Every step that counted, for tests: {round, note, pad, offset in ticks}. */
    private final List<int[]> measured = new ArrayList<>();
    /**
     * Every step during an answer, for tests: {tick, round, note due, pad, that note's beat, verdict ordinal, window,
     * grace, 1 if the pad is the note's, 1 if it is the note before's or after's, 1 if the floor was waiting out a silent
     * phrase}. A scenario checks each verdict against the offset the step actually landed at.
     */
    private final List<long[]> stepLog = new ArrayList<>();
    private int discords;
    private int rests;
    private long lastDiscordAt = -1;
    private long lastRestAt = -1;
    private long solvedAt = -1;

    // what clients draw (the server keeps its own copy of the same fields up to date)
    private int phase;
    private int round;
    private int rotation;
    private long rotatedAt = Long.MIN_VALUE;
    private long callStart = -1;
    private long answerStart = -1;
    private long restUntil = -1;
    private boolean replay;
    private int mistakes;
    private int window = ChoirRules.WINDOW;
    private int[] phrase = new int[0];
    /** Recent good steps: pad, tick, pad, tick... */
    private long[] hits = new long[0];
    private long discordAt = -1;

    private transient ChoirPhrase decoded;
    private transient int[] decodedFrom;

    public ConductorBlockEntity(BlockPos pos, BlockState state) {
        super(CryptRegistry.CONDUCTOR_ENTITY.get(), pos, state);
    }

    /** Sets the room up at worldgen: its seed, difficulty, layer tier (1 Reach, 2 Drift, 3 Deep: its XP) and vault. */
    public void configure(long seed, ChoirDifficulty difficulty, int tier, @Nullable BlockPos vault) {
        this.seed = seed;
        this.difficulty = difficulty.name();
        this.tier = tier;
        this.vault = vault == null ? null : vault.immutable();
        this.session = null;
        setChanged();
    }

    // ------------------------------------------------------------------ reading (both sides)

    public ChoirSession.Phase phase() {
        return ChoirSession.Phase.values()[Math.max(0, Math.min(ChoirSession.Phase.values().length - 1, phase))];
    }

    public int round() {
        return round;
    }

    public int rotation() {
        return rotation;
    }

    public long rotatedAt() {
        return rotatedAt;
    }

    public long callStart() {
        return callStart;
    }

    public long answerStart() {
        return answerStart;
    }

    public long restUntil() {
        return restUntil;
    }

    public boolean replaying() {
        return replay;
    }

    public int mistakes() {
        return mistakes;
    }

    public int window() {
        return window;
    }

    public long discordAt() {
        return discordAt;
    }

    public boolean solved() {
        return phase() == ChoirSession.Phase.SOLVED;
    }

    public ChoirDifficulty difficulty() {
        return ChoirDifficulty.byName(difficulty);
    }

    public @Nullable BlockPos vault() {
        return vault;
    }

    public long seed() {
        return seed;
    }

    /** The phrase being played or answered, or null (asleep). */
    public @Nullable ChoirPhrase phrase() {
        if (decodedFrom != phrase) {
            decoded = ChoirPhrase.decode(phrase);
            decodedFrom = phrase;
        }
        return decoded;
    }

    /** Recent good steps: {pad, tick} pairs flattened. */
    public long[] hits() {
        return hits;
    }

    /** The floor's centre on the floor's surface (the Conductor's feet). */
    public Vec3 centre() {
        return Vec3.atBottomCenterOf(worldPosition);
    }

    /** Where pad {@code pad}'s glyph lies at the current rotation. */
    public Vec3 padCentre(int pad) {
        double a = ChoirRules.slotAngle(ChoirRules.slotOf(pad, rotation));
        return centre().add(Math.cos(a) * ChoirRules.RADIUS, 0.05, Math.sin(a) * ChoirRules.RADIUS);
    }

    // ------------------------------------------------------------------ for tests (server)

    public List<int[]> measured() {
        return measured;
    }

    /** A copy of the step log ({@link #stepLog}'s layout). */
    public List<long[]> stepLog() {
        return new ArrayList<>(stepLog);
    }

    public int discords() {
        return discords;
    }

    public int rests() {
        return rests;
    }

    public long lastDiscordAt() {
        return lastDiscordAt;
    }

    public long lastRestAt() {
        return lastRestAt;
    }

    public long solvedAt() {
        return solvedAt;
    }

    /** Times the floor fell idle (tests). */
    public int idles() {
        return idles;
    }

    /** True while the floor waits for a step on a pad before it sings again (tests). */
    public boolean hushed() {
        return hushed;
    }

    /** True if {@code player} has stepped on a pad since the floor woke (tests). */
    public boolean playing(UUID player) {
        return players.playing(player);
    }

    // ------------------------------------------------------------------ the server's side

    /** The floor's play (server side; made on first use, restored from what was saved). */
    public ChoirSession session() {
        if (session == null) {
            ChoirDifficulty d = difficulty();
            long s = seed;
            session = new ChoirSession(d, co -> ChoirGenerator.song(s, d, co));
            if (sung || solved) {
                session.restore(coop, savedRound, savedRotation, solved);
            }
        }
        return session;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ConductorBlockEntity be) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        be.tick(server);
    }

    private void tick(ServerLevel level) {
        long now = level.getGameTime();
        ChoirSession s = session();
        ChoirSession.Phase ph = s.phase();
        boolean awake = ph == ChoirSession.Phase.CALL || ph == ChoirSession.Phase.ANSWER || ph == ChoirSession.Phase.REST;
        if (!awake && (now + worldPosition.asLong()) % 10 != 0) {
            return;
        }
        List<ServerPlayer> room = playersInRoom(level);
        if (room.isEmpty() && !awake) {
            lastPad.clear();
            return;
        }
        Vec3 c = centre();
        boolean onFloor = false;
        int maxGrace = 0;
        List<ServerPlayer> steppers = new ArrayList<>();
        List<Integer> stepPads = new ArrayList<>();
        for (ServerPlayer p : room) {
            if (p.isSpectator()) {
                continue;
            }
            double dx = p.getX() - c.x;
            double dz = p.getZ() - c.z;
            boolean level0 = Math.abs(p.getY() - c.y) < 0.6;
            boolean floor = level0 && Math.hypot(dx, dz) <= ChoirRules.FLOOR_RADIUS;
            if (floor) {
                onFloor = true;
                maxGrace = Math.max(maxGrace, grace(p));
            }
            int pad = floor && p.onGround() ? ChoirRules.padAt(dx, dz, s.rotation()) : -1;
            Integer before = lastPad.put(p.getUUID(), pad);
            if (pad >= 0 && (before == null || before != pad)) {
                steppers.add(p);
                stepPads.add(pad);
            }
        }
        lastPad.keySet().removeIf(id -> room.stream().noneMatch(p -> p.getUUID().equals(id)));
        Effects out = new Effects(level, room);
        if (hushed && (!onFloor || !steppers.isEmpty())) {
            hushed = false;
        }
        if (s.phase() == ChoirSession.Phase.IDLE && onFloor && !hushed) {
            s.wake(now, room.size(), CryptConfig.window());
            coop = s.song().coop();
            sung = true;
            out.dirty = true;
        }
        s.tick(now, room.size(), maxGrace, onFloor, out);
        for (int i = 0; i < steppers.size(); i++) {
            stepper = steppers.get(i);
            if (s.phase() != ChoirSession.Phase.IDLE && s.phase() != ChoirSession.Phase.SOLVED) {
                players.stepped(stepper.getUUID());
            }
            int pad = stepPads.get(i);
            ChoirJudge j = s.phase() == ChoirSession.Phase.ANSWER ? s.judge() : null;
            long[] entry = null;
            if (j != null && !j.done()) {
                ChoirPhrase judged = j.phrase();
                int note = j.next();
                boolean right = judged.sounds(note, pad);
                boolean near = note > 0 && judged.sounds(note - 1, pad) || note + 1 < judged.size() && judged.sounds(note + 1, pad);
                entry = new long[] {now, s.round(), note, pad, j.target(note), 0, j.window(), grace(stepper), right ? 1 : 0,
                        near ? 1 : 0, s.silent() ? 1 : 0};
            }
            ChoirJudge.Verdict v = s.step(now, pad, grace(stepper), room.size(), out);
            if (entry != null) {
                entry[5] = v.ordinal();
                stepLog.add(entry);
            }
        }
        stepper = null;
        metronome(level, now);
        if (s.phase() == ChoirSession.Phase.ANSWER && s.judge() != null && answerStart != s.answerStart()) {
            out.dirty = true;
        }
        if (out.dirty || s.phase().ordinal() != phase) {
            pull(s);
            sync();
        }
    }

    private static int grace(ServerPlayer p) {
        return p.connection == null ? 0 : ChoirRules.graceTicks(p.connection.latency());
    }

    /** Players in the room: within {@link #ROOM_RADIUS} of the axis, from the floor to the ceiling. */
    public List<ServerPlayer> playersInRoom(ServerLevel level) {
        Vec3 c = centre();
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.isAlive() && Math.hypot(p.getX() - c.x, p.getZ() - c.z) <= ROOM_RADIUS && p.getY() > c.y - 1.5 && p.getY() < c.y + 6) {
                out.add(p);
            }
        }
        return out;
    }

    /** Copies the session's state into the synced fields. */
    private void pull(ChoirSession s) {
        phase = s.phase().ordinal();
        round = s.round();
        rotation = s.rotation();
        rotatedAt = s.rotatedAt();
        callStart = s.callStart();
        answerStart = s.answerStart();
        restUntil = s.restUntil();
        replay = s.replay();
        mistakes = s.mistakes();
        window = s.window();
        ChoirPhrase p = s.phrase();
        phrase = p == null ? new int[0] : p.encode();
        savedRound = s.round();
        savedRotation = s.rotation();
        solved = s.phase() == ChoirSession.Phase.SOLVED;
    }

    /** The session's effects in the world, for one tick. */
    private final class Effects implements ChoirSession.Listener {
        final ServerLevel level;
        final List<ServerPlayer> room;
        boolean dirty;

        Effects(ServerLevel level, List<ServerPlayer> room) {
            this.level = level;
            this.room = room;
        }

        @Override
        public void call(int round, boolean again) {
            play(CryptRegistry.CHOIR_CALL.get(), centre().add(0, 2.2, 0), 0.9f, 1.0f);
            dirty = true;
        }

        @Override
        public void note(int pad, boolean dim) {
            play(CryptRegistry.PAD_NOTES.get(pad).get(), padCentre(pad).add(0, 0.6, 0), dim ? 0.3f : 1.0f, 1.0f);
        }

        @Override
        public void countIn(int beatsLeft) {
            play(CryptRegistry.CHOIR_TICK.get(), centre().add(0, 2.2, 0), 0.8f, beatsLeft == 1 ? 1.5f : 1.2f);
        }

        @Override
        public void rotate(int rot) {
            play(CryptRegistry.CHOIR_ROTATE.get(), centre().add(0, 0.5, 0), 1.0f, 1.0f);
            dirty = true;
        }

        @Override
        public void hit(int note, int pad, int offset) {
            play(CryptRegistry.PAD_NOTES.get(pad).get(), padCentre(pad).add(0, 0.6, 0), 0.8f, 1.0f);
            measured.add(new int[] {session().round(), note, pad, offset});
            long now = level.getGameTime();
            long[] next = new long[Math.min(16, hits.length + 2)];
            int keep = next.length - 2;
            System.arraycopy(hits, hits.length - keep, next, 0, keep);
            next[keep] = pad;
            next[keep + 1] = now;
            hits = next;
            dirty = true;
        }

        @Override
        public void discord(ChoirSession.Miss why, int count) {
            long now = level.getGameTime();
            discords++;
            lastDiscordAt = now;
            discordAt = now;
            play(CryptRegistry.CHOIR_DISCORD.get(), centre().add(0, 1.5, 0), 1.0f, 1.0f);
            List<ServerPlayer> hurt = new ArrayList<>();
            if (why == ChoirSession.Miss.WRONG_PAD && stepper != null) {
                hurt.add(stepper);
            } else {
                List<ServerPlayer> onFloor = new ArrayList<>();
                for (ServerPlayer p : room) {
                    if (Math.hypot(p.getX() - centre().x, p.getZ() - centre().z) <= ChoirRules.FLOOR_RADIUS) {
                        onFloor.add(p);
                    }
                }
                hurt.addAll(players.hurtByMissedBeat(onFloor, ServerPlayer::getUUID));
            }
            for (ServerPlayer p : hurt) {
                p.hurt(level.damageSources().source(CryptRegistry.DISCORD), ChoirRules.DISCORD_DAMAGE);
                p.knockback(ChoirRules.DISCORD_KNOCKBACK, centre().x - p.getX(), centre().z - p.getZ());
                p.hurtMarked = true;
                p.displayClientMessage(Component.translatable(why == ChoirSession.Miss.WRONG_PAD
                        ? "cosmicbreach.choir.discord.wrong" : "cosmicbreach.choir.discord.missed"), true);
            }
            long[] miss = session().lastMiss();
            CosmicBreach.LOGGER.debug("[cosmicbreach] Choir Floor at {}: Discord ({}) on note {} due at {}, at {}; mistake {}",
                    worldPosition, why, miss[0], miss[1], miss[2], count);
            dirty = true;
        }

        @Override
        public void rest(long until) {
            rests++;
            lastRestAt = level.getGameTime();
            for (ServerPlayer p : room) {
                p.displayClientMessage(Component.translatable("cosmicbreach.choir.rest"), true);
            }
            dirty = true;
        }

        @Override
        public void roundDone(int done) {
            play(CryptRegistry.CHOIR_ROUND.get(), centre().add(0, 2.0, 0), 1.0f, 1.0f);
            if (done < ChoirRules.ROUNDS - 1) {
                for (ServerPlayer p : room) {
                    p.displayClientMessage(Component.translatable("cosmicbreach.choir.round", done + 1), true);
                }
            }
            dirty = true;
        }

        @Override
        public void solved() {
            players.reset();
            solvedAt = level.getGameTime();
            play(CryptRegistry.CHOIR_SOLVE.get(), centre().add(0, 2.0, 0), 1.4f, 1.0f);
            XpSource.LayerTier layer = tier >= 3 ? XpSource.LayerTier.DEEP : tier == 2 ? XpSource.LayerTier.DRIFT : XpSource.LayerTier.REACH;
            for (ServerPlayer p : room) {
                if (solvedFor.add(p.getUUID())) {
                    AttunementXp.award(p, XpSource.PUZZLE_SOLVED.at(layer));
                }
                p.displayClientMessage(Component.translatable("cosmicbreach.choir.solved"), true);
            }
            if (vault != null && level.getBlockEntity(vault) instanceof VaultBlockEntity v) {
                v.unlock(level);
            }
            ChoirFloors.solved(level, ConductorBlockEntity.this);
            CosmicBreach.LOGGER.debug("[cosmicbreach] Choir Floor at {} solved", worldPosition);
            dirty = true;
        }

        @Override
        public void idle() {
            idles++;
            players.reset();
            hushed = session().silentIdle();
            dirty = true;
        }

        private void play(SoundEvent sound, Vec3 at, float volume, float pitch) {
            level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, pitch);
        }
    }

    /** The metronome's quiet tick on every beat of an answer (the Conductor's notes keep the beat of a call). */
    private void metronome(ServerLevel level, long now) {
        if (session().phase() == ChoirSession.Phase.ANSWER && now % ChoirRules.BEAT == 0) {
            Vec3 at = centre().add(0, 2.2, 0);
            level.playSound(null, at.x, at.y, at.z, CryptRegistry.CHOIR_TICK.get(), SoundSource.BLOCKS, 0.35f, 1.0f);
        }
    }

    // ------------------------------------------------------------------ debug and the API

    /** Answers the round now (debug). */
    public void debugRound(ServerLevel level) {
        ChoirSession s = session();
        List<ServerPlayer> room = playersInRoom(level);
        s.skipRound(level.getGameTime(), Math.max(1, room.size()), new Effects(level, room));
        sung = true;
        pull(s);
        sync();
    }

    /** Solves the floor now (debug; the Sanctum's scripted tests). */
    public void debugSolve(ServerLevel level) {
        ChoirSession s = session();
        List<ServerPlayer> room = playersInRoom(level);
        s.solveNow(new Effects(level, room));
        sung = true;
        pull(s);
        sync();
    }

    /** Puts the floor back at round {@code round} (0-based), idle and ready to wake (debug: a test retries a round). */
    public void debugSetRound(ServerLevel level, int round) {
        ChoirSession s = session();
        s.restore(s.song() != null && s.song().coop(), round, s.rotation(), false);
        hushed = false;
        players.reset();
        pull(s);
        sync();
    }

    /** Applies the server's Relaxed setting to an awake floor. */
    public void setWindow(int window) {
        session().setWindow(window);
        this.window = window;
        sync();
    }

    public int tier() {
        return tier;
    }

    // ------------------------------------------------------------------ lifecycle, saving, syncing

    @Override
    public void onLoad() {
        super.onLoad();
        ChoirFloors.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        ChoirFloors.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        ChoirFloors.remove(this);
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("seed", seed);
        tag.putString("difficulty", difficulty);
        tag.putInt("tier", tier);
        if (vault != null) {
            tag.put("vault", NbtUtils.writeBlockPos(vault));
        }
        tag.putBoolean("sung", sung);
        tag.putBoolean("coop", coop);
        tag.putInt("saved_round", savedRound);
        tag.putInt("saved_rotation", savedRotation);
        tag.putBoolean("solved", solved);
        ListTag list = new ListTag();
        solvedFor.forEach(id -> list.add(NbtUtils.createUUID(id)));
        tag.put("solved_for", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("seed")) {
            seed = tag.getLong("seed");
            difficulty = tag.getString("difficulty").isEmpty() ? ChoirDifficulty.CRYPT.name() : tag.getString("difficulty");
            tier = tag.contains("tier") ? tag.getInt("tier") : 3;
            vault = tag.contains("vault") ? NbtUtils.readBlockPos(tag, "vault").orElse(null) : null;
            sung = tag.getBoolean("sung");
            coop = tag.getBoolean("coop");
            savedRound = tag.getInt("saved_round");
            savedRotation = tag.getInt("saved_rotation");
            solved = tag.getBoolean("solved");
            solvedFor.clear();
            for (Tag t : tag.getList("solved_for", Tag.TAG_INT_ARRAY)) {
                solvedFor.add(NbtUtils.loadUUID(t));
            }
            session = null;
            round = savedRound;
            rotation = savedRotation;
            phase = solved ? ChoirSession.Phase.SOLVED.ordinal() : ChoirSession.Phase.IDLE.ordinal();
        }
        if (tag.contains("c_phase")) {
            phase = tag.getInt("c_phase");
            round = tag.getInt("c_round");
            rotation = tag.getInt("c_rotation");
            rotatedAt = tag.getLong("c_rotated_at");
            callStart = tag.getLong("c_call");
            answerStart = tag.getLong("c_answer");
            restUntil = tag.getLong("c_rest");
            replay = tag.getBoolean("c_replay");
            mistakes = tag.getInt("c_mistakes");
            window = tag.getInt("c_window");
            phrase = tag.getIntArray("c_phrase");
            hits = tag.getLongArray("c_hits");
            discordAt = tag.getLong("c_discord");
            difficulty = tag.getString("c_difficulty").isEmpty() ? difficulty : tag.getString("c_difficulty");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("c_phase", phase);
        tag.putInt("c_round", round);
        tag.putInt("c_rotation", rotation);
        tag.putLong("c_rotated_at", rotatedAt);
        tag.putLong("c_call", callStart);
        tag.putLong("c_answer", answerStart);
        tag.putLong("c_rest", restUntil);
        tag.putBoolean("c_replay", replay);
        tag.putInt("c_mistakes", mistakes);
        tag.putInt("c_window", window);
        tag.putIntArray("c_phrase", phrase);
        tag.putLongArray("c_hits", hits);
        tag.putLong("c_discord", discordAt);
        tag.putString("c_difficulty", difficulty);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Players within the room's box, for commands. */
    public AABB roomBox() {
        return new AABB(worldPosition).inflate(ROOM_RADIUS, 0, ROOM_RADIUS).expandTowards(0, 5, 0).move(0, -1, 0);
    }
}
