package com.wildbound.network;

import com.wildbound.Wildbound;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Clientbound cinematic camera command.
 *
 * <p>Values are RELATIVE OFFSETS (in degrees) from the player's current view,
 * never absolute world angles. The client interpolates them with an easing
 * curve for buttery-smooth cinematic motion.</p>
 *
 * <p>Mode controls the easing curve:</p>
 * <ul>
 *   <li>0 = SMOOTH (ease in-out, for dolly/focus)</li>
 *   <li>1 = PUNCH (fast attack, slow release - impacts)</li>
 *   <li>2 = SHAKE_KICK (sharp recoil that snaps back)</li>
 *   <li>3 = HOLD (linear hold then release)</li>
 * </ul>
 */
public record SkillCameraAimPayload(float yawOffset, float pitchOffset,
                                    int durationTicks, int mode, float intensity)
        implements CustomPacketPayload {
    public static final Type<SkillCameraAimPayload> TYPE =
            new Type<>(Wildbound.id("skill_camera_aim"));
    public static final StreamCodec<FriendlyByteBuf, SkillCameraAimPayload> CODEC =
            CustomPacketPayload.codec(SkillCameraAimPayload::write, SkillCameraAimPayload::read);

    public SkillCameraAimPayload {
        durationTicks = Math.max(0, durationTicks);
        mode = Math.max(0, Math.min(3, mode));
        intensity = Math.max(0f, Math.min(1f, intensity));
    }

    private static SkillCameraAimPayload read(FriendlyByteBuf buffer) {
        return new SkillCameraAimPayload(
                buffer.readFloat(), buffer.readFloat(), buffer.readVarInt(),
                buffer.readByte(), buffer.readFloat());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeFloat(yawOffset);
        buffer.writeFloat(pitchOffset);
        buffer.writeVarInt(durationTicks);
        buffer.writeByte(mode);
        buffer.writeFloat(intensity);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}