package com.poligorn.ikariaborders.xaero;

import com.poligorn.ikariaborders.client.ClientState;
import com.poligorn.ikariaborders.map.MapTarget;
import com.poligorn.ikariaborders.map.MapZone;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.common.minimap.highlight.ChunkHighlighter;
import xaero.hud.minimap.info.render.compile.InfoDisplayCompiler;

/** Thin Minimap adapter (separate class hierarchy from World Map). Loaded only if Xaero's Minimap is present. */
public final class MinimapZoneHighlighter extends ChunkHighlighter {
    private static final MapTarget T = MapTarget.MINIMAP;

    public MinimapZoneHighlighter() {
        super(true);
    }

    private static String dim(ResourceKey<Level> d) {
        return d.location().toString();
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

    @Override
    public void addChunkHighlightTooltips(InfoDisplayCompiler compiler, ResourceKey<Level> dimension, int chunkX, int chunkZ, int width) {
        MapZone z = ClientState.MODEL.zoneForTooltip(T, dim(dimension), chunkX, chunkZ);
        if (z != null) compiler.addLine(Component.literal(z.tooltip()).withStyle(Style.EMPTY.withColor(z.rgb() & 0xFFFFFF)));
    }
}
