package gregtech.integration.theoneprobe.provider;

import gregtech.api.capability.impl.ComputationRecipeLogic;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.integration.theoneprobe.ProbeInfoProvider;
import gregtech.integration.theoneprobe.ProbeInfoUtil;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextFormatting;

import mcjty.theoneprobe.api.IProbeInfo;
import org.jetbrains.annotations.Nullable;

public final class RecipeDetailsInfoProvider extends ProbeInfoProvider {

    public RecipeDetailsInfoProvider() {
        super("gregtech:recipe_details");
    }

    @Override
    protected void addProbeInfo(IProbeInfo info, TileEntity tile, @Nullable MetaTileEntity machine, boolean sneaking) {
        if (!ProbeInfoUtil.isFormed(machine)) return;
        var logic = ProbeInfoUtil.recipeLogic(tile);
        if (logic == null) return;
        if (logic.isHasNotEnoughEnergy()) {
            info.text(TextFormatting.YELLOW + ProbeInfoUtil.lang("gregtech.top.not_enough_energy"));
        }
        if (logic.isRecipeOutputsFull()) {
            info.text(TextFormatting.YELLOW + ProbeInfoUtil.lang("gregtech.top.outputs_full"));
        }
        // Previous recipes remain cached after completion; only show details while a recipe is in progress.
        if (logic.getMaxProgress() <= 0) return;
        if (logic instanceof ComputationRecipeLogic computation && computation.getRecipeCWUt() > 0) {
            ProbeInfoUtil.line(
                    info,
                    "gregtech.top.computation",
                    ProbeInfoUtil.number(computation.getCurrentDrawnCWUt()) + " / " +
                            ProbeInfoUtil.number(computation.getRecipeCWUt()) + " CWU/t");
            if (computation.isHasNotEnoughComputation()) {
                info.text(TextFormatting.YELLOW + ProbeInfoUtil.lang("gregtech.top.not_enough_computation"));
            }
        }
    }
}
