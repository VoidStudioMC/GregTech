package gregtech.api.items.datastick;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Registry and NBT storage shared by data sticks, data orbs and data modules. */
public final class DataStickRegistry {

    public static final String ROOT_TAG = "GT.DataStick";
    public static final String HANDLERS_TAG = "Handlers";
    public static final String ACTIVE_TAG = "Active";

    private static final Map<String, DataStickHandler<?>> HANDLERS = new LinkedHashMap<>();

    private DataStickRegistry() {}

    /** Register during mod initialization on both sides. IDs must be stable; prefer "modid:name". */
    @NotNull
    public static <T> DataStickHandler<T> register(@NotNull String id, @NotNull DataStickHandler<T> handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");

        if (id.trim().isEmpty() || HANDLERS.containsKey(id) || getId(handler) != null) {
            throw new IllegalArgumentException("Duplicate or empty data stick handler registration: " + id);
        }
        String storageKey = Objects.requireNonNull(handler.getStorageKey(), "storageKey");
        if (storageKey.trim().isEmpty() || ROOT_TAG.equals(storageKey) || "display".equals(storageKey)) {
            throw new IllegalArgumentException("Invalid data stick storage key: " + storageKey);
        }
        HANDLERS.put(id, handler);
        return handler;
    }

    @Nullable
    private static String getId(DataStickHandler<?> handler) {
        for (Map.Entry<String, DataStickHandler<?>> entry : HANDLERS.entrySet()) {
            if (entry.getValue() == handler) return entry.getKey();
        }
        return null;
    }

    /** The last successfully used handler (read or write), or null when the active area is missing or invalid. */
    @Nullable
    public static DataStickHandler<?> getHandler(@NotNull ItemStack stack) {
        NBTTagCompound root = stack.getTagCompound();
        if (root == null) return null;
        return getHandler(root, root.getCompoundTag(ROOT_TAG).getString(ACTIVE_TAG));
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
        if (!root.hasKey(storageKey, Constants.NBT.TAG_COMPOUND)) return null;
        NBTTagCompound handlers = root.getCompoundTag(ROOT_TAG).getCompoundTag(HANDLERS_TAG);
        DataStickHandler<?> handler = HANDLERS.get(handlers.getString(storageKey));
        return handler != null && storageKey.equals(handler.getStorageKey()) ? handler : null;
    }

    private static Set<String> getStorageKeys(NBTTagCompound root) {
        Set<String> keys = new LinkedHashSet<>();
        NBTTagCompound metadata = root.getCompoundTag(ROOT_TAG);
        if (metadata.hasKey(ACTIVE_TAG, Constants.NBT.TAG_STRING)) keys.add(metadata.getString(ACTIVE_TAG));
        keys.add(DataStickHandler.DEFAULT_STORAGE_KEY);
        for (DataStickHandler<?> handler : HANDLERS.values()) {
            keys.add(handler.getStorageKey());
        }
        return keys;
    }

    static <T> void write(NBTTagCompound root, DataStickHandler<T> handler, T data) {
        String id = getId(handler);
        if (id == null) throw new IllegalArgumentException("Data stick handler is not registered");
        String storageKey = handler.getStorageKey();
        // Serialize before changing the item, so a failed write keeps its original data.
        NBTTagCompound payload = Objects.requireNonNull(handler.serializeNBT(Objects.requireNonNull(data)), "payload")
                .copy();
        NBTTagCompound metadata = root.getCompoundTag(ROOT_TAG).copy();
        NBTTagCompound handlers = metadata.getCompoundTag(HANDLERS_TAG);
        handlers.setString(storageKey, id);
        metadata.setTag(HANDLERS_TAG, handlers);
        root.setTag(storageKey, payload);
        root.setTag(ROOT_TAG, metadata);
        setActive(root, storageKey);
    }

    private static void setActive(NBTTagCompound root, String storageKey) {
        root.getCompoundTag(ROOT_TAG).setString(ACTIVE_TAG, storageKey);
        NBTTagCompound display = root.getCompoundTag("display");
        display.removeTag("Name");
        display.removeTag("LocName");
        if (display.isEmpty()) root.removeTag("display");
    }

    @Nullable
    static <T> T read(ItemStack stack, DataStickHandler<T> handler) {
        T data = peek(stack, handler);
        if (data != null) {
            NBTTagCompound root = stack.getTagCompound().copy();
            setActive(root, handler.getStorageKey());
            stack.setTagCompound(root);
        }
        return data;
    }

    @Nullable
    static <T> T peek(ItemStack stack, DataStickHandler<T> handler) {
        NBTTagCompound root = stack.getTagCompound();
        String storageKey = handler.getStorageKey();
        if (getHandler(root, storageKey) != handler) return null;
        return handler.deserializeNBT(root.getCompoundTag(storageKey).copy());
    }

    @Nullable
    public static String getDisplayName(@NotNull ItemStack stack) {
        DataStickHandler<?> handler = getHandler(stack);
        return handler == null ? null : getDisplayName(stack, handler);
    }

    @Nullable
    private static <T> String getDisplayName(ItemStack stack, DataStickHandler<T> handler) {
        T data = handler.peek(stack);
        return data == null ? null : handler.getDisplayName(data);
    }

    public static void addInformation(@NotNull ItemStack stack, @NotNull List<String> lines) {
        for (DataStickHandler<?> handler : getHandlers(stack)) {
            addInformation(stack, handler, lines);
        }
    }

    private static <T> void addInformation(ItemStack stack, DataStickHandler<T> handler, List<String> lines) {
        T data = handler.peek(stack);
        if (data != null) handler.addInformation(data, lines);
    }
}
