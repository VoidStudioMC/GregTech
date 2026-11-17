package gregtech.api.pipenet;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;

import org.jetbrains.annotations.Nullable;

public final class PipeNetUtils {

    private PipeNetUtils() {}

    @Nullable
    public static Chunk getLoadedChunk(World world, int x, int z) {
        if (world == null) return null;
        IChunkProvider provider = world.getChunkProvider();
        // ChunkProviderServer.getLoadedChunk cancels queued unloads even for a read-only lookup.
        Chunk chunk = provider instanceof ChunkProviderServer server ?
                server.loadedChunks.get(ChunkPos.asLong(x, z)) : provider.getLoadedChunk(x, z);
        return chunk != null && chunk.isLoaded() ? chunk : null;
    }

    @Nullable
    public static TileEntity getTileEntityIfLoaded(World world, BlockPos pos) {
        if (world == null || world.isOutsideBuildHeight(pos)) return null;
        Chunk chunk = getLoadedChunk(world, pos.getX() >> 4, pos.getZ() >> 4);
        // Do not create tile entities or consult a dormant chunk when checking an old capability.
        TileEntity tile = chunk == null ? null : chunk.getTileEntityMap().get(pos);
        return tile != null && !tile.isInvalid() ? tile : null;
    }

    public static boolean isTileEntityLoaded(TileEntity tile) {
        return tile != null && !tile.isInvalid() &&
                getTileEntityIfLoaded(tile.getWorld(), tile.getPos()) == tile;
    }
}
