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

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = GTValues.MODID)
public class PipeNetEventHandler {

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
        if (pending == null) return;

        Set<PipeNet<?>> networks = new HashSet<>();
        for (ChunkPos pos : pending) {
            Chunk chunk = PipeNetUtils.getLoadedChunk(event.world, pos.x, pos.z);
            if (chunk != null) invalidateChunkCaches(chunk, networks);
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
        invalidateNeighbors(chunk, null, networks);
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            int x = chunk.x + facing.getXOffset();
            int z = chunk.z + facing.getZOffset();
            Chunk neighbor = PipeNetUtils.getLoadedChunk(world, x, z);
            if (neighbor != null) {
                invalidateNeighbors(neighbor, facing.getOpposite(), networks);
            }
        }
    }

    private static void invalidateNeighbors(Chunk chunk, @Nullable EnumFacing border, Set<PipeNet<?>> networks) {
        // Addon callbacks can replace tile entities while their neighbors are being invalidated.
        for (Map.Entry<BlockPos, TileEntity> entry : new ArrayList<>(chunk.getTileEntityMap().entrySet())) {
            BlockPos pos = entry.getKey();
            if (chunk.getTileEntityMap().get(pos) != entry.getValue()) continue;
            if (border != null && !isOnBorder(pos, border)) continue;
            if (!(entry.getValue() instanceof INeighborCache cache)) continue;

            if (border == null) {
                for (EnumFacing facing : EnumFacing.VALUES) {
                    cache.onNeighborChanged(facing);
                }
            } else {
                cache.onNeighborChanged(border);
            }

            if (cache instanceof IPipeTile<?, ?>pipe) {
                BlockPipe<?, ?, ?> block = pipe.getPipeBlock();
                if (block == null) continue;
                PipeNet<?> net = block.getWorldPipeNet(chunk.getWorld()).getNetFromPos(pos);
                if (net != null) networks.add(net);
            }
        }
    }

    private static boolean isOnBorder(BlockPos pos, EnumFacing border) {
        return switch (border) {
            case WEST -> (pos.getX() & 15) == 0;
            case EAST -> (pos.getX() & 15) == 15;
            case NORTH -> (pos.getZ() & 15) == 0;
            case SOUTH -> (pos.getZ() & 15) == 15;
            default -> false;
        };
    }
}
