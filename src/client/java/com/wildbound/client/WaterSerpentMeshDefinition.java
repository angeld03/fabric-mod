package com.wildbound.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Data-driven high-resolution water-serpent mesh profile.
 *
 * <p>The JSON defines a Catmull-Rom spine, cross-section thickness, head
 * landmarks, dorsal fins and tail flukes. Geometry and all animation remain
 * procedural, but artists can change the creature silhouette without editing
 * Java source.</p>
 */
public record WaterSerpentMeshDefinition(
        int longitudinalSegments,
        int radialSegments,
        float lateralSway,
        float verticalSway,
        float waveFrequency,
        List<SpinePoint> spine,
        Head head,
        List<Fin> dorsalFins,
        TailFluke tailFluke
) {
    public static final String RESOURCE = "/assets/wildbound/fx/leviathan_water_serpent.json";

    public WaterSerpentMeshDefinition {
        longitudinalSegments = clamp(longitudinalSegments, 48, 220);
        radialSegments = clamp(radialSegments, 12, 48);
        lateralSway = clamp(lateralSway, 0f, 1.5f);
        verticalSway = clamp(verticalSway, 0f, 0.8f);
        waveFrequency = clamp(waveFrequency, 0.1f, 3.0f);
        spine = spine == null || spine.size() < 4 ? DEFAULT_SPINE : List.copyOf(spine);
        head = head == null ? DEFAULT_HEAD : head;
        dorsalFins = dorsalFins == null ? List.of() : List.copyOf(dorsalFins);
        tailFluke = tailFluke == null ? DEFAULT_TAIL : tailFluke;
    }

    public static WaterSerpentMeshDefinition load() {
        try (InputStream stream = WaterSerpentMeshDefinition.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) return DEFAULT;
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject body = root.getAsJsonObject("body");
            JsonObject headObject = root.getAsJsonObject("head");
            JsonObject tailObject = root.getAsJsonObject("tail_fluke");

            List<SpinePoint> spine = new ArrayList<>();
            JsonArray spineArray = body.getAsJsonArray("spine");
            for (JsonElement element : spineArray) {
                JsonArray row = element.getAsJsonArray();
                if (row.size() < 5 || spine.size() >= 32) continue;
                spine.add(new SpinePoint(
                        row.get(0).getAsFloat(), row.get(1).getAsFloat(), row.get(2).getAsFloat(),
                        positive(row.get(3).getAsFloat(), 0.03f, 2.5f),
                        positive(row.get(4).getAsFloat(), 0.03f, 2.5f)));
            }

            List<Fin> fins = new ArrayList<>();
            JsonArray finArray = root.getAsJsonArray("dorsal_fins");
            if (finArray != null) {
                for (JsonElement element : finArray) {
                    JsonArray row = element.getAsJsonArray();
                    if (row.size() < 6 || fins.size() >= 16) continue;
                    fins.add(new Fin(row.get(0).getAsFloat(), row.get(1).getAsFloat(),
                            row.get(2).getAsFloat(), row.get(3).getAsFloat(),
                            row.get(4).getAsFloat(), row.get(5).getAsFloat()));
                }
            }

            Head head = new Head(
                    ellipsoid(headObject, "skull", DEFAULT_HEAD.skull),
                    ellipsoid(headObject, "brow", DEFAULT_HEAD.brow),
                    ellipsoid(headObject, "snout", DEFAULT_HEAD.snout),
                    ellipsoid(headObject, "jaw", DEFAULT_HEAD.jaw),
                    ellipsoid(headObject, "mouth", DEFAULT_HEAD.mouth),
                    number(headObject, "eye_offset_x", DEFAULT_HEAD.eyeOffsetX),
                    number(headObject, "eye_y", DEFAULT_HEAD.eyeY),
                    number(headObject, "eye_z", DEFAULT_HEAD.eyeZ),
                    positive(number(headObject, "eye_radius", DEFAULT_HEAD.eyeRadius), 0.025f, 0.5f),
                    positive(number(headObject, "jaw_open", DEFAULT_HEAD.jawOpen), 0.02f, 0.8f));

            JsonArray tailCenter = tailObject.getAsJsonArray("center");
            TailFluke tail = tailCenter != null && tailCenter.size() >= 3
                    ? new TailFluke(tailCenter.get(0).getAsFloat(), tailCenter.get(1).getAsFloat(),
                    tailCenter.get(2).getAsFloat(),
                    positive(number(tailObject, "spread", DEFAULT_TAIL.spread), 0.1f, 2.5f),
                    positive(number(tailObject, "height", DEFAULT_TAIL.height), 0.1f, 2.0f),
                    number(tailObject, "sweep_back", DEFAULT_TAIL.sweepBack))
                    : DEFAULT_TAIL;

            return new WaterSerpentMeshDefinition(
                    integer(body, "longitudinal_segments", 168),
                    integer(body, "radial_segments", 28),
                    number(body, "lateral_sway", 0.62f),
                    number(body, "vertical_sway", 0.22f),
                    number(body, "wave_frequency", 1.12f),
                    spine, head, fins, tail);
        } catch (Exception ignored) {
            return DEFAULT;
        }
    }

    private static Ellipsoid ellipsoid(JsonObject object, String key, Ellipsoid fallback) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) return fallback;
        JsonArray a = object.getAsJsonArray(key);
        if (a.size() < 6) return fallback;
        return new Ellipsoid(a.get(0).getAsFloat(), a.get(1).getAsFloat(),
                a.get(2).getAsFloat(), positive(a.get(3).getAsFloat(), 0.02f, 3.0f),
                positive(a.get(4).getAsFloat(), 0.02f, 3.0f),
                positive(a.get(5).getAsFloat(), 0.02f, 3.0f));
    }

    private static int integer(JsonObject object, String key, int fallback) {
        return object != null && object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    private static float number(JsonObject object, String key, float fallback) {
        if (object == null || !object.has(key)) return fallback;
        float value = object.get(key).getAsFloat();
        return Float.isFinite(value) ? value : fallback;
    }

    private static float positive(float value, float min, float max) {
        if (!Float.isFinite(value)) return min;
        return clamp(value, min, max);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        if (!Float.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static final List<SpinePoint> DEFAULT_SPINE = List.of(
            new SpinePoint(0, 0.36f, -8.45f, 0.035f, 0.05f),
            new SpinePoint(0, 0.42f, -6.65f, 0.19f, 0.18f),
            new SpinePoint(0, 0.59f, -3.95f, 0.44f, 0.35f),
            new SpinePoint(0, 0.91f, -1.20f, 0.58f, 0.48f),
            new SpinePoint(0, 1.08f, 0.0f, 0.61f, 0.51f),
            new SpinePoint(0, 1.28f, 1.95f, 0.70f, 0.60f)
    );

    private static final Head DEFAULT_HEAD = new Head(
            new Ellipsoid(0, 1.36f, 2.50f, 0.94f, 0.72f, 1.08f),
            new Ellipsoid(0, 1.65f, 2.86f, 0.82f, 0.25f, 0.54f),
            new Ellipsoid(0, 1.14f, 3.27f, 0.68f, 0.34f, 0.76f),
            new Ellipsoid(0, 0.77f, 3.04f, 0.65f, 0.21f, 0.73f),
            new Ellipsoid(0, 0.92f, 3.29f, 0.48f, 0.075f, 0.47f),
            0.645f, 1.57f, 3.03f, 0.115f, 0.24f
    );

    private static final TailFluke DEFAULT_TAIL =
            new TailFluke(0, 0.37f, -8.16f, 1.05f, 0.68f, -0.70f);

    private static final WaterSerpentMeshDefinition DEFAULT = defaults();

    private static WaterSerpentMeshDefinition defaults() {
        return new WaterSerpentMeshDefinition(168, 28, 0.62f, 0.22f, 1.12f,
                DEFAULT_SPINE, DEFAULT_HEAD, List.of(), DEFAULT_TAIL);
    }

    public record SpinePoint(float x, float y, float z, float width, float height) { }
    public record Ellipsoid(float x, float y, float z, float rx, float ry, float rz) { }
    public record Fin(float x, float y, float z, float dx, float dy, float dz) { }
    public record TailFluke(float x, float y, float z, float spread, float height, float sweepBack) { }
    public record Head(Ellipsoid skull, Ellipsoid brow, Ellipsoid snout, Ellipsoid jaw,
                       Ellipsoid mouth, float eyeOffsetX, float eyeY, float eyeZ,
                       float eyeRadius, float jawOpen) { }
}
