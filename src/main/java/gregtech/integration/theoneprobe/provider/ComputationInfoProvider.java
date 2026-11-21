package gregtech.integration.theoneprobe.provider;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityHPCA;
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
        if (!(machine instanceof MetaTileEntityHPCA hpca) || !hpca.isStructureFormed()) return;
        ProbeInfoUtil.line(info, "gregtech.top.computation_supplied",
                ProbeInfoUtil.number(hpca.getComputationCapacity()) + " CWU/t");
        ProbeInfoUtil.line(info, "gregtech.top.computation_current",
                ProbeInfoUtil.number(hpca.getCurrentCWUt()) + " CWU/t");
    }
}
