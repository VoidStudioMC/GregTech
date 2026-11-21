package gregtech.integration.theoneprobe.provider;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.TextFormattingUtil;
import gregtech.common.pipelike.cable.tile.TileEntityCable;
import gregtech.integration.theoneprobe.ProbeInfoProvider;
import gregtech.integration.theoneprobe.ProbeInfoUtil;

import net.minecraft.tileentity.TileEntity;

import mcjty.theoneprobe.api.IProbeInfo;
import org.jetbrains.annotations.Nullable;

/** Port of GT TOP Addition's cable load, voltage and amperage provider. */
public final class CableInfoProvider extends ProbeInfoProvider {

    public CableInfoProvider() {
        super("gregtech:cable");
    }

    @Override
    protected void addProbeInfo(IProbeInfo info, TileEntity tile, @Nullable MetaTileEntity machine, boolean sneaking) {
        if (!(tile instanceof TileEntityCable cable) || cable.getNodeData() == null) return;
        double amps = cable.getAverageAmperage();
        long maxAmps = cable.getMaxAmperage();
        long maxVoltage = cable.getMaxVoltage();
        long voltage = cable.getCurrentMaxVoltage();
        info.text(ProbeInfoUtil.lang("gregtech.top.cable_load"));
        // Use TOP's long progress overload: high-tier cable ratings overflow the original addon's int bars.
        ProbeInfoUtil.progress(
                info,
                Math.round(voltage * amps),
                Math.round((double) maxVoltage * maxAmps),
                " EU/t",
                ProbeInfoUtil.YELLOW);
        ProbeInfoUtil.line(
                info,
                "gregtech.top.cable_voltage",
                ProbeInfoUtil.number(voltage) + " / " + ProbeInfoUtil.voltage(maxVoltage));
        ProbeInfoUtil.line(
                info,
                "gregtech.top.cable_amperage",
                TextFormattingUtil.formatNumbers(amps) + " / " + ProbeInfoUtil.number(maxAmps) + " A");
    }

}
