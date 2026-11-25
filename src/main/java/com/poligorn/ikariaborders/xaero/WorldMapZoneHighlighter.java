package com.poligorn.ikariaborders.xaero;

import com.poligorn.ikariaborders.client.ClientState;
import com.poligorn.ikariaborders.map.MapTarget;
import com.poligorn.ikariaborders.map.MapZone;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.highlight.ChunkHighlighter;

import java.util.List;

/** Thin World Map adapter over {@link com.poligorn.ikariaborders.map.ZoneMapModel}. Loaded only if Xaero's World Map is present. */
public final class WorldMapZoneHighlighter extends ChunkHighlighter {
    private static final MapTarget T = MapTarget.WORLD_MAP;

    public WorldMapZoneHighlighter() {
        super(true); // draw outside discovered terrain too, like OPAC claims
    }

    private static String dim(ResourceKey<Level> d) {
        return d.location().toString();
    }

    @Override
    public int calculateRegionHash(ResourceKey<Level> dimension, int regionX, int regionZ) {
        return ClientState.MODEL.regionHash(T);
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> dimension, int regionX, int regionZ) {
        return ClientState.MODEL.regionHasHighlights(T, dim(dimension), regionX, regionZ);
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return ClientState.MODEL.chunkIsHighlit(T, dim(dimension), chunkX, chunkZ);
    }

    @Override
    protected int[] getColors(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return ClientState.MODEL.colors(T, dim(dimension), chunkX, chunkZ);
    }

    private static Component tooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        MapZone z = ClientState.MODEL.zoneForTooltip(T, dim(dimension), chunkX, chunkZ);
        return z == null ? null : Component.literal(z.tooltip()).withStyle(Style.EMPTY.withColor(z.rgb() & 0xFFFFFF));
    }

    @Override
    public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return tooltip(dimension, chunkX, chunkZ);
    }

    @Override
    public Component getChunkHighlightBluntTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return tooltip(dimension, chunkX, chunkZ);
    }

    /** World Map highlighters are not shown on the minimap (the minimap gets its own highlighter). */
    @Override
    public void addMinimapBlockHighlightTooltips(List<Component> list, ResourceKey<Level> dimension, int blockX, int blockZ, int width) {
    }
}
