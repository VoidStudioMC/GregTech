package gregtech.api.items.datastick;

import gregtech.api.GregTechAPI;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Codec and presentation for one kind of data stored on a data item. Register the same instance on both sides with
 * {@link GregTechAPI#DATA_STICK_REGISTRY} using
 * {@link DataStickRegistry#register(ResourceLocation, DataStickHandler)} during
 * mod initialization, before using it. Registration keys and storage keys must remain stable across saves.
 *
 * @param <T> the decoded data type
 */
public abstract class DataStickHandler<T> {

    public static final String DEFAULT_STORAGE_KEY = "handler";

    /**
     * Top-level item tag containing this handler's payload. Handlers sharing a key overwrite each other; other
     * storage areas are preserved. This key must remain stable for the lifetime of the handler.
     */
    @NotNull
    public String getStorageKey() {
        return DEFAULT_STORAGE_KEY;
    }

    @NotNull
    public abstract NBTTagCompound serializeNBT(@NotNull T data);

    /** Receives an isolated copy of the payload. Return null for data this handler cannot decode. */
    @Nullable
    public abstract T deserializeNBT(@NotNull NBTTagCompound tag);

    /** Return a localized name, or null to keep the item's default name. May also be called on the server. */
    @Nullable
    public String getDisplayName(@NotNull T data) {
        return null;
    }

    /** Append tooltip lines for the decoded payload. Data items display these on the client while Shift is held. */
    public void addInformation(@NotNull T data, @NotNull List<String> lines) {}

    /** Replace this storage area's payload and make this handler active for the name. Other areas are preserved. */
    public final void write(@NotNull ItemStack stack, @NotNull T data) {
        DataStickStorage.write(stack, this, data);
    }

    /** Write to an existing item tag, for callers that do not have an ItemStack. */
    public final void write(@NotNull NBTTagCompound root, @NotNull T data) {
        DataStickStorage.write(root, this, data);
    }

    /**
     * Read data for use and make this handler active for the name on success. Returns null without changing the item
     * when this area belongs to another handler, has no data, or cannot be decoded. Use {@link #peek(ItemStack)} for
     * inspection, validation and presentation.
     */
    @Nullable
    public final T read(@NotNull ItemStack stack) {
        return DataStickStorage.read(stack, this);
    }

    /** Read an isolated copy of this area's data without changing the item or its active handler. */
    @Nullable
    public final T peek(@NotNull ItemStack stack) {
        return DataStickStorage.peek(stack, this);
    }
}
