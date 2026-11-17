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
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = GTValues.MODID)
public class PipeNetEventHandler {

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        invalidateChunkCaches(event.getChunk());
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        invalidateChunkCaches(event.getChunk());
    }

    private static void invalidateChunkCaches(Chunk chunk) {
        World world = chunk.getWorld();
        if (world.isRemote) return;

        Set<PipeNet<?>> networks = new HashSet<>();
        invalidateNeighbors(chunk, null, networks);
        IChunkProvider provider = world.getChunkProvider();
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            int x = chunk.x + facing.getXOffset();
            int z = chunk.z + facing.getZOffset();
            // getLoadedChunk on the server cancels queued unloads, so read the loaded map directly.
            Chunk neighbor = provider instanceof ChunkProviderServer server ?
                    server.loadedChunks.get(ChunkPos.asLong(x, z)) : provider.getLoadedChunk(x, z);
            if (neighbor != null && neighbor.isLoaded()) {
                invalidateNeighbors(neighbor, facing.getOpposite(), networks);
            }
        }
        // A consumer can be in the changed chunk while every pipe is in a neighboring chunk.
        // Clear each affected route cache once, after both sides of the border have been invalidated.
        networks.forEach(PipeNet::onPipeConnectionsUpdate);
    }

    private static void invalidateNeighbors(Chunk chunk, @Nullable EnumFacing border, Set<PipeNet<?>> networks) {
        for (Map.Entry<BlockPos, TileEntity> entry : chunk.getTileEntityMap().entrySet()) {
            BlockPos pos = entry.getKey();
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
