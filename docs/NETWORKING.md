# Networking

The server is authoritative. The client never decides XP, levels, attributes, skills, resources or realm access.

Profile synchronization uses Fabric Data Attachments with `syncWith(RpgDataCodec.STREAM_CODEC, AttachmentSyncPredicate.all())`, so no custom packets are needed for the core profile.

`RpgDataCodec` reads/writes every field with bounded strings and list sizes to reject malformed data.

Future client requests (for example spending a skill point) must be sent as custom payloads and fully re-validated on the server using the same service methods.