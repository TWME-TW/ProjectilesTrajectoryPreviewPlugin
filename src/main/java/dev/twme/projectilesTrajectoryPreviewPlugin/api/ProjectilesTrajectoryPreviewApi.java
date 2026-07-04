package dev.twme.projectilesTrajectoryPreviewPlugin.api;

import org.bukkit.entity.Player;

public interface ProjectilesTrajectoryPreviewApi {

    boolean isEnabled();

    void refresh(Player player);

    void showDropPreview(Player player);

    void clear(Player player);

    void clearAll();

    void reload();
}