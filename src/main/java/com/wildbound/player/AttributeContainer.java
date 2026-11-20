package com.wildbound.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wildbound.attributes.RpgAttribute;

/** Immutable container for the five core attributes. */
public record AttributeContainer(int strength, int vitality, int agility, int intelligence, int spirit) {

    public static final AttributeContainer BASE = new AttributeContainer(5, 5, 5, 5, 5);

    public static final Codec<AttributeContainer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("strength").forGetter(AttributeContainer::strength),
            Codec.INT.fieldOf("vitality").forGetter(AttributeContainer::vitality),
            Codec.INT.fieldOf("agility").forGetter(AttributeContainer::agility),
            Codec.INT.fieldOf("intelligence").forGetter(AttributeContainer::intelligence),
            Codec.INT.fieldOf("spirit").forGetter(AttributeContainer::spirit)
    ).apply(instance, AttributeContainer::new));

    public int get(RpgAttribute attribute) {
        return switch (attribute) {
            case STRENGTH -> strength;
            case VITALITY -> vitality;
            case AGILITY -> agility;
            case INTELLIGENCE -> intelligence;
            case SPIRIT -> spirit;
        };
    }

    public AttributeContainer with(RpgAttribute attribute, int value) {
        int safe = Math.max(0, value);
        return switch (attribute) {
            case STRENGTH -> new AttributeContainer(safe, vitality, agility, intelligence, spirit);
            case VITALITY -> new AttributeContainer(strength, safe, agility, intelligence, spirit);
            case AGILITY -> new AttributeContainer(strength, vitality, safe, intelligence, spirit);
            case INTELLIGENCE -> new AttributeContainer(strength, vitality, agility, safe, spirit);
            case SPIRIT -> new AttributeContainer(strength, vitality, agility, intelligence, safe);
        };
    }

    /** Validates and clamps every value, used when loading old or malformed data. */
    public AttributeContainer validated(int max) {
        return new AttributeContainer(
                clamp(strength, max),
                clamp(vitality, max),
                clamp(agility, max),
                clamp(intelligence, max),
                clamp(spirit, max)
        );
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(value, max));
    }
}
