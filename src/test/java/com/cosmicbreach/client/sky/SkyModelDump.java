package com.cosmicbreach.client.sky;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prints {@link SkyModel}'s frames as JSON for the offline preview ({@code tools/sky/preview.py}), so the
 * preview renders the game's shaders with exactly the game's numbers. Needs only this package and
 * {@code world.VesperClock} on the classpath (no Minecraft):
 *
 * <pre>java -cp "build/classes/java/main;build/classes/java/test" com.cosmicbreach.client.sky.SkyModelDump reach:6000 drift:12500 ...</pre>
 *
 * Each argument is {@code layer:dayTime[:gameTime]} with layer {@code reach}, {@code drift}, {@code deep}
 * or a height such as {@code y310} (the weights that height gives), render distance 12 chunks.
 * {@code --geometry <dir>} also writes the static meshes (stars, clusters, aurora) as little-endian
 * binaries: quad count (int), then per vertex x, y, z, u, v (floats) and ARGB (int).
 */
public final class SkyModelDump {
    private SkyModelDump() {
    }

    public static void main(String[] argv) {
        List<String> args = new ArrayList<>(List.of(argv));
        int g = args.indexOf("--geometry");
        if (g >= 0) {
            Path dir = Path.of(args.get(g + 1));
            args.remove(g + 1);
            args.remove(g);
            writeMesh(dir.resolve("stars.bin"), SkyStars.field());
            writeMesh(dir.resolve("clusters.bin"), SkyStars.clusters());
            writeMesh(dir.resolve("aurora.bin"), SkyAurora.ribbons());
        }
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < args.size(); i++) {
            String[] parts = args.get(i).split(":");
            double[] weights = weights(parts[0]);
            double dayTime = Double.parseDouble(parts[1]);
            long gameTime = parts.length > 2 ? Long.parseLong(parts[2]) : (long) dayTime;
            SkyFrame f = new SkyFrame();
            SkyModel.compute(f, dayTime, gameTime, 0f, weights, 192.0, gameTime / 20.0, new SkyModel.Weather());
            json.append(frame(args.get(i), f)).append(i + 1 < args.size() ? ",\n" : "\n");
        }
        System.out.println(json.append("]"));
    }

    private static double[] weights(String layer) {
        return switch (layer) {
            case "reach" -> new double[] {1, 0, 0};
            case "drift" -> new double[] {0, 1, 0};
            case "deep" -> new double[] {0, 0, 1};
            default -> SkyLayers.targetWeights(Double.parseDouble(layer.substring(1)));
        };
    }

    private static String frame(String label, SkyFrame f) {
        StringBuilder b = new StringBuilder("{");
        field(b, "label", "\"" + label + "\"");
        field(b, "layers", arr(f.layers));
        field(b, "times", arr(f.times));
        field(b, "daylight", num(f.daylight));
        field(b, "zenith", arr(f.zenith));
        field(b, "mid", arr(f.mid));
        field(b, "haze", arr(f.haze));
        field(b, "horizon", arr(f.horizon));
        field(b, "low", arr(f.low));
        field(b, "nadir", arr(f.nadir));
        field(b, "skyRot", arr(f.skyRot));
        field(b, "nebulaA", Integer.toString(f.nebulaA));
        field(b, "nebulaB", Integer.toString(f.nebulaB));
        field(b, "nebulaMix", num(f.nebulaMix));
        field(b, "nebula", arr(f.nebula));
        field(b, "stars", arr(f.stars));
        field(b, "sunDir", arr(f.sunDir));
        field(b, "sunRadius", num(f.sunRadius));
        field(b, "sunVisible", num(f.sunVisible));
        field(b, "eclipse", num(f.eclipse));
        field(b, "discStrength", num(f.discStrength));
        field(b, "coronaStrength", num(f.coronaStrength));
        field(b, "glow", arr(f.glow));
        field(b, "planetDir", arr(f.planetDir));
        field(b, "planetRadius", num(f.planetRadius));
        field(b, "ringAxis", arr(f.ringAxis));
        field(b, "planetLightDir", arr(f.planetLightDir));
        field(b, "planetLight", arr(f.planetLight));
        field(b, "planetParams", arr(f.planetParams));
        field(b, "planetAmbient", arr(f.planetAmbient));
        field(b, "bandDrift", num(f.bandDrift));
        field(b, "vesperDir", arr(f.vesperDir));
        field(b, "beamAngle", num(f.beamAngle));
        field(b, "beamU", arr(f.beamU));
        field(b, "beamV", arr(f.beamV));
        field(b, "vesperCore", num(f.vesperCore));
        field(b, "vesperBeam", num(f.vesperBeam));
        field(b, "vesperPulse", num(f.vesperPulse));
        field(b, "aurora", num(f.aurora));
        field(b, "fogStart", num(f.fogStart));
        field(b, "fogEnd", num(f.fogEnd));
        field(b, "fogVertical", num(f.fogVertical));
        field(b, "fogShape", Integer.toString(SkyModel.fogShapeIndex(f.fogVertical)));
        field(b, "sunQuad", arr(SkyQuads.facing(f.sunDir, SkyQuads.sunHalfAngle(f))));
        field(b, "sunDisc", num(SkyQuads.sunDiscFraction(f)));
        field(b, "planetQuad", arr(SkyQuads.facing(f.planetDir, SkyQuads.planetHalfAngle(f))));
        field(b, "planetR", num(SkyQuads.planetRadius(f)));
        field(b, "vesperQuad", arr(SkyQuads.facing(f.vesperDir, SkyQuads.VESPER_HALF_ANGLE)));
        field(b, "beam0", arr(SkyQuads.beam(f, f.beamAngle)));
        field(b, "beam1", arr(SkyQuads.beam(f, f.beamAngle + Math.PI)));
        b.append("\"seconds\": ").append(num(f.seconds)).append("}");
        return b.toString();
    }

    private static void writeMesh(Path file, SkyStars.Mesh mesh) {
        int vertices = mesh.quads() * 4;
        ByteBuffer buf = ByteBuffer.allocate(4 + vertices * 24).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(mesh.quads());
        for (int v = 0; v < vertices; v++) {
            for (int k = 0; k < 5; k++) {
                buf.putFloat(mesh.vertices()[v * 5 + k]);
            }
            buf.putInt(mesh.colors()[v]);
        }
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, buf.array());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void field(StringBuilder b, String name, String value) {
        b.append('"').append(name).append("\": ").append(value).append(", ");
    }

    private static String num(double v) {
        return String.format(Locale.ROOT, "%.6f", v);
    }

    private static String arr(float[] v) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            b.append(num(v[i])).append(i + 1 < v.length ? ", " : "");
        }
        return b.append("]").toString();
    }

    private static String arr(double[] v) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            b.append(num(v[i])).append(i + 1 < v.length ? ", " : "");
        }
        return b.append("]").toString();
    }
}
