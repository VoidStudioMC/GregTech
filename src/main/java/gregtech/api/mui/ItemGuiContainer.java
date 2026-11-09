package gregtech.api.mui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.ModularContainer;
import org.jetbrains.annotations.NotNull;

/**
 * Checks the source item before each click, including when it was moved outside the GUI between ticks.
 */
public class ItemGuiContainer extends ModularContainer {

    @Override
    public @NotNull ItemStack slotClick(int slotId, int mouseButton, @NotNull ClickType clickType,
                                        @NotNull EntityPlayer player) {
        if (!canInteractWith(player)) return ItemStack.EMPTY;
        return super.slotClick(slotId, mouseButton, clickType, player);
    }
}
