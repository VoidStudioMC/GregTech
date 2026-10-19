package gregtech.common.items.behaviors;

import gregtech.api.items.datastick.DataStickHandler;
import gregtech.api.items.datastick.DataStickRegistry;
import gregtech.api.util.AssemblyLineManager;
import gregtech.api.util.LocalizationUtils;
import gregtech.api.util.TextFormattingUtil;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.TreeSet;

/** Built-in data formats. ME payloads use vanilla NBT decoding so these handlers do not require AE2 to be installed. */
public final class DataStickHandlers {

    public static final DataStickHandler<String> ASSEMBLY_LINE = DataStickRegistry.register("gregtech:assembly_line",
            new DataStickHandler<>() {

                @Override
                public @NotNull String getStorageKey() {
                    return AssemblyLineManager.RESEARCH_NBT_TAG;
                }

                @Override
                public @NotNull NBTTagCompound serializeNBT(@NotNull String data) {
                    NBTTagCompound tag = new NBTTagCompound();
                    tag.setString(AssemblyLineManager.RESEARCH_ID_NBT_TAG, data);
                    return tag;
                }

                @Override
                public @Nullable String deserializeNBT(@NotNull NBTTagCompound tag) {
                    String id = tag.getString(AssemblyLineManager.RESEARCH_ID_NBT_TAG);
                    return id.isEmpty() ? null : id;
                }

                @Override
                public void addInformation(@NotNull String data, @NotNull List<String> lines) {
                    DataItemBehavior.collectResearchItems(data, lines);
                }
            });

    public static final DataStickHandler<NBTTagCompound> ME_INPUT_BUS = DataStickRegistry.register(
            "gregtech:me_input_bus",
            new MEConfigurationHandler(false));
    public static final DataStickHandler<NBTTagCompound> ME_INPUT_HATCH = DataStickRegistry
            .register("gregtech:me_input_hatch", new MEConfigurationHandler(true));

    private DataStickHandlers() {}

    /** Force registration during item initialization, before any existing stacks are inspected. */
    public static void init() {}

    private static final class MEConfigurationHandler extends DataStickHandler<NBTTagCompound> {

        private final boolean fluids;

        private MEConfigurationHandler(boolean fluids) {
            this.fluids = fluids;
        }

        @Override
        public @NotNull NBTTagCompound serializeNBT(@NotNull NBTTagCompound data) {
            return data;
        }

        @Override
        public @Nullable NBTTagCompound deserializeNBT(@NotNull NBTTagCompound tag) {
            if (!tag.hasKey("ConfigStacks", Constants.NBT.TAG_COMPOUND) &&
                    !tag.hasKey("AutoPull", Constants.NBT.TAG_BYTE))
                return null;
            return tag;
        }

        @Override
        public String getDisplayName(@NotNull NBTTagCompound data) {
            return LocalizationUtils.format(fluids ? "gregtech.machine.me.fluid_import.data_stick.name" :
                    "gregtech.machine.me.item_import.data_stick.name");
        }

        @Override
        public void addInformation(@NotNull NBTTagCompound data, @NotNull List<String> lines) {
            if (data.hasKey("AutoPull", Constants.NBT.TAG_BYTE)) {
                lines.add(I18n.format(data.getBoolean("AutoPull") ? "gregtech.machine.me.stocking_auto_pull_enabled" :
                        "gregtech.machine.me.stocking_auto_pull_disabled"));
            }
            if (!fluids && data.hasKey("GhostCircuit", Constants.NBT.TAG_ANY_NUMERIC)) {
                int circuit = data.getByte("GhostCircuit");
                lines.add(circuit < 0 ? I18n.format("behavior.data_item.me.no_circuit") :
                        I18n.format("metaitem.int_circuit.configuration", circuit));
            }
            if (data.getBoolean("AutoPull")) return;

            NBTTagCompound stacks = data.getCompoundTag("ConfigStacks");
            TreeSet<Integer> slots = new TreeSet<>();
            for (String key : stacks.getKeySet()) {
                try {
                    int slot = Integer.parseInt(key);
                    if (slot >= 0 && stacks.hasKey(key, Constants.NBT.TAG_COMPOUND)) slots.add(slot);
                } catch (NumberFormatException ignored) {
                    // Ignore non-slot metadata in configurations from other versions or addons.
                }
            }
            int initialSize = lines.size();
            for (int slot : slots) {
                NBTTagCompound tag = stacks.getCompoundTag(Integer.toString(slot));
                if (fluids) {
                    FluidStack fluid = FluidStack.loadFluidStackFromNBT(tag);
                    if (fluid != null) {
                        lines.add(I18n.format("behavior.data_item.me.fluid", slot + 1,
                                TextFormattingUtil.formatNumbers(fluid.amount), fluid.getLocalizedName()));
                    }
                } else {
                    ItemStack item = new ItemStack(tag);
                    if (!item.isEmpty()) {
                        lines.add(I18n.format("behavior.data_item.me.item", slot + 1,
                                TextFormattingUtil.formatNumbers(item.getCount()), item.getDisplayName()));
                    }
                }
            }
            if (lines.size() == initialSize) lines.add(I18n.format("behavior.data_item.me.empty"));
        }
    }
}
