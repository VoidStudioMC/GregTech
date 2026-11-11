package gregtech.client.renderer.handler;

import gregtech.api.util.GTUtility;
import gregtech.client.utils.ItemRenderCompat;
import gregtech.client.utils.TooltipHelper;
import gregtech.common.ConfigHolder;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumTank;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.render.state.GlStateTracker;
import org.lwjgl.opengl.GL11;

import static net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack.FLUID_NBT_KEY;

@SideOnly(Side.CLIENT)
public final class QuantumTankItemRenderer {

    private QuantumTankItemRenderer() {}

    public static void renderSlotBackground(ItemStack rawStack, int x, int y) {
        if (!ConfigHolder.client.colorQuantumTankSlots || rawStack.isEmpty()) {
            return;
        }
        if (ConfigHolder.client.colorQuantumTankSlotsOnlyOnShift && !TooltipHelper.isShiftDown()) {
            return;
        }

        ItemStack stack = ItemRenderCompat.getRepresentedStack(rawStack);
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(FLUID_NBT_KEY, Constants.NBT.TAG_COMPOUND) ||
                !(GTUtility.getMetaTileEntity(stack) instanceof MetaTileEntityQuantumTank)) {
            return;
        }

        FluidStack fluid = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag(FLUID_NBT_KEY));
        if (fluid == null || fluid.amount <= 0) {
            return;
        }

        ResourceLocation still = fluid.getFluid().getStill(fluid);
        if (still == null) {
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(still.toString());
        int color = fluid.getFluid().getColor(fluid);
        float red = (color >> 16 & 0xFF) / 255.0f;
        float green = (color >> 8 & 0xFF) / 255.0f;
        float blue = (color & 0xFF) / 255.0f;

        boolean textureEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateTracker.pushState();
        try {
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.disableAlpha();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.color(red, green, blue, 1);

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            buffer.pos(x, y + 16, 0).tex(sprite.getMinU(), sprite.getMaxV()).endVertex();
            buffer.pos(x + 16, y + 16, 0).tex(sprite.getMaxU(), sprite.getMaxV()).endVertex();
            buffer.pos(x + 16, y, 0).tex(sprite.getMaxU(), sprite.getMinV()).endVertex();
            buffer.pos(x, y, 0).tex(sprite.getMinU(), sprite.getMinV()).endVertex();
            tessellator.draw();
        } finally {
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.bindTexture(previousTexture);
            if (!textureEnabled) {
                GlStateManager.disableTexture2D();
            }
            GlStateTracker.popState();
        }
    }
}
