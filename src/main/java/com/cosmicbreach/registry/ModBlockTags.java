package com.cosmicbreach.registry;

import com.cosmicbreach.CosmicBreach;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModBlockTags {
    /**
     * Blocks a right click opens or works even with a combat weapon in hand, instead of firing the
     * weapon's ability: doors, trapdoors, fence gates, buttons, levers, beds, bells, crafting tables,
     * anvils and the like. Blocks whose block entity has a menu (chests, furnaces) pass through anyway.
     */
    public static final TagKey<Block> ABILITY_PASSTHROUGH = TagKey.create(Registries.BLOCK, CosmicBreach.id("ability_passthrough"));

    private ModBlockTags() {
    }
}
