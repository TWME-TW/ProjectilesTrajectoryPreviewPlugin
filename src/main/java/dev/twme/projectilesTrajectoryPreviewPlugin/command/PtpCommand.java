package dev.twme.projectilesTrajectoryPreviewPlugin.command;

import dev.twme.projectilesTrajectoryPreviewPlugin.ProjectilesTrajectoryPreviewPlugin;
import dev.twme.projectilesTrajectoryPreviewPlugin.config.PreviewSettings;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PtpCommand implements CommandExecutor, TabCompleter {

    private final ProjectilesTrajectoryPreviewPlugin plugin;

    public PtpCommand(ProjectilesTrajectoryPreviewPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("ptp.use")) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            PreviewSettings settings = plugin.previewSettings();
            String displayModes = "Trajectory: " + settings.showTrajectory()
                    + ", outline: " + settings.showOutline()
                    + ", highlight: " + settings.showHighlight()
                    + ", uncertainty: " + settings.showUncertainty();
            sender.sendMessage("PTP enabled: " + settings.enabled());
            sender.sendMessage(displayModes);
            sender.sendMessage("Offhand: " + settings.enableOffhand() + ", drop preview: " + settings.showDropPreview());
            sender.sendMessage("Requested lines: " + settings.lineCount() + ", thickness: " + settings.lineThickness() + ", mode: " + settings.lineRenderMode() + ", style: " + settings.trajectoryStyle());
            sender.sendMessage("Line entity budget: " + settings.lineEntityBudget() + ", entities per line: " + settings.lineRenderMode().entitiesPerLogicalLine());
            sender.sendMessage("Update interval: " + (settings.updateIntervalNanos() / 1_000_000L) + "ms, fallback ticks: " + settings.fallbackUpdateTicks() + ", interpolation ticks: " + settings.transformationInterpolationTicks());
            return true;
        }

        if (args[0].equalsIgnoreCase("toggle")) {
            boolean next = !plugin.getConfig().getBoolean("enabled", true);
            plugin.getConfig().set("enabled", next);
            plugin.saveConfig();
            plugin.reloadPreviewSettings();
            sender.sendMessage("PTP enabled: " + next);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            plugin.reloadPreviewSettings();
            sender.sendMessage("PTP config reloaded.");
            return true;
        }

        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("toggle", "reload", "status");
        return List.of();
    }
}