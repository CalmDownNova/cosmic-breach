package com.cosmicbreach.client.gear;

import com.cosmicbreach.client.combat.ModKeyMappings;
import com.cosmicbreach.combat.data.WeaponDef;
import com.cosmicbreach.gear.GearRegistry;
import com.cosmicbreach.gear.net.SetAbilityPayload;
import com.cosmicbreach.gear.set.ArmorSets;
import com.cosmicbreach.gear.set.SetArmorItem;
import com.cosmicbreach.gear.vanguard.StarfallVanguard;
import com.cosmicbreach.item.CombatWeaponItem;
import com.cosmicbreach.item.GearTier;
import com.cosmicbreach.registry.ModItems;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;

/**
 * Gear on the client: the Forge's screen and rings, the set pieces' GeckoLib renderers, the Set Ability key
 * (Z: free in vanilla 1.21.1, Better Combat leaves its keys unbound and Combat Roll uses R), its HUD pip, and
 * the tier trim on weapon and armor icons (the item models' second layer, tinted by tier and hidden at the
 * unlock tier).
 */
public final class GearClient {
    public static final KeyMapping SET_ABILITY = new KeyMapping("key.cosmicbreach.set_ability", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, ModKeyMappings.CATEGORY);

    private GearClient() {
    }

    public static void register(IEventBus modBus, IEventBus gameBus) {
        SetArmorItem.clientRenderers = set -> new GeoRenderProvider() {
            private @Nullable SetArmorRenderer renderer;

            @Override
            public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(@Nullable T entity, ItemStack stack,
                                                                                  @Nullable EquipmentSlot slot,
                                                                                  @Nullable HumanoidModel<T> original) {
                if (renderer == null) {
                    renderer = new SetArmorRenderer(set);
                }
                return renderer;
            }
        };
        SetArmorModel.driver(StarfallVanguard.SET.id(), new VanguardCrest());
        SetArmorRenderer.glow(StarfallVanguard.SET.id(), new SetArmorRenderer.Glow(0xFF7A26, 0xFFE7A0, 20f, 0.85f));

        modBus.addListener(RegisterKeyMappingsEvent.class, event -> event.register(SET_ABILITY));
        modBus.addListener(RegisterMenuScreensEvent.class,
                event -> event.register(GearRegistry.ASTRAL_FORGE_MENU.get(), AstralForgeScreen::new));
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerBlockEntityRenderer(GearRegistry.ASTRAL_FORGE_ENTITY.get(), AstralForgeRenderer::new));
        modBus.addListener(RegisterGuiLayersEvent.class, SetAbilityHud::register);
        modBus.addListener(RegisterColorHandlersEvent.Item.class, GearClient::registerItemColors);
        gameBus.addListener(ClientTickEvent.Post.class, GearClient::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (SET_ABILITY.consumeClick()) {
            if (mc.player != null && mc.screen == null && ArmorSets.withAbility(mc.player) != null
                    && ArmorSets.state(mc.player).ready(mc.player.level().getGameTime())) {
                PacketDistributor.sendToServer(SetAbilityPayload.INSTANCE);
            }
        }
    }

    /** Layer 1 of a weapon's or set piece's icon is its trim: the tier's colour above the unlock tier, invisible at it. */
    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        List<Item> items = new ArrayList<>(List.of(ModItems.MERIDIAN.get(), ModItems.COMET_MAUL.get(), ModItems.BINARY_EDGES.get()));
        GearRegistry.VANGUARD.all().forEach(piece -> items.add(piece.get()));
        event.register((stack, tintIndex) -> tintIndex == 1 ? trimColor(stack) : -1, items.toArray(Item[]::new));
    }

    /** ARGB of a stack's trim: fully transparent until it is reforged. */
    public static int trimColor(ItemStack stack) {
        int unlock;
        if (stack.getItem() instanceof SetArmorItem piece) {
            unlock = piece.unlockTier();
        } else {
            WeaponDef weapon = CombatWeaponItem.weaponOf(stack, true);
            unlock = weapon == null ? 1 : weapon.tier();
        }
        int tier = GearTier.of(stack, unlock);
        return tier > unlock ? 0xFF000000 | GearTier.color(tier) : 0x00FFFFFF;
    }
}
