package me.jellysquid.mods.phosphor.common.chunk.light;

public interface PendingUpdateListener {
    default void onPendingUpdateRemoved(long key) {

    }

    default void onPendingUpdateAdded(long key) {

    }
}
