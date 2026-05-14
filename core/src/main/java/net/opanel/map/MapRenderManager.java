package net.opanel.map;

import net.opanel.OPanel;
import net.opanel.common.OPanelSave;
import net.opanel.common.OPanelWorldRegion;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class MapRenderManager {
    private final OPanel plugin;
    private final ExecutorService executor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors()
    );
    private final Map<String, List<OPanelWorldRegion>> saveRegionMap = new HashMap<>();

    private final Map<String, Set<Long>> availableTilesIndex = new ConcurrentHashMap<>();
    private final Map<String, Map<Long, byte[]>> tileBytesCache = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> indexVersion = new ConcurrentHashMap<>();

    public MapRenderManager(OPanel plugin) {
        this.plugin = plugin;
    }

    public static long packCoord(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    public static int unpackZ(long packed) {
        return (int) packed;
    }

    public void init() {
        for(OPanelSave save : plugin.getServer().getSaves()) {
            if(!save.isRunning()) continue; // skip the saves that is not running on the server
            saveRegionMap.put(save.getName(), save.getRegions());
        }

        // Walk MAP_DATA_PATH once to populate the available-tile index for every save dir that
        // already has rendered tiles. This avoids a Files.list() on every getAvailableTiles call.
        scanAllSavesOnDisk();

        if(!hasRenderedTiles()) {
            renderAll();
        }
    }

    private static final int PREHEAT_BATCH_SIZE = 2048;

    private void scanAllSavesOnDisk() {
        File mapDataDir = OPanel.MAP_DATA_PATH.toFile();
        if(!mapDataDir.exists() || !mapDataDir.isDirectory()) return;

        File[] saveDirs = mapDataDir.listFiles(File::isDirectory);
        if(saveDirs == null) return;

        // Only walk filenames to build the coord index. No file reads here
        Map<String, Map<Long, Path>> tilesToPreheat = new HashMap<>();
        for(File saveDir : saveDirs) {
            String saveName = saveDir.getName();
            Set<Long> coords = ConcurrentHashMap.newKeySet();
            Map<Long, byte[]> bytesMap = new ConcurrentHashMap<>();
            Map<Long, Path> entries = new HashMap<>();

            try(Stream<Path> stream = Files.list(saveDir.toPath())) {
                stream.forEach(path -> {
                    if(!path.toFile().isFile()) return;

                    String fileName = path.getFileName().toString();
                    if(!fileName.endsWith(".omap")) return;

                    String[] parts = fileName.split("\\.");
                    if(parts.length != 3) return;

                    final int x, z;
                    try {
                        x = Integer.parseInt(parts[0]);
                        z = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException ignored) {
                        return;
                    }

                    long packed = packCoord(x, z);
                    coords.add(packed);
                    entries.put(packed, path);
                });
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }

            availableTilesIndex.put(saveName, coords);
            tileBytesCache.put(saveName, bytesMap);
            indexVersion.computeIfAbsent(saveName, k -> new AtomicLong(0L)).incrementAndGet();
            tilesToPreheat.put(saveName, entries);
        }

        plugin.logger.info("Indexed all map tiles, preheating bytes cache in background");

        // Read all tile files with multithreading
        submitPreheatTasks(tilesToPreheat);
    }

    private void submitPreheatTasks(Map<String, Map<Long, Path>> tileEntries) {
        long start = System.currentTimeMillis();
        AtomicInteger remaining = new AtomicInteger(0);

        List<Runnable> chunkTasks = new ArrayList<>();
        for(Map.Entry<String, Map<Long, Path>> e : tileEntries.entrySet()) {
            String saveName = e.getKey();
            Map<Long, Path> entries = e.getValue();
            Map<Long, byte[]> bytesMap = tileBytesCache.get(saveName);
            if(bytesMap == null || entries.isEmpty()) continue;

            List<Map.Entry<Long, Path>> entryList = new ArrayList<>(entries.entrySet());
            for(int i = 0; i < entryList.size(); i += PREHEAT_BATCH_SIZE) {
                List<Map.Entry<Long, Path>> slice = entryList.subList(i, Math.min(i + PREHEAT_BATCH_SIZE, entryList.size()));
                remaining.incrementAndGet();
                chunkTasks.add(() -> {
                    for(Map.Entry<Long, Path> entry : slice) {
                        long packed = entry.getKey();
                        if(bytesMap.containsKey(packed)) continue; // already filled by registerRenderedTile
                        try {
                            bytesMap.put(packed, Files.readAllBytes(entry.getValue()));
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                    if(remaining.decrementAndGet() == 0) {
                        long elapsed = System.currentTimeMillis() - start;
                        plugin.logger.info("Map tiles bytes cache preheat completed in "+ elapsed +"ms");
                    }
                });
            }
        }

        if(chunkTasks.isEmpty()) {
            plugin.logger.info("Map tiles bytes cache preheat completed: nothing to preheat");
            return;
        }

        for(Runnable task : chunkTasks) {
            executor.execute(task);
        }
    }

    public boolean hasRenderedTiles() {
        File mapDataDir = OPanel.MAP_DATA_PATH.toFile();
        return mapDataDir.exists() && mapDataDir.isDirectory() && mapDataDir.list().length > 0;
    }

    public void renderAll() {
        for(Map.Entry<String, List<OPanelWorldRegion>> entry : saveRegionMap.entrySet()) {
            for(OPanelWorldRegion region : entry.getValue()) {
                executor.execute(new TileRenderTask(plugin, entry.getKey(), region));
            }
        }
    }

    public Future<?> renderTile(String saveName, OPanelWorldRegion region, Tile tile) {
        return executor.submit(new TileRenderTask(plugin, saveName, region, tile));
    }

    public void registerRenderedTile(String saveName, int x, int z, byte[] bytes) {
        long packed = packCoord(x, z);
        availableTilesIndex
            .computeIfAbsent(saveName, k -> ConcurrentHashMap.newKeySet())
            .add(packed);
        tileBytesCache
            .computeIfAbsent(saveName, k -> new ConcurrentHashMap<>())
            .put(packed, bytes);
        indexVersion
            .computeIfAbsent(saveName, k -> new AtomicLong(0L))
            .incrementAndGet();
    }

    public Set<Long> getAvailableTileCoords(String saveName) {
        Set<Long> set = availableTilesIndex.get(saveName);
        if(set == null) return Collections.emptySet();
        return new HashSet<>(set); // snapshot to avoid concurrent modification during iteration
    }

    public long getIndexVersion(String saveName) {
        AtomicLong v = indexVersion.get(saveName);
        return v == null ? 0L : v.get();
    }

    public byte[] loadTileBytes(String saveName, int x, int z) {
        long packed = packCoord(x, z);
        Set<Long> coords = availableTilesIndex.get(saveName);
        if(coords == null || !coords.contains(packed)) return null;

        Map<Long, byte[]> bytesMap = tileBytesCache.computeIfAbsent(saveName, k -> new ConcurrentHashMap<>());
        return bytesMap.computeIfAbsent(packed, k -> {
            Path tilePath = OPanel.MAP_DATA_PATH.resolve(saveName).resolve(x +"."+ z +".omap");
            if(!Files.isRegularFile(tilePath)) return null;
            try {
                return Files.readAllBytes(tilePath);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
