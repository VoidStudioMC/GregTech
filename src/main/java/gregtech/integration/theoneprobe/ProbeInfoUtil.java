package gregtech.integration.theoneprobe;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IMultiblockController;
import gregtech.api.capability.IWorkable;
import gregtech.api.capability.impl.AbstractRecipeLogic;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.util.GTUtility;
import gregtech.api.util.TextFormattingUtil;
import gregtech.integration.theoneprobe.element.FluidStackElement;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fluids.FluidStack;

import mcjty.theoneprobe.api.ElementAlignment;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.NumberFormat;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;

public final class ProbeInfoUtil {

    public static final int YELLOW = 0xFFEEE600;

    private ProbeInfoUtil() {}

    public static String lang(String key) {
        return IProbeInfo.STARTLOC + key + IProbeInfo.ENDLOC;
    }

    public static String number(long value) {
        return TextFormattingUtil.formatNumbers(value);
    }

    public static String voltage(long voltage) {
        int tier = Math.max(0, Math.min(GTUtility.getOCTierByVoltage(voltage), GTValues.VOCNF.length - 1));
        return number(voltage) + " EU/t (" + GTValues.VOCNF[tier] + TextFormatting.RESET + ")";
    }

    public static void line(IProbeInfo info, String key, String value) {
        info.text(TextFormatting.GRAY + lang(key) + ": " + TextFormatting.AQUA + value);
    }

    public static void item(IProbeInfo info, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        info.horizontal(info.defaultLayoutStyle().alignment(ElementAlignment.ALIGN_CENTER))
                .item(stack)
                .itemLabel(stack)
                .text(TextFormatting.AQUA + " × " + number(stack.getCount()));
    }

    public static void fluid(IProbeInfo info, FluidStack stack) {
        if (stack == null || stack.amount <= 0) return;
        info.element(new FluidStackElement(stack));
    }

    public static void progress(IProbeInfo info, long current, long max, String suffix, int color) {
        if (max <= 0) return;
        info.progress(
                Math.max(0, Math.min(current, max)),
                max,
                info.defaultProgressStyle()
                        .numberFormat(NumberFormat.COMMAS)
                        .suffix(" / " + number(max) + suffix)
                        .filledColor(color)
                        .alternateFilledColor(color)
                        .borderColor(0xFF555555));
    }

    public static boolean isFormed(@Nullable MetaTileEntity machine) {
        return !(machine instanceof IMultiblockController controller) || controller.isStructureFormed();
    }

    public static @Nullable MetaTileEntity getMetaTileEntity(@Nullable TileEntity tile) {
        return tile instanceof IGregTechTileEntity holder ? holder.getMetaTileEntity() : null;
    }

    public static void fillProgress(IProbeInfo info, BigInteger stored, BigInteger capacity) {
        if (capacity.signum() <= 0) return;
        // Preserve substation capacities beyond Long.MAX_VALUE, with a percentage accurate to 0.01%.
        long fill = stored.max(BigInteger.ZERO)
                .min(capacity)
                .multiply(BigInteger.valueOf(10_000))
                .divide(capacity)
                .longValue();
        info.progress(
                fill,
                10_000L,
                info.defaultProgressStyle()
                        .showText(false)
                        .filledColor(YELLOW)
                        .alternateFilledColor(YELLOW)
                        .borderColor(0xFF555555));
    }

    public static @Nullable AbstractRecipeLogic recipeLogic(TileEntity tile) {
        return tile.getCapability(GregtechTileCapabilities.CAPABILITY_RECIPE_LOGIC, null);
    }

    public static @Nullable IWorkable workable(TileEntity tile, @Nullable MetaTileEntity machine) {
        if (!isFormed(machine)) return null;
        return tile.getCapability(GregtechTileCapabilities.CAPABILITY_WORKABLE, null);
    }
}
