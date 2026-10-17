package gregtech.api.items.datastick;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Registry and NBT storage shared by data sticks, data orbs and data modules. */
public final class DataStickRegistry {

    public static final String ROOT_TAG = "GT.DataStick";
    public static final String DEFAULT_STORAGE_KEY = "handler";
    public static final String HANDLERS_TAG = "Handlers";
    public static final String ACTIVE_TAG = "Active";

    // The original single-payload envelope remains readable until its storage area is overwritten.
    public static final String HANDLER_TAG = "Handler";
    public static final String DATA_TAG = "Data";

    private static final Map<String, DataStickHandler<?>> HANDLERS = new LinkedHashMap<>();
    private static final Map<DataStickHandler<?>, Registration> REGISTRATIONS = new IdentityHashMap<>();
    private static final Map<String, DataStickHandler<?>> LEGACY_TAGS = new LinkedHashMap<>();

    private DataStickRegistry() {}

    /** Register during mod initialization on both sides. IDs must be stable; prefer "modid:name". */
    @NotNull
    public static <T> DataStickHandler<T> register(@NotNull String id, @NotNull DataStickHandler<T> handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        if (id.trim().isEmpty() || HANDLERS.containsKey(id) || REGISTRATIONS.containsKey(handler)) {
            throw new IllegalArgumentException("Duplicate or empty data stick handler registration: " + id);
        }
        String storageKey = Objects.requireNonNull(handler.getStorageKey(), "storageKey");
        DataStickHandler<?> legacy = LEGACY_TAGS.get(storageKey);
        if (storageKey.trim().isEmpty() || ROOT_TAG.equals(storageKey) || "display".equals(storageKey) ||
                legacy != null && !storageKey.equals(REGISTRATIONS.get(legacy).storageKey)) {
            throw new IllegalArgumentException("Invalid data stick storage key: " + storageKey);
        }
        HANDLERS.put(id, handler);
        REGISTRATIONS.put(handler, new Registration(id, storageKey));
        return handler;
    }

    /**
     * Accept a former top-level compound when this handler's storage area has no explicit handler ID. Legacy tags
     * are checked in registration order and removed only when their area is written. Reading does not mutate items.
     */
    public static void registerLegacyTag(@NotNull String tag, @NotNull DataStickHandler<?> handler) {
        Objects.requireNonNull(tag, "tag");
        Registration registration = REGISTRATIONS.get(handler);
        if (tag.isEmpty() || ROOT_TAG.equals(tag) || "display".equals(tag) || LEGACY_TAGS.containsKey(tag) ||
                registration == null) {
            throw new IllegalArgumentException("Invalid legacy data stick tag registration: " + tag);
        }
        for (Registration other : REGISTRATIONS.values()) {
            if (tag.equals(other.storageKey) && !tag.equals(registration.storageKey)) {
                throw new IllegalArgumentException("Legacy tag conflicts with a data stick storage area: " + tag);
            }
        }
        LEGACY_TAGS.put(tag, handler);
    }

    /** The last written handler, or the first available handler for old items without an active-area marker. */
    @Nullable
    public static DataStickHandler<?> getHandler(@NotNull ItemStack stack) {
        NBTTagCompound root = stack.getTagCompound();
        if (root == null) return null;
        NBTTagCompound metadata = root.getCompoundTag(ROOT_TAG);
        if (metadata.hasKey(ACTIVE_TAG)) return getHandler(root, metadata.getString(ACTIVE_TAG));
        if (hasLegacyEntry(root)) return getHandler(root, getLegacyStorageKey(metadata));
        for (String storageKey : getStorageKeys(root)) {
            DataStickHandler<?> handler = getHandler(root, storageKey);
            if (handler != null) return handler;
        }
        return null;
    }

    /** Look up an area's handler independently of which handler supplies the item's name. */
    @Nullable
    public static DataStickHandler<?> getHandler(@NotNull ItemStack stack, @NotNull String storageKey) {
        return getHandler(stack.getTagCompound(), storageKey);
    }

    /** Present handlers, active area first, followed by the default area and remaining areas in registration order. */
    @NotNull
    public static List<DataStickHandler<?>> getHandlers(@NotNull ItemStack stack) {
        NBTTagCompound root = stack.getTagCompound();
        if (root == null) return Collections.emptyList();
        List<DataStickHandler<?>> handlers = new ArrayList<>();
        for (String storageKey : getStorageKeys(root)) {
            DataStickHandler<?> handler = getHandler(root, storageKey);
            if (handler != null) handlers.add(handler);
        }
        return Collections.unmodifiableList(handlers);
    }

    @Nullable
    private static DataStickHandler<?> getHandler(@Nullable NBTTagCompound root, String storageKey) {
        if (root == null) return null;
        NBTTagCompound metadata = root.getCompoundTag(ROOT_TAG);
        if (metadata.hasKey(HANDLERS_TAG) && !metadata.hasKey(HANDLERS_TAG, Constants.NBT.TAG_COMPOUND)) return null;
        NBTTagCompound handlers = metadata.getCompoundTag(HANDLERS_TAG);
        // An explicit ID suppresses legacy fallback only in its own area, including unknown or malformed entries.
        if (handlers.hasKey(storageKey)) {
            return root.hasKey(storageKey, Constants.NBT.TAG_COMPOUND) ?
                    getRegisteredHandler(handlers.getString(storageKey), storageKey) : null;
        }
        if (hasLegacyEntry(root) && storageKey.equals(getLegacyStorageKey(metadata))) {
            return metadata.hasKey(DATA_TAG, Constants.NBT.TAG_COMPOUND) ?
                    getRegisteredHandler(metadata.getString(HANDLER_TAG), storageKey) : null;
        }
        for (Map.Entry<String, DataStickHandler<?>> legacy : LEGACY_TAGS.entrySet()) {
            if (storageKey.equals(REGISTRATIONS.get(legacy.getValue()).storageKey) &&
                    root.hasKey(legacy.getKey(), Constants.NBT.TAG_COMPOUND))
                return legacy.getValue();
        }
        return null;
    }

    @Nullable
    private static DataStickHandler<?> getRegisteredHandler(String id, String storageKey) {
        DataStickHandler<?> handler = HANDLERS.get(id);
        return handler != null && storageKey.equals(REGISTRATIONS.get(handler).storageKey) ? handler : null;
    }

    private static Set<String> getStorageKeys(NBTTagCompound root) {
        Set<String> keys = new LinkedHashSet<>();
        NBTTagCompound metadata = root.getCompoundTag(ROOT_TAG);
        if (metadata.hasKey(ACTIVE_TAG, Constants.NBT.TAG_STRING)) keys.add(metadata.getString(ACTIVE_TAG));
        else if (hasLegacyEntry(root)) keys.add(getLegacyStorageKey(metadata));
        keys.add(DEFAULT_STORAGE_KEY);
        for (DataStickHandler<?> handler : HANDLERS.values()) {
            keys.add(REGISTRATIONS.get(handler).storageKey);
        }
        return keys;
    }

    private static boolean hasLegacyEntry(NBTTagCompound root) {
        if (!root.hasKey(ROOT_TAG)) return false;
        NBTTagCompound metadata = root.getCompoundTag(ROOT_TAG);
        return metadata.hasKey(HANDLER_TAG) || metadata.hasKey(DATA_TAG) ||
                !metadata.hasKey(HANDLERS_TAG) && !metadata.hasKey(ACTIVE_TAG);
    }

    private static String getLegacyStorageKey(NBTTagCompound metadata) {
        DataStickHandler<?> handler = HANDLERS.get(metadata.getString(HANDLER_TAG));
        return handler == null ? DEFAULT_STORAGE_KEY : REGISTRATIONS.get(handler).storageKey;
    }

    static <T> void write(NBTTagCompound root, DataStickHandler<T> handler, T data) {
        Registration registration = REGISTRATIONS.get(handler);
        if (registration == null) throw new IllegalArgumentException("Data stick handler is not registered");
        // Serialize before changing the item, so a failed write keeps its original data.
        NBTTagCompound payload = Objects.requireNonNull(handler.serializeNBT(Objects.requireNonNull(data)), "payload")
                .copy();
        NBTTagCompound metadata = root.getCompoundTag(ROOT_TAG).copy();
        if (hasLegacyEntry(root)) {
            if (registration.storageKey.equals(getLegacyStorageKey(metadata))) {
                metadata.removeTag(HANDLER_TAG);
                metadata.removeTag(DATA_TAG);
            } else if (!metadata.hasKey(HANDLER_TAG) && !metadata.hasKey(DATA_TAG)) {
                // Keep a malformed old default entry from reactivating stale legacy data in another area.
                metadata.setString(HANDLER_TAG, "");
            }
        }
        NBTTagCompound handlers = metadata.getCompoundTag(HANDLERS_TAG);
        handlers.setString(registration.storageKey, registration.id);
        metadata.setTag(HANDLERS_TAG, handlers);
        metadata.setString(ACTIVE_TAG, registration.storageKey);
        for (Map.Entry<String, DataStickHandler<?>> legacy : LEGACY_TAGS.entrySet()) {
            if (registration.storageKey.equals(REGISTRATIONS.get(legacy.getValue()).storageKey)) {
                root.removeTag(legacy.getKey());
            }
        }
        NBTTagCompound display = root.getCompoundTag("display");
        display.removeTag("Name");
        display.removeTag("LocName");
        if (display.isEmpty()) root.removeTag("display");
        root.setTag(registration.storageKey, payload);
        root.setTag(ROOT_TAG, metadata);
    }

    @Nullable
    static <T> T read(ItemStack stack, DataStickHandler<T> handler) {
        NBTTagCompound root = stack.getTagCompound();
        Registration registration = REGISTRATIONS.get(handler);
        if (registration == null || getHandler(root, registration.storageKey) != handler) return null;
        if (root.getCompoundTag(ROOT_TAG).getCompoundTag(HANDLERS_TAG).hasKey(registration.storageKey)) {
            return handler.deserializeNBT(root.getCompoundTag(registration.storageKey).copy());
        }
        if (hasLegacyEntry(root) &&
                registration.storageKey.equals(getLegacyStorageKey(root.getCompoundTag(ROOT_TAG)))) {
            return handler.deserializeNBT(root.getCompoundTag(ROOT_TAG).getCompoundTag(DATA_TAG).copy());
        }
        for (Map.Entry<String, DataStickHandler<?>> legacy : LEGACY_TAGS.entrySet()) {
            if (legacy.getValue() == handler && root.hasKey(legacy.getKey(), Constants.NBT.TAG_COMPOUND)) {
                return handler.deserializeNBT(root.getCompoundTag(legacy.getKey()).copy());
            }
        }
        return null;
    }

    @Nullable
    public static String getDisplayName(@NotNull ItemStack stack) {
        DataStickHandler<?> handler = getHandler(stack);
        return handler == null ? null : getDisplayName(stack, handler);
    }

    @Nullable
    private static <T> String getDisplayName(ItemStack stack, DataStickHandler<T> handler) {
        T data = handler.read(stack);
        return data == null ? null : handler.getDisplayName(data);
    }

    public static void addInformation(@NotNull ItemStack stack, @NotNull List<String> lines) {
        for (DataStickHandler<?> handler : getHandlers(stack)) {
            addInformation(stack, handler, lines);
        }
    }

    private static <T> void addInformation(ItemStack stack, DataStickHandler<T> handler, List<String> lines) {
        T data = handler.read(stack);
        if (data != null) handler.addInformation(data, lines);
    }

    private static final class Registration {

        private final String id;
        private final String storageKey;

        private Registration(String id, String storageKey) {
            this.id = id;
            this.storageKey = storageKey;
        }
    }
}
