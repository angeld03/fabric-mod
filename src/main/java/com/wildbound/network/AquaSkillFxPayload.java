package com.wildbound.network;

import com.wildbound.Wildbound;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Compact server-authoritative event for the client-side Aqua geometry renderer.
 *
 * <p>START creates a timed effect, UPDATE corrects its anchor/direction, and
 * STOP starts a short visual fade. Geometry is deliberately not sent over the
 * network: each client builds the same procedural mesh from this seed.</p>
 */
public record AquaSkillFxPayload(
        Action action,
        long effectId,
        UUID casterId,
        String skillId,
        double x,
        double y,
        double z,
        double directionX,
        double directionY,
        double directionZ,
        int ageTicks,
        int durationTicks,
        long seed
) implements CustomPacketPayload {
    public static final Type<AquaSkillFxPayload> TYPE =
            new Type<>(Wildbound.id("aqua_skill_fx"));

    public static final StreamCodec<FriendlyByteBuf, AquaSkillFxPayload> CODEC =
            CustomPacketPayload.codec(AquaSkillFxPayload::write, AquaSkillFxPayload::read);

    public enum Action {
        START,
        UPDATE,
        STOP
    }

    public AquaSkillFxPayload {
        action = action == null ? Action.START : action;
        casterId = casterId == null ? new UUID(0L, 0L) : casterId;
        skillId = skillId == null ? "" : skillId.substring(0, Math.min(64, skillId.length()));
        x = finiteOrZero(x);
        y = finiteOrZero(y);
        z = finiteOrZero(z);

        double dx = finiteOrZero(directionX);
        double dy = finiteOrZero(directionY);
        double dz = finiteOrZero(directionZ);
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-6) {
            directionX = 0.0;
            directionY = 0.0;
            directionZ = 1.0;
        } else {
            directionX = dx / length;
            directionY = dy / length;
            directionZ = dz / length;
        }

        durationTicks = Math.max(1, Math.min(160, durationTicks));
        ageTicks = Math.max(0, Math.min(durationTicks, ageTicks));
    }

    public static AquaSkillFxPayload start(long effectId, UUID casterId, String skillId,
                                            double x, double y, double z,
                                            double dx, double dy, double dz,
                                            int durationTicks, long seed) {
        return new AquaSkillFxPayload(Action.START, effectId, casterId, skillId,
                x, y, z, dx, dy, dz, 0, durationTicks, seed);
    }

    private static AquaSkillFxPayload read(FriendlyByteBuf buffer) {
        int actionId = buffer.readUnsignedByte();
        Action[] actions = Action.values();
        Action action = actionId < actions.length ? actions[actionId] : Action.STOP;
        return new AquaSkillFxPayload(
                action,
                buffer.readVarLong(),
                buffer.readUUID(),
                buffer.readUtf(64),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readVarInt(), buffer.readVarInt(),
                buffer.readLong()
        );
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeByte(action.ordinal());
        buffer.writeVarLong(effectId);
        buffer.writeUUID(casterId);
        buffer.writeUtf(skillId, 64);
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeDouble(directionX);
        buffer.writeDouble(directionY);
        buffer.writeDouble(directionZ);
        buffer.writeVarInt(ageTicks);
        buffer.writeVarInt(durationTicks);
        buffer.writeLong(seed);
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
