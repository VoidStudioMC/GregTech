package gregtech.integration.theoneprobe.provider;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.metatileentities.storage.MetaTileEntityDrum;
import gregtech.integration.theoneprobe.ProbeInfoProvider;
import gregtech.integration.theoneprobe.ProbeInfoUtil;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextFormatting;

import mcjty.theoneprobe.api.IProbeInfo;
import org.jetbrains.annotations.Nullable;

public final class DrumInfoProvider extends ProbeInfoProvider {

    public DrumInfoProvider() {
        super("gregtech:drum");
    }

    @Override
    protected void addProbeInfo(IProbeInfo info, TileEntity tile, @Nullable MetaTileEntity machine, boolean sneaking) {
        if (!(machine instanceof MetaTileEntityDrum drum)) return;
        for (var tank : drum.getFluidInventory().getTankProperties()) {
            var fluid = tank.getContents();
            if (fluid == null || fluid.amount <= 0) {
                info.text(TextFormatting.GRAY + ProbeInfoUtil.lang("gregtech.fluid.empty"));
            } else {
                ProbeInfoUtil.fluid(info, fluid);
            }
        }
    }
}
