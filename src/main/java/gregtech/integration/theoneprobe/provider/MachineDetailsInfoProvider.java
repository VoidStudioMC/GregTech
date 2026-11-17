package gregtech.integration.theoneprobe.provider;

import gregtech.api.GTValues;
import gregtech.api.capability.IDistinctBusController;
import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.integration.theoneprobe.ProbeInfoProvider;
import gregtech.integration.theoneprobe.ProbeInfoUtil;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextFormatting;

import mcjty.theoneprobe.api.IProbeInfo;
import org.jetbrains.annotations.Nullable;

public final class MachineDetailsInfoProvider extends ProbeInfoProvider {

    public MachineDetailsInfoProvider() {
        super("gregtech:machine_details");
    }

    @Override
    protected void addProbeInfo(IProbeInfo info, TileEntity tile, @Nullable MetaTileEntity machine, boolean sneaking) {
        if (machine == null) return;

        if (machine instanceof ITieredMetaTileEntity tiered) {
            ProbeInfoUtil.line(info, "gregtech.top.tier", GTValues.VNF[tiered.getTier()]);
        }
        if (machine instanceof IMultiblockPart part) {
            boolean attached = part.isAttachedToMultiBlock();
            info.text((attached ? TextFormatting.GREEN : TextFormatting.YELLOW) +
                    ProbeInfoUtil.lang(attached ? "gregtech.top.part_attached" : "gregtech.top.part_detached"));
        }
        if (!ProbeInfoUtil.isFormed(machine)) return;

        var workable = ProbeInfoUtil.workable(tile, machine);
        if (workable != null && workable.isWorkingEnabled()) {
            info.text((workable.isActive() ? TextFormatting.GREEN : TextFormatting.GRAY) +
                    ProbeInfoUtil.lang(workable.isActive() ? "gregtech.top.running" : "gregtech.top.idling"));
        }
        if (machine.shouldShowVoidingModeButton()) {
            ProbeInfoUtil.line(info, "gregtech.top.voiding",
                    ProbeInfoUtil.lang(machine.getVoidingModeTooltip(machine.getVoidingMode())));
        }
        if (machine instanceof IDistinctBusController recipes && recipes.canBeDistinct()) {
            ProbeInfoUtil.line(info, "gregtech.top.distinct",
                    ProbeInfoUtil.lang(recipes.isDistinct() ? "gregtech.top.enabled" : "gregtech.top.disabled"));
        }
    }
}
