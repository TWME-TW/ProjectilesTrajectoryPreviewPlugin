package dev.twme.projectilesTrajectoryPreviewPlugin.api;

import dev.twme.projectilesTrajectoryPreviewPlugin.ProjectilesTrajectoryPreviewPlugin;
import org.bukkit.entity.Player;

public final class ProjectilesTrajectoryPreviewApiProvider implements ProjectilesTrajectoryPreviewApi {

    private final ProjectilesTrajectoryPreviewPlugin plugin;

    public ProjectilesTrajectoryPreviewApiProvider(ProjectilesTrajectoryPreviewPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isEnabled() {
        return isAvailable() && plugin.previewSettings().enabled();
    }

    @Override
    public void refresh(Player player) {
        if (player == null) return;
        if (!isAvailable()) return;
        plugin.previewManager().forceUpdate(player);
    }

    @Override
    public void showDropPreview(Player player) {
        if (player == null) return;
        if (!isAvailable()) return;
        plugin.previewManager().previewDrop(player);
    }

    @Override
    public void clear(Player player) {
        if (player == null) return;
        if (!isAvailable()) return;
        plugin.previewManager().clear(player);
    }

    @Override
    public void clearAll() {
        if (!isAvailable()) return;
        plugin.previewManager().clearAll();
    }

    @Override
    public void reload() {
        if (!isAvailable()) return;
        plugin.previewScheduler().runGlobal(() -> {
            plugin.reloadConfig();
            plugin.reloadPreviewSettings();
        });
    }

    private boolean isAvailable() {
        return plugin.isEnabled() && plugin.previewManager() != null;
    }
}