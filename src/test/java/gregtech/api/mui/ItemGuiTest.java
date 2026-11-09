package gregtech.api.mui;

import gregtech.Bootstrap;
import gregtech.api.util.world.DummyWorld;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import com.cleanroommc.modularui.api.RecipeViewerSettings;
import com.cleanroommc.modularui.core.mixins.early.minecraft.ContainerAccessor;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.factory.inventory.InventoryTypes;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.ItemSlotSH;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.mojang.authlib.GameProfile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ItemGuiTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 2, 3, 4, 5, 6, 7, 8 })
    void sourceSlotCannotBePickedUpShiftClickedDroppedOrSwapped(int sourceIndex) {
        Menu menu = new Menu(sourceIndex);
        int slotId = menu.playerSlot(sourceIndex).slotNumber;

        menu.container.slotClick(slotId, 0, ClickType.PICKUP, menu.player);
        menu.container.slotClick(slotId, 0, ClickType.QUICK_MOVE, menu.player);
        menu.container.slotClick(slotId, 1, ClickType.THROW, menu.player);
        menu.container.slotClick(slotId, (sourceIndex + 1) % 9, ClickType.SWAP, menu.player);
        // Also protect the hotbar source when the clicked slot is elsewhere in the menu.
        menu.container.slotClick(menu.contentSlot().slotNumber, sourceIndex, ClickType.SWAP, menu.player);

        assertSame(menu.sourceStack, menu.data.getUsedItemStack());
        assertEquals(1, menu.sourceStack.getCount());
        assertTrue(menu.player.inventory.getItemStack().isEmpty());
        assertEquals(Items.DIAMOND, menu.contents.getStackInSlot(0).getItem());
    }

    @Test
    void otherInventorySlotsAndContentsRemainAccessible() {
        Menu menu = new Menu(5);
        for (int i = 0; i < 36; i++) {
            if (i != 5) menu.player.inventory.setInventorySlotContents(i, new ItemStack(Items.STICK));
            assertEquals(i != 5, menu.playerSlot(i).canTakeStack(menu.player));
            assertEquals(i != 5, menu.playerSlot(i).isItemValid(new ItemStack(Items.STICK)));
        }

        menu.container.slotClick(menu.contentSlot().slotNumber, 0, ClickType.PICKUP, menu.player);

        assertTrue(menu.contents.getStackInSlot(0).isEmpty());
        assertEquals(Items.DIAMOND, menu.player.inventory.getItemStack().getItem());
        assertSame(menu.sourceStack, menu.data.getUsedItemStack());
    }

    @ParameterizedTest
    @ValueSource(ints = { 5, 40 })
    void removingSourceBlocksContentsBeforeTheNextTick(int sourceIndex) {
        Menu menu = new Menu(sourceIndex);
        menu.player.inventory.setInventorySlotContents(sourceIndex, ItemStack.EMPTY);

        menu.container.slotClick(menu.contentSlot().slotNumber, 0, ClickType.PICKUP, menu.player);

        assertFalse(menu.container.canInteractWith(menu.player));
        assertTrue(menu.player.inventory.getItemStack().isEmpty());
        assertEquals(Items.DIAMOND, menu.contents.getStackInSlot(0).getItem());
    }

    @Test
    void replacingSourceWithAnIdenticalCopyInvalidatesTheMenu() {
        Menu menu = new Menu(5);
        menu.player.inventory.setInventorySlotContents(5, menu.sourceStack.copy());

        menu.container.slotClick(menu.contentSlot().slotNumber, 0, ClickType.PICKUP, menu.player);

        assertFalse(menu.container.canInteractWith(menu.player));
        assertTrue(menu.player.inventory.getItemStack().isEmpty());
        assertEquals(Items.DIAMOND, menu.contents.getStackInSlot(0).getItem());
    }

    @ParameterizedTest
    @ValueSource(ints = { 5, 40 })
    void menuKeepsTheOriginalSlotWhenTheSelectedHotbarSlotChanges(int sourceIndex) {
        Menu menu = new Menu(sourceIndex);
        menu.player.inventory.currentItem = 7;
        menu.player.inventory.setInventorySlotContents(7, new ItemStack(Items.STICK));

        assertSame(menu.sourceStack, menu.data.getUsedItemStack());
        assertTrue(menu.container.canInteractWith(menu.player));
        if (sourceIndex < 9) assertFalse(menu.playerSlot(sourceIndex).canTakeStack(menu.player));
        assertTrue(menu.playerSlot(7).canTakeStack(menu.player));
    }

    @Test
    void delegatedToolMenusCanBindTheSameInventoryTwice() {
        Menu menu = new Menu(5);

        assertDoesNotThrow(() -> GTGuis.bindPlayerInventory(menu.data, menu.syncManager, menu.settings));
        assertFalse(menu.playerSlot(5).canTakeStack(menu.player));
        assertFalse(menu.playerSlot(5).isItemValid(new ItemStack(Items.STICK)));
        assertTrue(menu.container.canInteractWith(menu.player));
    }

    private static final class Menu {

        final EntityPlayer player = new EntityPlayer(DummyWorld.INSTANCE,
                new GameProfile(UUID.randomUUID(), "ItemGuiTest")) {

            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        final ItemStack sourceStack = new ItemStack(Items.APPLE);
        final PlayerInventoryGuiData data;
        final PanelSyncManager syncManager = new PanelSyncManager(new ModularSyncManager(true), true);
        final UISettings settings = new UISettings(RecipeViewerSettings.DUMMY);
        final ItemStackHandler contents = new ItemStackHandler(1);
        final TestContainer container = new TestContainer();

        Menu(int sourceIndex) {
            player.inventory.setInventorySlotContents(sourceIndex, sourceStack);
            data = new PlayerInventoryGuiData(player, InventoryTypes.PLAYER, sourceIndex);
            GTGuis.bindPlayerInventory(data, syncManager, settings);
            contents.setStackInSlot(0, new ItemStack(Items.DIAMOND));
            syncManager.registerSlotGroup("contents", 1);
            syncManager.itemSlot("contents", new ModularSlot(contents, 0).slotGroup("contents"));
            container.construct(player, syncManager.getModularSyncManager(), settings, "item_gui_test", data);
        }

        ModularSlot playerSlot(int index) {
            return ((ItemSlotSH) syncManager.findSyncHandler("player", index)).getSlot();
        }

        ModularSlot contentSlot() {
            return ((ItemSlotSH) syncManager.findSyncHandler("contents")).getSlot();
        }
    }

    // The test harness does not apply MUI's ContainerAccessor mixin. These clicks do not start a drag operation.
    private static final class TestContainer extends ItemGuiContainer implements ContainerAccessor {

        @Override
        public int getDragEvent() {
            return 0;
        }
    }
}
