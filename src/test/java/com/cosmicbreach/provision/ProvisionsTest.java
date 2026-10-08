package com.cosmicbreach.provision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** The provisions' numbers: food like its vanilla cousins, pickaxes at iron's and diamond's tiers, the cap spreading only in the dark. */
class ProvisionsTest {
    static void sameAs(FoodProperties vanilla, FoodProperties ours, String what) {
        assertEquals(vanilla.nutrition(), ours.nutrition(), what);
        assertEquals(vanilla.saturation(), ours.saturation(), 1e-6, what);
    }

    @Test
    void searedFoodFeedsLikeItsVanillaCousin() {
        sameAs(Foods.COOKED_BEEF, ProvisionRegistry.SEARED_VENISON_FOOD, "seared venison is steak");
        sameAs(Foods.COOKED_SALMON, ProvisionRegistry.SEARED_FILLET_FOOD, "a seared fillet is cooked salmon");
        sameAs(Foods.BREAD, ProvisionRegistry.UMBRAL_CAP_FOOD, "an Umbral Cap is bread");
        assertEquals(2, ProvisionRegistry.HALO_BERRIES_FOOD.nutrition());
        assertTrue(ProvisionRegistry.HALO_BERRIES_FOOD.eatSeconds() < 1.6f, "berries are eaten fast");
        assertTrue(ProvisionRegistry.LUMEN_VENISON_FOOD.nutrition() < ProvisionRegistry.SEARED_VENISON_FOOD.nutrition());
        assertTrue(ProvisionRegistry.MANTA_FILLET_FOOD.nutrition() < ProvisionRegistry.SEARED_FILLET_FOOD.nutrition());
    }

    /**
     * The model in {@link ReachabilityTest} trusts the {@code c:foods} tag, so an item registered without its food would still
     * count as food there. Here the registered items themselves carry the numbers above, and the foods checked are the ones the
     * registry holds: a seventh food registered later, or an item here that stopped being one, fails this test (A2 spec review,
     * Minor 2).
     */
    @Test
    void everyRegisteredFoodCarriesItsNumbers() {
        Map<Item, FoodProperties> foods = Map.of(
                ProvisionRegistry.HALO_BERRIES.get(), ProvisionRegistry.HALO_BERRIES_FOOD,
                ProvisionRegistry.LUMEN_VENISON.get(), ProvisionRegistry.LUMEN_VENISON_FOOD,
                ProvisionRegistry.SEARED_LUMEN_VENISON.get(), ProvisionRegistry.SEARED_VENISON_FOOD,
                ProvisionRegistry.MANTA_FILLET.get(), ProvisionRegistry.MANTA_FILLET_FOOD,
                ProvisionRegistry.SEARED_MANTA_FILLET.get(), ProvisionRegistry.SEARED_FILLET_FOOD,
                ProvisionRegistry.UMBRAL_CAP_ITEM.get(), ProvisionRegistry.UMBRAL_CAP_FOOD);
        Set<Item> registered = ProvisionRegistry.ITEMS.getEntries().stream()
                .map(holder -> (Item) holder.get())
                .filter(item -> item.components().has(DataComponents.FOOD))
                .collect(Collectors.toSet());
        assertEquals(registered, foods.keySet(), "the foods the provisions register are not the foods this check lists");
        foods.forEach((item, food) -> assertEquals(food, item.components().get(DataComponents.FOOD), item + " is registered without its food"));
    }

    @Test
    void thePickaxesMineWhatIronAndDiamondMine() {
        assertEquals(BlockTags.INCORRECT_FOR_IRON_TOOL, ProvisionRegistry.STARSTEEL_TIER.getIncorrectBlocksForDrops());
        assertEquals(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, ProvisionRegistry.NEBULITE_TIER.getIncorrectBlocksForDrops());
    }

    /**
     * Existing worlds' level 3 is fed by lichen, which does not regrow, and by caps, which spread: the spreading runs in
     * {@code randomTick}, which the game calls only for a block registered to tick at random.
     */
    @Test
    void theCapTicksAtRandomSoItCanSpread() {
        assertTrue(ProvisionRegistry.UMBRAL_CAP.get().defaultBlockState().isRandomlyTicking(), "the cap is not registered to tick at random");
    }

    /**
     * A food that is also a block item eats only when it cannot be planted (vanilla's BlockItem.useOn places first), and nearly
     * every floor of the Deep is cap soil: a hungry player who right clicks toward the floor mid fight would plant the cap and
     * heal nothing (A1 Quality Review, Minor 4). A hungry player standing eats, whatever the cursor is on; sneaking plants; a
     * player who is not hungry plants.
     */
    @Test
    void aHungryPlayerEatsTheCapAndSneakingOrBeingFullPlantsIt() {
        assertTrue(UmbralCapItem.eatsFirst(false, true), "hungry, standing: eat, whatever the cursor is on");
        assertFalse(UmbralCapItem.eatsFirst(true, true), "hungry but sneaking: plant");
        assertFalse(UmbralCapItem.eatsFirst(false, false), "not hungry: plant");
        assertFalse(UmbralCapItem.eatsFirst(true, false), "not hungry, sneaking: plant");
        assertInstanceOf(UmbralCapItem.class, ProvisionRegistry.UMBRAL_CAP_ITEM.get(), "the registered item makes the choice");
        assertInstanceOf(ItemNameBlockItem.class, ProvisionRegistry.UMBRAL_CAP_ITEM.get(), "and keeps the item's own name, not the block's");
    }

    /**
     * The cap's model is drawn up to a quarter block off centre (random offset), so its outline and the box a click lands on
     * must be moved by the same offset, or a placed cap shows an outline beside the drawing (the shape ignores the level for
     * this offset, so no level is needed).
     */
    @Test
    void theCapsOutlineFollowsItsRandomOffset() {
        BlockState cap = ProvisionRegistry.UMBRAL_CAP.get().defaultBlockState();
        int moved = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                BlockPos pos = new BlockPos(x, 64, z);
                Vec3 offset = cap.getOffset(null, pos);
                AABB box = cap.getShape(null, pos).bounds();
                assertEquals(0.5 + offset.x, box.getCenter().x, 1e-9, "outline centre x at " + pos);
                assertEquals(0.5 + offset.z, box.getCenter().z, 1e-9, "outline centre z at " + pos);
                assertEquals(0.5, box.getXsize(), 1e-9, "the cap's width at " + pos);
                moved += offset.x != 0.0 || offset.z != 0.0 ? 1 : 0;
            }
        }
        assertTrue(moved > 100, "most caps are offset, or this test proves nothing: " + moved + " of 256");
    }

    @Test
    void theCapSpreadsOnlyInTheDark() {
        assertTrue(UmbralCapBlock.spreadsAt(UmbralCapBlock.effectiveLight(15, 0, 0.4)), "under the Deep's share the sky gives 6");
        assertFalse(UmbralCapBlock.spreadsAt(UmbralCapBlock.effectiveLight(15, 0, 1.0)), "open sky above the Deep");
        assertFalse(UmbralCapBlock.spreadsAt(UmbralCapBlock.effectiveLight(0, 14, 0.4)), "a torch stops it");
        assertTrue(UmbralCapBlock.spreadsAt(UmbralCapBlock.effectiveLight(0, 9, 0.4)), "a lichen's glow does not");
    }
}
