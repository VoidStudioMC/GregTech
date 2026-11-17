package gregtech.integration.theoneprobe.provider;

import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityAEHostablePart;
import gregtech.integration.theoneprobe.ProbeInfoUtil;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ProbeMode;

public class AEMultiblockHatchProvider implements IProbeInfoProvider {

    @Override
    public String getID() {
        return ":ae_multiblock_hatch_provider";
    }

    @Override
    public void addProbeInfo(ProbeMode probeMode, IProbeInfo probeInfo, EntityPlayer entityPlayer, World world,
                             IBlockState blockState, IProbeHitData probeHitData) {
        if (blockState.getBlock().hasTileEntity(blockState) &&
                ProbeInfoUtil.getMetaTileEntity(world.getTileEntity(
                        probeHitData.getPos())) instanceof MetaTileEntityAEHostablePart<?>aeHostablePart) {
            if (aeHostablePart.isOnline()) {
                probeInfo.text("{*gregtech.gui.me_network.online*}");
            } else {
                probeInfo.text("{*gregtech.gui.me_network.offline*}");
            }
        }
    }
}
