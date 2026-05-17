package net.opanel.event;

public class OPanelChunkDirtyEvent extends OPanelEvent {
    private final String saveName;
    private final int chunkX;
    private final int chunkZ;

    public OPanelChunkDirtyEvent(String saveName, int chunkX, int chunkZ) {
        this.saveName = saveName;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public String getSaveName() {
        return saveName;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }
}
