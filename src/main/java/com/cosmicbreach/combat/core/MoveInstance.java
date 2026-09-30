package com.cosmicbreach.combat.core;

import com.cosmicbreach.combat.data.MoveDef;
import net.minecraft.resources.ResourceLocation;

/**
 * One use of a move. {@code serial} is unique per state machine, so hits can be de-duplicated per use;
 * {@code mv} is the motion value for this use (a charged release stores its own).
 */
public record MoveInstance(int serial, ResourceLocation id, MoveDef def, double mv, boolean critGuaranteed) {
}
