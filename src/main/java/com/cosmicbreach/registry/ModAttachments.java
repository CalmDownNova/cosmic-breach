package com.cosmicbreach.registry;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.combat.PlayerCombat;
import com.cosmicbreach.combat.server.PoiseTracker;
import com.cosmicbreach.combat.server.Suspension;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Data attachments. None has a serializer, so none is ever saved: combat state starts fresh on login,
 * respawn and chunk load, which also means nothing can stay stuck on an entity in a save.
 */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, CosmicBreach.MOD_ID);

    /** On players, both sides. */
    public static final Supplier<AttachmentType<PlayerCombat>> PLAYER_COMBAT = ATTACHMENTS.register("player_combat",
            () -> AttachmentType.builder(holder -> {
                if (!(holder instanceof Player player)) {
                    throw new IllegalArgumentException("PlayerCombat only attaches to players, not " + holder);
                }
                return new PlayerCombat(player);
            }).build());

    /** On any living entity that has taken Impact (server). */
    public static final Supplier<AttachmentType<PoiseTracker>> POISE = ATTACHMENTS.register("poise",
            () -> AttachmentType.builder(() -> new PoiseTracker()).build());

    /** On a living entity launched by Zenith while it is Suspended (server). */
    public static final Supplier<AttachmentType<Suspension>> SUSPENSION = ATTACHMENTS.register("suspension",
            () -> AttachmentType.builder(() -> new Suspension(0)).build());

    private ModAttachments() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
