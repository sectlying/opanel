package net.opanel.neoforge_helper;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.opanel.common.OPanelChunkAccessor;
import net.opanel.map.Tile;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public abstract class BaseNeoChunkAccessor implements OPanelChunkAccessor {
    protected final MinecraftServer server;

    public BaseNeoChunkAccessor(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public Tile readLiveTile(int chunkX, int chunkZ) {
        try {
            Future<Tile> future = server.submit(() -> readOnMainThread(chunkX, chunkZ));
            return future.get(SYNC_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    abstract protected Tile readOnMainThread(int chunkX, int chunkZ);
    abstract protected Tile.Section buildSection(LevelChunk chunk, int sectionY);
}
