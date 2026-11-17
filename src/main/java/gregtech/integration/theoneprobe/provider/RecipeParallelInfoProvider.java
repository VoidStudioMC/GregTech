package gregtech.integration.theoneprobe.provider;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.integration.theoneprobe.ProbeInfoProvider;
import gregtech.integration.theoneprobe.ProbeInfoUtil;

import net.minecraft.tileentity.TileEntity;

import mcjty.theoneprobe.api.IProbeInfo;
import org.jetbrains.annotations.Nullable;

/** Port of GT TOP Addition's parallel recipe provider. */
public final class RecipeParallelInfoProvider extends ProbeInfoProvider {

    public RecipeParallelInfoProvider() {
        super("gregtech:recipe_parallels");
    }

    @Override
    protected void addProbeInfo(IProbeInfo info, TileEntity tile, @Nullable MetaTileEntity machine, boolean sneaking) {
        if (!ProbeInfoUtil.isFormed(machine)) return;
        var logic = ProbeInfoUtil.recipeLogic(tile);
        if (logic == null || logic.getParallelLimit() <= 1) return;
        ProbeInfoUtil.line(
                info,
                "gregtech.top.parallels",
                ProbeInfoUtil.number(logic.getMaxProgress() > 0 ? logic.getParallelRecipesPerformed() : 0) + " / " +
                        ProbeInfoUtil.number(logic.getParallelLimit()));
    }
}
