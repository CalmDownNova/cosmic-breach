package com.cosmicbreach.mount;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The vanilla horse inventory for a celestial mount (GDD 8.1): the saddle slot takes the mount's own saddle, the armor
 * slot its barding, and one more slot under them takes its tack. Slots: 0 saddle, 1 armor, 2 to 37 the player's
 * inventory (hotbar last), 38 the tack. Opened by a menu type of its own, so the client finds the mount by id.
 */
public class MountMenu extends HorseInventoryMenu {
    public static final int TACK_X = 8;
    public static final int TACK_Y = 54;
    private static final int PLAYER_START = 2;

    private final CelestialMount mount;
    private final int tackSlot;

    /** Server side: the mount's own containers. */
    public MountMenu(int id, Inventory inventory, CelestialMount mount) {
        this(id, inventory, mount.getInventory(), mount.tackContainer(), mount);
    }

    public MountMenu(int id, Inventory inventory, Container saddle, Container tack, CelestialMount mount) {
        super(id, inventory, saddle, mount, 0);
        this.mount = mount;
        Slot saddleSlot = new Slot(saddle, 0, 8, 18) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return mount.isSaddleItem(stack) && !hasItem() && mount.isSaddleable();
            }

            @Override
            public boolean isActive() {
                return mount.isSaddleable();
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        };
        saddleSlot.index = 0;
        slots.set(0, saddleSlot);
        tackSlot = slots.size();
        addSlot(new Slot(tack, 0, TACK_X, TACK_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return mount.isTackItem(stack);
            }

            @Override
            public boolean isActive() {
                return mount.isTamed();
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
    }

    /** Client side, from the menu's opening packet: the mount by its id. */
    public static MountMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        Entity entity = inventory.player.level().getEntity(buf.readVarInt());
        if (!(entity instanceof CelestialMount mount)) {
            throw new IllegalStateException("the mount for this inventory is not loaded here");
        }
        return new MountMenu(id, inventory, new SimpleContainer(1), new SimpleContainer(1), mount);
    }

    public CelestialMount mount() {
        return mount;
    }

    /** The tack slot's index in {@link #slots}. */
    public int tackSlot() {
        return tackSlot;
    }

    @Override
    public MenuType<?> getType() {
        return Mounts.MENU.get();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int playerEnd = tackSlot;
        int hotbar = playerEnd - 9;
        if (index < PLAYER_START || index == tackSlot) {
            if (!moveItemStackTo(stack, PLAYER_START, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (slots.get(1).mayPlace(stack) && !slots.get(1).hasItem()) {
            if (!moveItemStackTo(stack, 1, 2, false)) {
                return ItemStack.EMPTY;
            }
        } else if (slots.get(0).mayPlace(stack) && !slots.get(0).hasItem()) {
            if (!moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (slots.get(tackSlot).mayPlace(stack) && !slots.get(tackSlot).hasItem()) {
            if (!moveItemStackTo(stack, tackSlot, tackSlot + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < hotbar) {
            if (!moveItemStackTo(stack, hotbar, playerEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, PLAYER_START, hotbar, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }
}
