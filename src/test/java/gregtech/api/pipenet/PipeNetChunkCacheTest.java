package gregtech.api.pipenet;

import gregtech.Bootstrap;
import gregtech.api.GregTechAPI;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.ILaserContainer;
import gregtech.api.capability.IOpticalComputationProvider;
import gregtech.api.cover.Cover;
import gregtech.api.metatileentity.NeighborCacheTileEntityBase;
import gregtech.api.unification.material.properties.FluidPipeProperties;
import gregtech.api.unification.material.properties.ItemPipeProperties;
import gregtech.api.unification.material.properties.WireProperties;
import gregtech.common.pipelike.cable.BlockCable;
import gregtech.common.pipelike.cable.Insulation;
import gregtech.common.pipelike.cable.net.EnergyNet;
import gregtech.common.pipelike.cable.net.EnergyNetHandler;
import gregtech.common.pipelike.cable.net.WorldENet;
import gregtech.common.pipelike.cable.tile.TileEntityCable;
import gregtech.common.pipelike.fluidpipe.BlockFluidPipe;
import gregtech.common.pipelike.fluidpipe.FluidPipeType;
import gregtech.common.pipelike.fluidpipe.net.PipeTankList;
import gregtech.common.pipelike.fluidpipe.tile.TileEntityFluidPipe;
import gregtech.common.pipelike.fluidpipe.tile.TileEntityFluidPipeTickable;
import gregtech.common.pipelike.itempipe.BlockItemPipe;
import gregtech.common.pipelike.itempipe.ItemPipeType;
import gregtech.common.pipelike.itempipe.net.ItemNetHandler;
import gregtech.common.pipelike.itempipe.net.ItemPipeNet;
import gregtech.common.pipelike.itempipe.net.WorldItemPipeNet;
import gregtech.common.pipelike.itempipe.tile.TileEntityItemPipe;
import gregtech.common.pipelike.laser.BlockLaserPipe;
import gregtech.common.pipelike.laser.LaserPipeType;
import gregtech.common.pipelike.laser.net.LaserNetHandler;
import gregtech.common.pipelike.laser.net.WorldLaserPipeNet;
import gregtech.common.pipelike.laser.tile.TileEntityLaserPipe;
import gregtech.common.pipelike.optical.BlockOpticalPipe;
import gregtech.common.pipelike.optical.OpticalPipeType;
import gregtech.common.pipelike.optical.net.OpticalNetHandler;
import gregtech.common.pipelike.optical.net.WorldOpticalPipeNet;
import gregtech.common.pipelike.optical.tile.TileEntityOpticalPipe;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.storage.WorldInfo;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.items.ItemStackHandler;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PipeNetChunkCacheTest {

    private static BlockCable cableBlock;
    private static BlockFluidPipe fluidPipeBlock;
    private static BlockItemPipe itemPipeBlock;
    private static BlockLaserPipe laserPipeBlock;
    private static BlockOpticalPipe opticalPipeBlock;
    private TestWorld world;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
        cableBlock = new BlockCable(Insulation.WIRE_SINGLE, GregTechAPI.materialManager.getRegistry("gregtech"));
        fluidPipeBlock = new BlockFluidPipe(FluidPipeType.NORMAL, GregTechAPI.materialManager.getRegistry("gregtech"));
        itemPipeBlock = new BlockItemPipe(ItemPipeType.NORMAL, GregTechAPI.materialManager.getRegistry("gregtech"));
        laserPipeBlock = new BlockLaserPipe(LaserPipeType.NORMAL);
        opticalPipeBlock = new BlockOpticalPipe(OpticalPipeType.NORMAL);
    }

    @AfterEach
    void unloadWorld() {
        PipeNetEventHandler.onWorldUnload(new WorldEvent.Unload(world));
    }

    @BeforeEach
    void setUp() {
        world = new TestWorld();
        world.load(new TestChunk(world, 0, 0));
    }

    @Test
    void clearedWeakReferenceIsRecomputed() throws Exception {
        CacheTile source = world.put(new BlockPos(1, 64, 1), new CacheTile());
        CacheTile neighbor = world.put(source.getPos().east(), new CacheTile());
        assertSame(neighbor, source.getNeighbor(EnumFacing.EAST));

        // Simulate reference processing without relying on nondeterministic garbage collection.
        Field field = NeighborCacheTileEntityBase.class.getDeclaredField("neighbors");
        field.setAccessible(true);
        List<?> refs = (List<?>) field.get(source);
        ((WeakReference<?>) refs.get(EnumFacing.EAST.getIndex())).clear();

        assertSame(neighbor, source.getNeighbor(EnumFacing.EAST));
    }

    @Test
    void missingNeighborRemainsCachedUntilNeighborUpdate() {
        CacheTile source = world.put(new BlockPos(1, 64, 1), new CacheTile());
        assertNull(source.getNeighbor(EnumFacing.EAST));
        int lookups = world.tileLookups;
        assertNull(source.getNeighbor(EnumFacing.EAST));
        assertEquals(lookups, world.tileLookups);

        CacheTile neighbor = world.put(source.getPos().east(), new CacheTile());
        assertNull(source.getNeighbor(EnumFacing.EAST), "A known empty side stays cached until a neighbor update");
        source.onNeighborChanged(EnumFacing.EAST);
        assertSame(neighbor, source.getNeighbor(EnumFacing.EAST));
    }

    @Test
    void tileLoadClearsNeighborsReadBeforeLoading() {
        CacheTile source = world.put(new BlockPos(1, 64, 1), new CacheTile());
        assertNull(source.getNeighbor(EnumFacing.EAST));
        CacheTile neighbor = world.put(source.getPos().east(), new CacheTile());

        source.onLoad();

        assertSame(neighbor, source.getNeighbor(EnumFacing.EAST));
    }

    @ParameterizedTest
    @CsvSource({ "15, 1, EAST", "0, 1, WEST", "1, 0, NORTH", "1, 15, SOUTH",
            "-1, -1, EAST", "-16, -1, WEST", "-1, -16, NORTH", "-1, -1, SOUTH" })
    void borderNeighborIsRefreshedWithoutLoadingMissingChunks(int x, int z, EnumFacing facing) {
        BlockPos sourcePos = new BlockPos(x, 64, z);
        world.load(new TestChunk(world, x >> 4, z >> 4));
        CacheTile source = world.put(sourcePos, new CacheTile());
        BlockPos targetPos = sourcePos.offset(facing);
        TestChunk targetChunk = new TestChunk(world, targetPos.getX() >> 4, targetPos.getZ() >> 4);

        assertNull(source.getNeighbor(facing));
        world.load(targetChunk);
        CacheTile oldNeighbor = world.put(targetPos, new CacheTile());
        assertSame(oldNeighbor, source.getNeighbor(facing));

        world.unload(targetChunk);
        assertFalse(oldNeighbor.isInvalid(), "Chunk unload does not invalidate tile entities");
        int lookups = world.tileLookups;
        assertNull(source.getNeighbor(facing));
        assertEquals(lookups, world.tileLookups, "Unloaded neighbors must not be queried");

        TestChunk replacementChunk = new TestChunk(world, targetChunk.x, targetChunk.z);
        CacheTile newNeighbor = world.put(replacementChunk, targetPos, new CacheTile());
        world.load(replacementChunk);
        assertSame(newNeighbor, source.getNeighbor(facing));
    }

    @Test
    void chunkEventsPreserveQueuedNeighborUnloads() {
        TestChunk sourceChunk = world.loadedChunk(0, 0);
        sourceChunk.unloadQueued = true;
        CacheTile source = world.put(new BlockPos(15, 64, 0), new CacheTile());
        TestChunk targetChunk = new TestChunk(world, 1, 0);
        world.load(targetChunk);
        assertTrue(sourceChunk.unloadQueued);
        world.unload(targetChunk);
        assertTrue(sourceChunk.unloadQueued);
        assertNull(source.getNeighbor(EnumFacing.EAST));
        assertTrue(sourceChunk.unloadQueued);
    }

    @Test
    void unloadingChunkIsIgnoredBeforeProviderRemovesIt() {
        CacheTile source = world.put(new BlockPos(15, 64, 0), new CacheTile());
        TestChunk targetChunk = new TestChunk(world, 1, 0);
        CacheTile neighbor = world.put(targetChunk, source.getPos().east(), new CacheTile());
        world.load(targetChunk);
        assertSame(neighbor, source.getNeighbor(EnumFacing.EAST));

        // Forge fires Unload after setting loaded=false, before removing the chunk from the provider.
        targetChunk.loaded = false;
        PipeNetEventHandler.onChunkUnload(new ChunkEvent.Unload(targetChunk));
        int lookups = world.tileLookups;

        assertNull(source.getNeighbor(EnumFacing.EAST));
        assertEquals(lookups, world.tileLookups);
    }

    @Test
    void energyTransferRecoversAfterCableChunkReload() {
        TestCable source = world.put(new BlockPos(15, 64, 0), new TestCable(EnumFacing.EAST));
        TestChunk targetChunk = new TestChunk(world, 1, 0);
        TestCable oldCable = world.put(targetChunk, new BlockPos(16, 64, 0),
                new TestCable(EnumFacing.WEST, EnumFacing.EAST));
        EnergyConsumer oldConsumer = world.put(targetChunk, new BlockPos(17, 64, 0), new EnergyConsumer());
        world.load(targetChunk);
        WorldENet worldNet = WorldENet.getWorldENet(world);
        addNode(worldNet, source);
        addNode(worldNet, oldCable);
        EnergyNet net = worldNet.getNetFromPos(source.getPos());
        EnergyNetHandler handler = new EnergyNetHandler(net, source, EnumFacing.WEST);

        assertEquals(1, handler.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));
        assertEquals(30, oldConsumer.energy);
        world.unload(targetChunk);
        assertEquals(0, handler.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));
        assertTrue(net.getNetData(source.getPos()).isEmpty());

        TestChunk replacementChunk = new TestChunk(world, 1, 0);
        TestCable newCable = world.put(replacementChunk, oldCable.getPos(),
                new TestCable(EnumFacing.WEST, EnumFacing.EAST));
        EnergyConsumer newConsumer = world.put(replacementChunk, oldConsumer.getPos(), new EnergyConsumer());
        world.load(replacementChunk);

        assertEquals(1, handler.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));
        assertEquals(30, newConsumer.energy);
        assertEquals(30, oldConsumer.energy, "Energy must not reach the unloaded consumer");
        assertSame(newCable, net.getNetData(source.getPos()).get(0).getTargetPipe());
    }

    @Test
    void energyRoutesRefreshWhenOnlyConsumerChunkLoads() {
        TestCable source = world.put(new BlockPos(15, 64, 0), new TestCable(EnumFacing.EAST));
        WorldENet worldNet = WorldENet.getWorldENet(world);
        addNode(worldNet, source);
        EnergyNetHandler handler = new EnergyNetHandler(worldNet.getNetFromPos(source.getPos()), source,
                EnumFacing.WEST);
        assertEquals(0, handler.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));

        TestChunk targetChunk = new TestChunk(world, 1, 0);
        EnergyConsumer consumer = world.put(targetChunk, new BlockPos(16, 64, 0), new EnergyConsumer());
        world.load(targetChunk);

        assertEquals(1, handler.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));
        assertEquals(31, consumer.energy);
    }

    @Test
    void loadedBranchKeepsWorkingWhileAnotherBranchIsUnloaded() {
        TestCable source = world.put(new BlockPos(15, 64, 0), new TestCable(EnumFacing.EAST, EnumFacing.SOUTH));
        EnergyConsumer loadedConsumer = world.put(source.getPos().south(), new EnergyConsumer());
        WorldENet worldNet = WorldENet.getWorldENet(world);
        addNode(worldNet, source);
        EnergyNet net = worldNet.getNetFromPos(source.getPos());
        EnergyNetHandler handler = new EnergyNetHandler(net, source, EnumFacing.WEST);

        assertEquals(1, handler.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));
        assertEquals(31, loadedConsumer.energy);
        assertEquals(1, net.getNetData(source.getPos()).size());

        TestChunk targetChunk = new TestChunk(world, 1, 0);
        EnergyConsumer newConsumer = world.put(targetChunk, source.getPos().east(), new EnergyConsumer());
        world.load(targetChunk);

        assertEquals(2, net.getNetData(source.getPos()).size());
        assertTrue(net.getNetData(source.getPos()).stream().anyMatch(path -> path.getHandler() == newConsumer));
    }

    @Test
    void disconnectedBorderKeepsCachedEnergyRoutes() {
        TestCable source = world.put(new BlockPos(15, 64, 1), new TestCable(EnumFacing.WEST));
        EnergyConsumer consumer = world.put(source.getPos().west(), new EnergyConsumer());
        WorldENet worldNet = WorldENet.getWorldENet(world);
        addNode(worldNet, source);
        EnergyNet net = worldNet.getNetFromPos(source.getPos());
        PipeNetEventHandler.onWorldTick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, world));
        List<?> routes = net.getNetData(source.getPos());
        assertNull(source.getNeighbor(EnumFacing.EAST));

        TestChunk target = new TestChunk(world, 1, 0);
        CacheTile neighbor = world.put(target, source.getPos().east(), new CacheTile());
        world.load(target);
        assertSame(routes, net.getNetData(source.getPos()), "A disconnected border cannot change the routes");
        assertSame(neighbor, source.getNeighbor(EnumFacing.EAST));
        PipeNetEventHandler.onWorldTick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, world));
        assertSame(routes, net.getNetData(source.getPos()));
        world.unload(target);
        assertSame(routes, net.getNetData(source.getPos()));
        assertNull(source.getNeighbor(EnumFacing.EAST));

        EnergyNetHandler handler = new EnergyNetHandler(net, source, EnumFacing.EAST);
        assertEquals(1, handler.acceptEnergyFromNetwork(EnumFacing.EAST, 32, 1));
        assertEquals(31, consumer.energy);
    }

    @Test
    void mergedBorderRefreshIncludesConnectedFaceAtChunkCorner() {
        PipeNetEventHandler.onWorldTick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, world));
        world.setData("gregtech.e_net", new CountingWorldENet());
        TestCable source = world.put(new BlockPos(15, 64, 15), new TestCable(EnumFacing.EAST));
        WorldENet worldNet = WorldENet.getWorldENet(world);
        addNode(worldNet, source);
        CountingEnergyNet net = (CountingEnergyNet) worldNet.getNetFromPos(source.getPos());
        EnergyNetHandler handler = new EnergyNetHandler(net, source, EnumFacing.WEST);
        TestChunk east = new TestChunk(world, 1, 0);
        LateEnergyConsumer consumer = world.put(east, source.getPos().east(), new LateEnergyConsumer());
        world.load(east);
        world.load(new TestChunk(world, 0, 1));
        assertEquals(0, handler.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));
        consumer.onLoad();
        net.updates = 0;

        PipeNetEventHandler.onWorldTick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, world));

        assertEquals(1, net.updates);
        assertEquals(1, handler.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));
        assertEquals(31, consumer.getEnergyStored());
    }

    @Test
    void chunkReloadInvalidatesCachedMissingNeighbor() {
        CacheTile source = world.put(new BlockPos(15, 64, 0), new CacheTile());
        TestChunk targetChunk = new TestChunk(world, 1, 0);
        world.load(targetChunk);
        assertNull(source.getNeighbor(EnumFacing.EAST));
        world.unload(targetChunk);

        TestChunk replacementChunk = new TestChunk(world, 1, 0);
        CacheTile neighbor = world.put(replacementChunk, source.getPos().east(), new CacheTile());
        world.load(replacementChunk);

        assertSame(neighbor, source.getNeighbor(EnumFacing.EAST));
    }

    @Test
    void fluidPipeFindsReplacementNeighborAfterChunkReload() {
        TestFluidPipe source = world.put(new BlockPos(15, 64, 0), new TestFluidPipe());
        TestChunk targetChunk = new TestChunk(world, 1, 0);
        CacheTile oldNeighbor = world.put(targetChunk, new BlockPos(16, 64, 0), new CacheTile());
        world.load(targetChunk);
        assertSame(oldNeighbor, source.getNeighbor(EnumFacing.EAST));
        world.unload(targetChunk);
        assertNull(source.getNeighbor(EnumFacing.EAST));

        TestChunk replacementChunk = new TestChunk(world, 1, 0);
        CacheTile newNeighbor = world.put(replacementChunk, oldNeighbor.getPos(), new CacheTile());
        world.load(replacementChunk);
        assertSame(newNeighbor, source.getNeighbor(EnumFacing.EAST));
    }

    @Test
    void cachedEnergyHandlerRejectsUnloadingAndUnloadedOwner() {
        TestCable source = world.put(new BlockPos(1, 64, 1), new TestCable(EnumFacing.EAST));
        EnergyConsumer consumer = world.put(source.getPos().east(), new EnergyConsumer());
        WorldENet worldNet = WorldENet.getWorldENet(world);
        addNode(worldNet, source);
        EnergyNetHandler handler = new EnergyNetHandler(worldNet.getNetFromPos(source.getPos()), source,
                EnumFacing.WEST);
        assertEquals(1, handler.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));

        TestChunk chunk = world.loadedChunk(0, 0);
        chunk.loaded = false;
        assertFalse(source.isInvalid());
        int lookups = world.tileLookups;
        assertEquals(0, handler.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));
        assertEquals(0, handler.getEnergyCanBeInserted());
        assertFalse(handler.inputsEnergy(EnumFacing.WEST));
        assertEquals(lookups, world.tileLookups);

        world.unload(chunk);
        lookups = world.tileLookups;
        assertEquals(0, handler.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));
        assertTrue(handler.getNet().getNetData(source.getPos()).isEmpty());
        assertEquals(31, consumer.energy);
        assertEquals(lookups, world.tileLookups);
    }

    @Test
    void cachedEnergyHandlerRejectsReplacementAtSamePosition() {
        TestCable source = world.put(new BlockPos(1, 64, 1), new TestCable(EnumFacing.EAST));
        WorldENet worldNet = WorldENet.getWorldENet(world);
        addNode(worldNet, source);
        EnergyNetHandler oldHandler = new EnergyNetHandler(worldNet.getNetFromPos(source.getPos()), source,
                EnumFacing.WEST);
        world.unload(world.loadedChunk(0, 0));

        TestChunk replacement = new TestChunk(world, 0, 0);
        world.put(replacement, source.getPos(), new TestCable(EnumFacing.EAST));
        EnergyConsumer consumer = world.put(replacement, source.getPos().east(), new EnergyConsumer());
        world.load(replacement);

        assertFalse(source.isInvalid());
        assertEquals(0, oldHandler.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));
        assertEquals(0, consumer.energy);
    }

    @Test
    void cachedEnergyHandlerFollowsNetworkAfterReconnect() {
        TestCable source = world.put(new BlockPos(1, 64, 1), new TestCable(EnumFacing.EAST));
        EnergyConsumer consumer = world.put(source.getPos().east(), new EnergyConsumer());
        WorldENet worldNet = WorldENet.getWorldENet(world);
        addNode(worldNet, source);
        EnergyNet oldNet = worldNet.getNetFromPos(source.getPos());
        EnergyNetHandler handler = new EnergyNetHandler(oldNet, source, EnumFacing.WEST);
        worldNet.removeNode(source.getPos());
        addNode(worldNet, source);
        assertNotSame(oldNet, worldNet.getNetFromPos(source.getPos()));

        assertEquals(1, handler.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));
        assertEquals(31, consumer.energy);
        assertSame(worldNet.getNetFromPos(source.getPos()), handler.getNet());
    }

    @Test
    void energyRoutesRecoverWhenCapabilityInitializesAfterChunkLoad() {
        TestCable source = world.put(new BlockPos(15, 64, 0), new TestCable(EnumFacing.EAST));
        WorldENet worldNet = WorldENet.getWorldENet(world);
        addNode(worldNet, source);
        EnergyNetHandler handler = new EnergyNetHandler(worldNet.getNetFromPos(source.getPos()), source,
                EnumFacing.WEST);
        TestChunk target = new TestChunk(world, 1, 0);
        LateEnergyConsumer consumer = world.put(target, source.getPos().east(), new LateEnergyConsumer());
        world.load(target);
        assertEquals(0, handler.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));
        PipeNetEventHandler.onWorldTick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.START, world));
        assertEquals(0, handler.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));
        consumer.onLoad();

        PipeNetEventHandler.onWorldTick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, world));

        assertEquals(1, handler.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));
        assertEquals(31, consumer.getEnergyStored());
    }

    @Test
    void cachedEnergyHandlerResumesForSameDormantTile() {
        TestCable source = world.put(new BlockPos(1, 64, 1), new TestCable(EnumFacing.EAST));
        EnergyConsumer consumer = world.put(source.getPos().east(), new EnergyConsumer());
        WorldENet worldNet = WorldENet.getWorldENet(world);
        addNode(worldNet, source);
        EnergyNetHandler handler = new EnergyNetHandler(worldNet.getNetFromPos(source.getPos()), source,
                EnumFacing.WEST);
        TestChunk chunk = world.loadedChunk(0, 0);
        world.unload(chunk);
        assertEquals(0, handler.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));
        world.load(chunk);

        assertEquals(1, handler.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));
        assertEquals(31, consumer.energy);
    }

    @Test
    void cachedItemHandlerRejectsReplacementAtSamePosition() {
        TestItemPipe source = world.put(new BlockPos(1, 64, 1), new TestItemPipe());
        WorldItemPipeNet worldNet = WorldItemPipeNet.getWorldPipeNet(world);
        worldNet.addNode(source.getPos(), source.getNodeData(), 0, 63, true);
        ItemNetHandler handler = new ItemNetHandler(worldNet.getNetFromPos(source.getPos()), source, EnumFacing.WEST) {

            @Override
            public Cover getCoverOnNeighbour(TileEntityItemPipe pipe, EnumFacing side) {
                return null;
            }
        };
        world.unload(world.loadedChunk(0, 0));
        ItemStack stack = new ItemStack(Items.APPLE, 16);
        assertSame(stack, handler.insertItem(0, stack, false));
        assertTrue(handler.getNet().getNetData(source.getPos(), EnumFacing.WEST).isEmpty());

        TestChunk replacement = new TestChunk(world, 0, 0);
        world.put(replacement, source.getPos(), new TestItemPipe());
        InventoryTile inventory = world.put(replacement, source.getPos().east(), new InventoryTile());
        world.load(replacement);

        assertSame(stack, handler.insertItem(0, stack, false));
        assertTrue(inventory.inventory.getStackInSlot(0).isEmpty());
    }

    @Test
    void cachedFluidHandlerDoesNotModifyUnloadedTanks() {
        TestTickableFluidPipe source = world.put(new BlockPos(1, 64, 1), new TestTickableFluidPipe());
        FluidTank tank = new FluidTank(new FluidStack(FluidRegistry.WATER, 100), 1000);
        PipeTankList handler = new PipeTankList(source, EnumFacing.WEST, tank);
        assertEquals(1, handler.getTankProperties().length);
        world.unload(world.loadedChunk(0, 0));

        assertEquals(0, handler.fill(new FluidStack(FluidRegistry.WATER, 100), true));
        assertNull(handler.drain(50, true));
        assertNull(handler.drain(new FluidStack(FluidRegistry.WATER, 50), true));
        assertEquals(0, handler.getTankProperties().length);
        assertEquals(100, tank.getFluidAmount());

        TestChunk replacement = new TestChunk(world, 0, 0);
        TestTickableFluidPipe newPipe = world.put(replacement, source.getPos(), new TestTickableFluidPipe());
        world.load(replacement);
        assertEquals(0, handler.fill(new FluidStack(FluidRegistry.WATER, 100), true));
        assertNull(handler.drain(50, true));
        assertEquals(100, tank.getFluidAmount());
        assertEquals(0, newPipe.getFluidTanks()[0].getFluidAmount());
    }

    @Test
    void laserActivationSkipsUnloadedNetworkNodes() {
        TestLaserPipe source = world.put(new BlockPos(15, 64, 0), new TestLaserPipe());
        LaserConsumer consumer = world.put(source.getPos().west(), new LaserConsumer());
        TestChunk target = new TestChunk(world, 1, 0);
        TestLaserPipe remote = world.put(target, source.getPos().east(), new TestLaserPipe());
        world.load(target);
        WorldLaserPipeNet worldNet = WorldLaserPipeNet.getWorldPipeNet(world);
        worldNet.addNode(source.getPos(), source.getNodeData(), 0, 63, true);
        worldNet.addNode(remote.getPos(), remote.getNodeData(), 0, 63, true);
        LaserNetHandler handler = new LaserNetHandler(worldNet.getNetFromPos(source.getPos()), source, EnumFacing.EAST);
        world.unload(target);

        assertEquals(1, handler.acceptEnergyFromNetwork(EnumFacing.EAST, 32, 1));
        assertEquals(32, consumer.getEnergyStored());
        assertEquals(1, source.activeCalls);
        assertEquals(0, remote.activeCalls);
    }

    @Test
    void cachedLaserHandlerRejectsReplacementAtSamePosition() {
        TestLaserPipe source = world.put(new BlockPos(1, 64, 1), new TestLaserPipe());
        WorldLaserPipeNet worldNet = WorldLaserPipeNet.getWorldPipeNet(world);
        worldNet.addNode(source.getPos(), source.getNodeData(), 0, 63, true);
        LaserNetHandler handler = new LaserNetHandler(worldNet.getNetFromPos(source.getPos()), source, EnumFacing.WEST);
        world.unload(world.loadedChunk(0, 0));
        assertEquals(0, handler.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));
        TestChunk replacement = new TestChunk(world, 0, 0);
        world.put(replacement, source.getPos(), new TestLaserPipe());
        LaserConsumer consumer = world.put(replacement, source.getPos().east(), new LaserConsumer());
        world.load(replacement);

        assertEquals(0, handler.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));
        assertEquals(0, consumer.getEnergyStored());
    }

    @Test
    void opticalActivationSkipsUnloadedNetworkNodes() {
        TestOpticalPipe source = world.put(new BlockPos(15, 64, 0), new TestOpticalPipe());
        ComputationTile consumer = world.put(source.getPos().west(), new ComputationTile());
        TestChunk target = new TestChunk(world, 1, 0);
        TestOpticalPipe remote = world.put(target, source.getPos().east(), new TestOpticalPipe());
        world.load(target);
        WorldOpticalPipeNet worldNet = WorldOpticalPipeNet.getWorldPipeNet(world);
        worldNet.addNode(source.getPos(), source.getNodeData(), 0, 63, true);
        worldNet.addNode(remote.getPos(), remote.getNodeData(), 0, 63, true);
        OpticalNetHandler handler = new OpticalNetHandler(worldNet.getNetFromPos(source.getPos()), source,
                EnumFacing.EAST);
        world.unload(target);

        assertEquals(32, handler.requestCWUt(32, false, Collections.emptySet()));
        assertEquals(1, consumer.requests);
        assertEquals(1, source.activeCalls);
        assertEquals(0, remote.activeCalls);
    }

    @Test
    void cachedOpticalHandlerRejectsReplacementAtSamePosition() {
        TestOpticalPipe source = world.put(new BlockPos(1, 64, 1), new TestOpticalPipe());
        WorldOpticalPipeNet worldNet = WorldOpticalPipeNet.getWorldPipeNet(world);
        worldNet.addNode(source.getPos(), source.getNodeData(), 0, 63, true);
        OpticalNetHandler handler = new OpticalNetHandler(worldNet.getNetFromPos(source.getPos()), source,
                EnumFacing.WEST);
        world.unload(world.loadedChunk(0, 0));
        assertEquals(0, handler.requestCWUt(32, false, Collections.emptySet()));
        TestChunk replacement = new TestChunk(world, 0, 0);
        world.put(replacement, source.getPos(), new TestOpticalPipe());
        ComputationTile consumer = world.put(replacement, source.getPos().east(), new ComputationTile());
        world.load(replacement);

        assertEquals(0, handler.requestCWUt(32, false, Collections.emptySet()));
        assertEquals(0, consumer.requests);
    }

    @Test
    void postLoadRefreshClearsSharedNetworkOncePerTick() {
        world.setData("gregtech.e_net", new CountingWorldENet());
        TestCable source = world.put(new BlockPos(15, 64, 0), new TestCable(EnumFacing.EAST));
        WorldENet worldNet = WorldENet.getWorldENet(world);
        addNode(worldNet, source);
        TestChunk target = new TestChunk(world, 1, 0);
        TestCable remote = world.put(target, source.getPos().east(), new TestCable(EnumFacing.WEST));
        world.load(target);
        addNode(worldNet, remote);
        CountingEnergyNet net = (CountingEnergyNet) worldNet.getNetFromPos(source.getPos());
        net.updates = 0;

        PipeNetEventHandler.onWorldTick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, world));
        assertEquals(1, net.updates);
        PipeNetEventHandler.onWorldTick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, world));
        assertEquals(1, net.updates, "Completed initialization must not trigger repeated route rebuilding");
    }

    @Test
    void postLoadRefreshInvalidatesSharedChunkFacesOnce() {
        int[] changes = new int[EnumFacing.VALUES.length];
        world.put(new BlockPos(15, 64, 15), new CacheTile() {

            @Override
            public void onNeighborChanged(@NotNull EnumFacing facing) {
                super.onNeighborChanged(facing);
                changes[facing.getIndex()]++;
            }
        });
        world.load(new TestChunk(world, 1, 0));
        world.load(new TestChunk(world, 0, 1));
        Arrays.fill(changes, 0);

        PipeNetEventHandler.onWorldTick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, world));

        assertArrayEquals(new int[] { 1, 1, 1, 1, 1, 1 }, changes,
                "A full refresh must subsume the border refreshes from adjacent pending chunks");
    }

    @ParameterizedTest
    @CsvSource({ "15, 15", "-1, -1" })
    void postLoadRefreshMergesBothBordersAtChunkCorner(int x, int z) {
        if (world.loadedChunk(x >> 4, z >> 4) == null) {
            world.load(new TestChunk(world, x >> 4, z >> 4));
        }
        PipeNetEventHandler.onWorldTick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, world));
        int[] changes = new int[EnumFacing.VALUES.length];
        CacheTile source = world.put(new BlockPos(x, 64, z), new CacheTile() {

            @Override
            public void onNeighborChanged(@NotNull EnumFacing facing) {
                super.onNeighborChanged(facing);
                changes[facing.getIndex()]++;
            }
        });
        assertNull(source.getNeighbor(EnumFacing.EAST));
        assertNull(source.getNeighbor(EnumFacing.SOUTH));
        TestChunk east = new TestChunk(world, (x + 1) >> 4, z >> 4);
        CacheTile eastNeighbor = world.put(east, source.getPos().east(), new CacheTile());
        world.load(east);
        TestChunk south = new TestChunk(world, x >> 4, (z + 1) >> 4);
        CacheTile southNeighbor = world.put(south, source.getPos().south(), new CacheTile());
        world.load(south);
        Arrays.fill(changes, 0);

        PipeNetEventHandler.onWorldTick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, world));

        int[] expected = new int[EnumFacing.VALUES.length];
        expected[EnumFacing.EAST.getIndex()] = 1;
        expected[EnumFacing.SOUTH.getIndex()] = 1;
        assertArrayEquals(expected, changes);
        assertSame(eastNeighbor, source.getNeighbor(EnumFacing.EAST));
        assertSame(southNeighbor, source.getNeighbor(EnumFacing.SOUTH));
    }

    @Test
    void neighborRefreshSkipsTilesReplacedByCallbacks() {
        CacheTile[] tiles = new CacheTile[2];
        int[] changes = new int[2];
        int[] replacementChanges = new int[1];
        CacheTile replacement = new CacheTile() {

            @Override
            public void onNeighborChanged(@NotNull EnumFacing facing) {
                replacementChanges[0]++;
                super.onNeighborChanged(facing);
            }
        };
        for (int i = 0; i < tiles.length; i++) {
            int index = i;
            tiles[i] = world.put(new BlockPos(i + 1, 64, 1), new CacheTile() {

                @Override
                public void onNeighborChanged(@NotNull EnumFacing facing) {
                    super.onNeighborChanged(facing);
                    changes[index]++;
                    if (changes[0] + changes[1] == 1) {
                        PipeNetChunkCacheTest.this.world.put(tiles[1 - index].getPos(), replacement);
                    }
                }
            });
        }

        PipeNetEventHandler.onWorldTick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, world));

        assertEquals(0, replacementChanges[0], "Refresh only the tile instances present when the scan started");
        assertTrue((changes[0] == 6 && changes[1] == 0) || (changes[0] == 0 && changes[1] == 6));
    }

    @Test
    void worldUnloadDiscardsPendingRefreshes() {
        world.setData("gregtech.e_net", new CountingWorldENet());
        TestCable source = world.put(new BlockPos(1, 64, 1), new TestCable(EnumFacing.EAST));
        WorldENet worldNet = WorldENet.getWorldENet(world);
        addNode(worldNet, source);
        CountingEnergyNet net = (CountingEnergyNet) worldNet.getNetFromPos(source.getPos());
        PipeNetEventHandler.onWorldUnload(new WorldEvent.Unload(world));

        PipeNetEventHandler.onWorldTick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, world));

        assertEquals(0, net.updates);
    }

    @Test
    void neighborLookupPreservesQueuedUnload() {
        CacheTile source = world.put(new BlockPos(15, 64, 0), new CacheTile());
        TestChunk target = new TestChunk(world, 1, 0);
        CacheTile neighbor = world.put(target, source.getPos().east(), new CacheTile());
        world.load(target);
        target.unloadQueued = true;

        assertSame(neighbor, source.getNeighbor(EnumFacing.EAST));
        assertTrue(target.unloadQueued);
    }

    @Test
    void energyHandlerRecoversWhenRecipientThrows() {
        TestCable source = world.put(new BlockPos(1, 64, 1), new TestCable(EnumFacing.EAST));
        EnergyConsumer consumer = world.put(source.getPos().east(), new EnergyConsumer() {

            private boolean throwOnce = true;

            @Override
            public long acceptEnergyFromNetwork(EnumFacing side, long voltage, long amperage) {
                if (throwOnce) {
                    throwOnce = false;
                    throw new IllegalStateException("Recipient failed");
                }
                return super.acceptEnergyFromNetwork(side, voltage, amperage);
            }
        });
        WorldENet worldNet = WorldENet.getWorldENet(world);
        addNode(worldNet, source);
        EnergyNetHandler handler = new EnergyNetHandler(worldNet.getNetFromPos(source.getPos()), source,
                EnumFacing.WEST);

        assertThrows(IllegalStateException.class, () -> handler.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));
        assertEquals(1, handler.acceptEnergyFromNetwork(EnumFacing.WEST, 32, 1));
        assertEquals(31, consumer.energy);
    }

    private static void addNode(WorldENet worldNet, TestCable cable) {
        int connections = 0;
        for (EnumFacing facing : cable.connections) {
            connections |= 1 << facing.getIndex();
        }
        worldNet.addNode(cable.getPos(), cable.getNodeData(), 0, connections, true);
    }

    private static class TestWorld extends World {

        private final Map<String, WorldSavedData> savedData = new HashMap<>();
        private int tileLookups;

        TestWorld() {
            super(null, new WorldInfo(new NBTTagCompound()), new WorldProviderSurface(), new Profiler(), false);
            chunkProvider = new ChunkProviderServer(null, null, null) {

                @NotNull
                @Override
                public Chunk provideChunk(int x, int z) {
                    Chunk chunk = loadedChunks.get(ChunkPos.asLong(x, z));
                    if (chunk == null) throw new AssertionError("Unexpected chunk load at " + x + ", " + z);
                    return chunk;
                }
            };
        }

        TestChunk loadedChunk(int x, int z) {
            return (TestChunk) ((ChunkProviderServer) chunkProvider).loadedChunks.get(ChunkPos.asLong(x, z));
        }

        void load(TestChunk chunk) {
            chunk.loaded = true;
            ((ChunkProviderServer) chunkProvider).loadedChunks.put(ChunkPos.asLong(chunk.x, chunk.z), chunk);
            PipeNetEventHandler.onChunkLoad(new ChunkEvent.Load(chunk));
        }

        void unload(TestChunk chunk) {
            chunk.loaded = false;
            PipeNetEventHandler.onChunkUnload(new ChunkEvent.Unload(chunk));
            ((ChunkProviderServer) chunkProvider).loadedChunks.remove(ChunkPos.asLong(chunk.x, chunk.z));
            chunk.getTileEntityMap().values().forEach(TileEntity::onChunkUnload);
        }

        <T extends TileEntity> T put(BlockPos pos, T tile) {
            return put(loadedChunk(pos.getX() >> 4, pos.getZ() >> 4), pos, tile);
        }

        <T extends TileEntity> T put(TestChunk chunk, BlockPos pos, T tile) {
            tile.setWorld(this);
            tile.setPos(pos);
            chunk.getTileEntityMap().put(pos, tile);
            return tile;
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            return chunkProvider;
        }

        @Override
        protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
            return loadedChunk(x, z) != null;
        }

        @Override
        public TileEntity getTileEntity(@NotNull BlockPos pos) {
            return getChunk(pos).getTileEntityMap().get(pos);
        }

        @NotNull
        @Override
        public IBlockState getBlockState(@NotNull BlockPos pos) {
            return Blocks.AIR.getDefaultState();
        }

        @Override
        public WorldSavedData loadData(@NotNull Class<? extends WorldSavedData> type, @NotNull String name) {
            return savedData.get(name);
        }

        @Override
        public void setData(@NotNull String name, @NotNull WorldSavedData data) {
            savedData.put(name, data);
        }
    }

    private static class TestChunk extends Chunk {

        private boolean loaded;

        TestChunk(World world, int x, int z) {
            super(world, x, z);
        }

        @Override
        public boolean isLoaded() {
            return loaded;
        }

        @Override
        public Map<BlockPos, TileEntity> getTileEntityMap() {
            ((TestWorld) getWorld()).tileLookups++;
            return super.getTileEntityMap();
        }
    }

    private static class CacheTile extends NeighborCacheTileEntityBase {

        @Override
        public World world() {
            return getWorld();
        }

        @Override
        public BlockPos pos() {
            return getPos();
        }

        @Override
        public void markAsDirty() {}

        @Override
        public void notifyBlockUpdate() {}

        @Override
        public void writeInitialSyncData(@NotNull PacketBuffer buf) {}

        @Override
        public void receiveInitialSyncData(@NotNull PacketBuffer buf) {}

        @Override
        public void receiveCustomData(int discriminator, @NotNull PacketBuffer buf) {}
    }

    private static class TestCable extends TileEntityCable {

        private final EnumSet<EnumFacing> connections;

        TestCable(EnumFacing... connections) {
            this.connections = EnumSet.copyOf(Arrays.asList(connections));
        }

        @Override
        public BlockCable getPipeBlock() {
            return cableBlock;
        }

        @Override
        public WireProperties getNodeData() {
            return new WireProperties(32, 4, 1);
        }

        @Override
        public boolean isConnected(EnumFacing side) {
            return connections.contains(side);
        }
    }

    private static class TestFluidPipe extends TileEntityFluidPipe {

        @Override
        public BlockFluidPipe getPipeBlock() {
            return fluidPipeBlock;
        }
    }

    private static class TestItemPipe extends TileEntityItemPipe {

        @Override
        public BlockItemPipe getPipeBlock() {
            return itemPipeBlock;
        }

        @Override
        public ItemPipeProperties getNodeData() {
            return new ItemPipeProperties(1, 64);
        }

        @Override
        public boolean isConnected(EnumFacing side) {
            return side.getAxis() == EnumFacing.Axis.X;
        }
    }

    private static class TestTickableFluidPipe extends TileEntityFluidPipeTickable {

        @Override
        public BlockFluidPipe getPipeBlock() {
            return fluidPipeBlock;
        }

        @Override
        public FluidPipeProperties getNodeData() {
            return new FluidPipeProperties(1000, 100, true, true, true, true);
        }
    }

    private static class TestLaserPipe extends TileEntityLaserPipe {

        private int activeCalls;

        @Override
        public BlockLaserPipe getPipeBlock() {
            return laserPipeBlock;
        }

        @Override
        public boolean isConnected(EnumFacing side) {
            return side.getAxis() == EnumFacing.Axis.X;
        }

        @Override
        public void setActive(boolean active, int duration) {
            if (active) activeCalls++;
        }
    }

    private static class TestOpticalPipe extends TileEntityOpticalPipe {

        private int activeCalls;

        @Override
        public BlockOpticalPipe getPipeBlock() {
            return opticalPipeBlock;
        }

        @Override
        public boolean isConnected(EnumFacing side) {
            return side.getAxis() == EnumFacing.Axis.X;
        }

        @Override
        public void setActive(boolean active, int duration) {
            if (active) activeCalls++;
        }
    }

    private static class InventoryTile extends TileEntity {

        private final ItemStackHandler inventory = new ItemStackHandler(1);

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getCapability(@NotNull Capability<T> capability, EnumFacing side) {
            return (T) inventory;
        }
    }

    private static class LaserConsumer extends EnergyConsumer implements ILaserContainer {}

    private static class ComputationTile extends TileEntity implements IOpticalComputationProvider {

        private int requests;

        @Override
        public boolean hasCapability(@NotNull Capability<?> capability, EnumFacing side) {
            return true;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getCapability(@NotNull Capability<T> capability, EnumFacing side) {
            return (T) this;
        }

        @Override
        public int requestCWUt(int amount, boolean simulate, @NotNull Collection<IOpticalComputationProvider> seen) {
            if (!simulate) requests++;
            return amount;
        }

        @Override
        public int getMaxCWUt(@NotNull Collection<IOpticalComputationProvider> seen) {
            return 32;
        }

        @Override
        public boolean canBridge(@NotNull Collection<IOpticalComputationProvider> seen) {
            return true;
        }
    }

    private static class EnergyConsumer extends TileEntity implements IEnergyContainer {

        private long energy;

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getCapability(@NotNull Capability<T> capability, EnumFacing side) {
            return (T) this;
        }

        @Override
        public long acceptEnergyFromNetwork(EnumFacing side, long voltage, long amperage) {
            energy += voltage * amperage;
            return amperage;
        }

        @Override
        public boolean inputsEnergy(EnumFacing side) {
            return true;
        }

        @Override
        public long changeEnergy(long amount) {
            energy += amount;
            return amount;
        }

        @Override
        public long getEnergyStored() {
            return energy;
        }

        @Override
        public long getEnergyCapacity() {
            return 10000;
        }

        @Override
        public long getInputAmperage() {
            return 4;
        }

        @Override
        public long getInputVoltage() {
            return 32;
        }
    }

    private static class LateEnergyConsumer extends EnergyConsumer {

        private boolean ready;

        @Override
        public void onLoad() {
            super.onLoad();
            ready = true;
        }

        @Override
        public <T> T getCapability(@NotNull Capability<T> capability, EnumFacing side) {
            return ready ? super.getCapability(capability, side) : null;
        }
    }

    private static class CountingWorldENet extends WorldENet {

        CountingWorldENet() {
            super("gregtech.e_net");
        }

        @Override
        protected EnergyNet createNetInstance() {
            return new CountingEnergyNet(this);
        }
    }

    private static class CountingEnergyNet extends EnergyNet {

        private int updates;

        CountingEnergyNet(WorldENet world) {
            super(world);
        }

        @Override
        public void onPipeConnectionsUpdate() {
            super.onPipeConnectionsUpdate();
            updates++;
        }
    }
}
