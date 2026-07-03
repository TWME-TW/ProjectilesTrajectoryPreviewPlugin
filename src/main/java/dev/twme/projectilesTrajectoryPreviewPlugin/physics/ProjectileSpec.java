package dev.twme.projectilesTrajectoryPreviewPlugin.physics;

import dev.twme.projectilesTrajectoryPreviewPlugin.config.PreviewSettings;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MainHand;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.util.Vector;

public record ProjectileSpec(
        double gravity,
        double drag,
        double waterDrag,
        double underwaterGravity,
        Vector initialVelocity,
        Vector offset,
        int handMultiplier,
        PhysicsOrder order,
        boolean waterCollision,
        boolean canHitEntities
) {

    private static final int TRIDENT_THROW_THRESHOLD_TICKS = 10;

    public static ProjectileSpec drop(Player player) {
        double gravity = 0.04;
        double drag = 0.98;
        double waterDrag = 0.98 * 0.9900000095367432;
        Vector velocity = dropVelocity(player);
        return new ProjectileSpec(gravity, drag, waterDrag, gravity,
                velocity, new Vector(0.2, -0.06, 0.2), handMultiplier(player, EquipmentSlot.HAND),
                PhysicsOrder.GRAVITY_POSITION_DRAG, true, false);
    }

    public static ProjectileSpec from(Player player, ItemStack stack, EquipmentSlot hand, PreviewSettings settings) {
        if (stack == null || stack.getType().isAir()) return null;
        if (!settings.isEnabled(stack.getType())) return null;

        Material type = stack.getType();
        Vector direction = player.getEyeLocation().getDirection().normalize();
        double gravity = 0.05;
        double drag = 0.99;
        double waterDrag = 0.6;
        Vector offset = new Vector(0.2, -0.06, 0.2);
        int handMultiplier = handMultiplier(player, hand);

        return switch (type) {
            case BOW -> bow(player, stack, hand, direction, gravity, drag, waterDrag, offset, handMultiplier);
            case CROSSBOW -> crossbow(player, stack, direction, gravity, drag, waterDrag, handMultiplier);
            case TRIDENT -> trident(player, stack, hand, direction, gravity, drag, handMultiplier);
            case SNOWBALL, EGG, ENDER_PEARL -> new ProjectileSpec(0.03, drag, 0.8, 0.03,
                    direction.multiply(1.5), offset, handMultiplier, PhysicsOrder.GRAVITY_DRAG_POSITION, false, true);
            case SPLASH_POTION, LINGERING_POTION -> new ProjectileSpec(gravity, drag, 0.8, gravity,
                    angleFromRot(player.getPitch(), player.getYaw(), -20.0f).multiply(0.5), offset, handMultiplier,
                    PhysicsOrder.GRAVITY_DRAG_POSITION, false, true);
            case EXPERIENCE_BOTTLE -> new ProjectileSpec(0.07, drag, 0.8, 0.07,
                    angleFromRot(player.getPitch(), player.getYaw(), -20.0f).multiply(0.7), offset, handMultiplier,
                    PhysicsOrder.GRAVITY_DRAG_POSITION, false, true);
            case FISHING_ROD -> new ProjectileSpec(0.03, 0.92, 0.92, 0.03,
                    fishingHookVelocity(player), new Vector(0.16, -0.06, 0.2), handMultiplier,
                    PhysicsOrder.GRAVITY_POSITION_DRAG, true, true);
            case WIND_CHARGE -> new ProjectileSpec(0.0, 0.95, 0.8, 0.0,
                    direction, offset, handMultiplier, PhysicsOrder.POSITION_DRAG_GRAVITY, false, true);
            default -> null;
        };
    }

    public static ProjectileSpec crossbowMultishot(Player player, ItemStack stack, EquipmentSlot hand,
                                                   PreviewSettings settings, double yawOffsetDegrees) {
        if (stack == null || stack.getType() != Material.CROSSBOW || !settings.isEnabled(Material.CROSSBOW)) return null;
        if (!stack.containsEnchantment(Enchantment.MULTISHOT)) return null;
        Vector direction = yawOffset(player.getEyeLocation().getDirection().normalize(), yawOffsetDegrees);
        return crossbow(player, stack, direction, 0.05, 0.99, 0.6, handMultiplier(player, hand));
    }

    private static ProjectileSpec bow(Player player, ItemStack stack, EquipmentSlot hand, Vector direction,
                                      double gravity, double drag, double waterDrag, Vector offset, int handMultiplier) {
        float power = bowPower(player, stack, hand);
        if (power < 0.1f) return null;
        return new ProjectileSpec(gravity, drag, waterDrag, gravity,
                direction.multiply(3.0 * power), offset, handMultiplier, PhysicsOrder.POSITION_DRAG_GRAVITY, false, true);
    }

    private static ProjectileSpec trident(Player player, ItemStack stack, EquipmentSlot hand, Vector direction,
                                          double gravity, double drag, int handMultiplier) {
        if (!isUsing(player, stack, hand) || player.getActiveItemUsedTime() < TRIDENT_THROW_THRESHOLD_TICKS) return null;
        if (stack.containsEnchantment(Enchantment.RIPTIDE)) return null;
        return new ProjectileSpec(gravity, drag, 0.99, gravity,
                direction.multiply(2.5), new Vector(0.2, 0.1, 0.2), handMultiplier,
                PhysicsOrder.POSITION_DRAG_GRAVITY, false, true);
    }

    private static ProjectileSpec crossbow(Player player, ItemStack stack, Vector direction,
                                           double gravity, double drag, double waterDrag, int handMultiplier) {
        if (!(stack.getItemMeta() instanceof CrossbowMeta meta) || !meta.hasChargedProjectiles()) return null;

        double speed = 3.15;
        double crossbowGravity = gravity;
        double crossbowWaterDrag = waterDrag;
        for (ItemStack projectile : meta.getChargedProjectiles()) {
            if (projectile.getType() == Material.FIREWORK_ROCKET) {
                speed = 1.6;
                crossbowGravity = 0.0;
                crossbowWaterDrag = drag;
                break;
            }
        }

        return new ProjectileSpec(crossbowGravity, drag, crossbowWaterDrag, crossbowGravity,
                direction.multiply(speed), new Vector(0.0, -0.06, 0.03), handMultiplier,
                PhysicsOrder.POSITION_DRAG_GRAVITY, false, true);
    }

    private static float bowPower(Player player, ItemStack stack, EquipmentSlot hand) {
        if (!isUsing(player, stack, hand)) return 0.0f;
        float ticks = player.getActiveItemUsedTime();
        float power = ticks / 20.0f;
        power = (power * power + power * 2.0f) / 3.0f;
        return Math.min(power, 1.0f);
    }

    private static boolean isUsing(Player player, ItemStack stack, EquipmentSlot hand) {
        return player.isHandRaised()
                && player.getActiveItemHand() == hand
                && player.getActiveItem().getType() == stack.getType();
    }

    private static int handMultiplier(Player player, EquipmentSlot hand) {
        int mainHand = player.getMainHand() == MainHand.RIGHT ? 1 : -1;
        return hand == EquipmentSlot.HAND ? mainHand : -mainHand;
    }

    private static Vector yawOffset(Vector vector, double degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = vector.getX() * cos - vector.getZ() * sin;
        double z = vector.getX() * sin + vector.getZ() * cos;
        return new Vector(x, vector.getY(), z).normalize();
    }

    private static Vector dropVelocity(Player player) {
        float pitch = player.getPitch();
        float yaw = player.getYaw();
        double pitchRadians = Math.toRadians(pitch);
        double yawRadians = Math.toRadians(yaw);
        float sinPitch = (float) Math.sin(pitchRadians);
        float cosPitch = (float) Math.cos(pitchRadians);
        float sinYaw = (float) Math.sin(yawRadians);
        float cosYaw = (float) Math.cos(yawRadians);
        float angle = 0.5f * 6.2831855f;
        float spread = 0.02f * 0.5f;
        return new Vector(
                -sinYaw * cosPitch * 0.3f + Math.cos(angle) * spread,
                -sinPitch * 0.3f + 0.1f,
                cosYaw * cosPitch * 0.3f + Math.sin(angle) * spread);
    }

    private static Vector fishingHookVelocity(Player player) {
        float pitch = player.getPitch();
        float yaw = player.getYaw();
        double cosNegYaw = Math.cos(-yaw * Math.PI / 180.0 - Math.PI);
        double sinNegYaw = Math.sin(-yaw * Math.PI / 180.0 - Math.PI);
        double negCosPitch = -Math.cos(-pitch * Math.PI / 180.0);
        double sinNegPitch = Math.sin(-pitch * Math.PI / 180.0);
        Vector velocity = new Vector(-sinNegYaw, clamp(-(sinNegPitch / negCosPitch), -5.0, 5.0), -cosNegYaw);
        double length = velocity.length();
        double scale = 0.6 / length + 0.5;
        return velocity.multiply(scale);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Vector angleFromRot(float pitch, float yaw, float pitchOffset) {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch + pitchOffset);
        double x = -Math.sin(yawRadians) * Math.cos(pitchRadians);
        double y = -Math.sin(pitchRadians);
        double z = Math.cos(yawRadians) * Math.cos(pitchRadians);
        return new Vector(x, y, z).normalize();
    }
}