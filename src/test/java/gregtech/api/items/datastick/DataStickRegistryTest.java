package gregtech.api.items.datastick;

import gregtech.Bootstrap;
import gregtech.api.util.LocalizationUtils;
import gregtech.common.items.behaviors.DataItemBehavior;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataStickRegistryTest {

    private static final DataStickHandler<String> FIRST = DataStickRegistry.register("test:first", new TextHandler());
    private static final DataStickHandler<String> SECOND = DataStickRegistry.register("test:second", new TextHandler());
    private static final DataStickHandler<String> OTHER = DataStickRegistry.register("test:other", new TextHandler() {

        @Override
        public @NotNull String getStorageKey() {
            return "test:other_area";
        }
    });
    private static final DataStickHandler<String> UNNAMED = DataStickRegistry.register("test:unnamed",
            new TextHandler() {

                @Override
                public @NotNull String getStorageKey() {
                    return "test:unnamed_area";
                }

                @Override
                public String getDisplayName(@NotNull String data) {
                    return null;
                }
            });
    private static final DataStickHandler<NBTTagCompound> RAW = DataStickRegistry.register("test:raw",
            new DataStickHandler<>() {

                @Override
                public @NotNull NBTTagCompound serializeNBT(@NotNull NBTTagCompound data) {
                    return data;
                }

                @Override
                public NBTTagCompound deserializeNBT(@NotNull NBTTagCompound tag) {
                    return tag;
                }
            });

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
        DataStickRegistry.registerLegacyTag("TestLegacyData", FIRST);
    }

    @Test
    void roundTripAndOverwriteUseOnlyTheActiveHandler() {
        ItemStack stack = new ItemStack(Items.STICK);
        FIRST.write(stack, "first payload");
        assertEquals("first payload", FIRST.read(stack));
        assertSame(FIRST, DataStickRegistry.getHandler(stack));

        SECOND.write(stack, "second payload");
        assertNull(FIRST.read(stack));
        assertEquals("second payload", SECOND.read(stack));
        assertSame(SECOND, DataStickRegistry.getHandler(stack));
        assertEquals("second payload", new DataItemBehavior().getItemStackDisplayName(stack, "unused"));
        List<String> lines = new ArrayList<>();
        new DataItemBehavior().addInformation(stack, lines);
        assertEquals(Collections.singletonList("Saved: second payload"), lines);
    }

    @Test
    void independentAreasKeepTheirDataAndTooltipsWhileOnlyTheLastWriterNamesTheItem() {
        ItemStack stack = new ItemStack(Items.STICK);
        FIRST.write(stack, "first");
        OTHER.write(stack, "other");
        NBTTagCompound otherPayload = stack.getTagCompound().getCompoundTag(OTHER.getStorageKey()).copy();
        assertEquals("first", FIRST.read(stack));
        assertEquals("other", OTHER.read(stack));
        assertSame(OTHER, DataStickRegistry.getHandler(stack));
        assertSame(FIRST, DataStickRegistry.getHandler(stack, DataStickRegistry.DEFAULT_STORAGE_KEY));
        assertEquals("other", DataStickRegistry.getDisplayName(stack));
        List<String> lines = new ArrayList<>();
        new DataItemBehavior().addInformation(stack, lines);
        assertEquals(Arrays.asList("Saved: other", "Saved: first"), lines);

        SECOND.write(stack, "second");
        assertNull(FIRST.read(stack));
        assertEquals("other", OTHER.read(stack));
        assertEquals(otherPayload, stack.getTagCompound().getCompoundTag(OTHER.getStorageKey()));
        assertSame(SECOND, DataStickRegistry.getHandler(stack));
        assertEquals("second", DataStickRegistry.getDisplayName(stack));
        lines.clear();
        new DataItemBehavior().addInformation(stack, lines);
        assertEquals(Arrays.asList("Saved: second", "Saved: other"), lines);
    }

    @Test
    void lastWriterWithoutANameUsesTheItemDefaultInsteadOfAnotherHandlerName() {
        ItemStack stack = new ItemStack(Items.STICK);
        FIRST.write(stack, "custom name");
        UNNAMED.write(stack, "unnamed payload");
        assertEquals("custom name", FIRST.read(stack));
        assertSame(UNNAMED, DataStickRegistry.getHandler(stack));
        assertNull(DataStickRegistry.getDisplayName(stack));
        assertEquals(LocalizationUtils.format("metaitem.tool.datastick.name"),
                new DataItemBehavior().getItemStackDisplayName(stack, "metaitem.tool.datastick.name"));
        List<String> lines = new ArrayList<>();
        new DataItemBehavior().addInformation(stack, lines);
        assertEquals(Arrays.asList("Saved: unnamed payload", "Saved: custom name"), lines);
    }

    @Test
    void oldSinglePayloadEnvelopeSurvivesWritesToAnotherArea() {
        ItemStack stack = new ItemStack(Items.STICK);
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString(DataStickRegistry.HANDLER_TAG, "test:first");
        entry.setTag(DataStickRegistry.DATA_TAG, FIRST.serializeNBT("old envelope"));
        root.setTag(DataStickRegistry.ROOT_TAG, entry);
        stack.setTagCompound(root);
        NBTTagCompound before = root.copy();
        assertEquals("old envelope", FIRST.read(stack));
        assertEquals(before, stack.getTagCompound());

        OTHER.write(stack, "separate");
        assertEquals("old envelope", FIRST.read(stack));
        assertEquals("separate", OTHER.read(stack));
        assertEquals("separate", DataStickRegistry.getDisplayName(stack));
        SECOND.write(stack, "new envelope");
        assertNull(FIRST.read(stack));
        assertEquals("separate", OTHER.read(stack));
        assertEquals("new envelope", SECOND.read(stack));
        assertFalse(stack.getTagCompound().getCompoundTag(DataStickRegistry.ROOT_TAG)
                .hasKey(DataStickRegistry.HANDLER_TAG));
    }

    @Test
    void payloadsAreIsolatedFromCallersAndCopies() {
        ItemStack stack = new ItemStack(Items.STICK);
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("value", "original");
        RAW.write(stack, payload);
        payload.setString("value", "changed input");
        RAW.read(stack).setString("value", "changed output");
        ItemStack copy = stack.copy();
        RAW.write(copy, payload);
        assertEquals("original", RAW.read(stack).getString("value"));
        assertEquals("changed input", RAW.read(copy).getString("value"));
        assertNull(DataStickRegistry.getDisplayName(stack));
    }

    @Test
    void legacyDataIsReadableWithoutMutatingTheItemAndIsRemovedOnWrite() {
        ItemStack stack = new ItemStack(Items.STICK);
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("TestLegacyData", FIRST.serializeNBT("legacy"));
        root.setString("unrelated", "keep me");
        NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", "old name");
        display.setString("LocName", "old.translation");
        display.setInteger("color", 42);
        root.setTag("display", display);
        stack.setTagCompound(root);
        NBTTagCompound before = root.copy();

        assertEquals("legacy", FIRST.read(stack));
        assertEquals(before, stack.getTagCompound());
        SECOND.write(stack, "new");
        assertFalse(stack.getTagCompound().hasKey("TestLegacyData"));
        assertFalse(stack.getTagCompound().getCompoundTag("display").hasKey("Name"));
        assertFalse(stack.getTagCompound().getCompoundTag("display").hasKey("LocName"));
        assertEquals(42, stack.getTagCompound().getCompoundTag("display").getInteger("color"));
        assertEquals("keep me", stack.getTagCompound().getString("unrelated"));
    }

    @Test
    void unknownOrMalformedHandlerNeverFallsBackToLegacyData() {
        ItemStack stack = new ItemStack(Items.STICK);
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("TestLegacyData", FIRST.serializeNBT("legacy"));
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString(DataStickRegistry.HANDLER_TAG, "missing:handler");
        entry.setTag(DataStickRegistry.DATA_TAG, FIRST.serializeNBT("unknown"));
        root.setTag(DataStickRegistry.ROOT_TAG, entry);
        stack.setTagCompound(root);

        assertNull(FIRST.read(stack));
        assertNull(DataStickRegistry.getHandler(stack));
        assertNull(DataStickRegistry.getDisplayName(stack));
        List<String> lines = new ArrayList<>();
        new DataItemBehavior().addInformation(stack, lines);
        assertTrue(lines.isEmpty());
        assertEquals(LocalizationUtils.format("metaitem.tool.datastick.name"),
                new DataItemBehavior().getItemStackDisplayName(stack, "metaitem.tool.datastick.name"));

        root.setString(DataStickRegistry.ROOT_TAG, "invalid compound");
        assertNull(FIRST.read(stack));
        entry.setString(DataStickRegistry.HANDLER_TAG, "test:first");
        entry.setString(DataStickRegistry.DATA_TAG, "invalid payload");
        root.setTag(DataStickRegistry.ROOT_TAG, entry);
        assertNull(FIRST.read(stack));
        entry.setTag(DataStickRegistry.DATA_TAG, new NBTTagCompound());
        assertNull(FIRST.read(stack));
        assertNull(DataStickRegistry.getDisplayName(stack));
    }

    @Test
    void unknownOrMisplacedHandlerOnlyDisablesItsOwnArea() {
        ItemStack stack = new ItemStack(Items.STICK);
        FIRST.write(stack, "first");
        OTHER.write(stack, "other");
        NBTTagCompound root = stack.getTagCompound();
        root.setTag("TestLegacyData", FIRST.serializeNBT("stale"));
        NBTTagCompound metadata = root.getCompoundTag(DataStickRegistry.ROOT_TAG);
        metadata.setString(DataStickRegistry.ACTIVE_TAG, DataStickRegistry.DEFAULT_STORAGE_KEY);
        for (String invalidId : Arrays.asList("missing:handler", "test:other")) {
            metadata.getCompoundTag(DataStickRegistry.HANDLERS_TAG)
                    .setString(DataStickRegistry.DEFAULT_STORAGE_KEY, invalidId);
            NBTTagCompound before = root.copy();
            assertNull(FIRST.read(stack));
            assertNull(DataStickRegistry.getHandler(stack));
            assertNull(DataStickRegistry.getDisplayName(stack));
            assertEquals("other", OTHER.read(stack));
            List<String> lines = new ArrayList<>();
            new DataItemBehavior().addInformation(stack, lines);
            assertEquals(Collections.singletonList("Saved: other"), lines);
            assertEquals(before, stack.getTagCompound());
        }
    }

    @Test
    void duplicateRegistrationAndUnregisteredWritesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> DataStickRegistry.register("test:first", new TextHandler()));
        assertThrows(IllegalArgumentException.class, () -> DataStickRegistry.register("test:alias", FIRST));
        assertThrows(IllegalArgumentException.class, () -> DataStickRegistry.register(" ", new TextHandler()));
        ItemStack stack = new ItemStack(Items.STICK);
        FIRST.write(stack, "keep me");
        assertThrows(IllegalArgumentException.class, () -> new TextHandler().write(stack, "invalid"));
        assertEquals("keep me", FIRST.read(stack));
    }

    @Test
    void storageAreasCannotTakeOverMetadataOrAnotherAreasLegacyTag() {
        for (String key : Arrays.asList("", DataStickRegistry.ROOT_TAG, "display", "TestLegacyData")) {
            DataStickHandler<String> handler = new TextHandler() {

                @Override
                public @NotNull String getStorageKey() {
                    return key;
                }
            };
            assertThrows(IllegalArgumentException.class,
                    () -> DataStickRegistry.register("test:invalid_area", handler));
        }
        assertThrows(IllegalArgumentException.class,
                () -> DataStickRegistry.registerLegacyTag(OTHER.getStorageKey(), FIRST));
    }

    @Test
    void failedSerializationKeepsEveryAreaAndTheActiveNameUnchanged() {
        DataStickHandler<String> failing = DataStickRegistry.register("test:failing", new TextHandler() {

            @Override
            public @NotNull NBTTagCompound serializeNBT(@NotNull String data) {
                throw new IllegalArgumentException("Cannot encode data");
            }
        });
        ItemStack stack = new ItemStack(Items.STICK);
        FIRST.write(stack, "first");
        OTHER.write(stack, "other");
        stack.setStackDisplayName("keep this name");
        NBTTagCompound before = stack.getTagCompound().copy();
        assertThrows(IllegalArgumentException.class, () -> failing.write(stack, "invalid"));
        assertEquals(before, stack.getTagCompound());
        assertThrows(IllegalArgumentException.class, () -> failing.write(stack.getTagCompound(), "invalid"));
        assertEquals(before, stack.getTagCompound());
    }

    @Test
    void emptyItemsHaveNoHandler() {
        assertNull(DataStickRegistry.getHandler(ItemStack.EMPTY));
        assertNull(FIRST.read(new ItemStack(Items.STICK)));
    }

    private static class TextHandler extends DataStickHandler<String> {

        @Override
        public @NotNull NBTTagCompound serializeNBT(@NotNull String data) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("text", data);
            return tag;
        }

        @Override
        public @Nullable String deserializeNBT(@NotNull NBTTagCompound tag) {
            return tag.hasKey("text", Constants.NBT.TAG_STRING) ? tag.getString("text") : null;
        }

        @Override
        public String getDisplayName(@NotNull String data) {
            return data;
        }

        @Override
        public void addInformation(@NotNull String data, @NotNull List<String> lines) {
            lines.add("Saved: " + data);
        }
    }
}
