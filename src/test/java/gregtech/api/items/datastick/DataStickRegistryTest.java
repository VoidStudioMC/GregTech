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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataStickRegistryTest {

    private static final DataStickHandler<String> FIRST = DataStickRegistry.register("test:first", new TextHandler());
    private static final DataStickHandler<String> SECOND = DataStickRegistry.register("test:second", new TextHandler());
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
