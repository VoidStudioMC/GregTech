package gregtech.integration.theoneprobe.provider;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityCleanroom;
import gregtech.integration.theoneprobe.ProbeInfoProvider;
import gregtech.integration.theoneprobe.ProbeInfoUtil;

import net.minecraft.tileentity.TileEntity;

import mcjty.theoneprobe.api.IProbeInfo;
import org.jetbrains.annotations.Nullable;

public final class CleanroomInfoProvider extends ProbeInfoProvider {

    public CleanroomInfoProvider() {
        super("gregtech:cleanroom");
    }

    @Override
    protected void addProbeInfo(IProbeInfo info, TileEntity tile, @Nullable MetaTileEntity machine, boolean sneaking) {
        if (!(machine instanceof MetaTileEntityCleanroom cleanroom) || !cleanroom.isStructureFormed()) return;
        ProbeInfoUtil.line(info, "gregtech.top.cleanliness", ProbeInfoUtil.number(cleanroom.getCleanAmount()) + "%");
    }
}
