package net.opanel.map;

import net.opanel.OPanel;
import net.opanel.common.OPanelSave;
import net.opanel.common.OPanelWorldRegion;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MapRenderManager {
    private final ExecutorService executor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors()
    );
    private final List<OPanelWorldRegion> regionsToRender = new ArrayList<>();

    public MapRenderManager(OPanel plugin) {
        for(OPanelSave save : plugin.getServer().getSaves()) {
            if(!save.isRunning()) continue; // skip the saves that is not running on the server
            regionsToRender.addAll(save.getRegions());
        }
    }

    public boolean hasRenderedTiles() {
        File mapDataDir = OPanel.MAP_DATA_PATH.toFile();
        return mapDataDir.exists() && mapDataDir.isDirectory() && mapDataDir.list().length > 0;
    }

    public void renderAll() {
        for(OPanelWorldRegion region : regionsToRender) {
            executor.submit(new TileRenderTask(region));
        }
    }

    public Future<?> renderTile(OPanelWorldRegion region, Tile tile) {
        return executor.submit(new TileRenderTask(region, tile));
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
