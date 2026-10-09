package com.cosmicbreach.gear.forge;

import com.cosmicbreach.gear.GearRegistry;
import com.cosmicbreach.item.GearTier;
import com.cosmicbreach.satchel.SatchelContents;
import com.cosmicbreach.satchel.SatchelLocator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * The Forge's menu: one slot for the piece to reforge and the player's inventory. Crafting takes the
 * ingredients from the inventory; the screen asks for it with a button id (vanilla's
 * {@code clickMenuButton}, as the stonecutter and enchanting table do): {@code 0} to {@value #REFORGE_BUTTON}
 * minus one crafts that recipe of {@link ForgeRecipes#sorted}, {@value #REFORGE_BUTTON} reforges the slotted piece.
 * The server checks everything again.
 */
public class AstralForgeMenu extends AbstractContainerMenu {
    public static final int REFORGE_BUTTON = 1000;
    /** Button ids from here up load the Satchel's Gear slot (id minus this) into the reforge slot. */
    public static final int PICK_GEAR = 2000;

    /** Slot positions in the screen (item corner, GUI pixels from its top left; the tab strip takes the first 24). */
    public static final int REFORGE_X = 36;
    public static final int REFORGE_Y = 103;
    public static final int INVENTORY_X = 71;
    public static final int INVENTORY_Y = 134;
    public static final int HOTBAR_Y = 192;

    private static final int PIECE_SLOT = 0;
    private static final int INVENTORY_START = 1;
    private static final int INVENTORY_END = 28;
    private static final int HOTBAR_END = 37;

    private final ContainerLevelAccess access;
    private final BlockPos pos;
    private final Level level;
    private final Container piece = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            slotsChanged(this);
        }
    };

    /** Client: the Forge's position comes with the open request. */
    public AstralForgeMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(id, inventory, ContainerLevelAccess.NULL, data.readBlockPos());
    }

    public AstralForgeMenu(int id, Inventory inventory, ContainerLevelAccess access, BlockPos pos) {
        super(GearRegistry.ASTRAL_FORGE_MENU.get(), id);
        this.access = access;
        this.pos = pos;
        this.level = inventory.player.level();
        addSlot(new Slot(piece, 0, REFORGE_X, REFORGE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isReforgeable(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }
    }

    public BlockPos pos() {
        return pos;
    }

    /** The Forge's tier, from its block state (1 if it is gone). */
    public int forgeTier() {
        BlockState state = level.getBlockState(pos);
        return state.is(GearRegistry.ASTRAL_FORGE.get()) ? state.getValue(AstralForgeBlock.TIER) : 1;
    }

    public List<RecipeHolder<ForgeRecipe>> recipes() {
        return ForgeRecipes.sorted(level.getRecipeManager());
    }

    /** The piece waiting to be reforged. */
    public ItemStack piece() {
        return piece.getItem(0);
    }

    public boolean isReforgeable(ItemStack stack) {
        return GearTier.unlockTier(stack, level.isClientSide()) > 0;
    }

    /** Whether the slotted piece can go up a tier now, for {@code player}. */
    public ForgeTiers.Check reforgeCheck(Player player) {
        ItemStack stack = piece();
        int unlock = GearTier.unlockTier(stack, level.isClientSide());
        if (stack.isEmpty() || unlock == 0) {
            return ForgeTiers.Check.NOT_REFORGEABLE;
        }
        int tier = GearTier.of(stack, unlock);
        int carried = ForgeTiers.reforgeCost(tier + 1)
                .map(cost -> ForgeCrafting.count(player.getInventory(), BuiltInRegistries.ITEM.get(cost.item())))
                .orElse(0);
        return ForgeTiers.check(tier, forgeTier(), carried);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == REFORGE_BUTTON) {
            return reforge(player);
        }
        if (id >= PICK_GEAR && id < PICK_GEAR + SatchelContents.GEAR_SLOTS) {
            return pickFromSatchel(player, id - PICK_GEAR);
        }
        List<RecipeHolder<ForgeRecipe>> recipes = recipes();
        if (id < 0 || id >= recipes.size()) {
            return false;
        }
        return craft(player, recipes.get(id).value());
    }

    /** The Satchel's Gear slots holding a piece that can be reforged. */
    public List<Integer> satchelPicks(Player player) {
        List<Integer> out = new ArrayList<>();
        SatchelLocator.contentsOf(player).ifPresent(c -> {
            for (int s = 0; s < SatchelContents.GEAR_SLOTS; s++) {
                if (isReforgeable(c.gear().get(s))) {
                    out.add(s);
                }
            }
        });
        return out;
    }

    /** Moves a piece from the Satchel's Gear tab into the reforge slot: out of one place and into the other, once. */
    private boolean pickFromSatchel(Player player, int gearSlot) {
        if (!piece().isEmpty()) {
            return false;
        }
        ItemStack candidate = SatchelLocator.contentsOf(player).map(c -> c.gear().get(gearSlot)).orElse(ItemStack.EMPTY);
        if (!isReforgeable(candidate)) {
            return false;
        }
        if (level.isClientSide()) {
            return true;
        }
        ItemStack taken = candidate.copy();
        boolean[] moved = {false};
        SatchelLocator.update(player, c -> {
            if (ItemStack.matches(c.gear().get(gearSlot), taken)) {
                moved[0] = true;
                return c.withGear(gearSlot, ItemStack.EMPTY);
            }
            return c;
        });
        if (!moved[0]) {
            return false;
        }
        piece.setItem(0, taken);
        broadcastChanges();
        return true;
    }

    private boolean craft(Player player, ForgeRecipe recipe) {
        if (recipe.tier() > forgeTier() || !ForgeCrafting.covers(recipe.ingredients(), ForgeCrafting.carried(player.getInventory()))) {
            return false;
        }
        if (level.isClientSide()) {
            return true;
        }
        if (!ForgeCrafting.take(player.getInventory(), recipe.ingredients())) {
            return false;
        }
        player.getInventory().placeItemBackInInventory(recipe.assemble(new ForgeRecipe.Input(List.of()), level.registryAccess()));
        celebrate(false);
        broadcastChanges();
        return true;
    }

    private boolean reforge(Player player) {
        if (reforgeCheck(player) != ForgeTiers.Check.OK) {
            return false;
        }
        if (level.isClientSide()) {
            return true;
        }
        ItemStack stack = piece();
        int tier = GearTier.of(stack, GearTier.unlockTier(stack, false));
        Optional<ForgeTiers.Cost> cost = ForgeTiers.reforgeCost(tier + 1);
        if (cost.isEmpty()) {
            return false;
        }
        Item metal = BuiltInRegistries.ITEM.get(cost.get().item());
        if (!ForgeCrafting.take(player.getInventory(),
                List.of(SizedIngredient.of(metal, cost.get().count())))) {
            return false;
        }
        ItemStack reforged = stack.copy();
        GearTier.set(reforged, tier + 1);
        piece.setItem(0, reforged);
        celebrate(true);
        broadcastChanges();
        return true;
    }

    /** The sound and sparks of a finished craft or reforge, and the rings' flash. */
    private void celebrate(boolean reforged) {
        access.execute((lvl, at) -> {
            if (lvl instanceof ServerLevel server) {
                server.blockEvent(at, GearRegistry.ASTRAL_FORGE.get(), AstralForgeBlock.EVENT_CRAFTED, 0);
                server.playSound(null, at, GearRegistry.FORGE_CRAFT.get(), SoundSource.BLOCKS, 1.0f,
                        reforged ? 0.85f : 1.0f);
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.getX() + 0.5, at.getY() + 0.95, at.getZ() + 0.5,
                        14, 0.3, 0.1, 0.3, 0.12);
            }
        });
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == PIECE_SLOT) {
            if (!moveItemStackTo(stack, INVENTORY_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (isReforgeable(stack) && !slots.get(PIECE_SLOT).hasItem()) {
            if (!moveItemStackTo(stack, PIECE_SLOT, PIECE_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < INVENTORY_END) {
            if (!moveItemStackTo(stack, INVENTORY_END, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, GearRegistry.ASTRAL_FORGE.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((lvl, at) -> clearContainer(player, piece));
    }
}
