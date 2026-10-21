package gregtech.api.items.datastick;

import gregtech.api.util.GTControlledRegistry;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

/**
 * Registry of the data formats supported by data sticks, data orbs and data modules. Item NBT and active-area
 * selection are handled by {@link DataStickStorage}.
 */
public final class DataStickRegistry extends GTControlledRegistry<ResourceLocation, DataStickHandler<?>> {

    public DataStickRegistry() {
        super(Short.MAX_VALUE);
    }

    /**
     * Register during mod initialization on both sides. Numeric IDs and handler instances must be unique across
     * all mods; IDs range from 0 to {@code Short.MAX_VALUE - 1}. Names are unique within their mod namespace.
     * Keys are saved in NBT as {@code modid:name} and must remain stable.
     * Numeric IDs determine iteration order. Handlers may share a storage key to replace each other's payload.
     */
    @Override
    public void register(int id, @NotNull ResourceLocation key, @NotNull DataStickHandler<?> handler) {
        if (key.getPath().trim().isEmpty() || containsKey(key) || getNameForObject(handler) != null) {
            throw new IllegalArgumentException("Duplicate or empty data stick handler registration: " + key);
        }
        // Check before the base registry changes its name-to-object mapping.
        if (getObjectById(id) != null) {
            throw new IllegalArgumentException("Duplicate data stick handler id: " + id);
        }

        String storageKey = handler.getStorageKey();
        if (storageKey.trim().isEmpty() || DataStickStorage.ROOT_TAG.equals(storageKey) ||
                "display".equals(storageKey)) {
            throw new IllegalArgumentException("Invalid data stick storage key: " + storageKey);
        }

        super.register(id, key, handler);
    }
}
