package gregtech.integration.theoneprobe.provider;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.TextFormattingUtil;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityPowerSubstation;
import gregtech.integration.theoneprobe.ProbeInfoProvider;
import gregtech.integration.theoneprobe.ProbeInfoUtil;

import net.minecraft.tileentity.TileEntity;

import mcjty.theoneprobe.api.IProbeInfo;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;

public final class PowerSubstationInfoProvider extends ProbeInfoProvider {

    public PowerSubstationInfoProvider() {
        super("gregtech:power_substation");
    }

    @Override
    protected void addProbeInfo(IProbeInfo info, TileEntity tile, @Nullable MetaTileEntity machine, boolean sneaking) {
        if (!(machine instanceof MetaTileEntityPowerSubstation substation) || !substation.isStructureFormed()) return;
        BigInteger stored = substation.getStoredEnergy();
        BigInteger capacity = substation.getEnergyCapacity();
        ProbeInfoUtil.line(info, "gregtech.top.energy_stored", TextFormattingUtil.formatNumbers(stored) + " EU");
        ProbeInfoUtil.line(
                info, "gregtech.top.energy_capacity", TextFormattingUtil.formatNumbers(capacity) + " EU");
        if (!sneaking) return;
        ProbeInfoUtil.fillProgress(info, stored, capacity);
        ProbeInfoUtil.line(info, "gregtech.top.energy_input_average",
                ProbeInfoUtil.number(substation.getAverageInLastSec()) + " EU/t");
        ProbeInfoUtil.line(info, "gregtech.top.energy_output_average",
                ProbeInfoUtil.number(substation.getAverageOutLastSec()) + " EU/t");
    }
}
