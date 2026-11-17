package gregtech.integration.theoneprobe.provider;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IMultipleRecipeMaps;
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
        IMultipleRecipeMaps multiple = tile.getCapability(GregtechTileCapabilities.CAPABILITY_MULTIPLE_RECIPEMAPS,
                null);
        if (multiple == null && machine instanceof IMultipleRecipeMaps maps) {
            multiple = maps;
        }
        if ((multiple == null || multiple.getAvailableRecipeMaps().length == 1) && logic.getRecipeMap() != null) {
            ProbeInfoUtil.line(
                    info,
                    "gregtech.top.recipe_map",
                    ProbeInfoUtil.lang(logic.getRecipeMap().getTranslationKey()));
        }
        if (logic.usesEnergy()) {
            ProbeInfoUtil.line(info, "gregtech.top.max_voltage", ProbeInfoUtil.voltage(logic.getMaxVoltage()));
        }
        if (logic.isHasNotEnoughEnergy()) {
            info.text(TextFormatting.YELLOW + ProbeInfoUtil.lang("gregtech.top.not_enough_energy"));
        }
        if (logic.isRecipeOutputsFull()) {
            info.text(TextFormatting.YELLOW + ProbeInfoUtil.lang("gregtech.top.outputs_full"));
        }
        // Previous recipes remain cached after completion; only show the currently prepared outputs.
        if (logic.getMaxProgress() <= 0) return;
        if (logic.getRecipeCWUt() > 0) {
            ProbeInfoUtil.line(
                    info,
                    "gregtech.top.computation",
                    ProbeInfoUtil.number(logic.getCurrentDrawnCWUt()) + " / " +
                            ProbeInfoUtil.number(logic.getRecipeCWUt()) + " CWU/t");
            if (logic.isHasNotEnoughComputation()) {
                info.text(TextFormatting.YELLOW + ProbeInfoUtil.lang("gregtech.top.not_enough_computation"));
            }
        }
        var fuelInputs = logic.getRecipeFuelInputs();
        if (!fuelInputs.isEmpty()) {
            info.text(TextFormatting.GRAY + ProbeInfoUtil.lang("gregtech.top.fuel"));
            fuelInputs.forEach(input -> ProbeInfoUtil.fluid(info, input));
            int duration = logic.getInfoProviderFuelDuration();
            ProbeInfoUtil.line(info, "gregtech.top.fuel_duration", ProbeInfoUtil.number(duration) + " t");
        }
    }
}
