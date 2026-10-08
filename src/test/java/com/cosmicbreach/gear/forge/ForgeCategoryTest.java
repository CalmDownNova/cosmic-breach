package com.cosmicbreach.gear.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.VarInt;
import org.junit.jupiter.api.Test;

/** A Forge recipe's tab as data (1.1 design section 10): named in "category", Other when missing or unknown, written by name. */
class ForgeCategoryTest {
    private static ForgeCategory read(String json) {
        return ForgeCategory.FIELD.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    @Test
    void aRecipeNamesItsTab() {
        assertEquals(ForgeCategory.WEAPONS, read("{\"category\": \"weapons\"}"));
        assertEquals(ForgeCategory.ARMOR, read("{\"category\": \"armor\"}"));
        assertEquals(ForgeCategory.TOOLS, read("{\"category\": \"tools\"}"));
        assertEquals(ForgeCategory.MOUNT_GEAR, read("{\"category\": \"mount_gear\"}"));
        assertEquals(ForgeCategory.OTHER, read("{\"category\": \"other\"}"));
    }

    @Test
    void withoutOneOrWithAnUnknownOneItGoesToOther() {
        assertEquals(ForgeCategory.OTHER, read("{}"));
        assertEquals(ForgeCategory.OTHER, read("{\"category\": \"hats\"}"));
    }

    @Test
    void anUnknownNameIsReportedAndAMissingOrKnownOneIsNot() {
        // a recipe in the wrong tab does not point at its cause, so a name this version doesn't know is logged (as a
        // warning, like vanilla's recipe load errors); a missing field is the normal default and says nothing
        List<String> unknown = new ArrayList<>();
        assertEquals(ForgeCategory.OTHER, ForgeCategory.named("weapon", unknown::add));
        assertEquals(ForgeCategory.OTHER, ForgeCategory.named("mount-gear", unknown::add));
        assertEquals(ForgeCategory.WEAPONS, ForgeCategory.named("weapons", unknown::add));
        assertEquals(ForgeCategory.OTHER, ForgeCategory.named("other", unknown::add));
        assertEquals(List.of("weapon", "mount-gear"), unknown);
        assertEquals(ForgeCategory.OTHER, read("{}"), "a recipe without the field still loads, in Other");
    }

    @Test
    void anIdThisVersionDoesNotKnowReadsAsOtherNotWeapons() {
        ByteBuf buf = Unpooled.buffer();
        VarInt.write(buf, ForgeCategory.values().length + 4);
        assertEquals(ForgeCategory.OTHER, ForgeCategory.STREAM_CODEC.decode(buf));
        for (ForgeCategory category : ForgeCategory.values()) {
            ByteBuf wire = Unpooled.buffer();
            ForgeCategory.STREAM_CODEC.encode(wire, category);
            assertEquals(category, ForgeCategory.STREAM_CODEC.decode(wire), "every tab survives the wire");
        }
    }

    @Test
    void theCategoryIsAlwaysWrittenOutByName() {
        JsonElement json = ForgeCategory.FIELD.codec().encodeStart(JsonOps.INSTANCE, ForgeCategory.MOUNT_GEAR).getOrThrow();
        assertEquals("{\"category\":\"mount_gear\"}", json.toString());
        JsonElement other = ForgeCategory.FIELD.codec().encodeStart(JsonOps.INSTANCE, ForgeCategory.OTHER).getOrThrow();
        assertEquals("{\"category\":\"other\"}", other.toString(), "the default is written too, so every file names its tab");
    }

    @Test
    void namesAndLangKeys() {
        assertEquals(ForgeCategory.MOUNT_GEAR, ForgeCategory.byName("mount_gear"));
        assertNull(ForgeCategory.byName("hats"));
        assertEquals("gui.cosmicbreach.forge.tab.mount_gear", ForgeCategory.MOUNT_GEAR.translationKey());
    }
}
