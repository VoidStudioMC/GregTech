package gregtech.common.covers;

import gregtech.api.capability.impl.CommonFluidFilters;
import gregtech.api.cover.CoverDefinition;
import gregtech.api.cover.CoverableView;
import gregtech.api.util.GTTransferUtils;

import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import org.jetbrains.annotations.NotNull;

public class CoverSteamRegulator extends CoverPump {

    protected static final CommonFluidFilters steamFilter = CommonFluidFilters.STEAM;

    public CoverSteamRegulator(@NotNull CoverDefinition definition, @NotNull CoverableView coverableView,
                               @NotNull EnumFacing attachedSide, int tier, int mbPerTick) {
        super(definition, coverableView, attachedSide, tier, mbPerTick);
    }

    @Override
    protected int doTransferFluidsInternal(IFluidHandler myFluidHandler, IFluidHandler fluidHandler,
                                           int transferLimit) {
        if (pumpMode == PumpMode.IMPORT) {
            return GTTransferUtils.transferFluids(fluidHandler, myFluidHandler, transferLimit,
                    steamFilter::test);
        } else if (pumpMode == PumpMode.EXPORT) {
            return GTTransferUtils.transferFluids(myFluidHandler, fluidHandler, transferLimit,
                    steamFilter::test);
        }
        return 0;
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
