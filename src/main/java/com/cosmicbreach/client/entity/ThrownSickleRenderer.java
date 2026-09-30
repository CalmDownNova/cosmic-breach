package com.cosmicbreach.client.entity;

import com.cosmicbreach.entity.edges.ThrownSickle;
import com.cosmicbreach.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The Binary Edges' thrown left sickle: the left sickle's own item model, spinning fast in a tilted plane along
 * its flight (and home again), and still, point first, while it is stuck. Its chain of light back to the hand
 * and its trail are drawn by {@code EdgesVisuals}.
 */
public class ThrownSickleRenderer extends EntityRenderer<ThrownSickle> {
    /** Degrees a tick the blade turns in its plane while it flies. */
    public static final float SPIN_PER_TICK = 72f;
    /** The spinning plane leans this far from upright ... */
    private static final float PLANE_ROLL = 50f;
    /** ... and turns this far off the flight, so the thrower behind it sees its face, not its edge. */
    private static final float PLANE_TURN = 55f;
    private static final float SCALE = 1.1f;

    private final ItemRenderer items;
    private ItemStack stack = ItemStack.EMPTY;

    public ThrownSickleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
        this.shadowRadius = 0f;
    }

    @Override
    public void render(ThrownSickle blade, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (stack.isEmpty()) {
            stack = new ItemStack(ModItems.BINARY_EDGES_LEFT.get());
        }
        pose.pushPose();
        // along the flight: local +X points where it flies (the way arrows are turned)
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, blade.yRotO, blade.getYRot()) - 90f));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, blade.xRotO, blade.getXRot())));
        if (blade.isStuck()) {
            // stuck point first: the crescent turned to face back where it came from, a little canted
            pose.mulPose(Axis.YP.rotationDegrees(80f));
            pose.mulPose(Axis.ZP.rotationDegrees(-20f));
        } else {
            pose.mulPose(Axis.YP.rotationDegrees(PLANE_TURN));
            pose.mulPose(Axis.XP.rotationDegrees(PLANE_ROLL));
            pose.mulPose(Axis.ZP.rotationDegrees(-(blade.tickCount + partialTick) * SPIN_PER_TICK));
        }
        pose.scale(SCALE, SCALE, SCALE);
        items.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, blade.level(),
                blade.getId());
        pose.popPose();
        super.render(blade, entityYaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ThrownSickle blade) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
