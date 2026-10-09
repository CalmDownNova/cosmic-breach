package com.cosmicbreach.satchel;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Which Satchel a player has in force: only one is honoured. The one worn in a Curios slot wins, else the first in the
 * main inventory (hotbar first), else the off hand. A {@link Ref} names a place, never a stack, and every change is read
 * from the place, changed on a copy and written back there ({@link #set}), so Curios and the inventory both see it.
 */
public final class SatchelLocator {
    /** Off hand, after the 36 main inventory slots. */
    public static final int OFFHAND = 36;

    /** Where a Satchel is: a Curios slot (index into the flattened equipped curios) or an inventory index (0 to 35, 36 off hand). */
    public record Ref(boolean curios, int index) {
        public static final StreamCodec<ByteBuf, Ref> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Ref::curios, ByteBufCodecs.VAR_INT, Ref::index, Ref::new);
    }

    public record Found(Ref ref, ItemStack stack) {
    }

    private SatchelLocator() {
    }

    private static Optional<IItemHandlerModifiable> curios(Player player) {
        return CuriosApi.getCuriosInventory(player).map(h -> h.getEquippedCurios());
    }

    /** The stack at a place (EMPTY if there is none). */
    public static ItemStack get(Player player, Ref ref) {
        if (ref.curios()) {
            return curios(player).filter(h -> ref.index() >= 0 && ref.index() < h.getSlots()).map(h -> h.getStackInSlot(ref.index())).orElse(ItemStack.EMPTY);
        }
        Inventory inv = player.getInventory();
        if (ref.index() == OFFHAND) {
            return inv.offhand.get(0);
        }
        return ref.index() >= 0 && ref.index() < inv.items.size() ? inv.items.get(ref.index()) : ItemStack.EMPTY;
    }

    /** Writes the stack back to its place. */
    public static void set(Player player, Ref ref, ItemStack stack) {
        if (ref.curios()) {
            curios(player).filter(h -> ref.index() >= 0 && ref.index() < h.getSlots()).ifPresent(h -> h.setStackInSlot(ref.index(), stack));
            return;
        }
        Inventory inv = player.getInventory();
        if (ref.index() == OFFHAND) {
            inv.offhand.set(0, stack);
        } else if (ref.index() >= 0 && ref.index() < inv.items.size()) {
            inv.items.set(ref.index(), stack);
        }
        inv.setChanged();
    }

    /** The Satchel in force, or empty. */
    public static Optional<Found> find(Player player) {
        Optional<IItemHandlerModifiable> worn = curios(player);
        if (worn.isPresent()) {
            IItemHandlerModifiable h = worn.get();
            for (int i = 0; i < h.getSlots(); i++) {
                if (SatchelFilter.isSatchel(h.getStackInSlot(i))) {
                    return Optional.of(new Found(new Ref(true, i), h.getStackInSlot(i)));
                }
            }
        }
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            if (SatchelFilter.isSatchel(inv.items.get(i))) {
                return Optional.of(new Found(new Ref(false, i), inv.items.get(i)));
            }
        }
        if (SatchelFilter.isSatchel(inv.offhand.get(0))) {
            return Optional.of(new Found(new Ref(false, OFFHAND), inv.offhand.get(0)));
        }
        return Optional.empty();
    }

    /** The Satchel in force: its contents (empty contents if they have none). */
    public static Optional<SatchelContents> contentsOf(Player player) {
        return find(player).map(f -> Satchels.contents(f.stack()));
    }

    /** Reads, changes and writes back the Satchel in force. Returns whether there was one. */
    public static boolean update(Player player, UnaryOperator<SatchelContents> change) {
        Optional<Found> found = find(player);
        if (found.isEmpty()) {
            return false;
        }
        return updateAt(player, found.get().ref(), change);
    }

    /** Reads, changes and writes back the Satchel at a place. False if there is no Satchel there. */
    public static boolean updateAt(Player player, Ref ref, UnaryOperator<SatchelContents> change) {
        ItemStack copy = get(player, ref).copy();
        if (!SatchelFilter.isSatchel(copy)) {
            return false;
        }
        SatchelContents before = Satchels.contents(copy);
        SatchelContents after = change.apply(before);
        if (!after.equals(before)) {
            Satchels.setContents(copy, after);
            set(player, ref, copy);
        }
        return true;
    }
}
