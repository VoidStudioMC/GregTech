package gregtech.integration.theoneprobe.provider;

import gregtech.api.capability.IOpticalComputationProvider;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.integration.theoneprobe.ProbeInfoProvider;
import gregtech.integration.theoneprobe.ProbeInfoUtil;

import net.minecraft.tileentity.TileEntity;

import mcjty.theoneprobe.api.IProbeInfo;
import org.jetbrains.annotations.Nullable;

public final class ComputationInfoProvider extends ProbeInfoProvider {

    public ComputationInfoProvider() {
        super("gregtech:computation_capacity");
    }

    @Override
    protected void addProbeInfo(IProbeInfo info, TileEntity tile, @Nullable MetaTileEntity machine, boolean sneaking) {
        if (!(machine instanceof IOpticalComputationProvider hpca) || !ProbeInfoUtil.isFormed(machine) ||
                hpca.getComputationCapacity() < 0)
            return;
        ProbeInfoUtil.line(info, "gregtech.top.computation_supplied",
                ProbeInfoUtil.number(hpca.getComputationCapacity()) + " CWU/t");
        if (hpca.getCurrentCWUt() >= 0) {
            ProbeInfoUtil.line(info, "gregtech.top.computation_current",
                    ProbeInfoUtil.number(hpca.getCurrentCWUt()) + " CWU/t");
        }
    }
}
