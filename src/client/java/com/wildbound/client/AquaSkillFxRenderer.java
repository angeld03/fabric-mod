package com.wildbound.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wildbound.network.AquaSkillFxPayload;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-only procedural wire-mesh renderer for the ten Aqua abilities.
 *
 * <p>Only compact cast events cross the network. Mesh vertices are generated
 * locally and submitted through Minecraft 26.x's deferred level-render path.
 * Active effects are distance culled, expire locally and share a hard budget.</p>
 */
public final class AquaSkillFxRenderer {
    private static final int MAX_EFFECTS = 24;
    private static final int MAX_LINES_PER_EFFECT = 420;
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
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        for (Effect effect : EFFECTS.values()) {
            double distanceSqr = effect.position.distanceToSqr(camera);
            if (distanceSqr > MAX_DISTANCE_SQR) continue;
            float age = (float) (now - effect.startTick) + partial;
            float progress = clamp(age / effect.duration, 0f, 1f);
            float fadeIn = clamp(age / 5f, 0f, 1f);
            float fadeOut = effect.stopping
                    ? clamp(1f - (age - effect.duration + 6f) / 6f, 0f, 1f)
                    : clamp((1f - progress) * 4.5f, 0.12f, 1f);
            float alpha = fadeIn * fadeOut;
            if (alpha <= 0.01f) continue;

            float lod = distanceSqr < 24 * 24 ? 1f : distanceSqr < 48 * 48 ? 0.68f : 0.38f;
            int lines = Math.max(36, Math.min(MAX_LINES_PER_EFFECT, (int) (MAX_LINES_PER_EFFECT * lod)));
            Vec3 relative = effect.position.subtract(camera);

            PoseStack pose = context.poseStack();
            pose.pushPose();
            pose.translate(relative.x, relative.y, relative.z);
            Vec3 direction = effect.direction;
            pose.translate(0.0, 0.65, 0.0);
            pose.mulPose(com.mojang.math.Axis.YP.rotation((float) -Math.atan2(direction.x, direction.z)));
            pose.mulPose(com.mojang.math.Axis.XP.rotation((float) Math.atan2(direction.y,
                    Math.sqrt(direction.x * direction.x + direction.z * direction.z))));

            RenderType type = RenderTypes.lines();
            context.submitNodeCollector().submitCustomGeometry(pose, type,
                    (matrix, buffer) -> draw(effect, age, alpha, lines, matrix, buffer));
            pose.popPose();
        }
    }

    private static void draw(Effect effect, float age, float alpha, int budget,
                             PoseStack.Pose pose, VertexConsumer buffer) {
        Mesh mesh = new Mesh(pose, buffer, alpha, budget);
        float t = age * 0.055f;
        switch (effect.skillId) {
            case "aqua_water_bolt" -> {
                mesh.tube(0, 0, 0, 0, 0, 3.4f, 0.42f, 3, 14, t);
                mesh.tube(0, 0, 0, 0, 0, 3.4f, 0.19f, 2, 12, -t * 1.4f);
                mesh.ring(0, 0, 3.2f, 0.7f + (float)Math.sin(t * 2) * 0.1f, 24);
            }
            case "aqua_healing_stream" -> {
                mesh.ellipsoid(0, 0.9f, 0, 1.05f, 1.65f, 1.05f, 16, 9, t);
                mesh.helix(0, -0.15f, 0, 0.92f, 2.2f, 3, 30, t);
                mesh.ring(0, -0.15f, 0, 1.25f, 28);
            }
            case "aqua_aqua_whip" -> {
                mesh.ribbon(0, 0.25f, 0, 0, 0.1f, 4.2f, 0.48f, 44, t);
                mesh.tube(0, 0.25f, 0, 0, 0.1f, 4.2f, 0.12f, 2, 10, t * 1.7f);
            }
            case "aqua_bubble_shield" -> {
                mesh.ellipsoid(0, 0.85f, 0, 1.25f, 1.65f, 1.25f, 20, 12, t);
                mesh.ellipsoid(0, 0.85f, 0, 1.12f, 1.52f, 1.12f, 16, 10, -t);
                mesh.helix(0, 0, 0, 1.18f, 1.8f, 4, 24, t);
            }
            case "aqua_tidal_wave" -> {
                mesh.wave(0, -0.2f, 2.0f, 5.5f, 3.2f, 28, 9, t);
                mesh.tube(-5.5f, 0, 2, 5.5f, 0, 2, 0.12f, 2, 18, t);
            }
            case "aqua_aqua_dash" -> {
                mesh.tube(0, 0.2f, -2.2f, 0, 0.2f, 2.8f, 0.78f, 4, 14, t);
                mesh.helix(0, -0.4f, -2.0f, 0.7f, 4.8f, 3, 34, t * 1.8f);
            }
            case "aqua_whirlpool" -> {
                mesh.funnel(0, -0.4f, 0, 3.0f, 3.0f, 7, 32, t);
                mesh.helix(0, -0.4f, 0, 2.8f, 3.3f, 4, 36, -t);
                mesh.ring(0, -0.35f, 0, 3.1f, 48);
            }
            case "aqua_pressure_lance" -> {
                mesh.tube(0, 0.1f, 0, 0, 0.1f, 5.8f, 0.36f, 4, 16, t);
                mesh.tube(0, 0.1f, 0, 0, 0.1f, 5.8f, 0.14f, 3, 12, -t * 1.8f);
                mesh.ring(0, 0.1f, 5.8f, 0.62f, 28);
            }
            case "aqua_tsunami" -> {
                mesh.torus(0, 0, 0, 2.1f + progress(age, effect.duration) * 3.4f, 0.28f, 30, 7, t);
                mesh.torus(0, 1.1f, 0, 1.7f + progress(age, effect.duration) * 2.7f, 0.2f, 26, 6, -t);
                mesh.helix(0, -0.1f, 0, 2.3f, 1.5f, 3, 26, t);
            }
            case "aqua_leviathan_s_wrath" -> {
                mesh.ellipsoid(0, 1.2f, 2.0f, 1.15f, 0.9f, 1.3f, 18, 10, t);
                mesh.tube(0, 0.9f, -3.5f, 0, 0.9f, 2.0f, 0.52f, 8, 12, t);
                for (int i = 0; i < 7; i++) {
                    float z = -2.6f + i * 0.62f;
                    float r = 0.46f + (float)Math.sin(t + i * 0.55f) * 0.1f;
                    mesh.ring(0, 0.9f, z, r, 12);
                }
                mesh.ellipsoid(0, 1.45f, 3.05f, 0.42f, 0.36f, 0.5f, 12, 7, -t);
            }
            default -> { }
        }
        mesh.finish();
    }

    private static float progress(float age, int duration) {
        return clamp(age / duration, 0f, 1f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Effect {
        private final long id;
        private final String skillId;
        private final long startTick;
        private final int duration;
        private Vec3 position;
        private Vec3 direction;
        private boolean stopping;

        private Effect(AquaSkillFxPayload payload) {
            id = payload.effectId();
            skillId = payload.skillId();
            startTick = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
            duration = payload.durationTicks();
            position = new Vec3(payload.x(), payload.y(), payload.z());
            direction = new Vec3(payload.directionX(), payload.directionY(), payload.directionZ());
        }

        private void update(AquaSkillFxPayload payload) {
            position = new Vec3(payload.x(), payload.y(), payload.z());
            direction = new Vec3(payload.directionX(), payload.directionY(), payload.directionZ());
        }
    }

    private static final class Mesh {
        private final PoseStack.Pose pose;
        private final VertexConsumer out;
        private final float alpha;
        private final int budget;
        private int emitted;

        private Mesh(PoseStack.Pose pose, VertexConsumer out, float alpha, int budget) {
            this.pose = pose;
            this.out = out;
            this.alpha = alpha;
            this.budget = budget;
        }

        private void line(float ax, float ay, float az, float bx, float by, float bz, int color) {
            if (emitted + 2 > budget) return;
            int a = (int)(Math.max(0.0f, Math.min(1.0f, alpha)) * 225);
            int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
            out.addVertex(pose, ax, ay, az).setColor(r, g, b, a).setLineWidth(2.2f);
            out.addVertex(pose, bx, by, bz).setColor(r, g, b, a).setLineWidth(2.2f);
            emitted += 2;
        }

        private void tube(float sx,float sy,float sz,float ex,float ey,float ez,float radius,
                          int rings,int sides,float phase) {
            float dx=ex-sx, dy=ey-sy, dz=ez-sz;
            float len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
            if (len < 0.001f) return;
            float ux=-dz/len, uz=dx/len;
            float vx=dy*ux, vy=dz*ux-dx*uz, vz=-dy*ux;
            float px=0,py=0,pz=0;
            for(int i=0;i<=rings;i++) {
                float t=i/(float)rings, cx=sx+dx*t, cy=sy+dy*t, cz=sz+dz*t;
                float taper=0.35f+0.65f*(float)Math.sin(Math.PI*t*0.5);
                float firstX=0,firstY=0,firstZ=0,prevX=0,prevY=0,prevZ=0;
                for(int j=0;j<sides;j++) {
                    float a=(float)(j*Math.PI*2/sides)+phase+t*2.2f;
                    float x=cx+(ux*(float)Math.cos(a)+vx*(float)Math.sin(a))*radius*taper;
                    float y=cy+(vy*(float)Math.sin(a))*radius*taper;
                    float z=cz+(uz*(float)Math.cos(a)+vz*(float)Math.sin(a))*radius*taper;
                    if(j==0){firstX=x;firstY=y;firstZ=z;} else line(prevX,prevY,prevZ,x,y,z,0x54DDFB);
                    prevX=x;prevY=y;prevZ=z;
                    if(i>0) line(px,py,pz,x,y,z,0x54DDFB);
                }
                line(prevX,prevY,prevZ,firstX,firstY,firstZ,0x54DDFB);
                px=firstX;py=firstY;pz=firstZ;
            }
        }

        private void ellipsoid(float x,float y,float z,float rx,float ry,float rz,int lon,int lat,float phase) {
            for(int a=0;a<lon;a++) {
                float u=(float)(a*Math.PI*2/lon)+phase*0.25f;
                for(int b=1;b<lat;b++) {
                    float v=(float)(-Math.PI/2+b*Math.PI/lat);
                    float x1=x+rx*(float)Math.cos(v)*(float)Math.cos(u);
                    float y1=y+ry*(float)Math.sin(v);
                    float z1=z+rz*(float)Math.cos(v)*(float)Math.sin(u);
                    float u2=u+(float)(Math.PI*2/lon);
                    float x2=x+rx*(float)Math.cos(v)*(float)Math.cos(u2);
                    float z2=z+rz*(float)Math.cos(v)*(float)Math.sin(u2);
                    line(x1,y1,z1,x2,y1,z2,0x62E8FF);
                    if(b<lat-1) {
                        float v2=(float)(-Math.PI/2+(b+1)*Math.PI/lat);
                        line(x1,y1,z1,x+rx*(float)Math.cos(v2)*(float)Math.cos(u),
                                y+ry*(float)Math.sin(v2),z+rz*(float)Math.cos(v2)*(float)Math.sin(u),0x2A9EFF);
                    }
                }
            }
        }

        private void ribbon(float sx,float sy,float sz,float ex,float ey,float ez,float width,int segments,float phase) {
            for(int i=0;i<segments;i++) {
                float t=i/(float)segments, t2=(i+1)/(float)segments;
                float wave=(float)Math.sin(t*Math.PI*3+phase)*width*0.25f;
                float wave2=(float)Math.sin(t2*Math.PI*3+phase)*width*0.25f;
                float x=sx+(ex-sx)*t, z=sz+(ez-sz)*t;
                float x2=sx+(ex-sx)*t2,z2=sz+(ez-sz)*t2;
                line(x,sy+wave-width,z,x2,sy+wave2-width,z2,0x42BFFF);
                line(x,sy+wave+width,z,x2,sy+wave2+width,z2,0x8BF6FF);
                line(x,sy+wave-width,z,x,sy+wave+width,z,0xD2FFFF);
            }
        }

        private void helix(float x,float y,float z,float radius,float height,int strands,int segments,float phase) {
            for(int s=0;s<strands;s++) {
                float px=0,py=0,pz=0;
                for(int i=0;i<=segments;i++) {
                    float t=i/(float)segments, a=phase+t*(float)Math.PI*4+s*(float)Math.PI*2/strands;
                    float r=radius*(0.55f+0.45f*t);
                    float nx=x+(float)Math.cos(a)*r, ny=y+t*height, nz=z+(float)Math.sin(a)*r;
                    if(i>0) line(px,py,pz,nx,ny,nz,0x7DEBFF);
                    px=nx;py=ny;pz=nz;
                }
            }
        }

        private void funnel(float x,float y,float z,float radius,float height,int layers,int points,float phase) {
            for(int l=0;l<layers;l++) {
                float t=l/(float)(layers-1), r=radius*(1-0.88f*t), py=y+height*t;
                float px=0,pz=0;
                for(int i=0;i<=points;i++) {
                    float a=(float)(i*Math.PI*2/points)+phase+t*2.4f;
                    float nx=x+(float)Math.cos(a)*r,nz=z+(float)Math.sin(a)*r;
                    if(i>0) line(px,py,pz,nx,py,nz,0x3DB9FF);
                    px=nx;pz=nz;
                }
                if(l>0) line(x+(float)Math.cos(phase)*radius,y,z+(float)Math.sin(phase)*radius,
                        x+(float)Math.cos(phase+t*2.4f)*r,py,z+(float)Math.sin(phase+t*2.4f)*r,0x85F4FF);
            }
        }

        private void wave(float x,float y,float z,float width,float height,int across,int vertical,float phase) {
            for(int i=0;i<=across;i++) {
                float u=i/(float)across*2-1, crest=Math.max(0,1-u*u), px=x+u*width;
                float prevY=0,prevZ=0;
                for(int j=0;j<=vertical;j++) {
                    float v=j/(float)vertical;
                    float py=y+crest*height*v+(float)Math.sin(v*Math.PI*1.25f+phase+u*2)*0.16f;
                    float pz=z+v*0.35f;
                    if(j>0) line(px,prevY,prevZ,px,py,pz,0x45CFFF);
                    prevY=py;prevZ=pz;
                }
                if(i>0) line(px,y,z,px,y+crest*height,z,0x9BF7FF);
            }
        }

        private void torus(float x,float y,float z,float major,float minor,int seg,int sides,float phase) {
            for(int i=0;i<seg;i++) {
                float a=(float)(i*Math.PI*2/seg)+phase*0.2f;
                for(int j=0;j<sides;j++) {
                    float b=(float)(j*Math.PI*2/sides);
                    float r=major+(float)Math.cos(b)*minor;
                    float px=x+(float)Math.cos(a)*r, py=y+(float)Math.sin(b)*minor, pz=z+(float)Math.sin(a)*r;
                    float a2=(float)((i+1)*Math.PI*2/seg)+phase*0.2f;
                    float qx=x+(float)Math.cos(a2)*r, qz=z+(float)Math.sin(a2)*r;
                    line(px,py,pz,qx,py,qz,0x4ACFFF);
                    float b2=(float)((j+1)*Math.PI*2/sides), r2=major+(float)Math.cos(b2)*minor;
                    line(px,py,pz,x+(float)Math.cos(a)*r2,y+(float)Math.sin(b2)*minor,z+(float)Math.sin(a)*r2,0x9AF6FF);
                }
            }
        }

        private void ring(float x,float y,float z,float radius,int segments) {
            float px=0,py=0,pz=0,fx=0,fy=0,fz=0;
            for(int i=0;i<segments;i++) {
                float a=(float)(i*Math.PI*2/segments);
                float nx=x+(float)Math.cos(a)*radius, nz=z+(float)Math.sin(a)*radius;
                if(i==0){fx=nx;fz=nz;} else line(px,y,pz,nx,y,nz,0x6BEAFF);
                px=nx;pz=nz;
            }
            line(px,y,pz,fx,y,fz,0x6BEAFF);
        }

        private void finish() { }
    }
}
