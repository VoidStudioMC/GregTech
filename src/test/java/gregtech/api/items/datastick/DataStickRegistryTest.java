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
    }

    @Test
    void roundTripAndOverwriteUseOnlyTheActiveHandler() {
        ItemStack stack = new ItemStack(Items.STICK);
        FIRST.write(stack, "first payload");
        assertEquals("first payload", FIRST.peek(stack));
        assertSame(FIRST, DataStickRegistry.getHandler(stack));

        SECOND.write(stack, "second payload");
        assertNull(FIRST.peek(stack));
        assertEquals("second payload", SECOND.peek(stack));
        assertSame(SECOND, DataStickRegistry.getHandler(stack));
        assertEquals("second payload", new DataItemBehavior().getItemStackDisplayName(stack, "unused"));
        List<String> lines = new ArrayList<>();
        DataStickRegistry.addInformation(stack, lines);
        assertEquals(Collections.singletonList("Saved: second payload"), lines);
    }

    @Test
    void independentAreasKeepTheirDataAndTooltipsWhileOnlyTheLastUsedHandlerNamesTheItem() {
        ItemStack stack = new ItemStack(Items.STICK);
        FIRST.write(stack, "first");
        OTHER.write(stack, "other");
        NBTTagCompound otherPayload = stack.getTagCompound().getCompoundTag(OTHER.getStorageKey()).copy();
        assertEquals("first", FIRST.peek(stack));
        assertEquals("other", OTHER.peek(stack));
        assertSame(OTHER, DataStickRegistry.getHandler(stack));
        assertSame(FIRST, DataStickRegistry.getHandler(stack, DataStickHandler.DEFAULT_STORAGE_KEY));
        assertEquals("other", DataStickRegistry.getDisplayName(stack));
        List<String> lines = new ArrayList<>();
        DataStickRegistry.addInformation(stack, lines);
        assertEquals(Arrays.asList("Saved: other", "Saved: first"), lines);

        SECOND.write(stack, "second");
        assertNull(FIRST.peek(stack));
        assertEquals("other", OTHER.peek(stack));
        assertEquals(otherPayload, stack.getTagCompound().getCompoundTag(OTHER.getStorageKey()));
        assertSame(SECOND, DataStickRegistry.getHandler(stack));
        assertEquals("second", DataStickRegistry.getDisplayName(stack));
        lines.clear();
        DataStickRegistry.addInformation(stack, lines);
        assertEquals(Arrays.asList("Saved: second", "Saved: other"), lines);
    }

    @Test
    void lastUsedHandlerWithoutANameUsesTheItemDefaultInsteadOfAnotherHandlerName() {
        ItemStack stack = new ItemStack(Items.STICK);
        FIRST.write(stack, "custom name");
        UNNAMED.write(stack, "unnamed payload");
        assertEquals("custom name", FIRST.peek(stack));
        assertEquals("custom name", FIRST.read(stack));
        assertEquals("custom name", DataStickRegistry.getDisplayName(stack));
        assertEquals("unnamed payload", UNNAMED.read(stack));
        assertSame(UNNAMED, DataStickRegistry.getHandler(stack));
        assertNull(DataStickRegistry.getDisplayName(stack));
        assertEquals(LocalizationUtils.format("metaitem.tool.datastick.name"),
                new DataItemBehavior().getItemStackDisplayName(stack, "metaitem.tool.datastick.name"));
        List<String> lines = new ArrayList<>();
        DataStickRegistry.addInformation(stack, lines);
        assertEquals(Arrays.asList("Saved: unnamed payload", "Saved: custom name"), lines);
    }

    @Test
    void successfulReadsSelectTheHandlerWithoutOverwritingStoredData() {
        ItemStack stack = new ItemStack(Items.STICK);
        FIRST.write(stack, "first");
        OTHER.write(stack, "other");
        stack.setStackDisplayName("previous name");
        stack.setTranslatableName("previous.translation");
        stack.getOrCreateSubCompound("display").setInteger("color", 42);
        NBTTagCompound expected = stack.getTagCompound().copy();
        expected.getCompoundTag("display").removeTag("Name");
        expected.getCompoundTag("display").removeTag("LocName");
        expected.getCompoundTag(DataStickRegistry.ROOT_TAG)
                .setString(DataStickRegistry.ACTIVE_TAG, FIRST.getStorageKey());

        assertEquals("first", FIRST.read(stack));
        assertSame(FIRST, DataStickRegistry.getHandler(stack));
        assertEquals(expected, stack.getTagCompound());
        assertEquals("first", new DataItemBehavior().getItemStackDisplayName(stack, "unused"));
        assertEquals("other", OTHER.peek(stack));
        assertSame(OTHER, DataStickRegistry.getHandler(stack, OTHER.getStorageKey()));
        assertEquals(Arrays.asList(FIRST, OTHER), DataStickRegistry.getHandlers(stack));
        List<String> lines = new ArrayList<>();
        DataStickRegistry.addInformation(stack, lines);
        assertEquals(Arrays.asList("Saved: first", "Saved: other"), lines);
        assertEquals(expected, stack.getTagCompound());

        assertEquals("other", OTHER.read(stack));
        assertSame(OTHER, DataStickRegistry.getHandler(stack));
        assertEquals("other", DataStickRegistry.getDisplayName(stack));
        expected.getCompoundTag(DataStickRegistry.ROOT_TAG)
                .setString(DataStickRegistry.ACTIVE_TAG, OTHER.getStorageKey());
        assertEquals(expected, stack.getTagCompound());
    }

    @Test
    void failedReadsKeepTheActiveHandlerAndItemUnchanged() {
        ItemStack stack = new ItemStack(Items.STICK);
        FIRST.write(stack, "first");
        OTHER.write(stack, "other");
        stack.setStackDisplayName("keep this name");
        NBTTagCompound before = stack.getTagCompound().copy();
        assertNull(SECOND.read(stack));
        assertEquals(before, stack.getTagCompound());

        stack.getTagCompound().setString(FIRST.getStorageKey(), "invalid compound");
        before = stack.getTagCompound().copy();
        assertNull(FIRST.read(stack));
        assertEquals(before, stack.getTagCompound());
        stack.getTagCompound().setTag(FIRST.getStorageKey(), new NBTTagCompound());
        before = stack.getTagCompound().copy();
        assertNull(FIRST.read(stack));
        assertEquals(before, stack.getTagCompound());
        assertSame(OTHER, DataStickRegistry.getHandler(stack));
        assertEquals("other", DataStickRegistry.getDisplayName(stack));
    }

    @Test
    void payloadsAreIsolatedFromCallersAndCopies() {
        ItemStack stack = new ItemStack(Items.STICK);
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("value", "original");
        RAW.write(stack, payload);
        payload.setString("value", "changed input");
        RAW.peek(stack).setString("value", "changed output");
        ItemStack copy = stack.copy();
        RAW.write(copy, payload);
        assertEquals("original", RAW.peek(stack).getString("value"));
        assertEquals("changed input", RAW.peek(copy).getString("value"));
        assertNull(DataStickRegistry.getDisplayName(stack));
    }

    @Test
    void writesClearPreviousNamesAndPreserveUnrelatedMetadata() {
        ItemStack stack = new ItemStack(Items.STICK);
        NBTTagCompound root = new NBTTagCompound();
        root.setString("unrelated", "keep me");
        NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", "old name");
        display.setString("LocName", "old.translation");
        display.setInteger("color", 42);
        root.setTag("display", display);
        stack.setTagCompound(root);
        SECOND.write(stack, "new");
        assertFalse(stack.getTagCompound().getCompoundTag("display").hasKey("Name"));
        assertFalse(stack.getTagCompound().getCompoundTag("display").hasKey("LocName"));
        assertEquals(42, stack.getTagCompound().getCompoundTag("display").getInteger("color"));
        assertEquals("keep me", stack.getTagCompound().getString("unrelated"));
    }

    @Test
    void unknownOrMalformedHandlerDataIsNotRead() {
        ItemStack stack = new ItemStack(Items.STICK);
        FIRST.write(stack, "payload");
        NBTTagCompound root = stack.getTagCompound();
        NBTTagCompound metadata = root.getCompoundTag(DataStickRegistry.ROOT_TAG);
        NBTTagCompound handlers = metadata.getCompoundTag(DataStickRegistry.HANDLERS_TAG);
        handlers.setString(DataStickHandler.DEFAULT_STORAGE_KEY, "missing:handler");

        assertNull(FIRST.peek(stack));
        assertNull(DataStickRegistry.getHandler(stack));
        assertNull(DataStickRegistry.getDisplayName(stack));
        List<String> lines = new ArrayList<>();
        DataStickRegistry.addInformation(stack, lines);
        assertTrue(lines.isEmpty());
        assertEquals(LocalizationUtils.format("metaitem.tool.datastick.name"),
                new DataItemBehavior().getItemStackDisplayName(stack, "metaitem.tool.datastick.name"));

        root.setString(DataStickRegistry.ROOT_TAG, "invalid compound");
        assertNull(FIRST.peek(stack));
        handlers.setString(DataStickHandler.DEFAULT_STORAGE_KEY, "test:first");
        root.setTag(DataStickRegistry.ROOT_TAG, metadata);
        root.setString(DataStickHandler.DEFAULT_STORAGE_KEY, "invalid payload");
        assertNull(FIRST.peek(stack));
        root.setTag(DataStickHandler.DEFAULT_STORAGE_KEY, new NBTTagCompound());
        assertNull(FIRST.peek(stack));
        assertNull(DataStickRegistry.getDisplayName(stack));
        root.setTag(DataStickHandler.DEFAULT_STORAGE_KEY, FIRST.serializeNBT("payload"));
        metadata.removeTag(DataStickRegistry.HANDLERS_TAG);
        assertNull(FIRST.peek(stack));
        assertTrue(DataStickRegistry.getHandlers(stack).isEmpty());
    }

    @Test
    void missingActiveAreaDoesNotSelectAnotherHandlerForTheName() {
        ItemStack stack = new ItemStack(Items.STICK);
        FIRST.write(stack, "first");
        OTHER.write(stack, "other");
        stack.getTagCompound().getCompoundTag(DataStickRegistry.ROOT_TAG).removeTag(DataStickRegistry.ACTIVE_TAG);
        assertNull(DataStickRegistry.getHandler(stack));
        assertNull(DataStickRegistry.getDisplayName(stack));
        assertEquals("first", FIRST.peek(stack));
        assertEquals("other", OTHER.peek(stack));
    }

    @Test
    void unknownOrMisplacedHandlerOnlyDisablesItsOwnArea() {
        ItemStack stack = new ItemStack(Items.STICK);
        FIRST.write(stack, "first");
        OTHER.write(stack, "other");
        NBTTagCompound root = stack.getTagCompound();
        NBTTagCompound metadata = root.getCompoundTag(DataStickRegistry.ROOT_TAG);
        metadata.setString(DataStickRegistry.ACTIVE_TAG, DataStickHandler.DEFAULT_STORAGE_KEY);
        for (String invalidId : Arrays.asList("missing:handler", "test:other")) {
            metadata.getCompoundTag(DataStickRegistry.HANDLERS_TAG)
                    .setString(DataStickHandler.DEFAULT_STORAGE_KEY, invalidId);
            NBTTagCompound before = root.copy();
            assertNull(FIRST.peek(stack));
            assertNull(DataStickRegistry.getHandler(stack));
            assertNull(DataStickRegistry.getDisplayName(stack));
            assertEquals("other", OTHER.peek(stack));
            List<String> lines = new ArrayList<>();
            DataStickRegistry.addInformation(stack, lines);
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
        assertEquals("keep me", FIRST.peek(stack));
    }

    @Test
    void storageAreasCannotTakeOverMetadata() {
        for (String key : Arrays.asList("", DataStickRegistry.ROOT_TAG, "display")) {
            DataStickHandler<String> handler = new TextHandler() {

                @Override
                public @NotNull String getStorageKey() {
                    return key;
                }
            };
            assertThrows(IllegalArgumentException.class,
                    () -> DataStickRegistry.register("test:invalid_area", handler));
        }
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
        assertNull(FIRST.read(ItemStack.EMPTY));
        assertNull(FIRST.peek(new ItemStack(Items.STICK)));
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
