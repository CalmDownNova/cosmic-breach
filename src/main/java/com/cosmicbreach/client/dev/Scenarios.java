package com.cosmicbreach.client.dev;

import com.cosmicbreach.client.dev.scenario.AnimScenario;
import com.cosmicbreach.client.dev.scenario.BlocksScenario;
import com.cosmicbreach.client.dev.scenario.ColossusScenario;
import com.cosmicbreach.client.dev.scenario.ComboScenario;
import com.cosmicbreach.client.dev.scenario.DimensionScenario;
import com.cosmicbreach.client.dev.scenario.ForgeScenario;
import com.cosmicbreach.client.dev.scenario.EdgesScenario;
import com.cosmicbreach.client.dev.scenario.FxScenario;
import com.cosmicbreach.client.dev.scenario.HarnessScenario;
import com.cosmicbreach.client.dev.scenario.HudScenario;
import com.cosmicbreach.client.dev.scenario.MaulScenario;
import com.cosmicbreach.client.dev.scenario.MovementScenario;
import com.cosmicbreach.client.dev.scenario.OnboardingScenario;
import com.cosmicbreach.client.dev.scenario.PackScenario;
import com.cosmicbreach.client.dev.scenario.ProgressionScenario;
import com.cosmicbreach.client.dev.scenario.SelftestScenario;
import com.cosmicbreach.client.dev.scenario.SkyScenario;
import com.cosmicbreach.client.dev.scenario.ShardlingScenario;
import com.cosmicbreach.client.dev.scenario.SmokeScenario;
import com.cosmicbreach.client.dev.scenario.VoiceScenario;
import com.cosmicbreach.client.dev.scenario.WeatherScenario;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Every autotest scenario, by the name passed as {@code -Pautotest=<name>}. To add one, write a
 * {@link Scenario} class in {@code client/dev/scenario/} and register it below.
 */
public final class Scenarios {
    private static final Map<String, Supplier<Scenario>> BY_NAME = new TreeMap<>();

    static {
        register("smoke", SmokeScenario::new);
        register("harness", HarnessScenario::new);
        register("selftest", SelftestScenario::new);
        register("combo", ComboScenario::new);
        register("movement", MovementScenario::new);
        register("hud", HudScenario::new);
        register("fx", FxScenario::new);
        register("fx-trails", () -> new FxScenario(true));
        register("anim", AnimScenario::new);
        register("anim-poses", () -> new AnimScenario(AnimScenario.Section.POSES));
        register("anim-moves", () -> new AnimScenario(AnimScenario.Section.MOVES));
        register("anim-walk", () -> new AnimScenario(AnimScenario.Section.WALKING));
        register("anim-hitstop", () -> new AnimScenario(AnimScenario.Section.HIT_STOP));
        register("anim-remote", () -> new AnimScenario(AnimScenario.Section.REMOTE));
        register("anim-blade", () -> new AnimScenario(AnimScenario.Section.BLADE));
        register("anim-fp", () -> new AnimScenario(AnimScenario.Section.FIRST_PERSON_OPTIONS));
        register("pack", PackScenario::new);
        register("shardling", ShardlingScenario::new);
        register("progression", ProgressionScenario::new);
        register("blocks", BlocksScenario::new);
        register("dimension", DimensionScenario::new);
        register("dimension-views", () -> new DimensionScenario(DimensionScenario.Part.VIEWS));
        register("dimension-rules", () -> new DimensionScenario(DimensionScenario.Part.RULES));
        register("dimension-timing", () -> new DimensionScenario(DimensionScenario.Part.TIMING));
        register("maul", MaulScenario::new);
        register("edges", EdgesScenario::new);
        register("edges-mechanics", () -> new EdgesScenario(false));
        register("edges-fp", EdgesScenario::framing);
        register("sky", SkyScenario::new);
        register("sky-views", () -> new SkyScenario(SkyScenario.Part.VIEWS));
        register("sky-checks", () -> new SkyScenario(SkyScenario.Part.CHECKS));
        register("sky-cost", () -> new SkyScenario(SkyScenario.Part.COST));
        register("weather", WeatherScenario::new);
        register("weather-flare", () -> new WeatherScenario(WeatherScenario.Part.FLARE));
        register("weather-shower", () -> new WeatherScenario(WeatherScenario.Part.SHOWER));
        register("weather-tide", () -> new WeatherScenario(WeatherScenario.Part.TIDE));
        register("weather-current", () -> new WeatherScenario(WeatherScenario.Part.CURRENT));
        register("weather-surge", () -> new WeatherScenario(WeatherScenario.Part.SURGE));
        register("forge", ForgeScenario::new);
        register("forge-looks", () -> new ForgeScenario(true));
        register("forge-screen", com.cosmicbreach.client.dev.scenario.ForgeScreenScenario::new);
        register("onboarding", OnboardingScenario::new);
        register("structures", com.cosmicbreach.client.dev.scenario.StructuresScenario::new);
        register("structures-reliquary", () -> new com.cosmicbreach.client.dev.scenario.StructuresScenario(
                com.cosmicbreach.client.dev.scenario.StructuresScenario.Part.RELIQUARY));
        register("structures-observatory", () -> new com.cosmicbreach.client.dev.scenario.StructuresScenario(
                com.cosmicbreach.client.dev.scenario.StructuresScenario.Part.OBSERVATORY));
        register("onboarding-starfall", () -> new OnboardingScenario(OnboardingScenario.Part.STARFALL));
        register("onboarding-ring", () -> new OnboardingScenario(OnboardingScenario.Part.RING));
        register("onboarding-rift", () -> new OnboardingScenario(OnboardingScenario.Part.RIFT));
        register("colossus-looks", () -> new ColossusScenario(ColossusScenario.Part.LOOKS));
        register("colossus", () -> new ColossusScenario(ColossusScenario.Part.MECHANICS));
        register("colossus-shatter", () -> new ColossusScenario(ColossusScenario.Part.SHATTER));
        register("colossus-world", () -> new ColossusScenario(ColossusScenario.Part.WORLD));
        register("colossus-fight", () -> new ColossusScenario(ColossusScenario.Part.FIGHT));
        register("colossus-death", () -> new ColossusScenario(ColossusScenario.Part.DEATH));
        register("voice", VoiceScenario::new);
        register("provisions", com.cosmicbreach.client.dev.scenario.ProvisionsScenario::new);
        register("sets", com.cosmicbreach.client.dev.scenario.SetsScenario::new);
        register("sets-driftweave", () -> new com.cosmicbreach.client.dev.scenario.SetsScenario(
                com.cosmicbreach.client.dev.scenario.SetsScenario.Part.DRIFTWEAVE));
        register("sets-regalia", () -> new com.cosmicbreach.client.dev.scenario.SetsScenario(
                com.cosmicbreach.client.dev.scenario.SetsScenario.Part.REGALIA));
        register("sets-looks", () -> new com.cosmicbreach.client.dev.scenario.SetsScenario(
                com.cosmicbreach.client.dev.scenario.SetsScenario.Part.LOOKS));
        register("choir", com.cosmicbreach.client.dev.scenario.ChoirScenario::new);
        register("crypt", com.cosmicbreach.client.dev.scenario.CryptScenario::new);
        register("stalker", com.cosmicbreach.client.dev.scenario.StalkerScenario::new);
        register("deeplight", com.cosmicbreach.client.dev.scenario.DeeplightScenario::new);
        register("astrolabe", com.cosmicbreach.client.dev.scenario.AstrolabeScenario::new);
        register("crypt-walk", () -> new com.cosmicbreach.client.dev.scenario.CryptScenario(
                com.cosmicbreach.client.dev.scenario.CryptScenario.Part.WALK));
        register("crypt-traps", () -> new com.cosmicbreach.client.dev.scenario.CryptScenario(
                com.cosmicbreach.client.dev.scenario.CryptScenario.Part.TRAPS));
        register("leviathan-looks", () -> new com.cosmicbreach.client.dev.scenario.LeviathanScenario(
                com.cosmicbreach.client.dev.scenario.LeviathanScenario.Part.LOOKS));
        register("leviathan", () -> new com.cosmicbreach.client.dev.scenario.LeviathanScenario(
                com.cosmicbreach.client.dev.scenario.LeviathanScenario.Part.MECHANICS));
        register("leviathan-moorage", () -> new com.cosmicbreach.client.dev.scenario.LeviathanScenario(
                com.cosmicbreach.client.dev.scenario.LeviathanScenario.Part.MOORAGE));
        register("leviathan-repeat", () -> new com.cosmicbreach.client.dev.scenario.LeviathanScenario(
                com.cosmicbreach.client.dev.scenario.LeviathanScenario.Part.REPEAT));
        register("leviathan-fight", () -> new com.cosmicbreach.client.dev.scenario.LeviathanScenario(
                com.cosmicbreach.client.dev.scenario.LeviathanScenario.Part.FIGHT));
        register("rift-lift", com.cosmicbreach.client.dev.scenario.RiftLiftScenario::new);
        register("gyre-knight", com.cosmicbreach.client.dev.scenario.GyreKnightScenario::new);
        register("boss-voice-colossus", () -> new com.cosmicbreach.client.dev.scenario.BossVoiceScenario(
                com.cosmicbreach.client.dev.scenario.BossVoiceScenario.Part.COLOSSUS));
        register("boss-voice-leviathan", () -> new com.cosmicbreach.client.dev.scenario.BossVoiceScenario(
                com.cosmicbreach.client.dev.scenario.BossVoiceScenario.Part.LEVIATHAN));
        register("boss-voice-unsung", () -> new com.cosmicbreach.client.dev.scenario.BossVoiceScenario(
                com.cosmicbreach.client.dev.scenario.BossVoiceScenario.Part.UNSUNG));
        register("gyre-dive", com.cosmicbreach.client.dev.scenario.GyreDiveScenario::new);
        register("render-fixes", com.cosmicbreach.client.dev.scenario.RenderFixesScenario::new);
        register("unsung", () -> new com.cosmicbreach.client.dev.scenario.UnsungScenario(com.cosmicbreach.client.dev.scenario.UnsungScenario.Part.MECHANICS));
        register("unsung-looks", () -> new com.cosmicbreach.client.dev.scenario.UnsungScenario(com.cosmicbreach.client.dev.scenario.UnsungScenario.Part.LOOKS));
        register("unsung-fight", () -> new com.cosmicbreach.client.dev.scenario.UnsungScenario(com.cosmicbreach.client.dev.scenario.UnsungScenario.Part.FIGHT));
        register("unsung-world", () -> new com.cosmicbreach.client.dev.scenario.UnsungScenario(com.cosmicbreach.client.dev.scenario.UnsungScenario.Part.WORLD));
        register("sanctum", com.cosmicbreach.client.dev.scenario.SanctumScenario::new);
        register("sanctum-approach", () -> new com.cosmicbreach.client.dev.scenario.SanctumScenario(com.cosmicbreach.client.dev.scenario.SanctumScenario.Part.APPROACH));
        register("sanctum-wings", () -> new com.cosmicbreach.client.dev.scenario.SanctumScenario(com.cosmicbreach.client.dev.scenario.SanctumScenario.Part.WINGS));
        register("sanctum-arena", () -> new com.cosmicbreach.client.dev.scenario.SanctumScenario(com.cosmicbreach.client.dev.scenario.SanctumScenario.Part.ARENA));
        register("sanctum-world", () -> new com.cosmicbreach.client.dev.scenario.SanctumScenario(com.cosmicbreach.client.dev.scenario.SanctumScenario.Part.WORLD));
        for (com.cosmicbreach.client.dev.scenario.BossVoiceTakesScenario.Part part : com.cosmicbreach.client.dev.scenario.BossVoiceTakesScenario.Part.values()) {
            register("boss-voice-takes-" + part.name().toLowerCase(java.util.Locale.ROOT),
                    () -> new com.cosmicbreach.client.dev.scenario.BossVoiceTakesScenario(part));
        }
        register("heliarch", () -> new com.cosmicbreach.client.dev.scenario.HeliarchScenario(com.cosmicbreach.client.dev.scenario.HeliarchScenario.Part.ALL));
        register("heliarch-looks", () -> new com.cosmicbreach.client.dev.scenario.HeliarchScenario(com.cosmicbreach.client.dev.scenario.HeliarchScenario.Part.LOOKS));
        register("heliarch-regent", () -> new com.cosmicbreach.client.dev.scenario.HeliarchScenario(com.cosmicbreach.client.dev.scenario.HeliarchScenario.Part.REGENT));
        register("heliarch-hollow", () -> new com.cosmicbreach.client.dev.scenario.HeliarchScenario(com.cosmicbreach.client.dev.scenario.HeliarchScenario.Part.HOLLOW));
        register("heliarch-end", () -> new com.cosmicbreach.client.dev.scenario.HeliarchScenario(com.cosmicbreach.client.dev.scenario.HeliarchScenario.Part.END));
        register("heliarch-lines", () -> new com.cosmicbreach.client.dev.scenario.HeliarchScenario(com.cosmicbreach.client.dev.scenario.HeliarchScenario.Part.LINES));
        register("heliarch-fight", () -> new com.cosmicbreach.client.dev.scenario.HeliarchScenario(com.cosmicbreach.client.dev.scenario.HeliarchScenario.Part.FIGHT));
        register("lastlight", com.cosmicbreach.client.dev.scenario.LastLightScenario::new);
        register("lastlight-looks", () -> new com.cosmicbreach.client.dev.scenario.LastLightScenario(true));
        register("cantor", com.cosmicbreach.client.dev.scenario.CantorScenario::new);
        register("curios", com.cosmicbreach.client.dev.scenario.CuriosScenario::new);
        register("stag", com.cosmicbreach.client.dev.scenario.StagScenario::new);
        register("manta", com.cosmicbreach.client.dev.scenario.MantaScenario::new);
        register("stable", com.cosmicbreach.client.dev.scenario.StableScenario::new);
        register("stable-stag", com.cosmicbreach.client.dev.scenario.StableStagScenario::new);
        register("stable-void", com.cosmicbreach.client.dev.scenario.StableVoidScenario::new);
        register("shrines", () -> new com.cosmicbreach.client.dev.scenario.ShrineScenario(
                com.cosmicbreach.client.dev.scenario.ShrineScenario.Part.KEEP));
        register("shrines-deaths", () -> new com.cosmicbreach.client.dev.scenario.ShrineScenario(
                com.cosmicbreach.client.dev.scenario.ShrineScenario.Part.DEATHS));
        register("shrines-sites", () -> new com.cosmicbreach.client.dev.scenario.ShrineScenario(
                com.cosmicbreach.client.dev.scenario.ShrineScenario.Part.SITES));
        register("ascent", com.cosmicbreach.client.dev.scenario.AscentScenario::new);
        register("familiars", com.cosmicbreach.client.dev.scenario.FamiliarsScenario::new);
        register("codex", com.cosmicbreach.client.dev.scenario.CodexScenario::new);
        register("config", com.cosmicbreach.client.dev.scenario.ConfigScenario::new);
        register("compat", com.cosmicbreach.client.dev.scenario.CompatScenario::new);
        register("compat-render", com.cosmicbreach.client.dev.scenario.RenderCompatScenario::new);
        register("stress", com.cosmicbreach.client.dev.scenario.StressScenario::new);
        register("stress-load", () -> new com.cosmicbreach.client.dev.scenario.StressScenario(
                com.cosmicbreach.client.dev.scenario.StressScenario.Part.LOAD));
        // quitting right after a long teleport (a stop that hangs fails the run); settled is the normal-play variant
        register("quit-teleport", () -> new com.cosmicbreach.client.dev.scenario.QuitAfterTeleportScenario(20, 1, false, 0));
        register("quit-teleport-chain", () -> new com.cosmicbreach.client.dev.scenario.QuitAfterTeleportScenario(20, 3, false, 0));
        register("quit-teleport-settled", () -> new com.cosmicbreach.client.dev.scenario.QuitAfterTeleportScenario(1, 1, true, 0));
        register("quit-teleport-settled-shrunk", () -> new com.cosmicbreach.client.dev.scenario.QuitAfterTeleportScenario(1, 1, true, 8));
        // the same quit in a vanilla dimension: whether a stall belongs to Aetheria's terrain or to the game's own stop
        // a quit three seconds after the teleport, where the first ring of fresh chunks is mid generation (the settled variants quit there
        // when the client has not yet been sent anything new): the same time in each dimension compares them like for like
        register("quit-teleport-3s", () -> new com.cosmicbreach.client.dev.scenario.QuitAfterTeleportScenario(60, 1, false, 0));
        register("quit-teleport-nether-3s", () -> new com.cosmicbreach.client.dev.scenario.QuitAfterTeleportScenario(60, 1, false, 0,
                com.cosmicbreach.client.dev.scenario.QuitAfterTeleportScenario.Place.NETHER));
        register("quit-teleport-overworld-3s", () -> new com.cosmicbreach.client.dev.scenario.QuitAfterTeleportScenario(60, 1, false, 0,
                com.cosmicbreach.client.dev.scenario.QuitAfterTeleportScenario.Place.OVERWORLD));
        register("quit-teleport-nether-settled", () -> new com.cosmicbreach.client.dev.scenario.QuitAfterTeleportScenario(1, 1, true, 0,
                com.cosmicbreach.client.dev.scenario.QuitAfterTeleportScenario.Place.NETHER));
        register("quit-teleport-overworld-settled", () -> new com.cosmicbreach.client.dev.scenario.QuitAfterTeleportScenario(1, 1, true, 0,
                com.cosmicbreach.client.dev.scenario.QuitAfterTeleportScenario.Place.OVERWORLD));
        for (com.cosmicbreach.client.dev.scenario.FamiliarsScenario.Part part : com.cosmicbreach.client.dev.scenario.FamiliarsScenario.Part.values()) {
            register("familiars-" + part.name().toLowerCase(java.util.Locale.ROOT), () -> new com.cosmicbreach.client.dev.scenario.FamiliarsScenario(part));
        }
        // multiplayer: join mode only (-Pjoin=<host:port>, an operator account; see scripts/mp-autotest.sh)
        register("mp-smoke", com.cosmicbreach.client.dev.scenario.MpSmokeScenario::new);
        register("mp-basics", com.cosmicbreach.client.dev.scenario.MpBasicsScenario::new);
        register("mp-travel", com.cosmicbreach.client.dev.scenario.MpTravelScenario::new);
        register("mp-final", com.cosmicbreach.client.dev.scenario.MpFinalScenario::new);
        register("mp-shrines", com.cosmicbreach.client.dev.scenario.MpShrinesScenario::new);
        register("mp-existing", com.cosmicbreach.client.dev.scenario.MpExistingScenario::new);
        for (com.cosmicbreach.client.dev.scenario.MpRewardsScenario.Mode mode : com.cosmicbreach.client.dev.scenario.MpRewardsScenario.Mode.values()) {
            String suffix = switch (mode) {
                case ALIVE -> "";
                case DEAD -> "-dead";
                case OFFLINE_SETUP -> "-offline";
                case RETURN -> "-return";
            };
            register("mp-rewards" + suffix, () -> new com.cosmicbreach.client.dev.scenario.MpRewardsScenario(mode));
        }
        register("mp-pair-a", () -> new com.cosmicbreach.client.dev.scenario.MpPairScenario(com.cosmicbreach.client.dev.scenario.MpPairScenario.Role.A));
        register("mp-pair-b", () -> new com.cosmicbreach.client.dev.scenario.MpPairScenario(com.cosmicbreach.client.dev.scenario.MpPairScenario.Role.B));
        register("mp-pair-b2", () -> new com.cosmicbreach.client.dev.scenario.MpPairScenario(
                com.cosmicbreach.client.dev.scenario.MpPairScenario.Role.B_AGAIN));
    }

    private Scenarios() {}

    private static void register(String name, Supplier<Scenario> factory) {
        if (BY_NAME.putIfAbsent(name, factory) != null) {
            throw new IllegalStateException("duplicate autotest scenario " + name);
        }
    }

    public static Optional<Scenario> create(String name) {
        return Optional.ofNullable(BY_NAME.get(name)).map(Supplier::get);
    }

    public static Set<String> names() {
        return BY_NAME.keySet();
    }
}
