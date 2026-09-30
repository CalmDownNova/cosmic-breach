package com.cosmicbreach.client.anim;

import com.cosmicbreach.CosmicBreach;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * How one animation plays beyond its keyframes: what the player sees of it in first person, and for
 * how many ticks its own legs carry the body's motion (a dash's glide, a lunge's step) so walking never
 * takes them over. After that window the legs hand over to vanilla walking whenever the player walks.
 *
 * @param firstPerson        {@link FirstPersonMode#THIRD_PERSON_MODEL} draws the animated blade (and
 *                           arms, if the configuration shows them) from the eyes; {@link FirstPersonMode#NONE}
 *                           keeps vanilla's first-person item
 * @param firstPersonConfig  which arms and items the animated first person shows
 * @param firstPersonPush    blocks the animated model sits further ahead of the eye in first person
 * @param firstPersonDrop    blocks it sits lower
 * @param firstPersonPitch   the camera pitch (degrees, down positive) whose framing first person keeps
 *                           at every pitch, or NaN to show the model where it really is
 * @param plantedTicks       ticks from the start in which the animation's legs stay, whatever the body does
 * @param firstPersonSide    blocks the animated model sits to the right of the eye in first person (a glaive's thrust
 *                           then runs from the lower right toward the crosshair instead of end-on under it)
 */
record AnimationStyle(FirstPersonMode firstPerson, FirstPersonConfiguration firstPersonConfig, float firstPersonPush,
                      float firstPersonDrop, float firstPersonPitch, int plantedTicks, float firstPersonSide) {
    /** A style with the model straight ahead of the eye. */
    AnimationStyle(FirstPersonMode firstPerson, FirstPersonConfiguration firstPersonConfig, float firstPersonPush,
                   float firstPersonDrop, float firstPersonPitch, int plantedTicks) {
        this(firstPerson, firstPersonConfig, firstPersonPush, firstPersonDrop, firstPersonPitch, plantedTicks, 0f);
    }

    /** Planted for as long as it plays (a dive in the air). */
    static final int ALWAYS = Integer.MAX_VALUE;

    /** How far right of the eye Last Light's thrusts sit in first person, blocks. */
    private static final float THRUST_SIDE = 0.45f;
    /** The blade alone: seen from inside the head the arms are huge and a high guard's fists fill the view. */
    private static final FirstPersonConfiguration BLADE = new FirstPersonConfiguration(false, false, true, false);
    /**
     * First person: the blade, a little ahead of and below the eye, framed as if looking 35 degrees down
     * wherever the player looks: a chest-high swing then crosses the view near the crosshair, where its
     * slash arc is drawn, instead of along the bottom edge.
     */
    private static final AnimationStyle SWING = new AnimationStyle(FirstPersonMode.THIRD_PERSON_MODEL, BLADE,
            0.3f, 0.1f, 35f, 0);
    /** Where the player looks on purpose (down at a plunge's landing): shown where it really is. */
    private static final AnimationStyle UNFRAMED = SWING.framedAt(Float.NaN);
    /**
     * Vanilla's first-person sword stays: the charge (its glow shows) and the dashes (the animated blade
     * trails behind the body, out of view, so the sword would vanish for the dash).
     */
    private static final AnimationStyle VANILLA_HAND = new AnimationStyle(FirstPersonMode.NONE, BLADE,
            0f, 0f, Float.NaN, 0);
    /** Both blades of a pair (the Binary Edges), framed like a swing: the off hand's sickle shows too. */
    private static final FirstPersonConfiguration TWIN_BLADES = new FirstPersonConfiguration(false, false, true, true);
    private static final AnimationStyle TWIN = new AnimationStyle(FirstPersonMode.THIRD_PERSON_MODEL, TWIN_BLADES,
            0.3f, 0.1f, 30f, 0);
    private static final AnimationStyle TWIN_UNFRAMED = TWIN.framedAt(Float.NaN);

    private static final Map<ResourceLocation, AnimationStyle> BY_ID = Map.ofEntries(
            Map.entry(id("meridian_l1"), SWING),
            Map.entry(id("meridian_l2"), SWING),
            Map.entry(id("meridian_l3"), SWING.planted(7)),
            Map.entry(id("meridian_charge"), VANILLA_HAND),
            Map.entry(id("meridian_line"), SWING.planted(7)),
            Map.entry(id("meridian_falling_star"), UNFRAMED.planted(ALWAYS)),
            Map.entry(id("meridian_falling_star_land"), UNFRAMED.planted(4)),
            Map.entry(id("meridian_pass"), SWING.planted(5)),
            Map.entry(id("meridian_zenith"), SWING.planted(9)),
            Map.entry(EngineAnimations.DASH_FORWARD, VANILLA_HAND.planted(8)),
            Map.entry(EngineAnimations.DASH_BACK, VANILLA_HAND.planted(8)),
            Map.entry(EngineAnimations.DASH_SIDE, VANILLA_HAND.planted(8)),
            Map.entry(EngineAnimations.PARRY, SWING),
            Map.entry(EngineAnimations.PARRY_SUCCESS, SWING),
            Map.entry(EngineAnimations.STAGGER, SWING.planted(10)),
            // The Comet Maul: swings framed like Meridian's, planted through their heavy landings
            Map.entry(id("maul_l1"), SWING.planted(12)),
            Map.entry(id("maul_l2"), SWING.planted(11)),
            Map.entry(id("maul_l3"), SWING.planted(16)),
            Map.entry(id("maul_charge"), VANILLA_HAND),
            Map.entry(id("maul_crater"), SWING.planted(14)),
            Map.entry(id("maul_meteorfall"), UNFRAMED.planted(ALWAYS)),
            Map.entry(id("maul_meteorfall_land"), UNFRAMED.planted(8)),
            Map.entry(id("maul_ram"), SWING.planted(8)),
            Map.entry(id("maul_well"), SWING.planted(ALWAYS)),
            Map.entry(id("maul_dash_forward"), VANILLA_HAND.planted(8)),
            Map.entry(id("maul_dash_back"), VANILLA_HAND.planted(8)),
            Map.entry(id("maul_stagger"), SWING.planted(10)),
            // The Binary Edges: both sickles in the animated first person; charge, dive and dashes keep vanilla's
            // hands (both sickles there too), so the dive's spin never whirls the blades round the camera
            Map.entry(id("edges_l1"), TWIN),
            Map.entry(id("edges_l2"), TWIN),
            Map.entry(id("edges_l3"), TWIN),
            Map.entry(id("edges_l4"), TWIN.planted(5)),
            Map.entry(id("edges_l5"), TWIN.planted(9)),
            Map.entry(id("edges_charge"), VANILLA_HAND),
            Map.entry(id("edges_orbit"), TWIN.planted(2)),
            Map.entry(id("edges_meteor"), VANILLA_HAND.planted(ALWAYS)),
            Map.entry(id("edges_meteor_land"), TWIN_UNFRAMED.planted(6)),
            Map.entry(id("edges_scissor"), TWIN.planted(4)),
            Map.entry(id("edges_tether"), TWIN.planted(3)),
            Map.entry(id("edges_blink"), TWIN.planted(8)),
            Map.entry(id("edges_dash_forward"), VANILLA_HAND.planted(8)),
            Map.entry(id("edges_dash_back"), VANILLA_HAND.planted(8)),
            Map.entry(id("edges_dash_side"), VANILLA_HAND.planted(8)),
            Map.entry(id("edges_parry"), TWIN),
            Map.entry(id("edges_parry_success"), TWIN),
            Map.entry(id("edges_stagger"), TWIN.planted(10)),
            // Last Light (G9b): its thrusts framed as if looking level, so the point drives past the crosshair on its right;
            // the Umbra Cantor keeps vanilla's first-person bow
            Map.entry(id("lastlight_l1"), SWING.framedAt(10f).sided(THRUST_SIDE)),
            Map.entry(id("lastlight_l2"), SWING.framedAt(10f).sided(THRUST_SIDE)),
            Map.entry(id("lastlight_l3"), SWING.framedAt(10f).planted(8).sided(THRUST_SIDE)),
            Map.entry(id("lastlight_charge"), VANILLA_HAND),
            Map.entry(id("lastlight_sunspear"), SWING.framedAt(10f).planted(8).sided(THRUST_SIDE)),
            Map.entry(id("lastlight_sunfall"), UNFRAMED.planted(ALWAYS)),
            Map.entry(id("lastlight_sunfall_land"), UNFRAMED.planted(4)),
            Map.entry(id("lastlight_sunstreak"), SWING.framedAt(10f).planted(5).sided(THRUST_SIDE)),
            Map.entry(id("lastlight_dawnguard"), SWING.planted(3)),
            Map.entry(id("lastlight_dash_forward"), VANILLA_HAND.planted(8)),
            Map.entry(id("lastlight_dash_back"), VANILLA_HAND.planted(8)),
            Map.entry(id("lastlight_stagger"), SWING.planted(10)),
            Map.entry(id("cantor_l1"), VANILLA_HAND),
            Map.entry(id("cantor_l2"), VANILLA_HAND),
            Map.entry(id("cantor_l3"), VANILLA_HAND),
            Map.entry(id("cantor_draw"), VANILLA_HAND),
            Map.entry(id("cantor_fermata"), VANILLA_HAND),
            Map.entry(id("cantor_grace_note"), VANILLA_HAND),
            Map.entry(id("cantor_syncopation"), VANILLA_HAND.planted(2)),
            Map.entry(id("cantor_cadence"), VANILLA_HAND));

    /** The style of {@code animation}; an animation not listed plays like a swing. */
    static AnimationStyle of(ResourceLocation animation) {
        return BY_ID.getOrDefault(animation, SWING);
    }

    private AnimationStyle framedAt(float pitch) {
        return new AnimationStyle(firstPerson, firstPersonConfig, firstPersonPush, firstPersonDrop, pitch, plantedTicks,
                firstPersonSide);
    }

    private AnimationStyle planted(int ticks) {
        return new AnimationStyle(firstPerson, firstPersonConfig, firstPersonPush, firstPersonDrop, firstPersonPitch, ticks,
                firstPersonSide);
    }

    private AnimationStyle sided(float blocks) {
        return new AnimationStyle(firstPerson, firstPersonConfig, firstPersonPush, firstPersonDrop, firstPersonPitch, plantedTicks,
                blocks);
    }

    private static ResourceLocation id(String path) {
        return CosmicBreach.id(path);
    }
}
