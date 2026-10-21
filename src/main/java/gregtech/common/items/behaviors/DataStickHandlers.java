package gregtech.common.items.behaviors;

import gregtech.api.GregTechAPI;
import gregtech.api.items.datastick.DataStickHandler;
import gregtech.api.util.AssemblyLineManager;
import gregtech.api.util.GTUtility;
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
import java.util.SortedSet;
import java.util.TreeSet;

/** Built-in data formats. ME payloads use vanilla NBT decoding so these handlers do not require AE2 to be installed. */
public final class DataStickHandlers {

    public static final DataStickHandler<String> ASSEMBLY_LINE = new DataStickHandler<>() {

        @Override
        @NotNull
        public String getStorageKey() {
            return AssemblyLineManager.RESEARCH_NBT_TAG;
        }

        @Override
        @NotNull
        public NBTTagCompound serializeNBT(@NotNull String data) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString(AssemblyLineManager.RESEARCH_ID_NBT_TAG, data);
            return tag;
        }

        @Override
        @Nullable
        public String deserializeNBT(@NotNull NBTTagCompound tag) {
            String id = tag.getString(AssemblyLineManager.RESEARCH_ID_NBT_TAG);
            return id.isEmpty() ? null : id;
        }

        @Override
        public void addInformation(@NotNull String data, @NotNull List<String> lines) {
            DataItemBehavior.collectResearchItems(data, lines);
        }
    };

    public static final DataStickHandler<NBTTagCompound> ME_INPUT_BUS = new MEConfigurationHandler(false);
    public static final DataStickHandler<NBTTagCompound> ME_INPUT_HATCH = new MEConfigurationHandler(true);

    private DataStickHandlers() {}

    /** Register built-in handlers during item initialization, before any stacks are inspected. Call once. */
    public static void init() {
        GregTechAPI.DATA_STICK_REGISTRY.register(0, GTUtility.gregtechId("assembly_line"), ASSEMBLY_LINE);
        GregTechAPI.DATA_STICK_REGISTRY.register(1, GTUtility.gregtechId("me_input_bus"), ME_INPUT_BUS);
        GregTechAPI.DATA_STICK_REGISTRY.register(2, GTUtility.gregtechId("me_input_hatch"), ME_INPUT_HATCH);
    }

    private static final class MEConfigurationHandler extends DataStickHandler<NBTTagCompound> {

        private static final String CONFIG_STACKS_TAG = "ConfigStacks";
        private static final String AUTO_PULL_TAG = "AutoPull";
        private static final String GHOST_CIRCUIT_TAG = "GhostCircuit";

        private final boolean fluids;

        private MEConfigurationHandler(boolean fluids) {
            this.fluids = fluids;
        }

        @Override
        @NotNull
        public NBTTagCompound serializeNBT(@NotNull NBTTagCompound data) {
            return data;
        }

        @Override
        @Nullable
        public NBTTagCompound deserializeNBT(@NotNull NBTTagCompound tag) {
            if (!tag.hasKey(CONFIG_STACKS_TAG, Constants.NBT.TAG_COMPOUND) &&
                    !tag.hasKey(AUTO_PULL_TAG, Constants.NBT.TAG_BYTE)) {
                return null;
            }
            return tag;
        }

        @Override
        public String getDisplayName(@NotNull NBTTagCompound data) {
            String nameKey = fluids ? "gregtech.machine.me.fluid_import.data_stick.name" :
                    "gregtech.machine.me.item_import.data_stick.name";
            return LocalizationUtils.format(nameKey);
        }

        @Override
        public void addInformation(@NotNull NBTTagCompound data, @NotNull List<String> lines) {
            boolean autoPull = data.getBoolean(AUTO_PULL_TAG);
            if (data.hasKey(AUTO_PULL_TAG, Constants.NBT.TAG_BYTE)) {
                String autoPullKey = autoPull ? "gregtech.machine.me.stocking_auto_pull_enabled" :
                        "gregtech.machine.me.stocking_auto_pull_disabled";
                lines.add(I18n.format(autoPullKey));
            }

            if (!fluids && data.hasKey(GHOST_CIRCUIT_TAG, Constants.NBT.TAG_ANY_NUMERIC)) {
                int circuit = data.getByte(GHOST_CIRCUIT_TAG);
                lines.add(circuit < 0 ? I18n.format("behavior.data_item.me.no_circuit") :
                        I18n.format("metaitem.int_circuit.configuration", circuit));
            }

            if (autoPull) {
                return;
            }

            addConfiguredSlotsInformation(data.getCompoundTag(CONFIG_STACKS_TAG), lines);
        }

        private void addConfiguredSlotsInformation(NBTTagCompound stacks, List<String> lines) {
            int initialTooltipSize = lines.size();
            for (int slot : getConfiguredSlots(stacks)) {
                NBTTagCompound stackTag = stacks.getCompoundTag(Integer.toString(slot));
                if (fluids) {
                    FluidStack fluid = FluidStack.loadFluidStackFromNBT(stackTag);
                    if (fluid == null) {
                        continue;
                    }

                    lines.add(I18n.format("behavior.data_item.me.fluid", slot + 1,
                            TextFormattingUtil.formatNumbers(fluid.amount), fluid.getLocalizedName()));
                } else {
                    ItemStack item = new ItemStack(stackTag);
                    if (item.isEmpty()) {
                        continue;
                    }

                    lines.add(I18n.format("behavior.data_item.me.item", slot + 1,
                            TextFormattingUtil.formatNumbers(item.getCount()), item.getDisplayName()));
                }
            }

            if (lines.size() == initialTooltipSize) {
                lines.add(I18n.format("behavior.data_item.me.empty"));
            }
        }

        private static SortedSet<Integer> getConfiguredSlots(NBTTagCompound stacks) {
            SortedSet<Integer> slots = new TreeSet<>();
            for (String key : stacks.getKeySet()) {
                try {
                    int slot = Integer.parseInt(key);
                    if (slot >= 0 && stacks.hasKey(key, Constants.NBT.TAG_COMPOUND)) {
                        slots.add(slot);
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore non-slot metadata in configurations from other versions or addons.
                }
            }
            return slots;
        }
    }
}
