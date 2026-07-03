package dev.twme.projectilesTrajectoryPreviewPlugin.preview;

import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3f;
import dev.twme.projectilesTrajectoryPreviewPlugin.config.PreviewSettings;
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

final class TextDisplayLinePool implements AutoCloseable {

    private static final float VIEW_RANGE = 100.0f;

    private final Player viewer;
    private final float thickness;
    private final PreviewSettings.LineRenderMode renderMode;
    private final int transformationInterpolationTicks;
    private final List<LineShape> lines;
    private Location origin;

    TextDisplayLinePool(Player viewer, int size, float thickness, PreviewSettings.LineRenderMode renderMode, int transformationInterpolationTicks) {
        this.viewer = viewer;
        this.thickness = thickness;
        this.renderMode = renderMode;
        this.transformationInterpolationTicks = transformationInterpolationTicks;
        this.origin = normalizedOrigin(viewer.getLocation());
        this.lines = new ArrayList<>(size);

        for (int index = 0; index < size; index++) {
            lines.add(createShape());
        }
    }

    void update(int index, Vector start, Vector end, int argbColor) {
        update(index, start, end, argbColor, false);
    }

    void update(int index, Vector start, Vector end, int argbColor, boolean offhand) {
        ensureOrigin();
        LineShape shape = lines.get(index);
        float[] rolls = renderMode.rolls(offhand);
        for (int rollIndex = 0; rollIndex < rolls.length; rollIndex++) {
            float roll = rolls[rollIndex];
            LinePair pair = shape.pairs().get(rollIndex);
            apply(pair.front(), lineMatrix(start, end, roll), argbColor);
            if (pair.back() != null) apply(pair.back(), lineMatrix(end, start, -roll), argbColor);
        }
    }

    void hide(int index) {
        hide(lines.get(index));
    }

    @Override
    public void close() {
        for (LineShape shape : lines) {
            for (LinePair line : shape.pairs()) {
                line.front().remove();
                if (line.back() != null) line.back().remove();
            }
        }
        lines.clear();
    }

    private Matrix4f lineMatrix(Vector start, Vector end, float roll) {
        Matrix4f matrix = TextDisplayUtil.textDisplayLine(toJoml(start), toJoml(end), thickness, roll);
        return new Matrix4f()
                .translate((float) -origin.getX(), (float) -origin.getY(), (float) -origin.getZ())
                .mul(matrix);
    }

    private LineShape createShape() {
        List<LinePair> pairs = new ArrayList<>(renderMode.rolls().length);
        for (int index = 0; index < renderMode.rolls().length; index++) {
            pairs.add(new LinePair(createLine(), renderMode.doubleSided() ? createLine() : null));
        }
        return new LineShape(pairs);
    }

    private void hide(LineShape shape) {
        for (LinePair line : shape.pairs()) {
            hide(line.front());
            if (line.back() != null) hide(line.back());
        }
    }

    private void hide(WrapperEntity line) {
        if (line.getEntityMeta() instanceof TextDisplayMeta meta) {
            meta.setBackgroundColor(0);
        }
        if (line.getEntityMeta() instanceof AbstractDisplayMeta meta) {
            meta.setScale(new Vector3f(0.0001f, 0.0001f, 0.0001f));
            meta.setTransformationInterpolationDuration(transformationInterpolationTicks);
            line.sendPacketToViewers(line.getEntityMeta().createPacket());
        }
    }

    private WrapperEntity createLine() {
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
            meta.setTransformationInterpolationDuration(transformationInterpolationTicks);
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
        for (LineShape shape : lines) {
            for (LinePair line : shape.pairs()) {
                line.front().teleport(packetLocation);
                if (line.back() != null) line.back().teleport(packetLocation);
            }
        }
    }

    private void apply(WrapperEntity entity, Matrix4f matrix, int argbColor) {
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
        meta.setTransformationInterpolationDuration(transformationInterpolationTicks);
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

    private record LineShape(List<LinePair> pairs) {
    }

    private record LinePair(WrapperEntity front, WrapperEntity back) {
    }
}