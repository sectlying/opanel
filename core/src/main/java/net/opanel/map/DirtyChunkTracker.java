package net.opanel.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-save set of chunk coordinates that need re-rendering. Block events on the
 * game thread mark chunks as dirty in O(1); the map render scheduler drains
 * batches off this tracker on its own thread.
 */
public class DirtyChunkTracker {
    private final Map<String, Set<Long>> dirty = new ConcurrentHashMap<>();

    public void markDirty(String saveName, int chunkX, int chunkZ) {
        dirty.computeIfAbsent(saveName, k -> ConcurrentHashMap.newKeySet())
            .add(MapRenderManager.packCoord(chunkX, chunkZ));
    }

    /**
     * Remove and return up to {@code maxCount} dirty coords for the given save.
     * Returns an empty list if the save has no pending dirty chunks.
     */
    public List<Long> drain(String saveName, int maxCount) {
        Set<Long> set = dirty.get(saveName);
        if(set == null || set.isEmpty()) return Collections.emptyList();

        List<Long> drained = new ArrayList<>(Math.min(maxCount, set.size()));
        Iterator<Long> it = set.iterator();
        while(it.hasNext() && drained.size() < maxCount) {
            Long packed = it.next();
            it.remove();
            drained.add(packed);
        }
        return drained;
    }

    public int pendingCount(String saveName) {
        Set<Long> set = dirty.get(saveName);
        return set == null ? 0 : set.size();
    }

    public Set<String> getSaveNames() {
        return dirty.keySet();
    }
}
