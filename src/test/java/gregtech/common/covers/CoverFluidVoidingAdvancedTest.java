package gregtech.common.covers;

import gregtech.Bootstrap;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.cover.CoverableView;
import gregtech.common.covers.filter.SimpleFluidFilter;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.IFluidHandler;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CoverFluidVoidingAdvancedTest {

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    public void matchingVoidsAllFluidsWithoutAFilter() {
        FluidTank water = waterTank(5000);
        FluidTank lava = lavaTank(3000);
        CoverFluidVoidingAdvanced cover = cover(new FluidTankList(true, water, lava));

        cover.doTransferFluids();

        assertEquals(0, water.getFluidAmount());
        assertEquals(0, lava.getFluidAmount());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    public void matchingRespectsWhitelistAndBlacklist(boolean blacklist) {
        FluidTank water = waterTank(5000);
        FluidTank lava = lavaTank(3000);
        CoverFluidVoidingAdvanced cover = cover(new FluidTankList(true, water, lava));
        SimpleFluidFilter filter = filter(cover);
        filter.getFilterReader().getFluidTank(0).setFluid(new FluidStack(FluidRegistry.WATER, 1));
        cover.getFluidFilterContainer().setBlacklistFilter(blacklist);

        cover.doTransferFluids();

        assertEquals(blacklist ? 5000 : 0, water.getFluidAmount());
        assertEquals(blacklist ? 0 : 3000, lava.getFluidAmount());
    }

    @Test
    public void overflowKeepsTheConfiguredAmount() {
        FluidTank water = waterTank(5000);
        CoverFluidVoidingAdvanced cover = overflowCover(water, 1000);

        cover.doTransferFluids();
        cover.doTransferFluids();

        assertEquals(1000, water.getFluidAmount());
    }

    @Test
    public void overflowLeavesFluidsBelowTheLimitUntouched() {
        FluidTank water = waterTank(500);
        CoverFluidVoidingAdvanced cover = overflowCover(water, 1000);

        cover.doTransferFluids();

        assertEquals(500, water.getFluidAmount());
    }

    @Test
    public void overflowUsesPerFluidFilterAmountsAndSkipsEmptyTanks() {
        FluidTank water = waterTank(5000);
        FluidTank lava = lavaTank(3000);
        CoverFluidVoidingAdvanced cover = cover(new FluidTankList(true, new FluidTank(64000), water, lava));
        cover.setVoidingMode(VoidingMode.VOID_OVERFLOW);
        SimpleFluidFilter filter = filter(cover);
        filter.getFilterReader().getFluidTank(0).setFluid(new FluidStack(FluidRegistry.WATER, 1000));
        filter.getFilterReader().getFluidTank(1).setFluid(new FluidStack(FluidRegistry.LAVA, 2000));

        cover.doTransferFluids();

        assertEquals(1000, water.getFluidAmount());
        assertEquals(2000, lava.getFluidAmount());
    }

    @Test
    public void overflowBlacklistUsesTheGlobalAmount() {
        FluidTank water = waterTank(5000);
        FluidTank lava = lavaTank(3000);
        CoverFluidVoidingAdvanced cover = cover(new FluidTankList(true, water, lava));
        cover.setVoidingMode(VoidingMode.VOID_OVERFLOW);
        SimpleFluidFilter filter = filter(cover);
        filter.getFilterReader().getFluidTank(0).setFluid(new FluidStack(FluidRegistry.WATER, 1));
        cover.getFluidFilterContainer().setBlacklistFilter(true);
        cover.setStringTransferRate("1000");

        cover.doTransferFluids();

        assertEquals(5000, water.getFluidAmount());
        assertEquals(1000, lava.getFluidAmount());
    }

    @Test
    public void overflowHonorsTheSuppliedPredicate() {
        FluidTank water = waterTank(5000);
        FluidTank lava = lavaTank(3000);
        IFluidHandler handler = new FluidTankList(true, water, lava);
        CoverFluidVoidingAdvanced cover = cover(handler);
        cover.setVoidingMode(VoidingMode.VOID_OVERFLOW);

        cover.voidOverflow(handler, fluid -> fluid.getFluid() == FluidRegistry.WATER, 1000);

        assertEquals(1000, water.getFluidAmount());
        assertEquals(3000, lava.getFluidAmount());
    }

    @Test
    public void overflowKeepsOneAmountAcrossTanksOfTheSameFluid() {
        FluidTank first = waterTank(1500);
        FluidTank second = waterTank(1500);
        CoverFluidVoidingAdvanced cover = overflowCover(new FluidTankList(true, first, second), 1000);

        cover.doTransferFluids();
        assertEquals(1000, first.getFluidAmount() + second.getFluidAmount());
        cover.doTransferFluids();

        assertEquals(1000, first.getFluidAmount() + second.getFluidAmount());
    }

    @Test
    public void overflowHandlesTotalsLargerThanAnInteger() {
        FluidTank first = new FluidTank(new FluidStack(FluidRegistry.WATER, Integer.MAX_VALUE), Integer.MAX_VALUE);
        FluidTank second = new FluidTank(new FluidStack(FluidRegistry.WATER, Integer.MAX_VALUE), Integer.MAX_VALUE);
        CoverFluidVoidingAdvanced cover = overflowCover(new FluidTankList(true, first, second), 1000);

        cover.doTransferFluids();

        assertEquals(1000L, (long) first.getFluidAmount() + second.getFluidAmount());
    }

    @Test
    public void overflowDoesNotDrainWhenDisabled() {
        FluidTank water = waterTank(5000);
        CoverFluidVoidingAdvanced cover = overflowCover(water, 1000);

        cover.update();
        assertEquals(5000, water.getFluidAmount());

        cover.setWorkingEnabled(true);
        cover.update();
        assertEquals(1000, water.getFluidAmount());
    }

    @Test
    public void overflowRestoresTheAmountAfterSaving() {
        FluidTank water = waterTank(5000);
        CoverFluidVoidingAdvanced cover = overflowCover(water, 1000);
        NBTTagCompound saved = new NBTTagCompound();
        cover.writeToNBT(saved);
        CoverFluidVoidingAdvanced restored = cover(water);

        restored.readFromNBT(saved);
        restored.doTransferFluids();

        assertEquals(VoidingMode.VOID_OVERFLOW, restored.getVoidingMode());
        assertEquals(1000, restored.getTransferAmount());
        assertEquals(1000, water.getFluidAmount());
    }

    @Test
    public void overflowMigratesTheLegacyAmountWithoutOverwritingModernSettings() {
        FluidTank water = waterTank(5000);
        CoverFluidVoidingAdvanced cover = overflowCover(water, 1000);
        NBTTagCompound saved = new NBTTagCompound();
        cover.writeToNBT(saved);
        saved.setInteger("TransferAmount", 2000);
        CoverFluidVoidingAdvanced modern = cover(water);
        modern.readFromNBT(saved);
        assertEquals(1000, modern.getTransferAmount());

        saved.getCompoundTag("Filter").removeTag("TransferStackSize");
        CoverFluidVoidingAdvanced legacy = cover(water);
        legacy.readFromNBT(saved);
        legacy.doTransferFluids();

        assertEquals(2000, legacy.getTransferAmount());
        assertEquals(2000, water.getFluidAmount());
    }

    @Test
    public void overflowSynchronizesModeAndAmount() {
        CoverFluidVoidingAdvanced cover = overflowCover(waterTank(5000), 1000);
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        try {
            cover.writeInitialSyncData(buffer);
            CoverFluidVoidingAdvanced client = cover(waterTank(5000));
            client.readInitialSyncData(buffer);

            assertEquals(VoidingMode.VOID_OVERFLOW, client.getVoidingMode());
            assertEquals(1000, client.getTransferAmount());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void bucketUnitsConvertWithoutIntegerOverflow() {
        CoverFluidVoidingAdvanced cover = overflowCover(waterTank(5000), 1000);
        cover.setBucketMode(CoverPump.BucketMode.BUCKET);
        cover.setStringTransferRate("2");
        assertEquals(2000, cover.getTransferAmount());
        assertEquals("2", cover.getStringTransferRate());

        cover.setStringTransferRate("3000000");
        assertEquals(Integer.MAX_VALUE, cover.getTransferAmount());
    }

    private static CoverFluidVoidingAdvanced overflowCover(IFluidHandler handler, int keepAmount) {
        CoverFluidVoidingAdvanced cover = cover(handler);
        cover.setVoidingMode(VoidingMode.VOID_OVERFLOW);
        cover.setStringTransferRate(Integer.toString(keepAmount));
        return cover;
    }

    private static CoverFluidVoidingAdvanced cover(IFluidHandler handler) {
        CoverableView view = (CoverableView) Proxy.newProxyInstance(CoverableView.class.getClassLoader(),
                new Class<?>[] { CoverableView.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getCapability":
                            return handler;
                        case "getOffsetTimer":
                            return 0L;
                        case "markDirty":
                            return null;
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
        return new CoverFluidVoidingAdvanced(null, view, EnumFacing.UP);
    }

    private static SimpleFluidFilter filter(CoverFluidVoidingAdvanced cover) {
        SimpleFluidFilter filter = new SimpleFluidFilter(new ItemStack(Items.PAPER));
        cover.getFluidFilterContainer().setFilter(filter);
        return filter;
    }

    private static FluidTank waterTank(int amount) {
        return new FluidTank(new FluidStack(FluidRegistry.WATER, amount), 64000);
    }

    private static FluidTank lavaTank(int amount) {
        return new FluidTank(new FluidStack(FluidRegistry.LAVA, amount), 64000);
    }
}
