package gregtech.integration.jei.utils.render;

import gregtech.api.recipes.chance.boost.BoostableChanceEntry;
import gregtech.api.recipes.chance.output.ChancedOutputLogic;
import gregtech.common.ConfigHolder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;

import mezz.jei.plugins.vanilla.ingredients.item.ItemStackRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ItemStackTextRenderer extends ItemStackRenderer {

    private final int chanceBase;
    private final int chanceBoost;
    private final ChancedOutputLogic chanceLogic;
    private final boolean notConsumed;

    public ItemStackTextRenderer(BoostableChanceEntry<ItemStack> chance, ChancedOutputLogic chanceLogic) {
        if (chance != null && chanceLogic != null) {
            this.chanceBase = chance.getChance();
            this.chanceBoost = chance.getChanceBoost();
            this.chanceLogic = chanceLogic;
        } else {
            this.chanceBase = -1;
            this.chanceBoost = -1;
            this.chanceLogic = null;
        }
        this.notConsumed = false;
    }

    public ItemStackTextRenderer(int chanceBase, int chanceBoost) {
        this.chanceBase = chanceBase;
        this.chanceBoost = chanceBoost;
        this.chanceLogic = null;
        this.notConsumed = false;
    }

    public ItemStackTextRenderer(boolean notConsumed) {
        this.chanceBase = -1;
        this.chanceBoost = -1;
        this.chanceLogic = null;
        this.notConsumed = notConsumed;
    }

    @Override
    public void render(@NotNull Minecraft minecraft, int xPosition, int yPosition, @Nullable ItemStack ingredient) {
        super.render(minecraft, xPosition, yPosition, ingredient);

        float scale = (float) ConfigHolder.client.guiConfig.chanceAndNotConsumedTextScaleInJei;
        float invScale = 1.0f / scale;

        if (this.chanceBase >= 0) {
            GlStateManager.disableBlend();
            GlStateManager.pushMatrix();
            GlStateManager.scale(scale, scale, 1);
            // z hackery to render the text above the item
            GlStateManager.translate(0, 0, 160);

            String s = (this.chanceBase / 100) + "%";
            if (this.chanceLogic != null && this.chanceLogic != ChancedOutputLogic.NONE &&
                    this.chanceLogic != ChancedOutputLogic.OR) {
                s += "*";
            } else if (this.chanceBoost > 0) {
                s += "+";
            }

            FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
            fontRenderer.drawStringWithShadow(s,
                    (xPosition + 6) * invScale - fontRenderer.getStringWidth(s) + 9.5f * invScale,
                    (yPosition + 1) * invScale, 0xFFFF00);

            GlStateManager.popMatrix();
            GlStateManager.enableBlend();
        } else if (this.notConsumed) {
            GlStateManager.disableBlend();
            GlStateManager.pushMatrix();
            GlStateManager.scale(scale, scale, 1);
            // z hackery to render the text above the item
            GlStateManager.translate(0, 0, 160);

            FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
            fontRenderer.drawStringWithShadow("NC",
                    (xPosition + 6) * invScale - fontRenderer.getStringWidth("NC") + 9.5f * invScale,
                    (yPosition + 1) * invScale, 0xFFFF00);

            GlStateManager.popMatrix();
            GlStateManager.enableBlend();
        }
    }
}
