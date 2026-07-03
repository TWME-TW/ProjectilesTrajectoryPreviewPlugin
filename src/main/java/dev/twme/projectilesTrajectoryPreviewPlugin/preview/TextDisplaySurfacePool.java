package dev.twme.projectilesTrajectoryPreviewPlugin.preview;

import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3f;
import dev.twme.textdisplayshape.util.TextDisplayUtil;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import java.util.ArrayList;
import java.util.List;
import me.tofaa.entitylib.meta.display.AbstractDisplayMeta;
import me.tofaa.entitylib.meta.display.TextDisplayMeta;
import me.tofaa.entitylib.wrapper.WrapperEntity;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

final class TextDisplaySurfacePool implements AutoCloseable {

    private static final float VIEW_RANGE = 100.0f;

    private final Player viewer;
    private final List<SurfacePair> surfaces;
    private Location origin;

    TextDisplaySurfacePool(Player viewer, int size) {
        this(viewer, size, false);
    }

    TextDisplaySurfacePool(Player viewer, int size, boolean doubleSided) {
        this.viewer = viewer;
        this.origin = normalizedOrigin(viewer.getLocation());
        this.surfaces = new ArrayList<>(size);

        for (int index = 0; index < size; index++) {
            surfaces.add(new SurfacePair(createSurface(), doubleSided ? createSurface() : null));
        }
    }

    void update(int index, Vector corner, Vector widthEnd, Vector heightEnd, int argbColor) {
        ensureOrigin();
        SurfacePair surface = surfaces.get(index);
        apply(surface.front(), surfaceMatrix(corner, widthEnd, heightEnd), argbColor, 0);
        if (surface.back() != null) apply(surface.back(), surfaceMatrix(corner, heightEnd, widthEnd), argbColor, 0);
    }

    private Matrix4f surfaceMatrix(Vector corner, Vector widthEnd, Vector heightEnd) {
        Matrix4f matrix = TextDisplayUtil.textDisplayParallelogram(toJoml(corner), toJoml(widthEnd), toJoml(heightEnd));
        return new Matrix4f()
                .translate((float) -origin.getX(), (float) -origin.getY(), (float) -origin.getZ())
                .mul(matrix);
    }

    void hide(int index) {
        SurfacePair surface = surfaces.get(index);
        hide(surface.front());
        if (surface.back() != null) hide(surface.back());
    }

    private void hide(WrapperEntity surface) {
        if (surface.getEntityMeta() instanceof TextDisplayMeta meta) {
            meta.setBackgroundColor(0);
        }
        if (surface.getEntityMeta() instanceof AbstractDisplayMeta meta) {
            meta.setScale(new Vector3f(0.0001f, 0.0001f, 0.0001f));
            meta.setTransformationInterpolationDuration(0);
            surface.sendPacketToViewers(surface.getEntityMeta().createPacket());
        }
    }

    @Override
    public void close() {
        for (SurfacePair surface : surfaces) {
            surface.front().remove();
            if (surface.back() != null) surface.back().remove();
        }
        surfaces.clear();
    }

    private WrapperEntity createSurface() {
        WrapperEntity entity = new WrapperEntity(EntityTypes.TEXT_DISPLAY);
        entity.spawn(SpigotConversionUtil.fromBukkitLocation(origin));
        if (entity.getEntityMeta() instanceof TextDisplayMeta meta) {
            meta.setText(Component.text(" "));
            meta.setBackgroundColor(0);
            meta.setSeeThrough(true);
        }
        if (entity.getEntityMeta() instanceof AbstractDisplayMeta meta) {
            meta.setBrightnessOverride(15 << 4 | 15 << 20);
            meta.setViewRange(VIEW_RANGE);
            meta.setInterpolationDelay(0);
            meta.setTransformationInterpolationDuration(0);
            meta.setPositionRotationInterpolationDuration(0);
            meta.setScale(new Vector3f(0.0001f, 0.0001f, 0.0001f));
        }
        entity.addViewer(viewer.getUniqueId());
        return entity;
    }

    private void ensureOrigin() {
        if (!viewer.getWorld().equals(origin.getWorld())) {
            origin = normalizedOrigin(viewer.getLocation());
            teleportAllToOrigin();
            return;
        }
        if (viewer.getLocation().distanceSquared(origin) < 80.0 * 80.0) return;
        origin = normalizedOrigin(viewer.getLocation());
        teleportAllToOrigin();
    }

    private void teleportAllToOrigin() {
        com.github.retrooper.packetevents.protocol.world.Location packetLocation = SpigotConversionUtil.fromBukkitLocation(origin);
        for (SurfacePair surface : surfaces) {
            surface.front().teleport(packetLocation);
            if (surface.back() != null) surface.back().teleport(packetLocation);
        }
    }

    private void apply(WrapperEntity entity, Matrix4f matrix, int argbColor, int interpolationTicks) {
        if (entity.getEntityMeta() instanceof TextDisplayMeta textMeta) {
            textMeta.setBackgroundColor(argbColor);
        }
        if (!(entity.getEntityMeta() instanceof AbstractDisplayMeta meta)) return;

        org.joml.Vector3f translation = new org.joml.Vector3f();
        matrix.getTranslation(translation);
        org.joml.Vector3f scale = new org.joml.Vector3f();
        matrix.getScale(scale);
        Quaternionf rotation = new Quaternionf();
        matrix.getUnnormalizedRotation(rotation);

        meta.setInterpolationDelay(0);
        meta.setTransformationInterpolationDuration(interpolationTicks);
        meta.setPositionRotationInterpolationDuration(0);
        meta.setTranslation(new Vector3f(translation.x, translation.y, translation.z));
        meta.setScale(new Vector3f(scale.x, scale.y, scale.z));
        meta.setLeftRotation(new Quaternion4f(rotation.x, rotation.y, rotation.z, rotation.w));
        entity.sendPacketToViewers(entity.getEntityMeta().createPacket());
    }

    private static org.joml.Vector3f toJoml(Vector vector) {
        return new org.joml.Vector3f((float) vector.getX(), (float) vector.getY(), (float) vector.getZ());
    }

    private static Location normalizedOrigin(Location location) {
        Location origin = location.clone();
        origin.setYaw(0.0f);
        origin.setPitch(0.0f);
        return origin;
    }

    private record SurfacePair(WrapperEntity front, WrapperEntity back) {
    }
}