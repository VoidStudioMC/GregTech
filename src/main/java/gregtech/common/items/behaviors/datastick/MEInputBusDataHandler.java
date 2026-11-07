package gregtech.common.items.behaviors.datastick;

import gregtech.api.util.LocalizationUtils;
import gregtech.api.util.TextFormattingUtil;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Item input configuration, including the programmed circuit, in the shared ME storage area. */
public final class MEInputBusDataHandler extends MEConfigurationDataHandler {

    @Override
    public String getDisplayName(@NotNull NBTTagCompound data) {
        return LocalizationUtils.format("gregtech.machine.me.item_import.data_stick.name");
    }

    @Override
    protected void addConfigurationInformation(NBTTagCompound data, List<String> lines) {
        if (data.hasKey("GhostCircuit", Constants.NBT.TAG_ANY_NUMERIC)) {
            int circuit = data.getByte("GhostCircuit");
            lines.add(circuit < 0 ? I18n.format("behavior.data_item.me.no_circuit") :
                    I18n.format("metaitem.int_circuit.configuration", circuit));
        }
    }

    @Override
    protected void addSlotInformation(NBTTagCompound stackTag, int slotNumber, List<String> lines) {
        ItemStack item = new ItemStack(stackTag);
        if (!item.isEmpty()) {
            lines.add(I18n.format("behavior.data_item.me.item", slotNumber,
                    TextFormattingUtil.formatNumbers(item.getCount()), item.getDisplayName()));
        }
    }
}
