package gregtech.api.items.datastick;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Registry of the data formats supported by data sticks, data orbs and data modules. Item NBT and active-area
 * selection are handled by {@link DataStickStorage}.
 */
public final class DataStickRegistry implements Iterable<DataStickHandler<?>> {

    private final Map<ResourceLocation, DataStickHandler<?>> handlers = new LinkedHashMap<>();
    private final Map<DataStickHandler<?>, ResourceLocation> keys = new IdentityHashMap<>();

    /**
     * Register during mod initialization on both sides. Names are unique within their mod namespace, and each
     * handler instance may only be registered once. Keys are saved in NBT as {@code modid:name} and must remain
     * stable. Handlers are iterated in registration order and may share a storage key to replace each other's payload.
     */
    public void register(@NotNull ResourceLocation key, @NotNull DataStickHandler<?> handler) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(handler, "handler");
        if (key.getPath().trim().isEmpty() || handlers.containsKey(key) || keys.containsKey(handler)) {
            throw new IllegalArgumentException("Duplicate or empty data stick handler registration: " + key);
        }

        String storageKey = Objects.requireNonNull(handler.getStorageKey(), "storageKey");
        if (storageKey.trim().isEmpty() || DataStickStorage.ROOT_TAG.equals(storageKey) ||
                "display".equals(storageKey)) {
            throw new IllegalArgumentException("Invalid data stick storage key: " + storageKey);
        }

        handlers.put(key, handler);
        keys.put(handler, key);
    }

    @Nullable
    public DataStickHandler<?> getObject(@NotNull ResourceLocation key) {
        return handlers.get(key);
    }

    @Nullable
    public ResourceLocation getNameForObject(@NotNull DataStickHandler<?> handler) {
        return keys.get(handler);
    }

    @NotNull
    public Set<ResourceLocation> getKeys() {
        return Collections.unmodifiableSet(handlers.keySet());
    }

    @Override
    @NotNull
    public Iterator<DataStickHandler<?>> iterator() {
        return Collections.unmodifiableCollection(handlers.values()).iterator();
    }
}
