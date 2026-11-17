package gregtech.api.pipenet;

import gregtech.Bootstrap;
import gregtech.api.GregTechAPI;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.metatileentity.NeighborCacheTileEntityBase;
import gregtech.api.unification.material.properties.WireProperties;
import gregtech.common.pipelike.cable.BlockCable;
import gregtech.common.pipelike.cable.Insulation;
import gregtech.common.pipelike.cable.net.EnergyNet;
import gregtech.common.pipelike.cable.net.EnergyNetHandler;
import gregtech.common.pipelike.cable.net.WorldENet;
import gregtech.common.pipelike.cable.tile.TileEntityCable;
import gregtech.common.pipelike.fluidpipe.BlockFluidPipe;
import gregtech.common.pipelike.fluidpipe.FluidPipeType;
import gregtech.common.pipelike.fluidpipe.tile.TileEntityFluidPipe;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
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

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PipeNetChunkCacheTest {

    private static BlockCable cableBlock;
    private static BlockFluidPipe fluidPipeBlock;
    private TestWorld world;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
        cableBlock = new BlockCable(Insulation.WIRE_SINGLE, GregTechAPI.materialManager.getRegistry("gregtech"));
        fluidPipeBlock = new BlockFluidPipe(FluidPipeType.NORMAL, GregTechAPI.materialManager.getRegistry("gregtech"));
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
            tileLookups++;
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
}
