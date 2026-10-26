package gregtech.integration.jei.utils.render;

import gregtech.api.recipes.chance.boost.BoostableChanceEntry;
import gregtech.api.recipes.chance.output.ChancedOutputLogic;
import gregtech.api.util.TextFormattingUtil;
import gregtech.client.utils.RenderUtil;
import gregtech.common.ConfigHolder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fluids.FluidStack;

import mezz.jei.api.gui.IDrawable;
import mezz.jei.plugins.vanilla.ingredients.fluid.FluidStackRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FluidStackTextRenderer extends FluidStackRenderer {

    private boolean notConsumed;
    private int chanceBase = -1;
    private int chanceBoost = -1;
    private ChancedOutputLogic chanceLogic;

    public FluidStackTextRenderer(int capacityMb, boolean showCapacity, int width, int height,
                                  @Nullable IDrawable overlay) {
        super(capacityMb, showCapacity, width, height, overlay);
        this.notConsumed = false;
    }

    public FluidStackTextRenderer setNotConsumed(boolean notConsumed) {
        this.notConsumed = notConsumed;
        return this;
    }

    public FluidStackTextRenderer(int capacityMb, boolean showCapacity, int width, int height,
                                  @Nullable IDrawable overlay,
                                  BoostableChanceEntry<FluidStack> chance, ChancedOutputLogic chanceLogic) {
        if (chance != null) {
            this.chanceBase = chance.getChance();
            this.chanceBoost = chance.getChanceBoost();
            this.chanceLogic = chanceLogic;
        }
        this.notConsumed = false;
    }

    @Override
    public void render(@NotNull Minecraft minecraft, final int xPosition, final int yPosition,
                       @Nullable FluidStack fluidStack) {
        if (fluidStack == null)
            return;

        GlStateManager.disableBlend();

        RenderUtil.drawFluidForGui(fluidStack, fluidStack.amount, xPosition, yPosition, 17, 17);

        float amountScale = (float) ConfigHolder.client.guiConfig.fluidAmountTextScaleInJei;
        float invAmountScale = 1.0f / amountScale;

        GlStateManager.pushMatrix();
        GlStateManager.scale(amountScale, amountScale, 1);

        String s = TextFormattingUtil.formatLongToCompactString(fluidStack.amount, 4) + "L";

        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        fontRenderer.drawStringWithShadow(s,
                (xPosition + 6) * invAmountScale - fontRenderer.getStringWidth(s) + 9.5f * invAmountScale,
                (yPosition + 11) * invAmountScale, 0xFFFFFF);

        GlStateManager.popMatrix();

        float scale = (float) ConfigHolder.client.guiConfig.chanceAndNotConsumedTextScaleInJei;
        float invScale = 1.0f / scale;

        if (this.chanceBase >= 0) {
            GlStateManager.pushMatrix();
            GlStateManager.scale(scale, scale, 1);
            // z hackery to render the text above the item
            GlStateManager.translate(0, 0, 160);

            String s2 = (this.chanceBase / 100) + "%";
            if (this.chanceLogic != null && this.chanceLogic != ChancedOutputLogic.NONE &&
                    this.chanceLogic != ChancedOutputLogic.OR) {
                s2 += "*";
            } else if (this.chanceBoost > 0) {
                s2 += "+";
            }

            fontRenderer.drawStringWithShadow(s2,
                    (xPosition + 6) * invScale - fontRenderer.getStringWidth(s2) + 9.5f * invScale,
                    (yPosition + 1) * invScale, 0xFFFF00);

            GlStateManager.popMatrix();
        } else if (notConsumed) {
            GlStateManager.pushMatrix();
            GlStateManager.scale(scale, scale, 1);

            fontRenderer.drawStringWithShadow("NC",
                    (xPosition + 6) * invScale - fontRenderer.getStringWidth("NC") + 9.5f * invScale,
                    (yPosition + 1) * invScale, 0xFFFF00);

            GlStateManager.popMatrix();
        }

        GlStateManager.enableBlend();
    }
}
