package com.heroicpaths.minion;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Существо игрока: зверь охотника или призванный миньон. Следует за хозяином, защищает его,
 * атакует его цели и не трогает хозяина, его группу и других его существ.
 */
public interface Minion {
    MinionData minionData();

    /** Общие данные существа: хозяин, время жизни, роль. */
    final class MinionData {
        @Nullable
        private UUID owner;
        /** Сколько тиков осталось жить; 0 — живёт, пока его не отзовут. */
        private int lifetime;
        /** Зверь охотника (а не временный призыв). */
        private boolean companion;

        @Nullable
        public UUID owner() {
            return owner;
        }

        public void setOwner(@Nullable UUID owner) {
            this.owner = owner;
        }

        public int lifetime() {
            return lifetime;
        }

        public void setLifetime(int lifetime) {
            this.lifetime = lifetime;
        }

        public boolean isCompanion() {
            return companion;
        }

        public void setCompanion(boolean companion) {
            this.companion = companion;
        }

        public void save(CompoundTag tag) {
            CompoundTag data = new CompoundTag();
            if (owner != null) data.putUUID("owner", owner);
            data.putInt("lifetime", lifetime);
            data.putBoolean("companion", companion);
            tag.put("heroicpaths_minion", data);
        }

        public void load(CompoundTag tag) {
            CompoundTag data = tag.getCompound("heroicpaths_minion");
            owner = data.hasUUID("owner") ? data.getUUID("owner") : null;
            lifetime = data.getInt("lifetime");
            companion = data.getBoolean("companion");
        }
    }
}
