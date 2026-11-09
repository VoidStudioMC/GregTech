package gregtech.api.mui.factory;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.mui.GTGuis;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumHand;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.AbstractUIFactory;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.factory.inventory.InventoryType;
import com.cleanroommc.modularui.factory.inventory.InventoryTypes;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class MetaItemGuiFactory extends AbstractUIFactory<PlayerInventoryGuiData> {

    public static final MetaItemGuiFactory INSTANCE = new MetaItemGuiFactory();

    private MetaItemGuiFactory() {
        super("gregtech:meta_item");
    }

    public static void open(EntityPlayer player, EnumHand hand) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(hand);
        int slotIndex = hand == EnumHand.OFF_HAND ? 40 : player.inventory.currentItem;
        PlayerInventoryGuiData guiData = new PlayerInventoryGuiData(player, InventoryTypes.PLAYER, slotIndex);
        GuiManager.open(INSTANCE, guiData, verifyServerSide(player));
    }

    @Override
    public @NotNull IGuiHolder<PlayerInventoryGuiData> getGuiHolder(PlayerInventoryGuiData data) {
        ItemStack stack = data.getUsedItemStack();
        if (!(stack.getItem() instanceof MetaItem<?>metaItem)) {
            throw new IllegalArgumentException("Found item is not a valid MetaItem!");
        }
        MetaItem<?>.MetaValueItem valueItem = metaItem.getItem(stack);
        if (valueItem == null || valueItem.getUIManager() == null) {
            throw new IllegalArgumentException("Found MetaItem is not a gui holder!");
        }
        return valueItem.getUIManager();
    }

    @Override
    public ModularPanel createPanel(PlayerInventoryGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        GTGuis.bindPlayerInventory(guiData, syncManager, settings);
        return super.createPanel(guiData, syncManager, settings);
    }

    @Override
    public void writeGuiData(PlayerInventoryGuiData guiData, PacketBuffer buffer) {
        guiData.getInventoryType().write(buffer);
        buffer.writeVarInt(guiData.getSlotIndex());
    }

    @Override
    public @NotNull PlayerInventoryGuiData readGuiData(EntityPlayer player, PacketBuffer buffer) {
        return new PlayerInventoryGuiData(player, InventoryType.read(buffer), buffer.readVarInt());
    }
}
