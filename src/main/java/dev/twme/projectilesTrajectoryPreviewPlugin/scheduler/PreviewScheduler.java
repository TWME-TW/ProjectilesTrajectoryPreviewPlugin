package dev.twme.projectilesTrajectoryPreviewPlugin.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class PreviewScheduler {

    private final Plugin plugin;

    public PreviewScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    public ScheduledTask runGlobal(Runnable task) {
        return Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
    }

    public ScheduledTask runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), delayTicks, periodTicks);
    }

    public ScheduledTask runEntity(Player player, Runnable task) {
        if (player == null) return null;
        return player.getScheduler().run(plugin, scheduledTask -> task.run(), null);
    }

    public ScheduledTask runEntityLater(Player player, Runnable task, long delayTicks) {
        if (player == null) return null;
        return player.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, delayTicks);
    }

    public void cancel(ScheduledTask task) {
        if (task != null) task.cancel();
    }
}