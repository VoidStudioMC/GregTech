package gregtech.api.metatileentity;

import gregtech.api.metatileentity.interfaces.INeighborCache;
import gregtech.api.pipenet.PipeNetUtils;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;

public abstract class NeighborCacheTileEntityBase extends SyncedTileEntityBase implements INeighborCache {

    private static final WeakReference<TileEntity> NULL = new WeakReference<>(null);
    private static final WeakReference<TileEntity> INVALID = new WeakReference<>(null);

    private final List<WeakReference<TileEntity>> neighbors = Arrays.asList(
            INVALID, INVALID, INVALID, INVALID, INVALID, INVALID);
    private boolean neighborsInvalidated = false;

    public NeighborCacheTileEntityBase() {
        invalidateNeighbors();
    }

    protected void invalidateNeighbors() {
        if (!this.neighborsInvalidated) {
            for (EnumFacing value : EnumFacing.VALUES) {
                this.neighbors.set(value.getIndex(), INVALID);
            }
            this.neighborsInvalidated = true;
        }
    }

    @MustBeInvokedByOverriders
    @Override
    public void setWorld(@NotNull World worldIn) {
        super.setWorld(worldIn);
        invalidateNeighbors();
    }

    @MustBeInvokedByOverriders
    @Override
    public void setPos(@NotNull BlockPos posIn) {
        super.setPos(posIn);
        invalidateNeighbors();
    }

    @MustBeInvokedByOverriders
    @Override
    public void invalidate() {
        super.invalidate();
        invalidateNeighbors();
    }

    @MustBeInvokedByOverriders
    @Override
    public void onLoad() {
        super.onLoad();
        // Neighbors may have been queried before deferred tile initialization finished.
        invalidateNeighbors();
    }

    @MustBeInvokedByOverriders
    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        invalidateNeighbors();
    }

    @Override
    public @Nullable TileEntity getNeighbor(@NotNull EnumFacing facing) {
        if (world == null || pos == null) return null;
        // Chunk unload does not invalidate its tile entities. Never reuse them across an unloaded border,
        // and do not load the neighboring chunk just to look up a connection.
        if (!isNeighborChunkLoaded(facing)) {
            onNeighborChanged(facing);
            return null;
        }
        // if the ref is INVALID, compute neighbor, otherwise, return TE or null
        WeakReference<TileEntity> ref = invalidRef(facing) ? computeNeighbor(facing) : getRef(facing);
        return ref.get();
    }

    private boolean isNeighborChunkLoaded(EnumFacing facing) {
        int x = (pos.getX() + facing.getXOffset()) >> 4;
        int z = (pos.getZ() + facing.getZOffset()) >> 4;
        if (x == pos.getX() >> 4 && z == pos.getZ() >> 4) return true;
        Chunk chunk = PipeNetUtils.getLoadedChunk(world, x, z);
        return chunk != null;
    }

    private boolean invalidRef(EnumFacing facing) {
        WeakReference<TileEntity> ref = getRef(facing);
        if (ref == INVALID) return true;
        if (ref == NULL) return false;
        TileEntity te = ref.get();
        return te == null || te.isInvalid();
    }

    @NotNull
    private WeakReference<TileEntity> computeNeighbor(EnumFacing facing) {
        TileEntity te = PipeNetUtils.getTileEntityIfLoaded(world, pos.offset(facing));
        // avoid making new references to null TEs
        WeakReference<TileEntity> ref = te == null ? NULL : new WeakReference<>(te);
        this.neighbors.set(facing.getIndex(), ref);
        this.neighborsInvalidated = false;
        return ref;
    }

    @NotNull
    private WeakReference<TileEntity> getRef(EnumFacing facing) {
        return this.neighbors.get(facing.getIndex());
    }

    public void onNeighborChanged(@NotNull EnumFacing facing) {
        this.neighbors.set(facing.getIndex(), INVALID);
    }
}
