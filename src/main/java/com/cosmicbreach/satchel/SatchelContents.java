package com.cosmicbreach.satchel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * What a Satchel holds (1.2 design section 3), stored on the item as a data component. Immutable: every change returns a new
 * value, so a change is one read and one write of the item and nothing else can hold a half-updated copy.
 *
 * <p>The Materials tab is a virtual slot for each item type ({@link #materials}, at most {@link #CAP} of each) with a
 * per-type switch for pickup ({@link #pickupOff}, default on). The Gear tab is {@value #GEAR_SLOTS} normal slots
 * ({@link #gear}, empty stacks for empty slots). Moves are conserving by construction: {@link #deposit} reports how many
 * it took and {@link #withdraw} how many it gave, and the caller moves exactly that many.
 */
public record SatchelContents(Map<ResourceLocation, Integer> materials, Set<ResourceLocation> pickupOff, List<ItemStack> gear) {
    public static final int CAP = 4096;
    public static final int GEAR_SLOTS = 18;
    /** The most types whose pickup can be switched off: well above the number of tagged item types, far under any size limit. */
    public static final int MAX_PICKUP_OFF = 128;
    public static final SatchelContents EMPTY = new SatchelContents(Map.of(), Set.of(), emptyGear());

    /** An amount moved and the contents after it. */
    public record Move(SatchelContents contents, int moved) {
    }

    private record Entry(ResourceLocation item, int count) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(Entry::item),
                Codec.intRange(1, CAP).fieldOf("count").forGetter(Entry::count)).apply(i, Entry::new));
    }

    private record GearEntry(int slot, ItemStack stack) {
        static final Codec<GearEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, GEAR_SLOTS - 1).fieldOf("slot").forGetter(GearEntry::slot),
                ItemStack.CODEC.fieldOf("item").forGetter(GearEntry::stack)).apply(i, GearEntry::new));
    }

    public static final Codec<SatchelContents> CODEC = RecordCodecBuilder.create(i -> i.group(
            Entry.CODEC.listOf().optionalFieldOf("materials", List.of()).forGetter(SatchelContents::entries),
            ResourceLocation.CODEC.listOf().optionalFieldOf("pickup_off", List.of()).forGetter(c -> List.copyOf(c.pickupOff)),
            GearEntry.CODEC.listOf().optionalFieldOf("gear", List.of()).forGetter(SatchelContents::gearEntries))
            .apply(i, SatchelContents::fromParts));

    public static final StreamCodec<RegistryFriendlyByteBuf, SatchelContents> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public SatchelContents {
        TreeMap<ResourceLocation, Integer> sorted = new TreeMap<>();
        materials.forEach((id, n) -> {
            if (n > 0) {
                sorted.put(id, Math.min(n, CAP));
            }
        });
        materials = Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
        LinkedHashSet<ResourceLocation> off = new LinkedHashSet<>();
        for (ResourceLocation id : pickupOff) {
            if (off.size() < MAX_PICKUP_OFF) {
                off.add(id); // stored data past the cap (an edited file) is cut
            }
        }
        pickupOff = Collections.unmodifiableSet(off);
        List<ItemStack> g = new ArrayList<>(GEAR_SLOTS);
        for (int s = 0; s < GEAR_SLOTS; s++) {
            g.add(s < gear.size() ? gear.get(s).copy() : ItemStack.EMPTY);
        }
        gear = Collections.unmodifiableList(g);
    }

    private static List<ItemStack> emptyGear() {
        return Collections.nCopies(GEAR_SLOTS, ItemStack.EMPTY);
    }

    private static SatchelContents fromParts(List<Entry> entries, List<ResourceLocation> off, List<GearEntry> gear) {
        Map<ResourceLocation, Integer> m = new LinkedHashMap<>();
        entries.forEach(e -> m.merge(e.item, e.count, Integer::sum));
        List<ItemStack> g = new ArrayList<>(emptyGear());
        gear.forEach(e -> g.set(e.slot, e.stack));
        return new SatchelContents(m, new LinkedHashSet<>(off), g);
    }

    private List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        materials.forEach((id, n) -> out.add(new Entry(id, n)));
        return out;
    }

    private List<GearEntry> gearEntries() {
        List<GearEntry> out = new ArrayList<>();
        for (int s = 0; s < GEAR_SLOTS; s++) {
            if (!gear.get(s).isEmpty()) {
                out.add(new GearEntry(s, gear.get(s)));
            }
        }
        return out;
    }

    public int count(ResourceLocation item) {
        return materials.getOrDefault(item, 0);
    }

    /** Whether pickup is on for this item type (the default). */
    public boolean pickupOn(ResourceLocation item) {
        return !pickupOff.contains(item);
    }

    /** Room left for this type. */
    public int room(ResourceLocation item) {
        return CAP - count(item);
    }

    /** Puts up to {@code wanted} of an item type in: as many as fit under the cap. */
    public Move deposit(ResourceLocation item, int wanted) {
        int take = Math.max(0, Math.min(wanted, room(item)));
        if (take == 0) {
            return new Move(this, 0);
        }
        Map<ResourceLocation, Integer> m = new LinkedHashMap<>(materials);
        m.merge(item, take, Integer::sum);
        return new Move(new SatchelContents(m, pickupOff, gear), take);
    }

    /** Takes up to {@code wanted} of an item type out: as many as there are. */
    public Move withdraw(ResourceLocation item, int wanted) {
        int take = Math.max(0, Math.min(wanted, count(item)));
        if (take == 0) {
            return new Move(this, 0);
        }
        Map<ResourceLocation, Integer> m = new LinkedHashMap<>(materials);
        m.put(item, count(item) - take);
        return new Move(new SatchelContents(m, pickupOff, gear), take);
    }

    public SatchelContents togglePickup(ResourceLocation item) {
        Set<ResourceLocation> off = new LinkedHashSet<>(pickupOff);
        if (!off.remove(item)) {
            off.add(item);
        }
        return new SatchelContents(materials, off, gear);
    }

    public SatchelContents withGear(List<ItemStack> newGear) {
        return new SatchelContents(materials, pickupOff, newGear);
    }

    public SatchelContents withGear(int slot, ItemStack stack) {
        List<ItemStack> g = new ArrayList<>(gear);
        g.set(slot, stack);
        return withGear(g);
    }

    public SatchelContents withMaterials(Map<ResourceLocation, Integer> m) {
        return new SatchelContents(m, pickupOff, gear);
    }

    /** Every item the satchel holds: the sum of the materials and the gear stacks' counts. */
    public long totalItems() {
        long n = 0;
        for (int c : materials.values()) {
            n += c;
        }
        for (ItemStack s : gear) {
            n += s.getCount();
        }
        return n;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SatchelContents c && materials.equals(c.materials) && pickupOff.equals(c.pickupOff)
                && ItemStack.listMatches(gear, c.gear);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * materials.hashCode() + pickupOff.hashCode()) + ItemStack.hashStackList(gear);
    }
}
