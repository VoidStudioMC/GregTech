package gregtech.common.covers;

import gregtech.api.capability.impl.CommonFluidFilters;
import gregtech.api.cover.CoverDefinition;
import gregtech.api.cover.CoverableView;
import gregtech.api.util.GTTransferUtils;

import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import org.jetbrains.annotations.NotNull;

public class CoverSteamRegulator extends CoverFluidRegulator {

    protected TransferMode transferMode = TransferMode.TRANSFER_ANY;
    protected static final CommonFluidFilters steamFilter = CommonFluidFilters.STEAM;

    public CoverSteamRegulator(@NotNull CoverDefinition definition, @NotNull CoverableView coverableView,
                               @NotNull EnumFacing attachedSide, int tier, int mbPerTick) {
        super(definition, coverableView, attachedSide, tier, mbPerTick);
    }

    @Override
    protected int doTransferFluidsInternal(IFluidHandler myFluidHandler, IFluidHandler fluidHandler,
                                           int transferLimit) {
        IFluidHandler sourceHandler;
        IFluidHandler destHandler;

        if (pumpMode == PumpMode.IMPORT) {
            sourceHandler = fluidHandler;
            destHandler = myFluidHandler;
        } else if (pumpMode == PumpMode.EXPORT) {
            sourceHandler = myFluidHandler;
            destHandler = fluidHandler;
        } else {
            return 0;
        }
        return switch (transferMode) {
            case TRANSFER_ANY -> GTTransferUtils.transferFluids(sourceHandler, destHandler, transferLimit,
                    steamFilter::test);
            case KEEP_EXACT -> doKeepExact(transferLimit, sourceHandler, destHandler,
                    steamFilter::test,
                    this.fluidFilterContainer.getTransferSize());
            case TRANSFER_EXACT -> doTransferExact(transferLimit, sourceHandler, destHandler,
                    steamFilter::test, this.fluidFilterContainer.getTransferSize());
        };
    }

    @Override
    protected boolean checkInputFluid(FluidStack fluidStack) {
        return steamFilter.test(fluidStack);
    }

    @Override
    protected boolean createFilterRow() {
        return false;
    }
}
