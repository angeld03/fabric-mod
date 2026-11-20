package com.wildbound.player;

import com.wildbound.Wildbound;
import com.wildbound.persistence.RpgDataCodec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/**
 * Registers the player RPG profile as a Fabric Data Attachment.
 * This gives us persistence, copy-on-death and server-driven sync for free.
 */
public final class RpgAttachments {
    public static final AttachmentType<PlayerRpgProfile> PROFILE = AttachmentRegistry.<PlayerRpgProfile>builder()
            .persistent(PlayerRpgProfile.CODEC)
            .copyOnDeath()
            .initializer(PlayerRpgProfile::empty)
            .syncWith(RpgDataCodec.STREAM_CODEC, AttachmentSyncPredicate.all())
            .buildAndRegister(Wildbound.id("rpg_profile"));

    private RpgAttachments() {
    }

    public static void register() {
        // Registration happens in the static field above.
    }
}
