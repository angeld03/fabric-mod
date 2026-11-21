package com.wildbound.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wildbound.network.AquaSkillFxPayload;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Volumetric water VFX for WILDBOUND's ten Aqua abilities.
 *
 * <p>Geometry is generated on the client as translucent, filled quad surfaces.
 * The server sends only a compact cast event; no particle cloud or mesh vertices
 * are streamed over the network. The renderer deliberately layers a deep-water
 * body, cyan transmission bands, foam crests and droplets to read as liquid
 * instead of debug wireframe.</p>
 */
public final class AquaSkillFxRenderer {
    private static final int MAX_EFFECTS = 24;
    private static final int MAX_QUADS_PER_EFFECT = 2_600;
    private static final double MAX_DISTANCE_SQR = 80.0 * 80.0;
    private static final Map<Long, Effect> EFFECTS = new LinkedHashMap<>();

    private AquaSkillFxRenderer() {
    }

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(AquaSkillFxRenderer::collect);
    }

    public static void accept(AquaSkillFxPayload payload) {
        if (payload == null) return;
        switch (payload.action()) {
            case START -> {
                EFFECTS.remove(payload.effectId());
                if (EFFECTS.size() >= MAX_EFFECTS) {
                    Iterator<Long> oldest = EFFECTS.keySet().iterator();
                    if (oldest.hasNext()) {
                        oldest.next();
                        oldest.remove();
                    }
                }
                EFFECTS.put(payload.effectId(), new Effect(payload));
            }
            case UPDATE -> {
                Effect effect = EFFECTS.get(payload.effectId());
                if (effect != null) effect.update(payload);
            }
            case STOP -> {
                Effect effect = EFFECTS.get(payload.effectId());
                if (effect != null) effect.stopping = true;
            }
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            EFFECTS.clear();
            return;
        }
        long now = mc.level.getGameTime();
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        EFFECTS.values().removeIf(effect ->
                now - effect.startTick > effect.duration + 8
                        || effect.position.distanceToSqr(camera) > MAX_DISTANCE_SQR * 1.8);
    }

    public static void reset() {
        EFFECTS.clear();
    }

    private static void collect(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context) {
        if (EFFECTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Vec3 camera = context.levelState().cameraRenderState.pos;
        long now = mc.level.getGameTime();
        for (Effect effect : EFFECTS.values()) {
            Vec3 anchor = renderAnchor(mc, effect);
            double distanceSqr = anchor.distanceToSqr(camera);
            if (distanceSqr > MAX_DISTANCE_SQR) continue;

            float age = (float) (now - effect.startTick) + 0.5f;
            float progress = clamp(age / effect.duration, 0f, 1f);
            float fadeIn = smooth(clamp(age / 4.5f, 0f, 1f));
            float fadeOut = effect.stopping
                    ? clamp(1f - Math.max(0f, age - effect.duration + 4f) / 4f, 0f, 1f)
                    : clamp((1f - progress) * 5.0f, 0.16f, 1f);
            float alpha = fadeIn * fadeOut;
            if (alpha <= 0.015f) continue;

            float lod = distanceSqr < 22 * 22 ? 1f : distanceSqr < 46 * 46 ? 0.72f : 0.46f;
            Vec3 relative = anchor.subtract(camera);
            PoseStack pose = context.poseStack();
            pose.pushPose();
            pose.translate(relative.x, relative.y + 0.68, relative.z);
            Vec3 direction = effect.direction;
            pose.rotate(new Quaternionf().rotationY((float) -Math.atan2(direction.x, direction.z)));
            pose.rotate(new Quaternionf().rotationX((float) Math.atan2(direction.y,
                    Math.sqrt(direction.x * direction.x + direction.z * direction.z))));

            context.submitNodeCollector().submitCustomGeometry(
                    pose,
                    RenderTypes.debugQuads(),
                    (matrix, buffer) -> draw(effect, age, progress, alpha, lod, matrix, buffer)
            );
            pose.popPose();
        }
    }

    private static Vec3 renderAnchor(Minecraft mc, Effect effect) {
        // Defensive client-side following for effects attached to the caster.
        // Projectiles and summoned creatures retain their cast origin and animate forward.
        if (effect.followsCaster) {
            Player caster = mc.level.getPlayerByUUID(effect.casterId);
            if (caster != null && caster.isAlive()) return caster.position();
        }
        return effect.position;
    }

    private static void draw(Effect effect, float age, float progress, float alpha, float lod,
                             PoseStack.Pose pose, VertexConsumer buffer) {
        Mesh mesh = new Mesh(pose, buffer, alpha, Math.max(260, (int) (MAX_QUADS_PER_EFFECT * lod)),
                age * 0.105f, effect.seed);

        switch (effect.skillId) {
            case "aqua_water_bolt" -> waterBolt(mesh, age, progress);
            case "aqua_healing_stream" -> healingStream(mesh, age, progress);
            case "aqua_aqua_whip" -> aquaWhip(mesh, age, progress);
            case "aqua_bubble_shield" -> bubbleShield(mesh, age, progress);
            case "aqua_tidal_wave" -> tidalWave(mesh, age, progress);
            case "aqua_aqua_dash" -> aquaDash(mesh, age, progress);
            case "aqua_whirlpool" -> whirlpool(mesh, age, progress);
            case "aqua_pressure_lance" -> pressureLance(mesh, age, progress);
            case "aqua_tsunami" -> tsunami(mesh, age, progress);
            case "aqua_leviathan_s_wrath" -> leviathansWrath(mesh, age, progress);
            default -> { }
        }
        mesh.finish();
    }

    // ---------------------------------------------------------------------
    // 1 — Water Bolt: a tapered liquid torpedo, wrapped by two flowing skins.
    // ---------------------------------------------------------------------
    private static void waterBolt(Mesh m, float age, float p) {
        float t = age * 0.12f;
        int volley = Math.min(2, (int) (p * 3.0f));
        float local = clamp((p * 3.0f) - volley, 0f, 1f);
        float travel = smooth(local);
        float z = 2.5f + travel * 15.5f;
        for (int i = 0; i < 3; i++) {
            float start = 0.08f + i * 0.31f;
            float q = clamp((p - start) / 0.25f, 0f, 1f);
            if (q <= 0.001f || q >= 1.0f && i > volley) continue;
            float headZ = 2.2f + smooth(q) * 15.5f;
            float side = (i - 1) * 0.42f;
            m.tube(new Vec3(side, 0.0, 0.0), new Vec3(side, 0.0, headZ),
                    0.29f, 22, 12, t + i * 1.7f, 0x087FCB, 116);
            m.tube(new Vec3(side, 0.0, 0.25), new Vec3(side, 0.0, headZ + 0.3f),
                    0.17f, 20, 10, -t * 1.45f + i, 0x37DFFF, 165);
            m.ellipsoid(side, 0.0f, headZ, 0.36f, 0.36f, 0.62f, 13, 8, t, 0x64EDFF, 190);
            m.splashDroplets(side, 0.0f, headZ, 0.55f, 5, i * 0.8f + t, 0xB9FAFF);
        }
        m.rippleRing(0, 0, z, 0.62f, 0.10f, 30, t, 0x8DF8FF, 125);
        m.ribbon(-0.05f, -0.05f, 0, -0.65f, 0.1f, z, 0.17f, 28, t, 0x36CFFF, 105);
        m.ribbon(0.05f, 0.08f, 0, 0.65f, -0.05f, z, 0.15f, 28, -t, 0xB8FFFF, 112);
    }

    // ---------------------------------------------------------------------
    // 2 — Healing Stream: a living column of water with upward spiral sheets.
    // ---------------------------------------------------------------------
    private static void healingStream(Mesh m, float age, float p) {
        float t = age * 0.075f;
        float pulse = 1f + 0.08f * (float) Math.sin(t * 2.2f);
        m.ellipsoid(0, 0.78f, 0, 1.12f * pulse, 1.48f, 1.12f * pulse,
                24, 13, t, 0x087FCB, 48);
        m.ellipsoid(0, 0.82f, 0, 0.86f, 1.27f, 0.86f,
                22, 12, -t * 1.2f, 0x2ECFFF, 74);
        for (int strand = 0; strand < 3; strand++) {
            m.spiralRibbon(0, -0.45f, 0, 0.78f + strand * 0.08f,
                    2.65f, 42, 0.19f, t + strand * 2.1f, 0x69EFFF, 150);
        }
        for (int ring = 0; ring < 4; ring++) {
            float y = -0.35f + ring * 0.73f;
            float radius = 0.9f + (float) Math.sin(t + ring) * 0.11f;
            m.rippleRing(0, y, 0, radius, 0.12f, 36, t + ring, 0xB8FFFF, 100);
        }
        m.floatingDrops(0, 0.35f, 0, 1.0f, 1.55f, 12, t, 0xB6F8FF);
        m.crownSplash(0, 0.1f, 0, 1.05f, t, 0xA9FAFF);
    }

    // ---------------------------------------------------------------------
    // 3 — Aqua Whip: a broad liquid lash, curled tip and torn spray.
    // ---------------------------------------------------------------------
    private static void aquaWhip(Mesh m, float age, float p) {
        float t = age * 0.13f;
        float reach = 2.0f + smooth(p) * 3.1f;
        m.curvedRibbon(0, 0.18f, 0, reach, 0.0f, 0.32f, 0.52f, 52, t,
                0x087FCB, 142);
        m.curvedRibbon(0, 0.22f, 0.02f, reach * 0.98f, 0.0f, 0.28f, 0.29f, 48,
                -t * 1.4f, 0x3FDFFF, 188);
        m.curvedRibbon(0, 0.25f, 0.04f, reach * 0.98f, 0.0f, 0.26f, 0.095f, 44,
                t * 1.8f, 0xD8FFFF, 220);
        m.tube(new Vec3(0, 0.15, 0), new Vec3(0, 0.3, reach),
                0.12f, 30, 8, t * 2.1f, 0x55E8FF, 176);
        m.crownSplash(0, 0.24f, reach, 0.5f, t * 1.3f, 0xBDFBFF);
        m.splashDroplets(0, 0.35f, reach, 0.72f, 13, t, 0xDBFFFF);
    }

    // ---------------------------------------------------------------------
    // 4 — Bubble Shield: two rippling translucent skins, caustic bands, foam.
    // ---------------------------------------------------------------------
    private static void bubbleShield(Mesh m, float age, float p) {
        float t = age * 0.075f;
        float pulse = 1f + 0.06f * (float) Math.sin(t * 1.8f);
        m.ellipsoid(0, 0.72f, 0, 1.45f * pulse, 1.78f * pulse, 1.45f * pulse,
                26, 16, t, 0x087BC5, 45);
        m.ellipsoid(0, 0.72f, 0, 1.29f, 1.63f, 1.29f,
                24, 14, -t * 1.3f, 0x44DAFF, 52);
        m.ellipsoid(0, 0.72f, 0, 1.08f, 1.43f, 1.08f,
                20, 12, t * 0.7f, 0xA9F8FF, 25);
        for (int i = 0; i < 5; i++) {
            float latitude = -0.95f + i * 0.47f;
            m.sphericalBand(0, 0.72f, 0, 1.48f, latitude, 1.43f,
                    48, t + i * 1.2f, 0xB9FFFF, 115);
        }
        m.floatingDrops(0, 0.75f, 0, 1.55f, 1.68f, 10, t, 0xC8FFFF);
    }

    // ---------------------------------------------------------------------
    // 5 — Tidal Wave: broad filled water wall with a curled, foaming crest.
    // ---------------------------------------------------------------------
    private static void tidalWave(Mesh m, float age, float p) {
        float t = age * 0.095f;
        float travel = smooth(p) * 5.0f;
        m.waveWall(0, -0.1f, 2.2f + travel, 6.1f, 4.25f, 0.8f, 34, 19, t,
                0x0876C3, 136);
        m.waveWall(0, 0.0f, 1.95f + travel, 5.8f, 3.9f, 0.42f, 30, 17, -t * 1.35f,
                0x26BCEB, 110);
        m.waveCrest(0, 3.9f, 2.35f + travel, 6.05f, 1.15f, t, 0xD4FFFF);
        m.foamArc(0, 3.65f, 2.15f + travel, 5.95f, 0.52f, 44, t, 0xE9FFFF, 205);
        m.splashDroplets(0, 3.9f, 2.25f + travel, 1.9f, 22, t, 0xC9FFFF);
        m.waterBase(0, -0.25f, 2.3f + travel, 6.0f, 1.1f, t, 0x1AAFE5, 88);
    }

    // ---------------------------------------------------------------------
    // 6 — Aqua Dash: flowing liquid mantle that wraps around the moving caster.
    // ---------------------------------------------------------------------
    private static void aquaDash(Mesh m, float age, float p) {
        float t = age * 0.15f;
        float pulse = 1f + 0.12f * (float) Math.sin(t * 2.1f);
        m.tube(new Vec3(0, 0.05, -2.65), new Vec3(0, 0.05, 2.65),
                0.86f * pulse, 32, 16, t, 0x087CCB, 104);
        m.tube(new Vec3(0, 0.05, -2.45), new Vec3(0, 0.05, 2.75),
                0.62f, 30, 14, -t * 1.6f, 0x37D9FF, 108);
        for (int i = 0; i < 4; i++) {
            m.spiralRibbon(0, -0.3f, -2.4f, 0.68f + i * 0.06f,
                    5.0f, 42, 0.16f, t * (i % 2 == 0 ? 1.6f : -1.35f) + i, 0xA2F8FF, 148);
        }
        m.ribbon(-0.1f, -0.1f, -2.5f, -1.1f, 0.15f, -5.2f, 0.36f, 30, t,
                0x168DDB, 104);
        m.ribbon(0.12f, 0.0f, -2.4f, 1.0f, 0.25f, -5.0f, 0.29f, 30, -t,
                0x8CF5FF, 128);
        m.splashDroplets(0, 0.2f, -4.4f, 1.15f, 15, t, 0xD7FFFF);
    }

    // ---------------------------------------------------------------------
    // 7 — Whirlpool: a deep liquid basin spiralling into a central funnel.
    // ---------------------------------------------------------------------
    private static void whirlpool(Mesh m, float age, float p) {
        float t = age * 0.11f;
        float radius = 2.5f + smooth(p) * 1.65f;
        m.whirlpoolSurface(0, -0.26f, 0, radius, 20, 38, t, 0x0875BE, 142);
        m.whirlpoolSurface(0, -0.18f, 0, radius * 0.82f, 15, 34, -t * 1.45f, 0x2BC7F0, 74);
        m.funnelVolume(0, -0.72f, 0, 1.3f, 2.2f, 18, 14, t, 0x086FB9, 116);
        for (int i = 0; i < 3; i++) {
            m.spiralRibbon(0, -0.15f, 0, radius * (0.62f + i * 0.07f),
                    2.1f, 32, 0.24f, t + i * 1.8f, 0x74EDFF, 130);
        }
        m.rippleRing(0, -0.05f, 0, radius * 0.94f, 0.21f, 44, t, 0xB9FFFF, 150);
        m.splashDroplets(0, 0.3f, 0, radius * 0.8f, 10, t, 0xD1FFFF);
    }

    // ---------------------------------------------------------------------
    // 8 — Pressure Lance: a compressed, tapered jet with a luminous inner core.
    // ---------------------------------------------------------------------
    private static void pressureLance(Mesh m, float age, float p) {
        float t = age * 0.12f;
        float extension = 3.0f + smooth(p) * 12.5f;
        m.taperedJet(0, 0.12f, 0, 0, 0.12f, extension,
                0.78f, 0.09f, 30, 16, t, 0x0879C7, 132);
        m.taperedJet(0, 0.12f, 0.12f, 0, 0.12f, extension + 0.15f,
                0.39f, 0.035f, 28, 13, -t * 1.7f, 0x32D7FF, 145);
        m.taperedJet(0, 0.12f, 0.2f, 0, 0.12f, extension + 0.2f,
                0.16f, 0.012f, 24, 10, t * 2.1f, 0xD3FFFF, 210);
        m.rippleRing(0, 0.12f, extension, 0.43f, 0.12f, 34, t, 0xC7FFFF, 180);
        m.splashDroplets(0, 0.12f, extension, 0.66f, 9, t, 0xC8FFFF);
    }

    // ---------------------------------------------------------------------
    // 9 — Tsunami: colossal advancing curtain, curling lip and impact wash.
    // ---------------------------------------------------------------------
    private static void tsunami(Mesh m, float age, float p) {
        float t = age * 0.085f;
        float expansion = smooth(p);
        float z = 3.3f + expansion * 8.5f;
        float scale = 0.88f + expansion * 0.54f;
        m.waveWall(0, -0.4f, z, 10.5f * scale, 7.0f * scale,
                1.65f, 36, 20, t, 0x075DAA, 158);
        m.waveWall(0, -0.2f, z - 0.72f, 10.0f * scale, 6.65f * scale,
                0.78f, 34, 18, -t * 1.3f, 0x149FD8, 122);
        m.waveCrest(0, 6.0f * scale, z + 0.25f, 10.1f * scale, 2.2f, t, 0xC7FFFF);
        m.foamArc(0, 5.8f * scale, z + 0.3f, 10.0f * scale, 0.85f,
                64, t, 0xF0FFFF, 225);
        m.waterBase(0, -0.55f, z, 10.6f * scale, 3.5f, t, 0x0977C5, 128);
        m.splashDroplets(0, 6.0f * scale, z, 3.0f * scale, 12, t, 0xD9FFFF);
        m.splashDroplets(-7.3f, 1.2f, z + 0.3f, 1.3f, 5, -t, 0xA9F7FF);
        m.splashDroplets(7.3f, 1.2f, z + 0.3f, 1.3f, 5, t, 0xA9F7FF);
    }

    // ---------------------------------------------------------------------
    // 10 — Leviathan's Wrath: a recognizable sea-serpent body made from skins.
    // ---------------------------------------------------------------------
    private static void leviathansWrath(Mesh m, float age, float p) {
        float t = age * 0.10f;
        float travel = smooth(p) * 7.2f;
        float sway = (float) Math.sin(t * 1.65f) * 0.42f;
        // Muscular neck and segmented serpentine trunk, overlapping into one silhouette.
        m.tube(new Vec3(0, 0.92, -4.5), new Vec3(sway * 0.4, 1.1, 1.35 + travel),
                0.63f, 28, 16, t, 0x075EA8, 144);
        for (int i = 0; i < 8; i++) {
            float q = i / 7f;
            float z = -3.7f + q * 5.0f + travel * 0.55f;
            float x = (float) Math.sin(t * 1.55f + q * 3.4f) * (0.42f + q * 0.24f);
            float y = 0.83f + (float) Math.sin(t * 1.2f + q * 2.4f) * 0.15f;
            float radius = 0.53f - q * 0.2f;
            m.ellipsoid(x, y, z, radius * 0.98f, radius * 0.83f, 0.86f,
                    15, 9, t + q, 0x0B80C5, 152);
            m.backFin(x, y + radius * 0.62f, z, radius * 1.25f, t + i, 0x38DFFF, 150);
        }
        // Head dome, lower jaw, snout and swept crown fins.
        m.ellipsoid(sway, 1.45f, 2.05f + travel, 1.02f, 0.82f, 1.2f,
                22, 13, t, 0x087CC4, 180);
        m.ellipsoid(sway, 0.91f, 2.74f + travel, 0.72f, 0.29f, 0.72f,
                18, 10, -t, 0x22B8E5, 185);
        m.ellipsoid(sway, 0.62f, 2.44f + travel, 0.78f, 0.20f, 0.72f,
                18, 9, t, 0x07528F, 170);
        m.fin(sway - 0.55f, 1.95f, 2.0f + travel, -0.9f, 1.55f, 0.1f, 0x0A69AF, 172);
        m.fin(sway + 0.55f, 1.95f, 2.0f + travel, 0.9f, 1.55f, 0.1f, 0x0A69AF, 172);
        m.fin(sway, 2.05f, 2.55f + travel, 0, 1.0f, -1.15f, 0x48DDF5, 185);
        // Bright eyes and a foaming jawline make the head read as a creature, not a blob.
        m.ellipsoid(sway - 0.73f, 1.66f, 2.76f + travel, 0.12f, 0.12f, 0.12f,
                10, 7, t, 0xC8FFFF, 240);
        m.ellipsoid(sway + 0.73f, 1.66f, 2.76f + travel, 0.12f, 0.12f, 0.12f,
                10, 7, -t, 0xC8FFFF, 240);
        m.rippleRing(sway, 0.74f, 3.08f + travel, 0.58f, 0.09f, 24, t, 0xD9FFFF, 220);
        m.splashDroplets(sway, 1.9f, 2.3f + travel, 1.45f, 15, t, 0xD6FFFF);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float smooth(float value) {
        float t = clamp(value, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static final class Effect {
        private final String skillId;
                private final java.util.UUID casterId;
        private final long startTick;
        private final int duration;
        private final long seed;
        private Vec3 position;
        private Vec3 direction;
        private final boolean followsCaster;
        private boolean stopping;

        private Effect(AquaSkillFxPayload payload) {
            skillId = payload.skillId();
            casterId = payload.casterId();
            startTick = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
            duration = payload.durationTicks();
            seed = payload.seed();
            position = new Vec3(payload.x(), payload.y(), payload.z());
            direction = new Vec3(payload.directionX(), payload.directionY(), payload.directionZ());
            followsCaster = skillId.equals("aqua_healing_stream")
                    || skillId.equals("aqua_bubble_shield")
                    || skillId.equals("aqua_aqua_dash")
                    || skillId.equals("aqua_whirlpool");
        }

        private void update(AquaSkillFxPayload payload) {
            position = new Vec3(payload.x(), payload.y(), payload.z());
            direction = new Vec3(payload.directionX(), payload.directionY(), payload.directionZ());
        }
    }

    /**
     * Small surface tessellator. Each patch is a real filled quad with layered
     * vertex alpha; no line primitives are used for the water body.
     */
    private static final class Mesh {
        private final PoseStack.Pose pose;
        private final VertexConsumer out;
        private final float effectAlpha;
        private final int maxQuads;
        private final float phase;
        private final long seed;
        private int quads;

        private Mesh(PoseStack.Pose pose, VertexConsumer out, float effectAlpha,
                     int maxQuads, float phase, long seed) {
            this.pose = pose;
            this.out = out;
            this.effectAlpha = effectAlpha;
            this.maxQuads = maxQuads;
            this.phase = phase;
            this.seed = seed;
        }

        private void vertex(Vec3 point, int rgb, int layerAlpha) {
            int a = Math.max(0, Math.min(255, Math.round(layerAlpha * effectAlpha)));
            int r = (rgb >> 16) & 255;
            int g = (rgb >> 8) & 255;
            int b = rgb & 255;
            out.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                    .setColor(r, g, b, a);
        }

        private void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, int layerAlpha) {
            if (quads >= maxQuads) return;
            vertex(a, rgb, layerAlpha);
            vertex(b, rgb, layerAlpha);
            vertex(c, rgb, layerAlpha);
            vertex(d, rgb, layerAlpha);
            quads++;
        }

        private void triangle(Vec3 a, Vec3 b, Vec3 c, int rgb, int layerAlpha) {
            quad(a, b, c, c, rgb, layerAlpha);
        }

        private void tube(Vec3 start, Vec3 end, float radius, int segments, int sides,
                          float twist, int rgb, int alpha) {
            Vec3 axis = end.subtract(start);
            double length = axis.length();
            if (length < 1.0E-4) return;
            Vec3 forward = axis.scale(1.0 / length);
            Vec3 reference = Math.abs(forward.y) > 0.92 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 u = forward.cross(reference).normalize();
            Vec3 v = forward.cross(u).normalize();
            for (int i = 0; i < segments && quads < maxQuads; i++) {
                double t0 = i / (double) segments;
                double t1 = (i + 1) / (double) segments;
                double taper0 = Math.sin(Math.PI * (0.08 + 0.92 * t0));
                double taper1 = Math.sin(Math.PI * (0.08 + 0.92 * t1));
                taper0 = Math.max(0.16, taper0);
                taper1 = Math.max(0.12, taper1);
                Vec3 c0 = start.add(axis.scale(t0));
                Vec3 c1 = start.add(axis.scale(t1));
                for (int j = 0; j < sides && quads < maxQuads; j++) {
                    double a0 = j * Math.PI * 2.0 / sides + twist + t0 * 2.1;
                    double a1 = (j + 1) * Math.PI * 2.0 / sides + twist + t0 * 2.1;
                    double b0 = j * Math.PI * 2.0 / sides + twist + t1 * 2.1;
                    double b1 = (j + 1) * Math.PI * 2.0 / sides + twist + t1 * 2.1;
                    Vec3 p0 = around(c0, u, v, a0, radius * taper0);
                    Vec3 p1 = around(c0, u, v, a1, radius * taper0);
                    Vec3 p2 = around(c1, u, v, b1, radius * taper1);
                    Vec3 p3 = around(c1, u, v, b0, radius * taper1);
                    int shade = j % 5 == 0 ? 0x83F4FF : rgb;
                    int opacity = j % 5 == 0 ? Math.min(245, alpha + 48) : alpha;
                    quad(p0, p1, p2, p3, shade, opacity);
                }
            }
        }

        private Vec3 around(Vec3 center, Vec3 u, Vec3 v, double angle, double radius) {
            return center.add(u.scale(Math.cos(angle) * radius)).add(v.scale(Math.sin(angle) * radius));
        }

        private void ellipsoid(float cx, float cy, float cz, float rx, float ry, float rz,
                               int lon, int lat, float twist, int rgb, int alpha) {
            for (int y = 0; y < lat && quads < maxQuads; y++) {
                double v0 = -Math.PI * 0.5 + y * Math.PI / lat;
                double v1 = -Math.PI * 0.5 + (y + 1) * Math.PI / lat;
                for (int x = 0; x < lon && quads < maxQuads; x++) {
                    double u0 = x * Math.PI * 2.0 / lon + twist * 0.18;
                    double u1 = (x + 1) * Math.PI * 2.0 / lon + twist * 0.18;
                    Vec3 a = ellipsoidPoint(cx, cy, cz, rx, ry, rz, u0, v0);
                    Vec3 b = ellipsoidPoint(cx, cy, cz, rx, ry, rz, u1, v0);
                    Vec3 c = ellipsoidPoint(cx, cy, cz, rx, ry, rz, u1, v1);
                    Vec3 d = ellipsoidPoint(cx, cy, cz, rx, ry, rz, u0, v1);
                    quad(a, b, c, d, rgb, alpha);
                }
            }
        }

        private Vec3 ellipsoidPoint(float cx, float cy, float cz, float rx, float ry, float rz,
                                    double lon, double lat) {
            double pulse = 1.0 + Math.sin(lon * 3.0 + phase + lat * 4.0) * 0.018;
            double cp = Math.cos(lat);
            return new Vec3(cx + Math.cos(lon) * cp * rx * pulse,
                    cy + Math.sin(lat) * ry * pulse,
                    cz + Math.sin(lon) * cp * rz * pulse);
        }

        private void ribbon(float sx, float sy, float sz, float ex, float ey, float ez,
                            float width, int segments, float twist, int rgb, int alpha) {
            Vec3 start = new Vec3(sx, sy, sz);
            Vec3 end = new Vec3(ex, ey, ez);
            Vec3 axis = end.subtract(start).normalize();
            Vec3 side = axis.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 1.0E-5) side = new Vec3(1, 0, 0);
            else side = side.normalize();
            for (int i = 0; i < segments && quads < maxQuads; i++) {
                double t0 = i / (double) segments;
                double t1 = (i + 1) / (double) segments;
                double w0 = width * (0.35 + 0.65 * Math.sin(Math.PI * t0));
                double w1 = width * (0.35 + 0.65 * Math.sin(Math.PI * t1));
                Vec3 c0 = start.add(axis.scale(t0 * start.distanceTo(end)))
                        .add(0, Math.sin(twist + t0 * 12) * width * 0.28, 0);
                Vec3 c1 = start.add(axis.scale(t1 * start.distanceTo(end)))
                        .add(0, Math.sin(twist + t1 * 12) * width * 0.28, 0);
                quad(c0.add(side.scale(-w0)), c1.add(side.scale(-w1)),
                        c1.add(side.scale(w1)), c0.add(side.scale(w0)), rgb, alpha);
            }
        }

        private void curvedRibbon(float sx, float sy, float sz, float ex, float ey, float ez,
                                  float width, int segments, float twist, int rgb, int alpha) {
            for (int i = 0; i < segments && quads < maxQuads; i++) {
                double t0 = i / (double) segments, t1 = (i + 1) / (double) segments;
                Vec3 a = whipPoint(sx, sy, sz, ex, ey, ez, t0, twist);
                Vec3 b = whipPoint(sx, sy, sz, ex, ey, ez, t1, twist);
                Vec3 side = new Vec3(1, 0, 0);
                double taper0 = Math.pow(1.0 - t0, 0.68) * width;
                double taper1 = Math.pow(1.0 - t1, 0.68) * width;
                quad(a.add(side.scale(-taper0)), b.add(side.scale(-taper1)),
                        b.add(side.scale(taper1)), a.add(side.scale(taper0)), rgb, alpha);
            }
        }

        private Vec3 whipPoint(float sx, float sy, float sz, float ex, float ey, float ez,
                               double t, float twist) {
            float z = (float) (sz + (ez - sz) * t);
            float x = (float) (sx + (ex - sx) * t + Math.sin(t * Math.PI) * 0.65);
            float y = (float) (sy + (ey - sy) * t + Math.sin(t * Math.PI) * 0.92
                    + Math.sin(t * Math.PI * 3.0 + twist) * 0.12);
            return new Vec3(x, y, z);
        }

        private void spiralRibbon(float cx, float baseY, float cz, float radius, float height,
                                  int segments, float width, float twist, int rgb, int alpha) {
            for (int i = 0; i < segments && quads < maxQuads; i++) {
                double t0 = i / (double) segments, t1 = (i + 1) / (double) segments;
                Vec3 a = spiralPoint(cx, baseY, cz, radius, height, t0, twist);
                Vec3 b = spiralPoint(cx, baseY, cz, radius, height, t1, twist);
                Vec3 tangent = b.subtract(a).normalize();
                Vec3 side = tangent.cross(new Vec3(0, 0, 1));
                if (side.lengthSqr() < 1.0E-5) side = new Vec3(1, 0, 0);
                else side = side.normalize();
                float w0 = width * (float) (0.55 + 0.45 * Math.sin(Math.PI * t0));
                float w1 = width * (float) (0.55 + 0.45 * Math.sin(Math.PI * t1));
                quad(a.add(side.scale(-w0)), b.add(side.scale(-w1)),
                        b.add(side.scale(w1)), a.add(side.scale(w0)), rgb, alpha);
            }
        }

        private Vec3 spiralPoint(float cx, float baseY, float cz, float radius,
                                 float height, double t, float twist) {
            double angle = t * Math.PI * 5.0 + twist;
            double r = radius * (0.84 + Math.sin(t * Math.PI) * 0.12);
            return new Vec3(cx + Math.cos(angle) * r, baseY + t * height, cz + Math.sin(angle) * r);
        }

        private void waveWall(float cx, float baseY, float cz, float width, float height, float depth,
                              int across, int vertical, float twist, int rgb, int alpha) {
            for (int i = 0; i < across && quads < maxQuads; i++) {
                double u0 = -1.0 + 2.0 * i / across;
                double u1 = -1.0 + 2.0 * (i + 1) / across;
                for (int j = 0; j < vertical && quads < maxQuads; j++) {
                    double v0 = j / (double) vertical;
                    double v1 = (j + 1) / (double) vertical;
                    Vec3 a = wavePoint(cx, baseY, cz, width, height, depth, u0, v0, twist);
                    Vec3 b = wavePoint(cx, baseY, cz, width, height, depth, u1, v0, twist);
                    Vec3 c = wavePoint(cx, baseY, cz, width, height, depth, u1, v1, twist);
                    Vec3 d = wavePoint(cx, baseY, cz, width, height, depth, u0, v1, twist);
                    int surfaceColor = j > vertical * 3 / 4 ? 0x2BBDEB : rgb;
                    int opacity = j > vertical * 3 / 4 ? Math.min(235, alpha + 22) : alpha;
                    quad(a, b, c, d, surfaceColor, opacity);
                }
            }
        }

        private Vec3 wavePoint(float cx, float baseY, float cz, float width, float height,
                               float depth, double u, double v, float twist) {
            double crest = Math.pow(Math.max(0.0, 1.0 - u * u), 0.58);
            double curl = Math.sin(v * Math.PI * 1.15 + twist + u * 2.4);
            double lip = Math.pow(v, 2.4) * (0.65 + 0.35 * Math.cos(u * Math.PI));
            double y = baseY + crest * height * v + Math.max(0.0, curl) * 0.16;
            double z = cz + depth * curl * v + lip * 0.82;
            return new Vec3(cx + u * width, y, z);
        }

        private void waveCrest(float cx, float y, float z, float width, float thickness,
                               float twist, int rgb) {
            int points = 42;
            for (int i = 0; i < points && quads < maxQuads; i++) {
                double t0 = i / (double) points, t1 = (i + 1) / (double) points;
                double u0 = t0 * 2 - 1, u1 = t1 * 2 - 1;
                double crest0 = Math.sqrt(Math.max(0, 1 - u0 * u0));
                double crest1 = Math.sqrt(Math.max(0, 1 - u1 * u1));
                Vec3 a = new Vec3(cx + u0 * width, y + crest0 * 0.18,
                        z + Math.sin(twist + t0 * 7) * 0.13);
                Vec3 b = new Vec3(cx + u1 * width, y + crest1 * 0.18,
                        z + Math.sin(twist + t1 * 7) * 0.13);
                quad(a.add(0, thickness, 0), b.add(0, thickness, 0),
                        b.add(0, -thickness * 0.5, 0.18), a.add(0, -thickness * 0.5, 0.18),
                        rgb, 210);
            }
        }

        private void foamArc(float cx, float y, float z, float width, float thickness,
                             int segments, float twist, int rgb, int alpha) {
            ribbon(cx - width, y, z, cx + width, y + 0.14f, z + 0.2f,
                    thickness, segments, twist, rgb, alpha);
        }

        private void whirlpoolSurface(float cx, float y, float cz, float radius,
                                      int rings, int sides, float twist, int rgb, int alpha) {
            for (int r = 0; r < rings && quads < maxQuads; r++) {
                double r0 = 0.12 + (radius - 0.12) * r / rings;
                double r1 = 0.12 + (radius - 0.12) * (r + 1) / rings;
                for (int i = 0; i < sides && quads < maxQuads; i++) {
                    double a0 = i * Math.PI * 2.0 / sides + twist;
                    double a1 = (i + 1) * Math.PI * 2.0 / sides + twist;
                    Vec3 a = basinPoint(cx, y, cz, r0, a0, radius, twist);
                    Vec3 b = basinPoint(cx, y, cz, r1, a0, radius, twist);
                    Vec3 c = basinPoint(cx, y, cz, r1, a1, radius, twist);
                    Vec3 d = basinPoint(cx, y, cz, r0, a1, radius, twist);
                    quad(a, b, c, d, r < rings / 5 ? 0x0870B6 : rgb,
                            r < rings / 5 ? Math.min(205, alpha + 30) : alpha);
                }
            }
        }

        private Vec3 basinPoint(float cx, float y, float cz, double r, double a,
                                float radius, float twist) {
            double radial = r / radius;
            double dip = 0.95 * (1.0 - radial) * (1.0 - radial);
            double wave = Math.sin(a * 5.0 + twist + radial * 8.0) * 0.075;
            double ripple = Math.cos(a * 9.0 - twist * 1.7) * 0.035;
            return new Vec3(cx + Math.cos(a) * r, y - dip + wave + ripple, cz + Math.sin(a) * r);
        }

        private void funnelVolume(float cx, float baseY, float cz, float topRadius,
                                  float height, int levels, int sides, float twist, int rgb, int alpha) {
            for (int i = 0; i < levels && quads < maxQuads; i++) {
                double t0 = i / (double) levels, t1 = (i + 1) / (double) levels;
                double r0 = topRadius * (1.0 - t0 * 0.92);
                double r1 = topRadius * (1.0 - t1 * 0.92);
                for (int j = 0; j < sides && quads < maxQuads; j++) {
                    double a0 = j * Math.PI * 2 / sides + twist + t0 * 2.4;
                    double a1 = (j + 1) * Math.PI * 2 / sides + twist + t0 * 2.4;
                    Vec3 a = new Vec3(cx + Math.cos(a0) * r0, baseY + t0 * height, cz + Math.sin(a0) * r0);
                    Vec3 b = new Vec3(cx + Math.cos(a1) * r0, baseY + t0 * height, cz + Math.sin(a1) * r0);
                    Vec3 c = new Vec3(cx + Math.cos(a1 + 0.3) * r1, baseY + t1 * height, cz + Math.sin(a1 + 0.3) * r1);
                    Vec3 d = new Vec3(cx + Math.cos(a0 + 0.3) * r1, baseY + t1 * height, cz + Math.sin(a0 + 0.3) * r1);
                    quad(a, b, c, d, rgb, alpha);
                }
            }
        }

        private void rippleRing(float cx, float cy, float cz, float radius, float thickness,
                                int segments, float twist, int rgb, int alpha) {
            for (int i = 0; i < segments && quads < maxQuads; i++) {
                double a0 = i * Math.PI * 2 / segments + twist;
                double a1 = (i + 1) * Math.PI * 2 / segments + twist;
                double wobble0 = 1 + Math.sin(a0 * 5 + twist) * 0.035;
                double wobble1 = 1 + Math.sin(a1 * 5 + twist) * 0.035;
                Vec3 a = new Vec3(cx + Math.cos(a0) * (radius - thickness) * wobble0,
                        cy, cz + Math.sin(a0) * (radius - thickness) * wobble0);
                Vec3 b = new Vec3(cx + Math.cos(a1) * (radius - thickness) * wobble1,
                        cy, cz + Math.sin(a1) * (radius - thickness) * wobble1);
                Vec3 c = new Vec3(cx + Math.cos(a1) * (radius + thickness) * wobble1,
                        cy + 0.035, cz + Math.sin(a1) * (radius + thickness) * wobble1);
                Vec3 d = new Vec3(cx + Math.cos(a0) * (radius + thickness) * wobble0,
                        cy + 0.035, cz + Math.sin(a0) * (radius + thickness) * wobble0);
                quad(a, b, c, d, rgb, alpha);
            }
        }

        private void sphericalBand(float cx, float cy, float cz, float radius,
                                   float latitude, float width, int segments, float twist,
                                   int rgb, int alpha) {
            double lat = latitude;
            double half = width / radius * 0.13;
            for (int i = 0; i < segments && quads < maxQuads; i++) {
                double a0 = i * Math.PI * 2 / segments + twist;
                double a1 = (i + 1) * Math.PI * 2 / segments + twist;
                Vec3 p0 = spherePoint(cx, cy, cz, radius, a0, lat - half);
                Vec3 p1 = spherePoint(cx, cy, cz, radius, a1, lat - half);
                Vec3 p2 = spherePoint(cx, cy, cz, radius, a1, lat + half);
                Vec3 p3 = spherePoint(cx, cy, cz, radius, a0, lat + half);
                quad(p0, p1, p2, p3, rgb, alpha);
            }
        }

        private Vec3 spherePoint(float cx, float cy, float cz, float r, double lon, double lat) {
            double cp = Math.cos(lat);
            return new Vec3(cx + Math.cos(lon) * cp * r, cy + Math.sin(lat) * r,
                    cz + Math.sin(lon) * cp * r);
        }

        private void crownSplash(float x, float y, float z, float radius, float twist, int rgb) {
            int lobes = 11;
            for (int i = 0; i < lobes; i++) {
                double a = i * Math.PI * 2 / lobes + twist;
                float r = radius * (0.78f + 0.16f * (float) Math.sin(i * 2.7 + twist));
                Vec3 base = new Vec3(x + Math.cos(a) * radius * 0.42, y, z + Math.sin(a) * radius * 0.42);
                Vec3 tip = new Vec3(x + Math.cos(a) * r, y + radius * (0.38 + 0.16 * Math.sin(a * 2 + twist)),
                        z + Math.sin(a) * r);
                Vec3 side = new Vec3(-Math.sin(a) * 0.13, 0, Math.cos(a) * 0.13);
                triangle(base.subtract(side), tip, base.add(side), rgb, 188);
                ellipsoid((float) tip.x, (float) tip.y, (float) tip.z, 0.075f, 0.11f, 0.075f,
                        7, 5, twist, 0xDFFFFF, 210);
            }
        }

        private void splashDroplets(float x, float y, float z, float radius,
                                    int count, float twist, int rgb) {
            for (int i = 0; i < count && quads < maxQuads; i++) {
                double a = i * 2.399963229728653 + twist;
                double lift = 0.28 + 0.72 * ((i * 7 % count) / (double) Math.max(1, count - 1));
                float dx = (float) Math.cos(a) * radius * (0.35f + 0.18f * (i % 3));
                float dz = (float) Math.sin(a) * radius * (0.35f + 0.18f * (i % 3));
                float dy = (float) (lift * radius * 0.58 + Math.sin(twist + i) * 0.12);
                float size = 0.045f + (i % 4) * 0.018f;
                ellipsoid(x + dx, y + dy, z + dz, size, size * (1.5f + lift), size,
                        7, 5, twist + i, rgb, 210);
            }
        }

        private void floatingDrops(float cx, float cy, float cz, float rx, float ry,
                                   int count, float twist, int rgb) {
            for (int i = 0; i < count && quads < maxQuads; i++) {
                double angle = i * 2.399963229728653 + twist;
                double band = (i % 4) / 3.0;
                float x = cx + (float) Math.cos(angle) * rx * (0.72f + (float) band * 0.2f);
                float z = cz + (float) Math.sin(angle) * rx * (0.72f + (float) band * 0.2f);
                float y = cy + (float) Math.sin(twist * 0.8f + i * 1.6) * 0.16f
                        + (float) band * ry * 0.45f;
                float size = 0.045f + (i % 3) * 0.025f;
                ellipsoid(x, y, z, size, size * 1.45f, size,
                        7, 5, twist + i, rgb, 190);
            }
        }

        private void waterBase(float cx, float cy, float cz, float width, float depth,
                               float twist, int rgb, int alpha) {
            int segments = 32;
            for (int i = 0; i < segments && quads < maxQuads; i++) {
                double x0 = -width + 2.0 * width * i / segments;
                double x1 = -width + 2.0 * width * (i + 1) / segments;
                double ripple0 = Math.sin(i * 0.8 + twist) * 0.16;
                double ripple1 = Math.sin((i + 1) * 0.8 + twist) * 0.16;
                quad(new Vec3(cx + x0, cy, cz - depth),
                        new Vec3(cx + x1, cy, cz - depth),
                        new Vec3(cx + x1, cy + ripple1, cz + depth),
                        new Vec3(cx + x0, cy + ripple0, cz + depth), rgb, alpha);
            }
        }

        private void taperedJet(float sx, float sy, float sz, float ex, float ey, float ez,
                                float baseRadius, float tipRadius, int segments, int sides,
                                float twist, int rgb, int alpha) {
            Vec3 start = new Vec3(sx, sy, sz);
            Vec3 end = new Vec3(ex, ey, ez);
            Vec3 axis = end.subtract(start);
            Vec3 forward = axis.normalize();
            Vec3 ref = Math.abs(forward.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 u = forward.cross(ref).normalize();
            Vec3 v = forward.cross(u).normalize();
            for (int i = 0; i < segments && quads < maxQuads; i++) {
                double t0 = i / (double) segments, t1 = (i + 1) / (double) segments;
                double r0 = baseRadius * (1 - t0) + tipRadius * t0;
                double r1 = baseRadius * (1 - t1) + tipRadius * t1;
                r0 *= 0.88 + 0.12 * Math.sin(t0 * Math.PI * 5 + twist);
                r1 *= 0.88 + 0.12 * Math.sin(t1 * Math.PI * 5 + twist);
                Vec3 c0 = start.add(axis.scale(t0));
                Vec3 c1 = start.add(axis.scale(t1));
                for (int j = 0; j < sides && quads < maxQuads; j++) {
                    double a0 = j * Math.PI * 2 / sides + twist + t0 * 2.2;
                    double a1 = (j + 1) * Math.PI * 2 / sides + twist + t0 * 2.2;
                    double b0 = j * Math.PI * 2 / sides + twist + t1 * 2.2;
                    double b1 = (j + 1) * Math.PI * 2 / sides + twist + t1 * 2.2;
                    quad(around(c0, u, v, a0, r0), around(c0, u, v, a1, r0),
                            around(c1, u, v, b1, r1), around(c1, u, v, b0, r1),
                            j % 4 == 0 ? 0x8CEEFF : rgb, j % 4 == 0 ? Math.min(245, alpha + 40) : alpha);
                }
            }
        }

        private void backFin(float x, float y, float z, float spread, float twist, int rgb, int alpha) {
            Vec3 base = new Vec3(x, y, z);
            Vec3 top = new Vec3(x + Math.sin(twist) * 0.12, y + spread, z - 0.46);
            Vec3 back = new Vec3(x, y + spread * 0.18, z - 1.05);
            Vec3 edge = new Vec3(x + spread * 0.72, y + spread * 0.34, z - 0.42);
            triangle(base, top, edge, rgb, alpha);
            triangle(base, edge, back, 0x32CFF0, alpha - 18);
            triangle(base, back, top, 0x76EFFF, alpha - 6);
        }

        private void fin(float x, float y, float z, float dx, float dy, float dz, int rgb, int alpha) {
            Vec3 root = new Vec3(x, y, z);
            Vec3 tip = new Vec3(x + dx, y + dy, z + dz);
            Vec3 outer = new Vec3(x + dx * 0.58, y + dy * 0.55, z + dz * 0.2 - 0.22);
            triangle(root, tip, outer, rgb, alpha);
        }

        private void finish() {
            // Filled QUADS are submitted as one translucent mesh per effect.
        }
    }
}
