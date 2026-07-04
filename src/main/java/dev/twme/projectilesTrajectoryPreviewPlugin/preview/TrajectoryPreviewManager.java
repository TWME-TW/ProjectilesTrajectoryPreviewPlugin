package dev.twme.projectilesTrajectoryPreviewPlugin.preview;

import dev.twme.projectilesTrajectoryPreviewPlugin.ProjectilesTrajectoryPreviewPlugin;
import dev.twme.projectilesTrajectoryPreviewPlugin.config.PreviewSettings;
import dev.twme.projectilesTrajectoryPreviewPlugin.physics.ProjectileSpec;
import dev.twme.projectilesTrajectoryPreviewPlugin.physics.TrajectoryCalculator;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class TrajectoryPreviewManager implements AutoCloseable {

    private final ProjectilesTrajectoryPreviewPlugin plugin;
    private final TrajectoryCalculator calculator = new TrajectoryCalculator();
    private final Map<UUID, PlayerPreview> previews = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dropPreviewExpiresAt = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> dropPreviewClearTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextPacketUpdateAt = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> pendingPacketUpdates = new ConcurrentHashMap<>();
    private final ScheduledTask cleanupTask;
    private ScheduledTask fallbackUpdateTask;

    public TrajectoryPreviewManager(ProjectilesTrajectoryPreviewPlugin plugin) {
        this.plugin = plugin;
        this.cleanupTask = plugin.previewScheduler().runGlobalTimer(this::cleanupOfflinePlayers, 20L, 20L);
        rescheduleFallbackUpdate();
    }

    public void rescheduleFallbackUpdate() {
        plugin.previewScheduler().cancel(fallbackUpdateTask);
        fallbackUpdateTask = null;
        long periodTicks = plugin.previewSettings().fallbackUpdateTicks();
        if (periodTicks <= 0L) return;
        fallbackUpdateTask = plugin.previewScheduler().runGlobalTimer(this::fallbackUpdateOnlinePlayers, periodTicks, periodTicks);
    }

    private void fallbackUpdateOnlinePlayers() {
        PreviewSettings settings = plugin.previewSettings();
        if (!settings.enabled()) {
            clearAll();
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            forceUpdate(player);
        }
    }

    public void update(Player player, boolean rotationChanged, boolean positionChanged) {
        if (!rotationChanged && !positionChanged) return;
        UUID playerId = player.getUniqueId();
        long now = System.nanoTime();
        long nextAllowed = nextPacketUpdateAt.getOrDefault(playerId, 0L);
        if (now < nextAllowed) return;
        nextPacketUpdateAt.put(playerId, now + plugin.previewSettings().updateIntervalNanos());
        if (pendingPacketUpdates.putIfAbsent(playerId, Boolean.TRUE) != null) return;
        plugin.previewScheduler().runEntity(player, () -> updateOnRegion(player));
    }

    public void previewDrop(Player player) {
        plugin.previewScheduler().runEntity(player, () -> {
            PreviewSettings settings = plugin.previewSettings();
            if (!settings.enabled() || !settings.showDropPreview()) return;
            UUID playerId = player.getUniqueId();
            dropPreviewExpiresAt.put(playerId, System.nanoTime() + settings.dropPreviewDurationNanos());
            scheduleDropPreviewClear(player, settings);
            updateOnRegion(player, true);
        });
    }

    public void forceUpdate(Player player) {
        plugin.previewScheduler().runEntity(player, () -> updateOnRegion(player, true));
    }

    private void scheduleDropPreviewClear(Player player, PreviewSettings settings) {
        UUID playerId = player.getUniqueId();
        ScheduledTask existingTask = dropPreviewClearTasks.remove(playerId);
        plugin.previewScheduler().cancel(existingTask);

        long delayTicks = Math.max(1L, (long) Math.ceil(settings.dropPreviewDurationNanos() / 50_000_000.0));
        ScheduledTask task = plugin.previewScheduler().runEntityLater(player, () -> {
            dropPreviewClearTasks.remove(playerId);
            Long dropUntil = dropPreviewExpiresAt.get(playerId);
            if (dropUntil != null && dropUntil > System.nanoTime()) return;
            dropPreviewExpiresAt.remove(playerId);

            Player onlinePlayer = Bukkit.getPlayer(playerId);
            if (onlinePlayer != null) updateOnRegion(onlinePlayer, true);
            else remove(playerId);
        }, delayTicks);
        if (task != null) dropPreviewClearTasks.put(playerId, task);
    }

    private void updateOnRegion(Player player) {
        updateOnRegion(player, false);
    }

    private void updateOnRegion(Player player, boolean force) {
        pendingPacketUpdates.remove(player.getUniqueId());
        if (!player.isOnline() || player.isDead()) {
            remove(player.getUniqueId());
            return;
        }

        PreviewSettings settings = plugin.previewSettings();
        PlayerPreview preview = previews.computeIfAbsent(player.getUniqueId(), uuid -> new PlayerPreview(player, settings));
        long now = System.nanoTime();
        if (!preview.matches(settings)) {
            preview.close();
            preview = new PlayerPreview(player, settings);
            previews.put(player.getUniqueId(), preview);
        }
        if (!force && !preview.shouldUpdate(now, settings.updateIntervalNanos())) return;

        if (!settings.enabled()) {
            preview.clear();
            return;
        }

        List<ProjectileSpec> specs = getProjectileSpecs(player, settings, now);
        if (specs.isEmpty()) {
            preview.clear();
            return;
        }

        List<TrajectoryCalculator.Trajectory> trajectories = new ArrayList<>();
        for (ProjectileSpec spec : specs) {
            trajectories.add(calculator.calculate(player, spec));
        }
        preview.render(trajectories);
    }

    private List<ProjectileSpec> getProjectileSpecs(Player player, PreviewSettings settings, long now) {
        Long dropUntil = dropPreviewExpiresAt.get(player.getUniqueId());
        if (dropUntil != null) {
            if (dropUntil > now && settings.showDropPreview()) return List.of(ProjectileSpec.drop(player));
            dropPreviewExpiresAt.remove(player.getUniqueId());
        }

        ItemStack itemStack = player.getInventory().getItemInMainHand();
        List<ProjectileSpec> specs = getProjectileSpecs(player, itemStack, EquipmentSlot.HAND, settings);
        if (!specs.isEmpty() || !settings.enableOffhand()) return specs;
        return getProjectileSpecs(player, player.getInventory().getItemInOffHand(), EquipmentSlot.OFF_HAND, settings);
    }

    private List<ProjectileSpec> getProjectileSpecs(Player player, ItemStack stack, EquipmentSlot hand, PreviewSettings settings) {
        List<ProjectileSpec> specs = new ArrayList<>();
        ProjectileSpec spec = ProjectileSpec.from(player, stack, hand, settings);
        if (spec != null) specs.add(spec);
        ProjectileSpec multishotLeft = ProjectileSpec.crossbowMultishot(player, stack, hand, settings, 10.0);
        ProjectileSpec multishotRight = ProjectileSpec.crossbowMultishot(player, stack, hand, settings, -10.0);
        if (multishotLeft != null) {
            specs.add(multishotLeft);
            if (multishotRight != null) specs.add(multishotRight);
        }
        return specs;
    }

    private void cleanupOfflinePlayers() {
        Iterator<Map.Entry<UUID, PlayerPreview>> iterator = previews.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PlayerPreview> entry = iterator.next();
            if (Bukkit.getPlayer(entry.getKey()) != null) continue;
            entry.getValue().close();
            clearDropPreviewState(entry.getKey());
            iterator.remove();
        }
    }

    private void remove(UUID playerId) {
        PlayerPreview preview = previews.remove(playerId);
        clearDropPreviewState(playerId);
        nextPacketUpdateAt.remove(playerId);
        pendingPacketUpdates.remove(playerId);
        if (preview != null) preview.close();
    }

    private void clearDropPreviewState(UUID playerId) {
        dropPreviewExpiresAt.remove(playerId);
        ScheduledTask task = dropPreviewClearTasks.remove(playerId);
        plugin.previewScheduler().cancel(task);
    }

    public void clearAll() {
        for (UUID playerId : previews.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) clear(player);
        }
    }

    public void clear(Player player) {
        if (player == null) return;
        plugin.previewScheduler().runEntity(player, () -> {
            PlayerPreview preview = previews.get(player.getUniqueId());
            if (preview != null) preview.clear();
        });
    }

    @Override
    public void close() {
        plugin.previewScheduler().cancel(cleanupTask);
        plugin.previewScheduler().cancel(fallbackUpdateTask);
        dropPreviewExpiresAt.clear();
        nextPacketUpdateAt.clear();
        pendingPacketUpdates.clear();
        dropPreviewClearTasks.values().forEach(plugin.previewScheduler()::cancel);
        dropPreviewClearTasks.clear();
        previews.values().forEach(PlayerPreview::close);
        previews.clear();
    }
}