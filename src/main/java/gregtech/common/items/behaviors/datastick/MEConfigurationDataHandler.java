package gregtech.common.items.behaviors.datastick;

import gregtech.api.items.datastick.DataStickHandler;

import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/** Shared ME configuration codec and tooltip layout; does not require AE2 to be installed. */
abstract class MEConfigurationDataHandler extends DataStickHandler<NBTTagCompound> {

    private static final String CONFIG_STACKS_TAG = "ConfigStacks";
    private static final String AUTO_PULL_TAG = "AutoPull";

    @Override
    @NotNull
    public final NBTTagCompound serializeNBT(@NotNull NBTTagCompound data) {
        return data;
    }

    @Override
    @Nullable
    public final NBTTagCompound deserializeNBT(@NotNull NBTTagCompound tag) {
        if (!tag.hasKey(CONFIG_STACKS_TAG, Constants.NBT.TAG_COMPOUND) &&
                !tag.hasKey(AUTO_PULL_TAG, Constants.NBT.TAG_BYTE)) {
            return null;
        }
        return tag;
    }

    @Override
    public final void addInformation(@NotNull NBTTagCompound data, @NotNull List<String> lines) {
        boolean autoPull = data.getBoolean(AUTO_PULL_TAG);
        if (data.hasKey(AUTO_PULL_TAG, Constants.NBT.TAG_BYTE)) {
            String autoPullKey = autoPull ? "gregtech.machine.me.stocking_auto_pull_enabled" :
                    "gregtech.machine.me.stocking_auto_pull_disabled";
            lines.add(I18n.format(autoPullKey));
        }

        addConfigurationInformation(data, lines);
        if (autoPull) {
            return;
        }

        NBTTagCompound stacks = data.getCompoundTag(CONFIG_STACKS_TAG);
        int initialTooltipSize = lines.size();
        for (int slot : getConfiguredSlots(stacks)) {
            addSlotInformation(stacks.getCompoundTag(Integer.toString(slot)), slot + 1, lines);
        }
        if (lines.size() == initialTooltipSize) {
            lines.add(I18n.format("behavior.data_item.me.empty"));
        }
    }

    protected void addConfigurationInformation(NBTTagCompound data, List<String> lines) {}

    protected abstract void addSlotInformation(NBTTagCompound stackTag, int slotNumber, List<String> lines);

    private static SortedSet<Integer> getConfiguredSlots(NBTTagCompound stacks) {
        SortedSet<Integer> slots = new TreeSet<>();
        for (String key : stacks.getKeySet()) {
            try {
                int slot = Integer.parseInt(key);
                if (slot >= 0 && slot < Integer.MAX_VALUE && key.equals(Integer.toString(slot)) &&
                        stacks.hasKey(key, Constants.NBT.TAG_COMPOUND)) {
                    slots.add(slot);
                }
            } catch (NumberFormatException ignored) {
                // Ignore non-slot metadata in configurations from other versions or addons.
            }
        }
        return slots;
    }
}
