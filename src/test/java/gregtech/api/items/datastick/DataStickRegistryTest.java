package gregtech.api.items.datastick;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataStickRegistryTest {

    private final DataStickRegistry registry = new DataStickRegistry();
    private final DataStickHandler<NBTTagCompound> handler = new CompoundHandler();
    private final ResourceLocation first = new ResourceLocation("test", "first");

    @Test
    void handlersCanBeLookedUpByName() {
        registry.register(first, handler);

        assertSame(handler, registry.getObject(new ResourceLocation("test:first")));
        assertEquals(first, registry.getNameForObject(handler));
    }

    @Test
    void duplicateRegistrationsLeaveLookupsAndIterationOrderUnchanged() {
        registry.register(first, handler);
        DataStickHandler<?> other = new CompoundHandler();
        ResourceLocation second = new ResourceLocation("test", "second");

        assertThrows(IllegalArgumentException.class, () -> registry.register(first, other));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(new ResourceLocation("test", "alias"), handler));
        assertEquals(1, registry.getKeys().size());
        assertSame(handler, registry.getObject(first));
        assertNull(registry.getObject(second));
        assertNull(registry.getNameForObject(other));

        registry.register(second, other);
        assertSame(other, registry.getObject(second));
        List<DataStickHandler<?>> handlers = new ArrayList<>();
        registry.forEach(handlers::add);
        assertEquals(Arrays.asList(handler, other), handlers);
    }

    @Test
    void handlerNamesAreUniqueWithinEachModNamespace() {
        ResourceLocation addonFirst = new ResourceLocation("addon", "first");
        DataStickHandler<?> addonHandler = new CompoundHandler();
        registry.register(first, handler);
        registry.register(addonFirst, addonHandler);

        assertSame(handler, registry.getObject(first));
        assertSame(addonHandler, registry.getObject(addonFirst));
        assertEquals(first, registry.getNameForObject(handler));
        assertEquals(addonFirst, registry.getNameForObject(addonHandler));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(new ResourceLocation("ADDON:FIRST"), new CompoundHandler()));
        assertEquals(2, registry.getKeys().size());
    }

    @Test
    void handlerLookupUsesInstanceIdentityEvenWhenHandlersCompareEqual() {
        DataStickHandler<?> one = new EqualCompoundHandler();
        DataStickHandler<?> two = new EqualCompoundHandler();
        ResourceLocation second = new ResourceLocation("test", "second");
        registry.register(first, one);
        registry.register(second, two);

        assertSame(one, registry.getObject(first));
        assertSame(two, registry.getObject(second));
        assertEquals(first, registry.getNameForObject(one));
        assertEquals(second, registry.getNameForObject(two));
        assertNull(registry.getNameForObject(new EqualCompoundHandler()));
    }

    @Test
    void emptyNamesAreRejected() {
        for (String path : Arrays.asList("", " ")) {
            assertThrows(IllegalArgumentException.class,
                    () -> registry.register(new ResourceLocation("test", path), handler));
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
                    () -> registry.register(new ResourceLocation("test", "invalid_area"), invalid));
            assertTrue(registry.getKeys().isEmpty());
        }
    }

    @Test
    void registryViewsCannotRemoveHandlersOrBreakReverseLookup() {
        registry.register(first, handler);
        assertThrows(UnsupportedOperationException.class, () -> registry.getKeys().remove(first));
        Iterator<DataStickHandler<?>> iterator = registry.iterator();
        assertSame(handler, iterator.next());
        assertThrows(UnsupportedOperationException.class, iterator::remove);
        assertSame(handler, registry.getObject(first));
        assertEquals(first, registry.getNameForObject(handler));
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

    private static final class EqualCompoundHandler extends CompoundHandler {

        @Override
        public boolean equals(Object other) {
            return other instanceof EqualCompoundHandler;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }
}
