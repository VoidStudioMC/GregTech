package gregtech.integration.theoneprobe.provider;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.integration.theoneprobe.ProbeInfoProvider;
import gregtech.integration.theoneprobe.ProbeInfoUtil;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextFormatting;

import mcjty.theoneprobe.api.IProbeInfo;
import org.jetbrains.annotations.Nullable;

/** Port of GT TOP Addition's item output provider, showing icons and exact counts while sneaking. */
public final class RecipeItemOutputInfoProvider extends ProbeInfoProvider {

    public RecipeItemOutputInfoProvider() {
        super("gregtech:recipe_item_outputs");
    }

    @Override
    protected void addProbeInfo(IProbeInfo info, TileEntity tile, @Nullable MetaTileEntity machine, boolean sneaking) {
        if (!sneaking || !ProbeInfoUtil.isFormed(machine)) return;
        var logic = ProbeInfoUtil.recipeLogic(tile);
        if (logic == null || logic.getMaxProgress() <= 0) return;
        var outputs = logic.getRecipeItemOutputs();
        if (outputs.stream().noneMatch(stack -> stack != null && !stack.isEmpty())) return;
        info.text(TextFormatting.GRAY + ProbeInfoUtil.lang("gregtech.top.item_outputs"));
        outputs.forEach(stack -> ProbeInfoUtil.item(info, stack));
    }
}
