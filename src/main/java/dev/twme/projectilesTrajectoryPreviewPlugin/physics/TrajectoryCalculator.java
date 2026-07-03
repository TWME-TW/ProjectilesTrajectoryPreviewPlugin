package dev.twme.projectilesTrajectoryPreviewPlugin.physics;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.FluidCollisionMode;
import org.bukkit.World;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.NPC;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class TrajectoryCalculator {

    private static final int MAX_STEPS = 120;
    private static final double ENTITY_TRACE_RADIUS = 0.1;

    public Trajectory calculate(Player player, ProjectileSpec spec) {
        World world = player.getWorld();
        Vector eye = player.getEyeLocation().toVector();
        Vector position = eye.clone().add(new Vector(0, -0.10000000149011612, 0));
        Vector handDelta = handDelta(player, eye, position, spec);
        Vector velocity = spec.initialVelocity().clone().add(player.getVelocity());
        Vector previous = position.clone();
        double gravity = spec.gravity();
        double drag = spec.drag();
        List<Vector> points = new ArrayList<>();

        for (int step = 0; step < MAX_STEPS; step++) {
            points.add(position.clone());

            applyStep(spec.order(), position, velocity, drag, gravity);

            Vector segment = position.clone().subtract(previous);
            double length = segment.length();
            if (length <= 1.0E-6) break;

            RayTraceResult blockHit = world.rayTraceBlocks(previous.toLocation(world), segment, length,
                    spec.waterCollision() ? FluidCollisionMode.ALWAYS : FluidCollisionMode.NEVER, true);
            RayTraceResult entityHit = spec.canHitEntities()
                    ? world.rayTraceEntities(previous.toLocation(world), segment, length, ENTITY_TRACE_RADIUS,
                            entity -> isValidTarget(player, entity))
                    : null;

            RayTraceResult nearestHit = nearest(previous, blockHit, entityHit);
            if (nearestHit != null && nearestHit.getHitPosition() != null) {
                points.add(nearestHit.getHitPosition().clone());
                return new Trajectory(displayPoints(points, handDelta), targetBox(nearestHit), targetKind(nearestHit), spec.offhand());
            }

            if (!spec.waterCollision() && crossesWater(world, previous, segment, length)) {
                drag = spec.waterDrag();
                gravity = spec.underwaterGravity();
            } else {
                drag = spec.drag();
                gravity = spec.gravity();
            }

            previous = position.clone();
        }

        return new Trajectory(displayPoints(points, handDelta), null, TargetKind.NONE, spec.offhand());
    }

    private static void applyStep(PhysicsOrder order, Vector position, Vector velocity, double drag, double gravity) {
        switch (order) {
            case POSITION_DRAG_GRAVITY -> {
                position.add(velocity);
                velocity.multiply(drag);
                velocity.subtract(new Vector(0, gravity, 0));
            }
            case GRAVITY_POSITION_DRAG -> {
                velocity.subtract(new Vector(0, gravity, 0));
                position.add(velocity);
                velocity.multiply(drag);
            }
            case GRAVITY_DRAG_POSITION -> {
                velocity.subtract(new Vector(0, gravity, 0));
                velocity.multiply(drag);
                position.add(velocity);
            }
        }
    }

    private static boolean crossesWater(World world, Vector start, Vector direction, double length) {
        return world.rayTraceBlocks(start.toLocation(world), direction, length, FluidCollisionMode.ALWAYS, true) != null;
    }

    private static RayTraceResult nearest(Vector start, RayTraceResult blockHit, RayTraceResult entityHit) {
        if (blockHit == null) return entityHit;
        if (entityHit == null) return blockHit;
        double blockDistance = start.distanceSquared(blockHit.getHitPosition());
        double entityDistance = start.distanceSquared(entityHit.getHitPosition());
        return entityDistance < blockDistance ? entityHit : blockHit;
    }

    private static BoundingBox targetBox(RayTraceResult hit) {
        if (hit.getHitBlock() != null) return hit.getHitBlock().getBoundingBox();
        if (hit.getHitEntity() != null) return hit.getHitEntity().getBoundingBox();
        return null;
    }

    private static TargetKind targetKind(RayTraceResult hit) {
        if (hit.getHitBlock() != null) return TargetKind.BLOCK;
        Entity entity = hit.getHitEntity();
        if (entity == null) return TargetKind.NONE;
        if (entity instanceof Player) return TargetKind.PLAYER;
        if (entity instanceof Monster) return TargetKind.HOSTILE;
        if (entity instanceof AbstractVillager || entity instanceof Ageable) return TargetKind.PASSIVE;
        if (entity instanceof Mob) return TargetKind.MOB;
        if (entity instanceof LivingEntity || entity instanceof NPC) return TargetKind.LIVING;
        return TargetKind.OTHER_ENTITY;
    }

    private static List<Vector> displayPoints(List<Vector> points, Vector handDelta) {
        List<Vector> displayPoints = new ArrayList<>(points.size());
        int size = points.size();
        for (int index = 0; index < size; index++) {
            double factor = (size - index) / (double) size;
            displayPoints.add(points.get(index).clone().add(handDelta.clone().multiply(factor)));
        }
        return displayPoints;
    }

    private static Vector handDelta(Player player, Vector eye, Vector startPosition, ProjectileSpec spec) {
        Vector forward = player.getEyeLocation().getDirection().normalize();
        double yaw = Math.toRadians(-player.getEyeLocation().getYaw());
        double pitch = Math.toRadians(-player.getEyeLocation().getPitch());
        Vector up = new Vector(-Math.sin(pitch) * Math.sin(yaw), Math.cos(pitch), -Math.sin(pitch) * Math.cos(yaw)).normalize();
        Vector right = forward.clone().crossProduct(up).normalize();
        return right.multiply(spec.handMultiplier() * spec.offset().getX())
                .add(up.multiply(spec.offset().getY()))
                .add(forward.multiply(spec.offset().getZ()))
                .add(eye.clone().subtract(startPosition));
    }

    private static boolean isValidTarget(Player player, Entity entity) {
        return entity != player
                && !entity.isDead()
                && entity.isValid()
                && !(entity instanceof Projectile)
                && !(entity instanceof Item)
                && !(entity instanceof ExperienceOrb)
                && !(entity instanceof EnderDragon);
    }

    public enum TargetKind {
        NONE,
        BLOCK,
        PLAYER,
        PASSIVE,
        HOSTILE,
        MOB,
        LIVING,
        OTHER_ENTITY
    }

    public record Trajectory(List<Vector> points, BoundingBox targetBox, TargetKind targetKind, boolean offhand) {
    }
}