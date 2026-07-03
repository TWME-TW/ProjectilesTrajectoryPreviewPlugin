package dev.twme.projectilesTrajectoryPreviewPlugin.listener;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import dev.twme.projectilesTrajectoryPreviewPlugin.preview.TrajectoryPreviewManager;
import org.bukkit.entity.Player;

public final class PlayerLookPacketListener implements PacketListener {

    private final TrajectoryPreviewManager previewManager;
    private volatile boolean active = true;

    public PlayerLookPacketListener(TrajectoryPreviewManager previewManager) {
        this.previewManager = previewManager;
    }

    public void deactivate() {
        active = false;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!active) return;

        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) return;

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging packet = new WrapperPlayClientPlayerDigging(event);
            DiggingAction action = packet.getAction();
            if (action == DiggingAction.DROP_ITEM || action == DiggingAction.DROP_ITEM_STACK) {
                previewManager.previewDrop(player);
            }
            return;
        }

        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_FLYING) return;

        WrapperPlayClientPlayerFlying packet = new WrapperPlayClientPlayerFlying(event);
        previewManager.update(player, packet.hasRotationChanged(), packet.hasPositionChanged());
    }
}