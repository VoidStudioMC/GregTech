package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IControllable;
import gregtech.api.capability.impl.CommonFluidFilters;
import gregtech.api.capability.impl.FilteredItemHandler;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.NotifiableFilteredFluidHandler;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumTank;
import gregtech.common.mui.widget.GTFluidSlot;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.RichTextWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MetaTileEntityLargeSteamHatch extends MetaTileEntityMultiblockNotifiablePart
                                           implements IMultiblockAbilityPart<IFluidTank>, IControllable {

    public static final int INITIAL_INVENTORY_SIZE = 64000;

    protected final FluidTank fluidTank;
    protected boolean workingEnabled;

    public MetaTileEntityLargeSteamHatch(ResourceLocation metaTileEntityId, int tier, boolean isExportHatch) {
        super(metaTileEntityId, tier, isExportHatch);
        this.fluidTank = new NotifiableFilteredFluidHandler(getInventorySize(), this, isExportHatch).setFilter(
                CommonFluidFilters.STEAM);
        initializeInventory();
        this.workingEnabled = true;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityLargeSteamHatch(metaTileEntityId, getTier(), isExportHatch);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("workingEnabled", workingEnabled);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        if (data.hasKey("workingEnabled")) {
            this.workingEnabled = data.getBoolean("workingEnabled");
        }
        if (data.hasKey("ContainerInventory")) {
            MetaTileEntityQuantumTank.legacyTankItemHandlerNBTReading(this, data.getCompoundTag("ContainerInventory"),
                    0, 1);
        }
    }

    @Override
    public void update() {
        super.update();
        if (!getWorld().isRemote) {
            if (workingEnabled) {
                fillContainerFromInternalTank(fluidTank);
                if (isExportHatch) {
                    pushFluidsIntoNearbyHandlers(getFrontFacing());
                } else {
                    fillInternalTankFromFluidContainer(fluidTank);
                    pullFluidsFromNearbyHandlers(getFrontFacing());
                }
            }
        }
    }

    @Override
    public void setWorkingEnabled(boolean workingEnabled) {
        this.workingEnabled = workingEnabled;
        World world = getWorld();
        if (world != null && !world.isRemote) {
            writeCustomData(GregtechDataCodes.WORKING_ENABLED, buf -> buf.writeBoolean(workingEnabled));
        }
    }

    @Override
    public boolean isWorkingEnabled() {
        return workingEnabled;
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == GregtechTileCapabilities.CAPABILITY_CONTROLLABLE) {
            return GregtechTileCapabilities.CAPABILITY_CONTROLLABLE.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(workingEnabled);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.workingEnabled = buf.readBoolean();
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == GregtechDataCodes.WORKING_ENABLED) {
            this.workingEnabled = buf.readBoolean();
        }
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (shouldRenderOverlay()) {
            SimpleOverlayRenderer renderer = isExportHatch ? Textures.PIPE_OUT_OVERLAY : Textures.PIPE_IN_OVERLAY;
            renderer.renderSided(getFrontFacing(), renderState, translation, pipeline);
            SimpleOverlayRenderer overlay = isExportHatch ? Textures.FLUID_HATCH_OUTPUT_OVERLAY :
                    Textures.FLUID_HATCH_INPUT_OVERLAY;
            overlay.renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }

    protected int getInventorySize() {
        return INITIAL_INVENTORY_SIZE * Math.min(Integer.MAX_VALUE, 1 << getTier());
    }

    @Override
    protected FluidTankList createImportFluidHandler() {
        return isExportHatch ? new FluidTankList(false) : new FluidTankList(false, fluidTank);
    }

    @Override
    protected FluidTankList createExportFluidHandler() {
        return isExportHatch ? new FluidTankList(false, fluidTank) : new FluidTankList(false);
    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        return new FilteredItemHandler(this, 1).setFillPredicate(
                FilteredItemHandler.getCapabilityFilter(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY));
    }

    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        return new GTItemStackHandler(this, 1);
    }

    @Override
    public @NotNull List<MultiblockAbility<?>> getAbilities() {
        return isExportHatch ?
                Collections.singletonList(MultiblockAbility.EXPORT_FLUIDS) :
                Arrays.asList(MultiblockAbility.IMPORT_FLUIDS, MultiblockAbility.IMPORT_ITEMS);
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        if (abilityInstances.isKey(MultiblockAbility.EXPORT_FLUIDS) ||
                abilityInstances.isKey(MultiblockAbility.IMPORT_FLUIDS)) {
            abilityInstances.add(this.fluidTank);
        }
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        var fluidSyncHandler = GTFluidSlot.sync(fluidTank)
                .showAmountOnSlot(false)
                .accessibility(true, !isExportHatch);

        return GTGuis.createPanel(this, 176, 166)
                .child(IKey.lang(getMetaFullName()).asWidget().pos(6, 6))

                // export specific
                .childIf(isExportHatch, new ItemSlot()
                        .pos(90, 44)
                        .background(GTGuiTextures.SLOT, GTGuiTextures.OUT_SLOT_OVERLAY)
                        .slot(new ModularSlot(exportItems, 0)
                                .accessibility(false, true)))

                // import specific
                .childIf(!isExportHatch, GTGuiTextures.TANK_ICON.asWidget()
                        .pos(91, 36)
                        .size(14, 15))
                .childIf(!isExportHatch, new ItemSlot()
                        .pos(90, 53)
                        .background(GTGuiTextures.SLOT, GTGuiTextures.OUT_SLOT_OVERLAY)
                        .slot(new ModularSlot(exportItems, 0)
                                .accessibility(false, true)))

                // common ui
                // todo split up these lines into their own widgets and use a scrolling text widget for the name
                .child(new RichTextWidget()
                        .size(81 - 6, (isExportHatch ? 46 : 55) - 8)
                        // .padding(3, 4)
                        .background(GTGuiTextures.DISPLAY.asIcon().size(81, isExportHatch ? 46 : 55))
                        .pos(7 + 3, 16 + 4)
                        .textColor(Color.WHITE.main)
                        .alignment(Alignment.TopLeft)
                        .autoUpdate(true)
                        .textBuilder(richText -> {
                            richText.addLine(IKey.lang("gregtech.gui.fluid_amount"));

                            IKey nameKey = fluidSyncHandler.getFluidNameKey();
                            if (nameKey == IKey.EMPTY) return;

                            String formatted = nameKey.getFormatted();
                            if (formatted.length() > 25) {
                                nameKey = IKey.str(formatted.substring(0, 25) + TextFormatting.WHITE + "...");
                            }

                            richText.addLine(nameKey);
                            richText.addLine(IKey.str(fluidSyncHandler.getFormattedFluidAmount()));
                        }))
                .child(new GTFluidSlot()
                        .disableBackground()
                        .pos(69, isExportHatch ? 43 : 52)
                        .size(18)
                        .syncHandler(fluidSyncHandler))
                .child(new ItemSlot()
                        .pos(90, 16)
                        .background(GTGuiTextures.SLOT, GTGuiTextures.IN_SLOT_OVERLAY)
                        .slot(new ModularSlot(importItems, 0)
                                .singletonSlotGroup()
                                .filter(stack -> {
                                    if (!isExportHatch) return true;
                                    var h = FluidUtil.getFluidHandler(stack);
                                    if (h == null) return false;
                                    return h.getTankProperties()[0].getContents() == null;
                                })
                                .accessibility(true, true)))
                .bindPlayerInventory();
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        if (this.isExportHatch)
            tooltip.add(I18n.format("gregtech.machine.fluid_hatch.export.tooltip"));
        else
            tooltip.add(I18n.format("gregtech.machine.fluid_hatch.import.tooltip"));
        tooltip.add(I18n.format("gregtech.universal.tooltip.fluid_storage_capacity", getInventorySize()));
        tooltip.add(I18n.format("gregtech.universal.enabled"));
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.access_covers"));
        tooltip.add(I18n.format("gregtech.tool_action.wrench.set_facing"));
        super.addToolUsages(stack, world, tooltip, advanced);
    }
}
