package com.cosmicbreach.combat.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The volume a move hits during its active ticks, relative to the attacker. The origin is the
 * attacker's chest for most moves and its feet for landings; forward is the attacker's yaw, flattened.
 */
public sealed interface HitShape permits HitShape.Arc, HitShape.Line, HitShape.Sphere, HitShape.Compound {

    Codec<HitShape> CODEC = ShapeType.CODEC.dispatch("type", HitShape::shapeType, ShapeType::codec);

    ShapeType shapeType();

    /** How far from the origin this shape can reach; used to pick candidate entities. */
    double reach();

    boolean hits(Vec3 origin, float yawDeg, AABB target);

    /** Unit vector along the ground for a Minecraft yaw (0 = south, +Z). */
    static Vec3 forward(float yawDeg) {
        double r = Math.toRadians(yawDeg);
        return new Vec3(-Math.sin(r), 0.0, Math.cos(r));
    }

    private static boolean verticalOverlap(double originY, double height, AABB t) {
        double lo = originY - height / 2.0;
        double hi = originY + height / 2.0;
        return t.maxY >= lo && t.minY <= hi;
    }

    enum ShapeType implements StringRepresentable {
        ARC("arc"),
        LINE("line"),
        SPHERE("sphere"),
        COMPOUND("compound");

        public static final Codec<ShapeType> CODEC = StringRepresentable.fromEnum(ShapeType::values);

        private final String serializedName;

        ShapeType(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }

        MapCodec<? extends HitShape> codec() {
            return switch (this) {
                case ARC -> Arc.MAP_CODEC;
                case LINE -> Line.MAP_CODEC;
                case SPHERE -> Sphere.MAP_CODEC;
                case COMPOUND -> Compound.MAP_CODEC;
            };
        }
    }

    /** A horizontal sector: within {@code radius}, within {@code angle}/2 of forward, inside a band {@code height} tall. */
    record Arc(double radius, double angle, double height) implements HitShape {
        static final MapCodec<Arc> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.fieldOf("radius").forGetter(Arc::radius),
                Codec.DOUBLE.fieldOf("angle").forGetter(Arc::angle),
                Codec.DOUBLE.optionalFieldOf("height", 2.5).forGetter(Arc::height)
        ).apply(i, Arc::new));

        @Override
        public ShapeType shapeType() {
            return ShapeType.ARC;
        }

        @Override
        public double reach() {
            return radius;
        }

        @Override
        public boolean hits(Vec3 origin, float yawDeg, AABB t) {
            if (!verticalOverlap(origin.y, height, t)) {
                return false;
            }
            Vec3 f = forward(yawDeg);
            double cosHalf = Math.cos(Math.toRadians(Math.min(360.0, angle) / 2.0));
            double nearestX = Mth.clamp(origin.x, t.minX, t.maxX);
            double nearestZ = Mth.clamp(origin.z, t.minZ, t.maxZ);
            double midX = (t.minX + t.maxX) / 2.0;
            double midZ = (t.minZ + t.maxZ) / 2.0;
            double[][] samples = {
                    {nearestX, nearestZ}, {midX, midZ},
                    {t.minX, t.minZ}, {t.minX, t.maxZ}, {t.maxX, t.minZ}, {t.maxX, t.maxZ},
                    {midX, t.minZ}, {midX, t.maxZ}, {t.minX, midZ}, {t.maxX, midZ}
            };
            for (double[] p : samples) {
                double dx = p[0] - origin.x;
                double dz = p[1] - origin.z;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius) {
                    continue;
                }
                if (d < 0.35) {
                    return true; // overlapping the attacker
                }
                if ((dx * f.x + dz * f.z) / d >= cosHalf - 1e-9) {
                    return true;
                }
            }
            return false;
        }
    }

    /** A box along forward: {@code length} ahead of the origin, {@code width} across, {@code height} tall. */
    record Line(double length, double width, double height) implements HitShape {
        static final MapCodec<Line> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.fieldOf("length").forGetter(Line::length),
                Codec.DOUBLE.fieldOf("width").forGetter(Line::width),
                Codec.DOUBLE.optionalFieldOf("height", 2.5).forGetter(Line::height)
        ).apply(i, Line::new));

        @Override
        public ShapeType shapeType() {
            return ShapeType.LINE;
        }

        @Override
        public double reach() {
            return Math.sqrt(length * length + width * width / 4.0);
        }

        @Override
        public boolean hits(Vec3 origin, float yawDeg, AABB t) {
            if (!verticalOverlap(origin.y, height, t)) {
                return false;
            }
            Vec3 f = forward(yawDeg);
            double rx = -f.z;
            double rz = f.x;
            double hw = width / 2.0;
            double[][] rect = {
                    {origin.x + rx * hw, origin.z + rz * hw},
                    {origin.x - rx * hw, origin.z - rz * hw},
                    {origin.x + f.x * length + rx * hw, origin.z + f.z * length + rz * hw},
                    {origin.x + f.x * length - rx * hw, origin.z + f.z * length - rz * hw}
            };
            double[][] box = {{t.minX, t.minZ}, {t.minX, t.maxZ}, {t.maxX, t.minZ}, {t.maxX, t.maxZ}};
            double[][] axes = {{1, 0}, {0, 1}, {f.x, f.z}, {rx, rz}};
            for (double[] axis : axes) {
                if (separated(axis, rect, box)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean separated(double[] axis, double[][] a, double[][] b) {
            double minA = Double.POSITIVE_INFINITY, maxA = Double.NEGATIVE_INFINITY;
            double minB = Double.POSITIVE_INFINITY, maxB = Double.NEGATIVE_INFINITY;
            for (double[] p : a) {
                double d = p[0] * axis[0] + p[1] * axis[1];
                minA = Math.min(minA, d);
                maxA = Math.max(maxA, d);
            }
            for (double[] p : b) {
                double d = p[0] * axis[0] + p[1] * axis[1];
                minB = Math.min(minB, d);
                maxB = Math.max(maxB, d);
            }
            return maxA < minB - 1e-9 || maxB < minA - 1e-9;
        }
    }

    /** A sphere of {@code radius}, centred {@code offset} blocks ahead of the origin. */
    record Sphere(double radius, double offset) implements HitShape {
        static final MapCodec<Sphere> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.fieldOf("radius").forGetter(Sphere::radius),
                Codec.DOUBLE.optionalFieldOf("offset", 0.0).forGetter(Sphere::offset)
        ).apply(i, Sphere::new));

        @Override
        public ShapeType shapeType() {
            return ShapeType.SPHERE;
        }

        @Override
        public double reach() {
            return radius + Math.abs(offset);
        }

        @Override
        public boolean hits(Vec3 origin, float yawDeg, AABB t) {
            Vec3 c = origin.add(forward(yawDeg).scale(offset));
            double qx = Mth.clamp(c.x, t.minX, t.maxX);
            double qy = Mth.clamp(c.y, t.minY, t.maxY);
            double qz = Mth.clamp(c.z, t.minZ, t.maxZ);
            double dx = qx - c.x;
            double dy = qy - c.y;
            double dz = qz - c.z;
            return dx * dx + dy * dy + dz * dz <= radius * radius + 1e-9;
        }
    }

    /**
     * Several shapes at once: a target inside any of them is hit (once, however many it is in). The
     * Comet Maul's Overhead Smash is a narrow arc plus a ring where the head lands:
     * {@code {"type": "compound", "shapes": [{"type": "arc", ...}, {"type": "sphere", "radius": 1.5, "offset": 2.4}]}}.
     */
    record Compound(List<HitShape> shapes) implements HitShape {
        static final MapCodec<Compound> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.lazyInitialized(() -> HitShape.CODEC).listOf()
                        .validate(l -> l.isEmpty() ? DataResult.error(() -> "A compound shape needs at least one shape")
                                : DataResult.success(l))
                        .fieldOf("shapes").forGetter(Compound::shapes)
        ).apply(i, Compound::new));

        public Compound {
            shapes = List.copyOf(shapes);
        }

        @Override
        public ShapeType shapeType() {
            return ShapeType.COMPOUND;
        }

        @Override
        public double reach() {
            double reach = 0.0;
            for (HitShape shape : shapes) {
                reach = Math.max(reach, shape.reach());
            }
            return reach;
        }

        @Override
        public boolean hits(Vec3 origin, float yawDeg, AABB target) {
            for (HitShape shape : shapes) {
                if (shape.hits(origin, yawDeg, target)) {
                    return true;
                }
            }
            return false;
        }
    }
}
