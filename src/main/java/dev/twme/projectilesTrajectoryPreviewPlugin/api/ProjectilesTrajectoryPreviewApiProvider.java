package dev.twme.projectilesTrajectoryPreviewPlugin.api;

import dev.twme.projectilesTrajectoryPreviewPlugin.ProjectilesTrajectoryPreviewPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class ProjectilesTrajectoryPreviewApiProvider implements ProjectilesTrajectoryPreviewApi {

    private final ProjectilesTrajectoryPreviewPlugin plugin;

    public ProjectilesTrajectoryPreviewApiProvider(ProjectilesTrajectoryPreviewPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isEnabled() {
        return plugin.previewSettings().enabled();
    }

    @Override
    public void refresh(Player player) {
        if (player == null) return;
        runSync(() -> plugin.previewManager().forceUpdate(player));
    }

    @Override
    public void showDropPreview(Player player) {
        if (player == null) return;
        plugin.previewManager().previewDrop(player);
    }

    @Override
    public void clear(Player player) {
        if (player == null) return;
        runSync(() -> plugin.previewManager().clear(player));
    }

    @Override
    public void clearAll() {
        runSync(() -> plugin.previewManager().clearAll());
    }

    @Override
    public void reload() {
        runSync(() -> {
            plugin.reloadConfig();
            plugin.reloadPreviewSettings();
        });
    }

    private void runSync(Runnable task) {
        if (!plugin.isEnabled() || plugin.previewManager() == null) return;
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }
}