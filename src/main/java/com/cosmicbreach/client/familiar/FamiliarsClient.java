package com.cosmicbreach.client.familiar;

import com.cosmicbreach.CosmicBreach;
import com.cosmicbreach.familiar.FamiliarBond;
import com.cosmicbreach.familiar.FamiliarKind;
import com.cosmicbreach.familiar.FamiliarLanternItem;
import com.cosmicbreach.familiar.FamiliarRegistry;
import com.cosmicbreach.familiar.LanternState;
import com.cosmicbreach.familiar.StarEggItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * The familiars on the client: their renderers and the brazier's, the familiar key ({@link FamiliarKeys}), their moments
 * ({@link FamiliarFx}), and the lantern's and the egg's looks: {@code cosmicbreach:familiar_kind} (0.1 Emberwisp, 0.2
 * Gravikin, 0.3 Prism Moth, 0 none) and {@code cosmicbreach:lantern} (0 lit, 0.5 out, 1 dark).
 */
public final class FamiliarsClient {
    public static final ResourceLocation KIND = CosmicBreach.id("familiar_kind");
    public static final ResourceLocation STATE = CosmicBreach.id("lantern");

    private FamiliarsClient() {
    }

    public static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(FamiliarRegistry.EMBERWISP.get(), c -> new FamiliarRenderer<>(c, FamiliarKind.EMBERWISP));
            event.registerEntityRenderer(FamiliarRegistry.GRAVIKIN.get(), c -> new FamiliarRenderer<>(c, FamiliarKind.GRAVIKIN));
            event.registerEntityRenderer(FamiliarRegistry.PRISM_MOTH.get(), c -> new FamiliarRenderer<>(c, FamiliarKind.PRISM_MOTH));
            event.registerBlockEntityRenderer(FamiliarRegistry.BRAZIER_ENTITY.get(), BrazierRenderer::new);
        });
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> event.register(FamiliarKeys.KEY));
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(FamiliarsClient::itemProperties));
        gameBus.addListener(ClientTickEvent.Post.class, FamiliarKeys::tick);
        gameBus.addListener(ClientTickEvent.Post.class, FamiliarFx::tick);
    }

    private static void itemProperties() {
        ClampedItemPropertyFunction lanternKind = (stack, level, entity, seed) -> kindValue(FamiliarLanternItem.bond(stack) == null ? null
                : FamiliarLanternItem.bond(stack).kind());
        ItemProperties.register(FamiliarRegistry.LANTERN.get(), KIND, lanternKind);
        ItemProperties.register(FamiliarRegistry.LANTERN.get(), STATE, (stack, level, entity, seed) -> switch (state(stack)) {
            case LIT -> 0f;
            case OUT -> 0.5f;
            case DARK -> 1f;
        });
        ItemProperties.register(FamiliarRegistry.STAR_EGG.get(), KIND, (stack, level, entity, seed) -> kindValue(StarEggItem.kind(stack)));
    }

    private static float kindValue(FamiliarKind kind) {
        return kind == null ? 0f : (kind.ordinal() + 1) * 0.1f;
    }

    /** What a lantern shows to this client: dark after a death, out while the local player has its familiar out, else lit. */
    public static LanternState state(ItemStack stack) {
        FamiliarBond b = FamiliarLanternItem.bond(stack);
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (b == null || level == null) {
            return LanternState.LIT;
        }
        Player player = mc.player;
        boolean out = player != null && b.id().equals(player.getData(FamiliarRegistry.ACTIVE));
        return b.state(out, level.getGameTime());
    }
}
