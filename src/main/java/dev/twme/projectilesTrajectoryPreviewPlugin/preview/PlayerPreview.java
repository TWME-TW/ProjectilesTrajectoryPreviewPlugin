package dev.twme.projectilesTrajectoryPreviewPlugin.preview;

import dev.twme.projectilesTrajectoryPreviewPlugin.config.PreviewSettings;
import dev.twme.projectilesTrajectoryPreviewPlugin.physics.TrajectoryCalculator;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

final class PlayerPreview implements AutoCloseable {

    private static final int OUTLINE_LINE_COUNT = 12;
    private static final int HIGHLIGHT_FACE_COUNT = 6;
    private static final int UNCERTAINTY_FACE_COUNT = 3;
    private static final int UNCERTAINTY_ENTITIES_PER_FACE = 2;

    private final PreviewSettings settings;
    private final int trajectoryLineCount;
    private final TextDisplayLinePool linePool;
    private final TextDisplayLinePool outlinePool;
    private final TextDisplaySurfacePool highlightPool;
    private final TextDisplaySurfacePool uncertaintyPool;
    private long lastUpdateNanos;

    PlayerPreview(Player player, PreviewSettings settings) {
        this.settings = settings;
        this.trajectoryLineCount = effectiveTrajectoryLineCount(settings);
        this.linePool = new TextDisplayLinePool(player, trajectoryLineCount, settings.lineThickness(), settings.lineRenderMode());
        this.outlinePool = new TextDisplayLinePool(player, OUTLINE_LINE_COUNT, settings.lineThickness(), settings.lineRenderMode());
        this.highlightPool = new TextDisplaySurfacePool(player, HIGHLIGHT_FACE_COUNT);
        this.uncertaintyPool = new TextDisplaySurfacePool(player, UNCERTAINTY_FACE_COUNT, true);
    }

    private static int effectiveTrajectoryLineCount(PreviewSettings settings) {
        int reservedEntities = OUTLINE_LINE_COUNT * settings.lineRenderMode().entitiesPerLogicalLine()
            + HIGHLIGHT_FACE_COUNT
            + UNCERTAINTY_FACE_COUNT * UNCERTAINTY_ENTITIES_PER_FACE;
        int availableEntities = Math.max(settings.lineRenderMode().entitiesPerLogicalLine() * 8, settings.lineEntityBudget() - reservedEntities);
        int budgetedLines = availableEntities / settings.lineRenderMode().entitiesPerLogicalLine();
        return Math.max(8, Math.min(settings.lineCount(), budgetedLines));
    }

    boolean matches(PreviewSettings settings) {
        return this.settings == settings;
    }

    boolean shouldUpdate(long now, long minIntervalNanos) {
        if (now - lastUpdateNanos < minIntervalNanos) return false;
        lastUpdateNanos = now;
        return true;
    }

    void render(List<TrajectoryCalculator.Trajectory> trajectories) {
        if (trajectories.isEmpty()) {
            clear();
            return;
        }

        int lineCount = trajectoryLineCount;
        int displayIndex = 0;
        BoundingBox firstTargetBox = null;
        TrajectoryCalculator.TargetKind firstTargetKind = TrajectoryCalculator.TargetKind.NONE;
        Vector firstImpactPoint = null;
        double firstTravelDistance = 0.0;
        List<TrajectoryCalculator.Trajectory> visibleTrajectories = trajectories.stream()
                .filter(trajectory -> settings.showTrajectory().allows(trajectory.targetKind()))
                .toList();
        int budgetPerTrajectory = Math.max(1, lineCount / Math.max(1, visibleTrajectories.size()));

        for (TrajectoryCalculator.Trajectory trajectory : trajectories) {
            if (trajectory.points().size() < 2) continue;
            if (firstTargetBox == null) {
                firstTargetBox = trajectory.targetBox();
                firstTargetKind = trajectory.targetKind();
                if (trajectory.targetBox() != null) {
                    List<Vector> points = trajectory.points();
                    firstImpactPoint = points.get(points.size() - 1);
                    firstTravelDistance = points.get(0).distance(firstImpactPoint);
                }
            }
        }

        for (TrajectoryCalculator.Trajectory trajectory : visibleTrajectories) {
            if (trajectory.points().size() < 2) continue;
            displayIndex = renderTrajectory(trajectory, displayIndex, Math.min(lineCount, displayIndex + budgetPerTrajectory));
            if (displayIndex >= lineCount) break;
        }

        while (displayIndex < lineCount) {
            linePool.hide(displayIndex++);
        }

        renderHighlight(settings.showHighlight().allows(firstTargetKind) ? firstTargetBox : null, firstTargetKind);
        renderOutline(settings.showOutline().allows(firstTargetKind) ? firstTargetBox : null, firstTargetKind);
        renderUncertainty(settings.showUncertainty().allows(firstTargetKind) ? firstImpactPoint : null, firstTravelDistance, firstTargetKind);
    }

    private int renderTrajectory(TrajectoryCalculator.Trajectory trajectory, int displayIndex, int maxDisplayIndex) {
        List<Vector> points = trajectory.points();
        int segmentBudget = Math.max(1, maxDisplayIndex - displayIndex);
        int visibleSegments = Math.min(segmentBudget, points.size() - 1);
        int stride = Math.max(1, (points.size() - 1 + visibleSegments - 1) / visibleSegments);

        for (int pointIndex = 0; pointIndex < points.size() - 1 && displayIndex < maxDisplayIndex; pointIndex += stride) {
            Vector start = points.get(pointIndex);
            Vector end = points.get(Math.min(points.size() - 1, pointIndex + stride));
            if (settings.trajectoryStyle().segmentScale() < 1.0) {
                end = start.clone().add(end.clone().subtract(start).multiply(settings.trajectoryStyle().segmentScale()));
            }
            linePool.update(displayIndex++, start, end, settings.trajectoryColor(trajectory.targetKind()));
        }
        return displayIndex;
    }

    void clear() {
        for (int index = 0; index < trajectoryLineCount; index++) {
            linePool.hide(index);
        }
        for (int index = 0; index < OUTLINE_LINE_COUNT; index++) {
            outlinePool.hide(index);
        }
        for (int index = 0; index < HIGHLIGHT_FACE_COUNT; index++) {
            highlightPool.hide(index);
        }
        for (int index = 0; index < UNCERTAINTY_FACE_COUNT; index++) {
            uncertaintyPool.hide(index);
        }
    }

    @Override
    public void close() {
        linePool.close();
        outlinePool.close();
        highlightPool.close();
        uncertaintyPool.close();
    }

    private void renderUncertainty(Vector center, double travelDistance, TrajectoryCalculator.TargetKind targetKind) {
        if (center == null) {
            for (int index = 0; index < UNCERTAINTY_FACE_COUNT; index++) {
                uncertaintyPool.hide(index);
            }
            return;
        }

        double radius = settings.uncertaintyRadius(travelDistance);
        int color = settings.uncertaintyColor(targetKind);
        uncertaintyPool.update(0,
                center.clone().add(new Vector(-radius, 0, -radius)),
                center.clone().add(new Vector(radius, 0, -radius)),
                center.clone().add(new Vector(-radius, 0, radius)),
                color);
        uncertaintyPool.update(1,
                center.clone().add(new Vector(-radius, -radius, 0)),
                center.clone().add(new Vector(radius, -radius, 0)),
                center.clone().add(new Vector(-radius, radius, 0)),
                color);
        uncertaintyPool.update(2,
                center.clone().add(new Vector(0, -radius, -radius)),
                center.clone().add(new Vector(0, radius, -radius)),
                center.clone().add(new Vector(0, -radius, radius)),
                color);
    }

    private void renderHighlight(BoundingBox box, TrajectoryCalculator.TargetKind targetKind) {
        if (box == null) {
            for (int index = 0; index < HIGHLIGHT_FACE_COUNT; index++) {
                highlightPool.hide(index);
            }
            return;
        }

        BoxCorners corners = BoxCorners.of(box);
        int color = settings.highlightColor(targetKind);
        highlightPool.update(0, corners.v000(), corners.v100(), corners.v001(), color);
        highlightPool.update(1, corners.v010(), corners.v011(), corners.v110(), color);
        highlightPool.update(2, corners.v000(), corners.v010(), corners.v100(), color);
        highlightPool.update(3, corners.v001(), corners.v101(), corners.v011(), color);
        highlightPool.update(4, corners.v000(), corners.v001(), corners.v010(), color);
        highlightPool.update(5, corners.v100(), corners.v110(), corners.v101(), color);
    }

    private void renderOutline(BoundingBox box, TrajectoryCalculator.TargetKind targetKind) {
        if (box == null) {
            for (int index = 0; index < OUTLINE_LINE_COUNT; index++) {
                outlinePool.hide(index);
            }
            return;
        }

        BoxCorners corners = BoxCorners.of(box);
        int color = settings.outlineColor(targetKind);
        outlinePool.update(0, corners.v000(), corners.v100(), color);
        outlinePool.update(1, corners.v001(), corners.v101(), color);
        outlinePool.update(2, corners.v010(), corners.v110(), color);
        outlinePool.update(3, corners.v011(), corners.v111(), color);
        outlinePool.update(4, corners.v000(), corners.v001(), color);
        outlinePool.update(5, corners.v100(), corners.v101(), color);
        outlinePool.update(6, corners.v010(), corners.v011(), color);
        outlinePool.update(7, corners.v110(), corners.v111(), color);
        outlinePool.update(8, corners.v000(), corners.v010(), color);
        outlinePool.update(9, corners.v100(), corners.v110(), color);
        outlinePool.update(10, corners.v001(), corners.v011(), color);
        outlinePool.update(11, corners.v101(), corners.v111(), color);
    }

    private record BoxCorners(Vector v000, Vector v001, Vector v010, Vector v011,
                              Vector v100, Vector v101, Vector v110, Vector v111) {

        private static BoxCorners of(BoundingBox box) {
            double minX = box.getMinX();
            double minY = box.getMinY();
            double minZ = box.getMinZ();
            double maxX = box.getMaxX();
            double maxY = box.getMaxY();
            double maxZ = box.getMaxZ();

            return new BoxCorners(
                    new Vector(minX, minY, minZ),
                    new Vector(minX, minY, maxZ),
                    new Vector(minX, maxY, minZ),
                    new Vector(minX, maxY, maxZ),
                    new Vector(maxX, minY, minZ),
                    new Vector(maxX, minY, maxZ),
                    new Vector(maxX, maxY, minZ),
                    new Vector(maxX, maxY, maxZ));
        }
    }
}