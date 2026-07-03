package dev.twme.projectilesTrajectoryPreviewPlugin.config;

import java.util.EnumMap;
import java.util.Map;
import dev.twme.projectilesTrajectoryPreviewPlugin.physics.TrajectoryCalculator.TargetKind;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

public final class PreviewSettings {

    private final boolean enabled;
    private final boolean enableOffhand;
    private final DisplayMode showTrajectory;
    private final DisplayMode showOutline;
    private final DisplayMode showHighlight;
    private final DisplayMode showUncertainty;
    private final boolean showDropPreview;
    private final long dropPreviewDurationNanos;
    private final long updateIntervalNanos;
    private final long fallbackUpdateTicks;
    private final int lineCount;
    private final float lineThickness;
    private final LineRenderMode lineRenderMode;
    private final int lineEntityBudget;
    private final TrajectoryStyle trajectoryStyle;
    private final ColorMode trajectoryColor;
    private final ColorMode outlineColor;
    private final ColorMode highlightColor;
    private final ColorMode uncertaintyColor;
    private final double uncertaintyBaseRadius;
    private final double uncertaintySpreadPerBlock;
    private final double uncertaintyMaxRadius;
    private final Map<ProjectileToggle, Boolean> projectileToggles;

    private PreviewSettings(FileConfiguration config) {
        this.enabled = config.getBoolean("enabled", true);
        this.enableOffhand = config.getBoolean("enable-offhand", true);
        this.showTrajectory = DisplayMode.from(config.get("show-trajectory", "enabled"), DisplayMode.ENABLED);
        this.showOutline = DisplayMode.from(config.get("show-outline", "enabled"), DisplayMode.ENABLED);
        this.showHighlight = DisplayMode.from(config.get("show-highlight", "enabled"), DisplayMode.ENABLED);
        this.showUncertainty = DisplayMode.from(config.get("show-uncertainty", "enabled"), DisplayMode.ENABLED);
        this.showDropPreview = config.getBoolean("show-drop-preview", true);
        this.dropPreviewDurationNanos = Math.max(50L, config.getLong("drop-preview-duration-ms", 450L)) * 1_000_000L;
        this.updateIntervalNanos = Math.max(5L, config.getLong("update-interval-ms", 25L)) * 1_000_000L;
        this.fallbackUpdateTicks = Math.max(0L, config.getLong("fallback-update-ticks", 0L));
        this.lineThickness = Math.max(0.001f, (float) config.getDouble("line-thickness", 0.025));
        this.lineRenderMode = LineRenderMode.from(config.getString("line-render-mode", null), config.getBoolean("double-sided-lines", false));
        this.lineEntityBudget = Math.max(64, config.getInt("line-entity-budget", 128));
        this.lineCount = Math.max(8, config.getInt("line-count", 48));
        this.trajectoryStyle = TrajectoryStyle.from(config.getString("trajectory-style", "solid"));
        this.trajectoryColor = ColorMode.from(config.getString("trajectory-color", "depends-on-target"), config.getInt("trajectory-alpha", 210));
        this.outlineColor = ColorMode.from(config.getString("outline-color", "#FFEB50"), config.getInt("outline-alpha", 190));
        this.highlightColor = ColorMode.from(config.getString("highlight-color", "#FFEB50"), config.getInt("highlight-alpha", 55));
        this.uncertaintyColor = ColorMode.from(config.getString("uncertainty-color", "#F2F7FF"), config.getInt("uncertainty-alpha", 42));
        this.uncertaintyBaseRadius = Math.max(0.0, config.getDouble("uncertainty-base-radius", 0.08));
        this.uncertaintySpreadPerBlock = Math.max(0.0, config.getDouble("uncertainty-spread-per-block", 0.01725));
        this.uncertaintyMaxRadius = Math.max(0.05, config.getDouble("uncertainty-max-radius", 1.25));
        this.projectileToggles = new EnumMap<>(ProjectileToggle.class);
        for (ProjectileToggle toggle : ProjectileToggle.values()) {
            projectileToggles.put(toggle, config.getBoolean("projectiles." + toggle.configKey, true));
        }
    }

    public static PreviewSettings load(FileConfiguration config) {
        return new PreviewSettings(config);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean enableOffhand() {
        return enableOffhand;
    }

    public DisplayMode showTrajectory() {
        return showTrajectory;
    }

    public DisplayMode showOutline() {
        return showOutline;
    }

    public DisplayMode showHighlight() {
        return showHighlight;
    }

    public DisplayMode showUncertainty() {
        return showUncertainty;
    }

    public boolean showDropPreview() {
        return showDropPreview;
    }

    public long dropPreviewDurationNanos() {
        return dropPreviewDurationNanos;
    }

    public long updateIntervalNanos() {
        return updateIntervalNanos;
    }

    public long fallbackUpdateTicks() {
        return fallbackUpdateTicks;
    }

    public int lineCount() {
        return lineCount;
    }

    public float lineThickness() {
        return lineThickness;
    }

    public LineRenderMode lineRenderMode() {
        return lineRenderMode;
    }

    public int lineEntityBudget() {
        return lineEntityBudget;
    }

    public TrajectoryStyle trajectoryStyle() {
        return trajectoryStyle;
    }

    public int trajectoryColor(TargetKind targetKind) {
        return trajectoryColor.argb(targetKind);
    }

    public int outlineColor(TargetKind targetKind) {
        return outlineColor.argb(targetKind);
    }

    public int highlightColor(TargetKind targetKind) {
        return highlightColor.argb(targetKind);
    }

    public int uncertaintyColor(TargetKind targetKind) {
        return uncertaintyColor.argb(targetKind);
    }

    public double uncertaintyRadius(double travelDistance) {
        return Math.min(uncertaintyMaxRadius, uncertaintyBaseRadius + Math.max(0.0, travelDistance) * uncertaintySpreadPerBlock);
    }

    public boolean isEnabled(Material material) {
        ProjectileToggle toggle = ProjectileToggle.from(material);
        return toggle != null && projectileToggles.getOrDefault(toggle, true);
    }

    private static int argb(String hex, int alpha) {
        String normalized = hex == null ? "" : hex.trim();
        if (normalized.startsWith("#")) normalized = normalized.substring(1);
        try {
            int rgb = Integer.parseInt(normalized, 16);
            return Color.fromARGB(clamp(alpha), (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF).asARGB();
        } catch (NumberFormatException ignored) {
            return Color.fromARGB(clamp(alpha), 255, 255, 255).asARGB();
        }
    }

    private static int clamp(int alpha) {
        return Math.max(0, Math.min(255, alpha));
    }

    private record ColorMode(boolean dependsOnTarget, int fixedArgb, int alpha) {

        private static ColorMode from(String value, int alpha) {
            String normalized = value == null ? "" : value.trim();
            if (normalized.equalsIgnoreCase("depends-on-target") || normalized.equalsIgnoreCase("depends_on_target")) {
                return new ColorMode(true, 0, clamp(alpha));
            }
            return new ColorMode(false, PreviewSettings.argb(normalized, alpha), clamp(alpha));
        }

        private int argb(TargetKind targetKind) {
            if (!dependsOnTarget) return fixedArgb;
            return switch (targetKind == null ? TargetKind.NONE : targetKind) {
                case PLAYER -> Color.fromARGB(alpha, 0, 0, 255).asARGB();
                case PASSIVE -> Color.fromARGB(alpha, 0, 255, 0).asARGB();
                case HOSTILE -> Color.fromARGB(alpha, 255, 0, 0).asARGB();
                case MOB -> Color.fromARGB(alpha, 128, 0, 128).asARGB();
                case LIVING -> Color.fromARGB(alpha, 0, 255, 255).asARGB();
                case OTHER_ENTITY -> Color.fromARGB(alpha, 255, 0, 255).asARGB();
                case BLOCK, NONE -> Color.fromARGB(alpha, 255, 255, 255).asARGB();
            };
        }
    }

    public enum TrajectoryStyle {
        SOLID(1.0),
        DASHED(0.5),
        DOTTED(0.15);

        private final double segmentScale;

        TrajectoryStyle(double segmentScale) {
            this.segmentScale = segmentScale;
        }

        public double segmentScale() {
            return segmentScale;
        }

        private static TrajectoryStyle from(String value) {
            if (value == null) return SOLID;
            for (TrajectoryStyle style : values()) {
                if (style.name().equalsIgnoreCase(value.replace('-', '_'))) return style;
            }
            return SOLID;
        }
    }

    public enum LineRenderMode {
        STANDARD(1, new float[] {0.0f}, false),
        SINGLE_135(1, new float[] {(float) Math.toRadians(135.0)}, false),
        DOUBLE_SIDED(2, new float[] {0.0f}, true),
        CROSSED_DOUBLE_SIDED(4, new float[] {(float) Math.toRadians(45.0), (float) Math.toRadians(135.0)}, true);

        private final int entitiesPerLogicalLine;
        private final float[] rolls;
        private final boolean doubleSided;

        LineRenderMode(int entitiesPerLogicalLine, float[] rolls, boolean doubleSided) {
            this.entitiesPerLogicalLine = entitiesPerLogicalLine;
            this.rolls = rolls;
            this.doubleSided = doubleSided;
        }

        public int entitiesPerLogicalLine() {
            return entitiesPerLogicalLine;
        }

        public float[] rolls() {
            return rolls;
        }

        public boolean doubleSided() {
            return doubleSided;
        }

        private static LineRenderMode from(String value, boolean legacyDoubleSided) {
            if (value == null || value.isBlank()) return legacyDoubleSided ? DOUBLE_SIDED : SINGLE_135;
            String normalized = value.trim().replace('-', '_');
            for (LineRenderMode mode : values()) {
                if (mode.name().equalsIgnoreCase(normalized)) return mode;
            }
            return legacyDoubleSided ? DOUBLE_SIDED : SINGLE_135;
        }
    }

    public enum DisplayMode {
        ENABLED,
        TARGET_IS_ENTITY,
        DISABLED;

        public boolean allows(TargetKind targetKind) {
            return switch (this) {
                case ENABLED -> true;
                case TARGET_IS_ENTITY -> targetKind != null
                        && targetKind != TargetKind.NONE
                        && targetKind != TargetKind.BLOCK;
                case DISABLED -> false;
            };
        }

        private static DisplayMode from(Object value, DisplayMode fallback) {
            if (value instanceof Boolean booleanValue) return booleanValue ? ENABLED : DISABLED;
            if (value == null) return fallback;
            String normalized = value.toString().trim().replace('-', '_');
            for (DisplayMode mode : values()) {
                if (mode.name().equalsIgnoreCase(normalized)) return mode;
            }
            return fallback;
        }
    }

    private enum ProjectileToggle {
        BOW("bow", Material.BOW),
        CROSSBOW("crossbow", Material.CROSSBOW),
        TRIDENT("trident", Material.TRIDENT),
        ENDER_PEARL("ender-pearl", Material.ENDER_PEARL),
        SNOWBALL("snowball", Material.SNOWBALL),
        EGG("egg", Material.EGG),
        WIND_CHARGE("wind-charge", Material.WIND_CHARGE),
        POTION("potion", Material.SPLASH_POTION, Material.LINGERING_POTION),
        EXPERIENCE_BOTTLE("experience-bottle", Material.EXPERIENCE_BOTTLE),
        FISHING_ROD("fishing-rod", Material.FISHING_ROD);

        private final String configKey;
        private final Material[] materials;

        ProjectileToggle(String configKey, Material... materials) {
            this.configKey = configKey;
            this.materials = materials;
        }

        private static ProjectileToggle from(Material material) {
            for (ProjectileToggle toggle : values()) {
                for (Material candidate : toggle.materials) {
                    if (candidate == material) return toggle;
                }
            }
            return null;
        }
    }
}