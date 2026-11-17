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
        double measuredVoltage = cable.getAverageVoltage();
        double voltage = measuredVoltage <= maxVoltage ? measuredVoltage / voltageDivisor(amps) : maxVoltage;
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
                TextFormattingUtil.formatNumbers(voltage) + " / " + ProbeInfoUtil.voltage(maxVoltage));
        ProbeInfoUtil.line(
                info,
                "gregtech.top.cable_amperage",
                TextFormattingUtil.formatNumbers(amps) + " / " + ProbeInfoUtil.number(maxAmps) + " A");
    }

    private static int voltageDivisor(double amps) {
        if (amps <= 4) return 1;
        if (amps <= 16) return 2;
        if (amps <= 64) return 3;
        if (amps <= 256) return 4;
        if (amps <= 1024) return 5;
        if (amps <= 4096) return 6;
        if (amps <= 16384) return 7;
        return 1;
    }
}
