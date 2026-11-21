package com.wildbound.realms;

/** Progression definition for a realm. Worlds and portals come in later phases. */
public record RealmDefinition(
        String id,
        String name,
        int minLevel,
        int maxLevel,
        String previousRealm,
        String accessItem
) {
    public RealmDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Realm id cannot be blank");
    }
}
