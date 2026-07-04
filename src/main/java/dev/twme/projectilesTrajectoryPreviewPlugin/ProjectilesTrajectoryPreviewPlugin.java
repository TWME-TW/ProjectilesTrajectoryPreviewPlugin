package dev.twme.projectilesTrajectoryPreviewPlugin;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import dev.twme.projectilesTrajectoryPreviewPlugin.api.ProjectilesTrajectoryPreviewApi;
import dev.twme.projectilesTrajectoryPreviewPlugin.api.ProjectilesTrajectoryPreviewApiProvider;
import dev.twme.projectilesTrajectoryPreviewPlugin.command.PtpCommand;
import dev.twme.projectilesTrajectoryPreviewPlugin.config.PreviewSettings;
import dev.twme.projectilesTrajectoryPreviewPlugin.listener.PlayerLookPacketListener;
import dev.twme.projectilesTrajectoryPreviewPlugin.preview.TrajectoryPreviewManager;
import dev.twme.projectilesTrajectoryPreviewPlugin.scheduler.PreviewScheduler;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import me.tofaa.entitylib.APIConfig;
import me.tofaa.entitylib.EntityLib;
import me.tofaa.entitylib.spigot.SpigotEntityLibPlatform;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProjectilesTrajectoryPreviewPlugin extends JavaPlugin {

    private TrajectoryPreviewManager previewManager;
    private PlayerLookPacketListener packetListener;
    private PacketListenerCommon registeredPacketListener;
    private PreviewSettings previewSettings;
    private ProjectilesTrajectoryPreviewApi api;
    private PreviewScheduler previewScheduler;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings()
                .debug(false)
                .checkForUpdates(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadPreviewSettings();

        PacketEvents.getAPI().init();

        SpigotEntityLibPlatform platform = new SpigotEntityLibPlatform(this);
        APIConfig settings = new APIConfig(PacketEvents.getAPI()).usePlatformLogger();
        EntityLib.init(platform, settings);

        previewScheduler = new PreviewScheduler(this);
        previewManager = new TrajectoryPreviewManager(this);
        api = new ProjectilesTrajectoryPreviewApiProvider(this);
        getServer().getServicesManager().register(ProjectilesTrajectoryPreviewApi.class, api, this, ServicePriority.Normal);

        packetListener = new PlayerLookPacketListener(previewManager);
        registeredPacketListener = PacketEvents.getAPI().getEventManager()
                .registerListener(packetListener, PacketListenerPriority.NORMAL);

        PtpCommand command = new PtpCommand(this);
        PluginCommand pluginCommand = getCommand("ptp");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        api = null;
        if (packetListener != null) {
            packetListener.deactivate();
        }
        if (registeredPacketListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(registeredPacketListener);
            registeredPacketListener = null;
        }
        if (previewManager != null) {
            previewManager.close();
            previewManager = null;
        }
        previewScheduler = null;
        PacketEvents.getAPI().terminate();
    }

    public PreviewSettings previewSettings() {
        return previewSettings;
    }

    public TrajectoryPreviewManager previewManager() {
        return previewManager;
    }

    public PreviewScheduler previewScheduler() {
        return previewScheduler;
    }

    public ProjectilesTrajectoryPreviewApi api() {
        return api;
    }

    public void reloadPreviewSettings() {
        previewSettings = PreviewSettings.load(getConfig());
        if (previewManager != null) {
            previewManager.clearAll();
            previewManager.rescheduleFallbackUpdate();
        }
    }
}
