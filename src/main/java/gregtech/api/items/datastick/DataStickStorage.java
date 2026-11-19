package gregtech.api.items.datastick;

import gregtech.api.GregTechAPI;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * NBT storage and active-area selection for data items. Payloads occupy the top-level tags returned by
 * {@link DataStickHandler#getStorageKey()}. The {@code GT.DataStick.Handlers} compound maps each storage key to its
 * registry key, and {@code GT.DataStick.Active} contains the last successfully read or written storage key.
 * Reading for presentation with {@link DataStickHandler#peek(ItemStack)} leaves the active area unchanged.
 */
public final class DataStickStorage {

    public static final String ROOT_TAG = "GT.DataStick";
    public static final String HANDLERS_TAG = "Handlers";
    public static final String ACTIVE_TAG = "Active";

    private DataStickStorage() {}

    /** The handler selected by the last successful read or write, or null if its area is missing or invalid. */
    @Nullable
    public static DataStickHandler<?> getActiveHandler(@NotNull ItemStack stack) {
        NBTTagCompound root = stack.getTagCompound();
        return root == null ? null : getHandler(root, root.getCompoundTag(ROOT_TAG).getString(ACTIVE_TAG));
    }

    /** Present handlers, active area first, then the default area and remaining areas in registration order. */
    @NotNull
    public static List<DataStickHandler<?>> getHandlers(@NotNull ItemStack stack) {
        NBTTagCompound root = stack.getTagCompound();
        if (root == null) {
            return Collections.emptyList();
        }

        Set<String> keys = new LinkedHashSet<>();
        keys.add(root.getCompoundTag(ROOT_TAG).getString(ACTIVE_TAG));
        keys.add(DataStickHandler.DEFAULT_STORAGE_KEY);
        for (DataStickHandler<?> handler : GregTechAPI.DATA_STICK_REGISTRY) {
            keys.add(handler.getStorageKey());
        }

        List<DataStickHandler<?>> handlers = new ArrayList<>();
        for (String key : keys) {
            DataStickHandler<?> handler = getHandler(root, key);
            if (handler != null) {
                handlers.add(handler);
            }
        }
        return handlers;
    }

    static <T> void write(ItemStack stack, DataStickHandler<T> handler, T data) {
        NBTTagCompound root = stack.hasTagCompound() ? stack.getTagCompound().copy() : new NBTTagCompound();
        write(root, handler, data);
        stack.setTagCompound(root);
    }

    static <T> void write(NBTTagCompound root, DataStickHandler<T> handler, T data) {
        ResourceLocation id = GregTechAPI.DATA_STICK_REGISTRY.getNameForObject(handler);
        if (id == null) {
            throw new IllegalArgumentException("Data stick handler is not registered");
        }
        // Serialize before changing the item, so a failed write keeps its original data.
        NBTTagCompound payload = Objects.requireNonNull(handler.serializeNBT(Objects.requireNonNull(data)), "payload")
                .copy();

        String storageKey = handler.getStorageKey();
        NBTTagCompound metadata = root.getCompoundTag(ROOT_TAG);
        NBTTagCompound handlerIds = metadata.getCompoundTag(HANDLERS_TAG);
        handlerIds.setString(storageKey, id.toString());
        metadata.setTag(HANDLERS_TAG, handlerIds);
        root.setTag(storageKey, payload);
        root.setTag(ROOT_TAG, metadata);
        setActive(root, storageKey);
    }

    @Nullable
    static <T> T read(ItemStack stack, DataStickHandler<T> handler) {
        T data = peek(stack, handler);
        if (data != null) {
            setActive(stack.getTagCompound(), handler.getStorageKey());
        }
        return data;
    }

    @Nullable
    static <T> T peek(ItemStack stack, DataStickHandler<T> handler) {
        NBTTagCompound root = stack.getTagCompound();
        String storageKey = handler.getStorageKey();
        return getHandler(root, storageKey) == handler ?
                handler.deserializeNBT(root.getCompoundTag(storageKey).copy()) : null;
    }

    @Nullable
    private static DataStickHandler<?> getHandler(@Nullable NBTTagCompound root, String storageKey) {
        if (root == null || !root.hasKey(storageKey, Constants.NBT.TAG_COMPOUND)) {
            return null;
        }
        String id = root.getCompoundTag(ROOT_TAG).getCompoundTag(HANDLERS_TAG).getString(storageKey);
        int separator = id.indexOf(':');
        if (separator <= 0 || separator == id.length() - 1) {
            return null;
        }
        ResourceLocation key = new ResourceLocation(id.substring(0, separator), id.substring(separator + 1));
        // Stored keys are fully qualified. Do not resolve malformed NBT through namespace or case defaults.
        if (!id.equals(key.toString())) {
            return null;
        }
        DataStickHandler<?> handler = GregTechAPI.DATA_STICK_REGISTRY.getObject(key);
        return handler != null && storageKey.equals(handler.getStorageKey()) ? handler : null;
    }

    private static void setActive(NBTTagCompound root, String storageKey) {
        root.getCompoundTag(ROOT_TAG).setString(ACTIVE_TAG, storageKey);

        NBTTagCompound display = root.getCompoundTag("display");
        display.removeTag("Name");
        display.removeTag("LocName");
        if (display.isEmpty()) {
            root.removeTag("display");
        }
    }
}
