package net.opanel.bukkit_helper;

import net.opanel.common.OPanelChunkAccessor;
import net.opanel.map.Tile;
import net.opanel.utils.AnvilUtility;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class BaseBukkitChunkAccessor implements OPanelChunkAccessor {
    private static final int SYNC_CALL_TIMEOUT_SECONDS = 5;
    private static final String FALLBACK_BIOME = "minecraft:plains";

    private final JavaPlugin plugin;

    public BaseBukkitChunkAccessor(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Tile readLiveTile(String saveName, int chunkX, int chunkZ) {
        try {
            Future<Tile> future = Bukkit.getScheduler().callSyncMethod(plugin, () ->
                readOnMainThread(saveName, chunkX, chunkZ)
            );
            return future.get(SYNC_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    protected int getMinY() {
        return -64;
    }

    private Tile readOnMainThread(String saveName, int chunkX, int chunkZ) {
        World world = plugin.getServer().getWorld(saveName);
        if(world == null) return null;
        // OPanel renders only the overworld; non-NORMAL dimensions are out of scope.
        if(world.getEnvironment() != World.Environment.NORMAL) return null;
        if(!world.isChunkLoaded(chunkX, chunkZ)) return null;

        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        ChunkSnapshot snap = chunk.getChunkSnapshot(true, true, false);

        final int minY = getMinY();
        final int maxY = world.getMaxHeight();
        final int firstSection = minY >> 4;
        final int lastSection = (maxY - 1) >> 4;

        List<Tile.Section> sections = new ArrayList<>(lastSection - firstSection + 1);
        for(int sectionY = firstSection; sectionY <= lastSection; sectionY++) {
            Tile.Section section = buildSection(snap, sectionY);
            if(section != null) sections.add(section);
        }

        int[] heightMap = new int[256];
        for(int z = 0; z < 16; z++) {
            for(int x = 0; x < 16; x++) {
                int highest = snap.getHighestBlockYAt(x, z);
                // Tile.getHeight() returns storedHeight + minY - 1, so invert.
                int stored = highest + 1 - minY;
                if(stored < 0) stored = 0;
                if(stored > 511) stored = 511; // 9-bit ceiling
                heightMap[z * 16 + x] = stored;
            }
        }
        long[] packedHeightMap = AnvilUtility.bitpack(heightMap, 9);

        return new Tile(chunkX, chunkZ, sections, packedHeightMap, true);
    }

    private Tile.Section buildSection(ChunkSnapshot snap, int sectionY) {
        List<String> palette = new ArrayList<>();
        Map<String, Integer> paletteIndex = new HashMap<>();
        int[] blockStates = new int[16 * 16 * 16];
        for(int y = 0; y < 16; y++) {
            int worldY = sectionY * 16 + y;
            for(int z = 0; z < 16; z++) {
                for(int x = 0; x < 16; x++) {
                    BlockData data = snap.getBlockData(x, worldY, z);
                    String id = data.getMaterial().getKey().toString();
                    Integer idx = paletteIndex.get(id);
                    if(idx == null) {
                        idx = palette.size();
                        palette.add(id);
                        paletteIndex.put(id, idx);
                    }
                    blockStates[y * 256 + z * 16 + x] = idx;
                }
            }
        }
        int blockBits = AnvilUtility.paletteSizeToBitsSize(palette.size(), 4);
        long[] packedBlockStates = AnvilUtility.bitpack(blockStates, blockBits);

        // Biomes are stored on a 4×4×4 grid (64 cells per section).
        List<String> biomesPalette = new ArrayList<>();
        Map<String, Integer> biomesIndex = new HashMap<>();
        int[] biomes = new int[64];
        for(int by = 0; by < 4; by++) {
            int worldY = sectionY * 16 + by * 4;
            for(int bz = 0; bz < 4; bz++) {
                for(int bx = 0; bx < 4; bx++) {
                    String biomeKey;
                    try {
                        biomeKey = snap.getBiome(bx * 4, worldY, bz * 4).getKey().toString();
                    } catch (Throwable t) {
                        biomeKey = FALLBACK_BIOME;
                    }
                    Integer idx = biomesIndex.get(biomeKey);
                    if(idx == null) {
                        idx = biomesPalette.size();
                        biomesPalette.add(biomeKey);
                        biomesIndex.put(biomeKey, idx);
                    }
                    biomes[by * 16 + bz * 4 + bx] = idx;
                }
            }
        }

        long[] packedBiomes = null;
        if(biomesPalette.size() > 1) {
            int biomesBits = AnvilUtility.paletteSizeToBitsSize(biomesPalette.size());
            packedBiomes = AnvilUtility.bitpack(biomes, biomesBits);
        }

        return Tile.createSection(sectionY, palette, packedBlockStates, biomesPalette, packedBiomes);
    }
}
