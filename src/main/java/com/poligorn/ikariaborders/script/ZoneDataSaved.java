package com.poligorn.ikariaborders.script;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/** SavedData wrapper that persists {@link ZoneDataStore} (as one JSON string) in the overworld data storage. */
public final class ZoneDataSaved extends SavedData {
    public static final String NAME = "ikaria_borders_zone_data";

    final ZoneDataStore store = new ZoneDataStore();

    private ZoneDataSaved() {
        store.onChange(this::setDirty);
    }

    static SavedData.Factory<ZoneDataSaved> factory() {
        return new SavedData.Factory<>(ZoneDataSaved::new, ZoneDataSaved::load, null);
    }

    private static ZoneDataSaved load(CompoundTag tag, HolderLookup.Provider registries) {
        ZoneDataSaved d = new ZoneDataSaved();
        d.store.load(tag.getString("json"));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("json", store.toJson());
        return tag;
    }
}
