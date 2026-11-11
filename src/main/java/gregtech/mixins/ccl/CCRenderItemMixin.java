package gregtech.mixins.ccl;

import gregtech.client.renderer.handler.QuantumTankItemRenderer;

import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.item.ItemStack;

import codechicken.lib.render.item.CCRenderItem;
import codechicken.lib.render.item.IItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CCRenderItem.class, remap = false)
public class CCRenderItemMixin {

    // CCL overrides this Minecraft method, which has different names in development and production.
    @Inject(method = { "renderItemModelIntoGUI", "func_191962_a" }, at = @At("HEAD"), require = 1)
    private void gregTechCEu$renderQuantumTankBackground(ItemStack stack, int x, int y, IBakedModel model,
                                                         CallbackInfo ci) {
        if (model instanceof IItemRenderer) {
            QuantumTankItemRenderer.renderSlotBackground(stack, x, y);
        }
    }
}
