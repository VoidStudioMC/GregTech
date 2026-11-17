package gregtech.api.pipenet;

import gregtech.api.GTValues;
import gregtech.api.metatileentity.interfaces.INeighborCache;
import gregtech.api.pipenet.block.BlockPipe;
import gregtech.api.pipenet.tile.IPipeTile;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = GTValues.MODID)
public class PipeNetEventHandler {

    private static final int ALL_FACES = (1 << EnumFacing.VALUES.length) - 1;
    private static final Map<World, Set<ChunkPos>> pendingLoads = new HashMap<>();

    public static void scheduleChunkCacheInvalidation(World world, BlockPos pos) {
        if (world == null || world.isRemote) return;
        pendingLoads.computeIfAbsent(world, key -> new HashSet<>()).add(new ChunkPos(pos));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onChunkLoad(ChunkEvent.Load event) {
        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();
        if (world.isRemote) return;
        invalidateChunkCaches(chunk);
        // TileEntity.onLoad can be deferred until the end of updateEntities. An earlier route lookup
        // must not keep a missing capability cached after those tile entities finish initializing.
        pendingLoads.computeIfAbsent(world, key -> new HashSet<>()).add(chunk.getPos());
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        Set<ChunkPos> pending = pendingLoads.get(event.getWorld());
        if (pending != null) pending.remove(event.getChunk().getPos());
        invalidateChunkCaches(event.getChunk());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.world.isRemote || event.phase != TickEvent.Phase.END) return;
        Set<ChunkPos> pending = pendingLoads.remove(event.world);
        if (pending == null || pending.isEmpty()) return;

        if (pending.size() == 1) {
            ChunkPos pos = pending.iterator().next();
            Chunk chunk = PipeNetUtils.getLoadedChunk(event.world, pos.x, pos.z);
            if (chunk != null) invalidateChunkCaches(chunk);
            return;
        }

        // Merge shared borders before scanning tile entities. A changed chunk needs all faces refreshed,
        // even if another pending chunk also includes it as a neighbor.
        Long2IntMap affectedChunks = new Long2IntOpenHashMap();
        for (ChunkPos pos : pending) {
            if (PipeNetUtils.getLoadedChunk(event.world, pos.x, pos.z) == null) continue;
            affectedChunks.put(ChunkPos.asLong(pos.x, pos.z), ALL_FACES);
            for (EnumFacing facing : EnumFacing.HORIZONTALS) {
                long neighbor = ChunkPos.asLong(pos.x + facing.getXOffset(), pos.z + facing.getZOffset());
                affectedChunks.put(neighbor, affectedChunks.get(neighbor) | (1 << facing.getOpposite().getIndex()));
            }
        }

        Set<PipeNet<?>> networks = new HashSet<>();
        for (Long2IntMap.Entry entry : affectedChunks.long2IntEntrySet()) {
            long pos = entry.getLongKey();
            Chunk chunk = PipeNetUtils.getLoadedChunk(event.world, (int) pos, (int) (pos >> 32));
            if (chunk != null) invalidateNeighbors(chunk, entry.getIntValue(), networks);
        }
        networks.forEach(PipeNet::onPipeConnectionsUpdate);
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        pendingLoads.remove(event.getWorld());
    }

    private static void invalidateChunkCaches(Chunk chunk) {
        World world = chunk.getWorld();
        if (world.isRemote) return;

        Set<PipeNet<?>> networks = new HashSet<>();
        invalidateChunkCaches(chunk, networks);
        networks.forEach(PipeNet::onPipeConnectionsUpdate);
    }

    private static void invalidateChunkCaches(Chunk chunk, Set<PipeNet<?>> networks) {
        World world = chunk.getWorld();
        // A consumer can be in the changed chunk while every pipe is in a neighboring chunk.
        invalidateNeighbors(chunk, ALL_FACES, networks);
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            int x = chunk.x + facing.getXOffset();
            int z = chunk.z + facing.getZOffset();
            Chunk neighbor = PipeNetUtils.getLoadedChunk(world, x, z);
            if (neighbor != null) {
                invalidateNeighbors(neighbor, 1 << facing.getOpposite().getIndex(), networks);
            }
        }
    }

    private static void invalidateNeighbors(Chunk chunk, int faces, Set<PipeNet<?>> networks) {
        Map<BlockPos, TileEntity> tiles = chunk.getTileEntityMap();
        // Addon callbacks can replace tile entities while their neighbors are being invalidated.
        for (TileEntity tile : tiles.values().toArray(new TileEntity[0])) {
            if (!(tile instanceof INeighborCache cache)) continue;
            BlockPos pos = tile.getPos();
            if (tiles.get(pos) != tile) continue;
            int affectedFaces = faces == ALL_FACES ? ALL_FACES : faces & getBorderFaces(pos);
            if (affectedFaces == 0) continue;

            for (EnumFacing facing : EnumFacing.VALUES) {
                if ((affectedFaces & (1 << facing.getIndex())) != 0) {
                    cache.onNeighborChanged(facing);
                }
            }

            if (cache instanceof IPipeTile<?, ?>pipe) {
                BlockPipe<?, ?, ?> block = pipe.getPipeBlock();
                if (block == null) continue;
                PipeNet<?> net = block.getWorldPipeNet(chunk.getWorld()).getNetFromPos(pos);
                if (net != null) networks.add(net);
            }
        }
    }

    private static int getBorderFaces(BlockPos pos) {
        int faces = 0;
        int x = pos.getX() & 15;
        int z = pos.getZ() & 15;
        if (x == 0) faces |= 1 << EnumFacing.WEST.getIndex();
        if (x == 15) faces |= 1 << EnumFacing.EAST.getIndex();
        if (z == 0) faces |= 1 << EnumFacing.NORTH.getIndex();
        if (z == 15) faces |= 1 << EnumFacing.SOUTH.getIndex();
        return faces;
    }
}
