package gregtech.integration.theoneprobe;

import gregtech.api.metatileentity.MetaTileEntity;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ProbeMode;
import org.jetbrains.annotations.Nullable;

public abstract class ProbeInfoProvider implements IProbeInfoProvider {

    private final String id;

    protected ProbeInfoProvider(String id) {
        this.id = id;
    }

    @Override
    public final String getID() {
        return id;
    }

    @Override
    public final void addProbeInfo(
                                   ProbeMode mode, IProbeInfo info, EntityPlayer player, World world, IBlockState state,
                                   IProbeHitData hit) {
        TileEntity tile = world.getTileEntity(hit.getPos());
        if (tile == null) return;
        MetaTileEntity machine = ProbeInfoUtil.getMetaTileEntity(tile);
        addProbeInfo(info, tile, machine, player.isSneaking());
    }

    protected abstract void addProbeInfo(
                                         IProbeInfo info, TileEntity tile, @Nullable MetaTileEntity machine,
                                         boolean sneaking);
}
