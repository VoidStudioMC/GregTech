package gregtech.integration.theoneprobe.provider;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.integration.theoneprobe.ProbeInfoProvider;
import gregtech.integration.theoneprobe.ProbeInfoUtil;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextFormatting;

import mcjty.theoneprobe.api.IProbeInfo;
import org.jetbrains.annotations.Nullable;

/** Port of GT TOP Addition's fluid output provider, including fluid icons and exact amounts. */
public final class RecipeFluidOutputInfoProvider extends ProbeInfoProvider {

    public RecipeFluidOutputInfoProvider() {
        super("gregtech:recipe_fluid_outputs");
    }

    @Override
    protected void addProbeInfo(IProbeInfo info, TileEntity tile, @Nullable MetaTileEntity machine, boolean sneaking) {
        if (!sneaking || !ProbeInfoUtil.isFormed(machine)) return;
        var logic = ProbeInfoUtil.recipeLogic(tile);
        if (logic == null || logic.getMaxProgress() <= 0) return;
        var outputs = logic.getRecipeFluidOutputs();
        if (outputs.stream().noneMatch(stack -> stack != null && stack.amount > 0)) return;
        info.text(TextFormatting.GRAY + ProbeInfoUtil.lang("gregtech.top.fluid_outputs"));
        outputs.forEach(stack -> ProbeInfoUtil.fluid(info, stack));
    }
}
