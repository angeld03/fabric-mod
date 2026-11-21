package com.wildbound.realms;

import com.wildbound.core.WildboundConstants;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** The six realms of WILDBOUND, registered in progression order. */
public final class RealmRegistry {
    private static final Map<String, RealmDefinition> REALMS = new LinkedHashMap<>();

    static {
        register(new RealmDefinition(WildboundConstants.REALM_EMERALD_VALE, "Emerald Vale", 1, 15, "", ""));
        register(new RealmDefinition(WildboundConstants.REALM_FROSTFALL, "Frostfall", 16, 30,
                WildboundConstants.REALM_EMERALD_VALE, ""));
        register(new RealmDefinition(WildboundConstants.REALM_BLIGHTED_WILDS, "Blighted Wilds", 31, 45,
                WildboundConstants.REALM_FROSTFALL, ""));
        register(new RealmDefinition(WildboundConstants.REALM_SOLMARA, "Solmara", 46, 60,
                WildboundConstants.REALM_BLIGHTED_WILDS, ""));
        register(new RealmDefinition(WildboundConstants.REALM_ASHEN_CROWN, "Ashen Crown", 61, 75,
                WildboundConstants.REALM_SOLMARA, ""));
        register(new RealmDefinition(WildboundConstants.REALM_FORGOTTEN_REACH, "Forgotten Reach", 76, 90,
                WildboundConstants.REALM_ASHEN_CROWN, ""));
    }

    private RealmRegistry() {
    }

    public static void register(RealmDefinition definition) {
        if (REALMS.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalStateException("Duplicate realm id: " + definition.id());
        }
    }

    public static Optional<RealmDefinition> get(String id) {
        return Optional.ofNullable(REALMS.get(id));
    }

    public static Collection<RealmDefinition> all() {
        return REALMS.values();
    }
}
