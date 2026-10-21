package gregtech.api.items.datastick;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class DataStickRegistryTest {

    private final DataStickRegistry registry = new DataStickRegistry();
    private final DataStickHandler<NBTTagCompound> handler = new CompoundHandler();
    private final ResourceLocation first = new ResourceLocation("test", "first");

    @Test
    void handlersCanBeLookedUpByNameAndId() {
        registry.register(0, first, handler);

        assertSame(handler, registry.getObject(new ResourceLocation("test:first")));
        assertSame(handler, registry.getObjectById(0));
        assertEquals(first, registry.getNameForObject(handler));
        assertEquals(0, registry.getIDForObject(handler));
    }

    @Test
    void duplicateRegistrationsLeaveEveryLookupUnchanged() {
        registry.register(0, first, handler);
        DataStickHandler<?> other = new CompoundHandler();
        ResourceLocation second = new ResourceLocation("test", "second");

        assertThrows(IllegalArgumentException.class, () -> registry.register(1, first, other));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(1, new ResourceLocation("test", "alias"), handler));
        assertThrows(IllegalArgumentException.class, () -> registry.register(0, second, other));

        assertEquals(1, registry.getKeys().size());
        assertSame(handler, registry.getObject(first));
        assertSame(handler, registry.getObjectById(0));
        assertNull(registry.getObject(second));
        assertNull(registry.getObjectById(1));
        assertNull(registry.getNameForObject(other));

        registry.register(1, second, other);
        assertSame(other, registry.getObject(second));
        assertSame(other, registry.getObjectById(1));
    }

    @Test
    void handlerNamesAreUniqueWithinEachModNamespace() {
        ResourceLocation addonFirst = new ResourceLocation("addon", "first");
        DataStickHandler<?> addonHandler = new CompoundHandler();
        registry.register(0, first, handler);
        registry.register(1, addonFirst, addonHandler);

        assertSame(handler, registry.getObject(first));
        assertSame(addonHandler, registry.getObject(addonFirst));
        assertEquals(first, registry.getNameForObject(handler));
        assertEquals(addonFirst, registry.getNameForObject(addonHandler));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(2, new ResourceLocation("ADDON:FIRST"), new CompoundHandler()));
        assertEquals(2, registry.getKeys().size());
    }

    @Test
    void invalidIdsAndEmptyNamesAreRejected() {
        for (int id : new int[] { -1, Short.MAX_VALUE }) {
            assertThrows(IndexOutOfBoundsException.class, () -> registry.register(id, first, handler));
        }
        for (String path : Arrays.asList("", " ")) {
            assertThrows(IllegalArgumentException.class,
                    () -> registry.register(0, new ResourceLocation("test", path), handler));
        }
        assertTrue(registry.getKeys().isEmpty());
        assertNull(registry.getNameForObject(handler));
    }

    @Test
    void storageAreasCannotTakeOverMetadata() {
        for (String key : Arrays.asList("", " ", DataStickStorage.ROOT_TAG, "display")) {
            DataStickHandler<?> invalid = new CompoundHandler() {

                @Override
                public @NotNull String getStorageKey() {
                    return key;
                }
            };
            assertThrows(IllegalArgumentException.class,
                    () -> registry.register(0, new ResourceLocation("test", "invalid_area"), invalid));
            assertTrue(registry.getKeys().isEmpty());
        }
    }

    private static class CompoundHandler extends DataStickHandler<NBTTagCompound> {

        @Override
        public @NotNull NBTTagCompound serializeNBT(@NotNull NBTTagCompound data) {
            return data;
        }

        @Override
        public NBTTagCompound deserializeNBT(@NotNull NBTTagCompound tag) {
            return tag;
        }
    }
}
