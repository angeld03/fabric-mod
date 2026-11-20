package com.wildbound.attributes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Locale;

/** The five core attributes of WILDBOUND. */
public enum RpgAttribute {
    STRENGTH,
    VITALITY,
    AGILITY,
    INTELLIGENCE,
    SPIRIT;

    public static final Codec<RpgAttribute> CODEC = Codec.STRING.comapFlatMap(
            name -> {
                try {
                    return DataResult.success(RpgAttribute.valueOf(name.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown RPG attribute: " + name);
                }
            },
            Enum::name
    );

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
