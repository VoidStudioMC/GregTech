package gregtech.integration.theoneprobe.element;

import gregtech.api.util.TextFormattingUtil;
import gregtech.client.utils.RenderUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import io.netty.buffer.ByteBuf;
import mcjty.theoneprobe.api.IElement;
import mcjty.theoneprobe.api.ITheOneProbe;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/** Fluid icon ported from GT TOP Addition's FluidStackElement, originally based on Gate Guardian's Monazite work. */
public final class FluidStackElement implements IElement {

    private static int elementId = -1;
    private final @Nullable FluidStack stack;

    public static void register(ITheOneProbe probe) {
        if (elementId < 0) {
            elementId = probe.registerElementFactory(FluidStackElement::new);
        }
    }

    public FluidStackElement(FluidStack stack) {
        this.stack = stack.copy();
    }

    private FluidStackElement(ByteBuf buffer) {
        try {
            NBTTagCompound tag = new PacketBuffer(buffer).readCompoundTag();
            this.stack = tag == null ? null : FluidStack.loadFluidStackFromNBT(tag);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot decode TOP fluid element", exception);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void render(int x, int y) {
        if (stack == null || stack.getFluid() == null || stack.amount <= 0) return;
        GlStateManager.disableBlend();
        RenderUtil.drawFluidForGui(stack, stack.amount, x + 2, y + 2, 16, 16);
        GlStateManager.pushMatrix();
        GlStateManager.scale(0.5, 0.5, 1);
        String amount = TextFormattingUtil.formatLongToCompactString(stack.amount, 4) + "L";
        var font = Minecraft.getMinecraft().fontRenderer;
        font.drawStringWithShadow(amount, (x + 18) * 2 - font.getStringWidth(amount), (y + 12) * 2, 0xFFFFFF);
        GlStateManager.popMatrix();
        GlStateManager.enableBlend();
        font.drawStringWithShadow(getLabel(), x + 22, y + 6, 0xFFFFFF);
    }

    @SideOnly(Side.CLIENT)
    private String getLabel() {
        return stack == null ? "" : TextFormatting.GRAY + stack.getLocalizedName() + TextFormatting.AQUA + " × " +
                TextFormattingUtil.formatNumbers(stack.amount) + " L";
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getWidth() {
        return 22 + Minecraft.getMinecraft().fontRenderer.getStringWidth(getLabel());
    }

    @Override
    public int getHeight() {
        return 20;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        new PacketBuffer(buffer).writeCompoundTag(stack == null ? null : stack.writeToNBT(new NBTTagCompound()));
    }

    @Override
    public int getID() {
        if (elementId < 0) throw new IllegalStateException("TOP fluid element factory has not been registered");
        return elementId;
    }
}
