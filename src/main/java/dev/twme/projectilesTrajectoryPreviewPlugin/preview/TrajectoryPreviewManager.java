package dev.twme.projectilesTrajectoryPreviewPlugin.preview;

import dev.twme.projectilesTrajectoryPreviewPlugin.ProjectilesTrajectoryPreviewPlugin;
import dev.twme.projectilesTrajectoryPreviewPlugin.config.PreviewSettings;
import dev.twme.projectilesTrajectoryPreviewPlugin.physics.ProjectileSpec;
import dev.twme.projectilesTrajectoryPreviewPlugin.physics.TrajectoryCalculator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public final class TrajectoryPreviewManager implements AutoCloseable {

    private final ProjectilesTrajectoryPreviewPlugin plugin;
    private final TrajectoryCalculator calculator = new TrajectoryCalculator();
    private final Map<UUID, PlayerPreview> previews = new HashMap<>();
    private final Map<UUID, Long> dropPreviewExpiresAt = new HashMap<>();
    private final Map<UUID, BukkitTask> dropPreviewClearTasks = new HashMap<>();
    private final Map<UUID, Long> nextPacketUpdateAt = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> pendingPacketUpdates = new ConcurrentHashMap<>();
    private final BukkitTask cleanupTask;
    private BukkitTask fallbackUpdateTask;

    public TrajectoryPreviewManager(ProjectilesTrajectoryPreviewPlugin plugin) {
        this.plugin = plugin;
        this.cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupOfflinePlayers, 20L, 20L);
        rescheduleFallbackUpdate();
    }

    public void rescheduleFallbackUpdate() {
        if (fallbackUpdateTask != null) fallbackUpdateTask.cancel();
        fallbackUpdateTask = null;
        long periodTicks = plugin.previewSettings().fallbackUpdateTicks();
        if (periodTicks <= 0L) return;
        fallbackUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::fallbackUpdateOnlinePlayers, periodTicks, periodTicks);
    }

    private void fallbackUpdateOnlinePlayers() {
        PreviewSettings settings = plugin.previewSettings();
        if (!settings.enabled()) {
            previews.values().forEach(PlayerPreview::clear);
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            updateOnMainThread(player, false);
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
        Bukkit.getScheduler().runTask(plugin, () -> updateOnMainThread(player));
    }

    public void previewDrop(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            PreviewSettings settings = plugin.previewSettings();
            if (!settings.enabled() || !settings.showDropPreview()) return;
            UUID playerId = player.getUniqueId();
            dropPreviewExpiresAt.put(playerId, System.nanoTime() + settings.dropPreviewDurationNanos());
            scheduleDropPreviewClear(player, settings);
            updateOnMainThread(player, true);
        });
    }

    public void forceUpdate(Player player) {
        updateOnMainThread(player, true);
    }

    private void scheduleDropPreviewClear(Player player, PreviewSettings settings) {
        UUID playerId = player.getUniqueId();
        BukkitTask existingTask = dropPreviewClearTasks.remove(playerId);
        if (existingTask != null) existingTask.cancel();

        long delayTicks = Math.max(1L, (long) Math.ceil(settings.dropPreviewDurationNanos() / 50_000_000.0));
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            dropPreviewClearTasks.remove(playerId);
            Long dropUntil = dropPreviewExpiresAt.get(playerId);
            if (dropUntil != null && dropUntil > System.nanoTime()) return;
            dropPreviewExpiresAt.remove(playerId);

            Player onlinePlayer = Bukkit.getPlayer(playerId);
            if (onlinePlayer != null) updateOnMainThread(onlinePlayer, true);
            else remove(playerId);
        }, delayTicks);
        dropPreviewClearTasks.put(playerId, task);
    }

    private void updateOnMainThread(Player player) {
        updateOnMainThread(player, false);
    }

    private void updateOnMainThread(Player player, boolean force) {
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
        BukkitTask task = dropPreviewClearTasks.remove(playerId);
        if (task != null) task.cancel();
    }

    public void clearAll() {
        previews.values().forEach(PlayerPreview::clear);
    }

    public void clear(Player player) {
        if (player == null) return;
        PlayerPreview preview = previews.get(player.getUniqueId());
        if (preview != null) preview.clear();
    }

    @Override
    public void close() {
        cleanupTask.cancel();
        if (fallbackUpdateTask != null) fallbackUpdateTask.cancel();
        dropPreviewExpiresAt.clear();
        nextPacketUpdateAt.clear();
        pendingPacketUpdates.clear();
        dropPreviewClearTasks.values().forEach(BukkitTask::cancel);
        dropPreviewClearTasks.clear();
        previews.values().forEach(PlayerPreview::close);
        previews.clear();
    }
}