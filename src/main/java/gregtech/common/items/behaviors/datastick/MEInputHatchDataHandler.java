package gregtech.common.items.behaviors.datastick;

import gregtech.api.util.LocalizationUtils;
import gregtech.api.util.TextFormattingUtil;

import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Fluid input configuration in the shared ME storage area. */
public final class MEInputHatchDataHandler extends MEConfigurationDataHandler {

    @Override
    public String getDisplayName(@NotNull NBTTagCompound data) {
        return LocalizationUtils.format("gregtech.machine.me.fluid_import.data_stick.name");
    }

    @Override
    protected void addSlotInformation(NBTTagCompound stackTag, int slotNumber, List<String> lines) {
        FluidStack fluid = FluidStack.loadFluidStackFromNBT(stackTag);
        if (fluid != null && fluid.amount > 0) {
            lines.add(I18n.format("behavior.data_item.me.fluid", slotNumber,
                    TextFormattingUtil.formatNumbers(fluid.amount), fluid.getLocalizedName()));
        }
    }
}
