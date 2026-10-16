package gregtech.api.items.datastick;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Registry and NBT storage shared by data sticks, data orbs and data modules. */
public final class DataStickRegistry {

    public static final String ROOT_TAG = "GT.DataStick";
    public static final String HANDLER_TAG = "Handler";
    public static final String DATA_TAG = "Data";

    private static final Map<String, DataStickHandler<?>> HANDLERS = new HashMap<>();
    private static final Map<DataStickHandler<?>, String> IDS = new IdentityHashMap<>();
    private static final Map<String, DataStickHandler<?>> LEGACY_TAGS = new LinkedHashMap<>();

    private DataStickRegistry() {}

    /** Register during mod initialization on both sides. IDs must be stable; prefer "modid:name". */
    @NotNull
    public static <T> DataStickHandler<T> register(@NotNull String id, @NotNull DataStickHandler<T> handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        if (id.trim().isEmpty() || HANDLERS.containsKey(id) || IDS.containsKey(handler)) {
            throw new IllegalArgumentException("Duplicate or empty data stick handler registration: " + id);
        }
        HANDLERS.put(id, handler);
        IDS.put(handler, id);
        return handler;
    }

    /**
     * Accept a former top-level compound as this handler's payload when there is no new-format tag. Legacy tags are
     * checked in registration order and removed on the next write. Reading does not mutate existing items.
     */
    public static void registerLegacyTag(@NotNull String tag, @NotNull DataStickHandler<?> handler) {
        Objects.requireNonNull(tag, "tag");
        if (tag.isEmpty() || ROOT_TAG.equals(tag) || "display".equals(tag) || LEGACY_TAGS.containsKey(tag) ||
                !IDS.containsKey(handler)) {
            throw new IllegalArgumentException("Invalid legacy data stick tag registration: " + tag);
        }
        LEGACY_TAGS.put(tag, handler);
    }

    @Nullable
    public static DataStickHandler<?> getHandler(@NotNull ItemStack stack) {
        return getHandler(stack.getTagCompound());
    }

    @Nullable
    private static DataStickHandler<?> getHandler(@Nullable NBTTagCompound root) {
        if (root == null) return null;
        // An explicit handler always wins, even when missing or malformed. Never resurrect stale legacy data.
        if (root.hasKey(ROOT_TAG)) {
            NBTTagCompound entry = root.getCompoundTag(ROOT_TAG);
            if (!entry.hasKey(HANDLER_TAG, Constants.NBT.TAG_STRING) ||
                    !entry.hasKey(DATA_TAG, Constants.NBT.TAG_COMPOUND))
                return null;
            return HANDLERS.get(entry.getString(HANDLER_TAG));
        }
        for (Map.Entry<String, DataStickHandler<?>> legacy : LEGACY_TAGS.entrySet()) {
            if (root.hasKey(legacy.getKey(), Constants.NBT.TAG_COMPOUND)) return legacy.getValue();
        }
        return null;
    }

    static <T> void write(NBTTagCompound root, DataStickHandler<T> handler, T data) {
        String id = IDS.get(handler);
        if (id == null) throw new IllegalArgumentException("Data stick handler is not registered");
        // Serialize before changing the item, so a failed write keeps its original data.
        NBTTagCompound payload = Objects.requireNonNull(handler.serializeNBT(Objects.requireNonNull(data)), "payload")
                .copy();
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString(HANDLER_TAG, id);
        entry.setTag(DATA_TAG, payload);
        for (String legacy : LEGACY_TAGS.keySet()) {
            root.removeTag(legacy);
        }
        NBTTagCompound display = root.getCompoundTag("display");
        display.removeTag("Name");
        display.removeTag("LocName");
        if (display.isEmpty()) root.removeTag("display");
        root.setTag(ROOT_TAG, entry);
    }

    @Nullable
    static <T> T read(ItemStack stack, DataStickHandler<T> handler) {
        NBTTagCompound root = stack.getTagCompound();
        if (getHandler(root) != handler) return null;
        if (root.hasKey(ROOT_TAG)) {
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
        DataStickHandler<?> handler = getHandler(stack);
        if (handler != null) addInformation(stack, handler, lines);
    }

    private static <T> void addInformation(ItemStack stack, DataStickHandler<T> handler, List<String> lines) {
        T data = handler.read(stack);
        if (data != null) handler.addInformation(data, lines);
    }
}
