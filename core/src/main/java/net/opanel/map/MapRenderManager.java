package net.opanel.map;

import net.opanel.OPanel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapRenderManager {
    private final OPanel plugin;

    private final ExecutorService executor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors()
    );

    public MapRenderManager(OPanel plugin) {
        this.plugin = plugin;
    }

    public void shutdown() {

    }
}
