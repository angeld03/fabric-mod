package com.wildbound.api;

import com.wildbound.progression.LevelUpResult;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

/** Callbacks fired by the core engine. Other modules subscribe here. */
public final class RpgProgressionEvents {
    private RpgProgressionEvents() {
    }

    public interface OnLevelUp {
        void onLevelUp(ServerPlayer player, LevelUpResult result);
    }

    public interface OnExperienceGained {
        void onExperienceGained(ServerPlayer player, long amount, ExperienceSource source);
    }

    public static final Event<OnLevelUp> LEVEL_UP = EventFactory.createArrayBacked(OnLevelUp.class,
            callbacks -> (player, result) -> {
                for (OnLevelUp callback : callbacks) callback.onLevelUp(player, result);
            });

    public static final Event<OnExperienceGained> EXPERIENCE_GAINED = EventFactory.createArrayBacked(OnExperienceGained.class,
            callbacks -> (player, amount, source) -> {
                for (OnExperienceGained callback : callbacks) callback.onExperienceGained(player, amount, source);
            });
}
