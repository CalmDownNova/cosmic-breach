package com.cosmicbreach.satchel;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Satchel's menu (1.2 design section 3). Slots 0 to 17 are the Gear tab, then the player's inventory (27, then the
 * hotbar). The Materials tab has no slots: its types are virtual, and every move is a request the server checks again
 * ({@link #act}). The server is the only side that changes the item: the Gear slots write through to it, materials
 * move by {@link SatchelContents#deposit} and {@link SatchelContents#withdraw}, which report exactly what moved.
 *
 * <p>Dupe safety: the Gear tab is a view of the item, so the menu remembers the gear it last loaded or wrote and, if the
 * item at its place no longer holds exactly that (it was swapped for another Satchel, say), it closes and writes
 * nothing. The inventory slot holding the open Satchel cannot be picked up or placed into.
 */
public class SatchelMenu extends AbstractContainerMenu {
    public static final int WITHDRAW = 0;
    public static final int TOGGLE = 1;
    public static final int DEPOSIT_CARRIED = 2;

    public static final int GEAR_X = 8;
    public static final int GEAR_Y = 40;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 98;
    public static final int HOTBAR_Y = 156;

    private static final int GEAR_END = SatchelContents.GEAR_SLOTS;
    private static final int INVENTORY_END = GEAR_END + 27;
    private static final int HOTBAR_END = INVENTORY_END + 9;

    private final Player player;
    private final SatchelLocator.Ref ref;
    private final SimpleContainer gear = new SimpleContainer(SatchelContents.GEAR_SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            saveGear();
        }
    };
    private boolean loading;
    private boolean broken;
    private List<ItemStack> lastGear = List.of();
    private SatchelContents lastSent;
    /** Client: the contents the server last sent, for the Materials tab. */
    private SatchelContents shown = SatchelContents.EMPTY;
    /** Client: which tab the screen shows; the Gear slots are only active on theirs. */
    public boolean materialsTab;

    public SatchelMenu(int id, Inventory inventory, SatchelLocator.Ref ref) {
        super(Satchels.MENU.get(), id);
        this.player = inventory.player;
        this.ref = ref;
        SatchelContents contents = Satchels.contents(SatchelLocator.get(player, ref));
        loading = true;
        for (int i = 0; i < SatchelContents.GEAR_SLOTS; i++) {
            gear.setItem(i, contents.gear().get(i).copy());
        }
        loading = false;
        lastGear = copyOf(contents.gear());
        shown = contents;
        for (int i = 0; i < SatchelContents.GEAR_SLOTS; i++) {
            addSlot(new Slot(gear, i, GEAR_X + (i % 9) * 18, GEAR_Y + (i / 9) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return SatchelFilter.isGear(stack);
                }

                @Override
                public boolean isActive() {
                    return !materialsTab;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(guarded(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(guarded(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }
    }

    /** An inventory slot, locked if it holds the open Satchel. */
    private Slot guarded(Inventory inventory, int index, int x, int y) {
        boolean locked = !ref.curios() && ref.index() == index;
        return new Slot(inventory, index, x, y) {
            @Override
            public boolean mayPickup(Player p) {
                return !locked && super.mayPickup(p);
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return !locked && super.mayPlace(stack);
            }
        };
    }

    public static SatchelMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new SatchelMenu(id, inventory, SatchelLocator.Ref.STREAM_CODEC.decode(buf));
    }

    /** Opens the Satchel at a place for a player (server). */
    public static void open(ServerPlayer player, SatchelLocator.Ref ref) {
        if (!SatchelFilter.isSatchel(SatchelLocator.get(player, ref))) {
            return;
        }
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new SatchelMenu(id, inv, ref), Component.translatable("gui.cosmicbreach.satchel")),
                buf -> SatchelLocator.Ref.STREAM_CODEC.encode(buf, ref));
    }

    private static List<ItemStack> copyOf(List<ItemStack> stacks) {
        List<ItemStack> out = new ArrayList<>(stacks.size());
        stacks.forEach(s -> out.add(s.copy()));
        return out;
    }

    private List<ItemStack> gearNow() {
        List<ItemStack> out = new ArrayList<>(SatchelContents.GEAR_SLOTS);
        for (int i = 0; i < SatchelContents.GEAR_SLOTS; i++) {
            out.add(gear.getItem(i).copy());
        }
        return out;
    }

    /** Server: writes the Gear slots to the item, unless the item is no longer the one this menu opened. */
    private void saveGear() {
        if (loading || player.level().isClientSide() || broken) {
            return;
        }
        ItemStack stack = SatchelLocator.get(player, ref);
        if (!SatchelFilter.isSatchel(stack) || !ItemStack.listMatches(Satchels.contents(stack).gear(), lastGear)) {
            broken = true;
            return;
        }
        List<ItemStack> now = gearNow();
        SatchelLocator.updateAt(player, ref, c -> c.withGear(now));
        lastGear = copyOf(now);
    }

    public SatchelLocator.Ref ref() {
        return ref;
    }

    /** What the Materials tab shows: on the server the item, on the client what the server last sent. */
    public SatchelContents contents() {
        return player.level().isClientSide() ? shown : Satchels.contents(SatchelLocator.get(player, ref));
    }

    public void setShown(SatchelContents contents) {
        this.shown = contents;
    }

    // ------------------------------------------------------------------ requests (server)

    /** A request from the screen. Everything is checked here: the item, the type, the amounts. */
    public void act(ServerPlayer who, int action, ResourceLocation item, int amount) {
        if (who != player || broken || !SatchelFilter.isSatchel(SatchelLocator.get(player, ref))) {
            return;
        }
        switch (action) {
            case TOGGLE -> SatchelLocator.updateAt(player, ref, c -> toggleAllowed(c, item, isMaterialId(item)) ? c.togglePickup(item) : c);
            case WITHDRAW -> withdraw(item, amount);
            case DEPOSIT_CARRIED -> depositCarried();
            default -> {
            }
        }
    }

    /** True if {@code id} names a registered item that passes the material filter. */
    private static boolean isMaterialId(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        return item != net.minecraft.world.item.Items.AIR && SatchelFilter.isMaterial(new ItemStack(item));
    }

    /**
     * Whether a pickup switch request is honoured (the client chooses the id, so it is checked): a type already switched off
     * may always be switched back on; a new one must be a material and the list of switched-off types stays under
     * {@value SatchelContents#MAX_PICKUP_OFF}.
     */
    public static boolean toggleAllowed(SatchelContents contents, ResourceLocation id, boolean material) {
        if (contents.pickupOff().contains(id)) {
            return true;
        }
        return material && contents.pickupOff().size() < SatchelContents.MAX_PICKUP_OFF;
    }

    private void withdraw(ResourceLocation id, int amount) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == net.minecraft.world.item.Items.AIR || amount < 1) {
            return;
        }
        int want = Math.min(amount, SatchelContents.CAP);
        SatchelLocator.updateAt(player, ref, c -> {
            SatchelContents.Move out = c.withdraw(id, want);
            int left = give(item, out.moved());
            return left > 0 ? out.contents().deposit(id, left).contents() : out.contents();
        });
    }

    /** Puts {@code count} of an item in the inventory; returns how many did not fit. */
    private int give(Item item, int count) {
        int left = count;
        while (left > 0) {
            ItemStack chunk = new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize()));
            int size = chunk.getCount();
            // counted in the inventory itself: with infinite materials (creative) Inventory.add reports success and empties the
            // stack even when nothing fitted, which would lose the items
            int before = held(item);
            player.getInventory().add(chunk);
            int placed = held(item) - before;
            left -= Math.max(0, placed);
            if (placed < size) {
                break; // the inventory is full
            }
        }
        return left;
    }

    private int held(Item item) {
        int n = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private void depositCarried() {
        ItemStack carried = getCarried();
        if (!SatchelFilter.isMaterial(carried)) {
            return;
        }
        ResourceLocation id = SatchelFilter.id(carried);
        int[] moved = {0};
        SatchelLocator.updateAt(player, ref, c -> {
            SatchelContents.Move in = c.deposit(id, carried.getCount());
            moved[0] = in.moved();
            return in.contents();
        });
        if (moved[0] > 0) {
            carried.shrink(moved[0]);
            setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        }
    }

    // ------------------------------------------------------------------ sync

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        pushContents(false);
    }

    @Override
    public void sendAllDataToRemote() {
        super.sendAllDataToRemote();
        pushContents(true);
    }

    private void pushContents(boolean force) {
        if (player instanceof ServerPlayer server) {
            SatchelContents now = Satchels.contents(SatchelLocator.get(player, ref));
            if (force || !now.equals(lastSent)) {
                lastSent = now;
                PacketDistributor.sendToPlayer(server, new SatchelNet.SatchelContentsPayload(containerId, now));
            }
        }
    }

    // ------------------------------------------------------------------ slots

    /**
     * The number-key and off-hand swap would move the locked slot's Satchel through the hotbar (the swap reads the hotbar
     * slot itself, not the clicked one), so a swap with the open Satchel's own slot is refused.
     */
    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType type, Player who) {
        if (type == net.minecraft.world.inventory.ClickType.SWAP && !ref.curios()) {
            int hotbarIndex = button == 40 ? SatchelLocator.OFFHAND : button;
            if (hotbarIndex == ref.index()) {
                return;
            }
        }
        super.clicked(slotId, button, type, who);
    }

    @Override
    public ItemStack quickMoveStack(Player who, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(who)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < GEAR_END) {
            if (!moveItemStackTo(stack, GEAR_END, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (SatchelFilter.isMaterial(stack)) {
            if (who.level().isClientSide()) {
                return ItemStack.EMPTY; // the server does it and the slot sync tells the client
            }
            ResourceLocation id = SatchelFilter.id(stack);
            int[] moved = {0};
            SatchelLocator.updateAt(player, ref, c -> {
                SatchelContents.Move in = c.deposit(id, stack.getCount());
                moved[0] = in.moved();
                return in.contents();
            });
            if (moved[0] <= 0) {
                return ItemStack.EMPTY;
            }
            stack.shrink(moved[0]);
        } else if (SatchelFilter.isGear(stack)) {
            if (!moveItemStackTo(stack, 0, GEAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < INVENTORY_END) {
            if (!moveItemStackTo(stack, INVENTORY_END, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, GEAR_END, INVENTORY_END, false)) {
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
        slot.onTake(who, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player who) {
        return !broken && SatchelFilter.isSatchel(SatchelLocator.get(who, ref));
    }

    @Override
    public MenuType<?> getType() {
        return Satchels.MENU.get();
    }

    /** Test and scenario access: the Gear container. */
    public Container gearContainer() {
        return gear;
    }
}
