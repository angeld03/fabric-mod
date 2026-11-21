package com.wildbound.skills;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The complete elemental catalog: 18 elements, 10 skills each (180 in total).
 *
 * <p>Every skill belongs to exactly one element. Tiers are unlocked in order,
 * from the level 1 opener to the level 55 ultimate, so the branch always reads
 * as a path instead of an unordered list.</p>
 */
public final class SkillCatalog {
    /** Required player level for tiers 1..10. */
    public static final int[] TIER_LEVELS = {1, 3, 6, 10, 15, 21, 28, 36, 45, 55};
    /** Skill point cost for tiers 1..10. */
    public static final int[] TIER_COSTS = {1, 1, 1, 1, 1, 1, 1, 1, 2, 3};

    private static final String[] TIER_DESCRIPTIONS = {
            "Proyectil básico de %s. Daño elemental a distancia.",
            "Técnica de control de %s. Manipula el campo de batalla.",
            "Manifestación ofensiva de %s en el área cercana.",
            "Impulso de movilidad imbuido de %s.",
            "Estallido de %s que golpea a todos los enemigos próximos.",
            "Marca a distancia: el poder de %s queda latente en el objetivo.",
            "Invocación avanzada que canaliza %s de forma sostenida.",
            "Defensa o control superior otorgado por %s.",
            "Poder de rango mayor. Requiere dominar %s.",
            "Habilidad definitiva de %s. El poder elemental alcanza su forma final."
    };

    private static final Map<String, SkillElement> BY_ID = new LinkedHashMap<>();
    private static final List<SkillElement> ORDER = new ArrayList<>();
    private static boolean initialized;

    private SkillCatalog() {
    }

    /** Registers all 180 definitions. Safe to call several times. */
    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        add("aqua", "Agua", "Aqua", 0xFF57C8FF, 0xFF1259A8,
                "Water Bolt", "Healing Stream", "Aqua Whip", "Bubble Shield", "Tidal Wave",
                "Aqua Dash", "Whirlpool", "Pressure Lance", "Tsunami", "Leviathan's Wrath");

        add("inferno", "Fuego", "Inferno", 0xFFFF7A3D, 0xFFB71C1C,
                "Ember Bolt", "Flame Aura", "Fireball", "Blaze Dash", "Infernal Ring",
                "Combustion Mark", "Meteor Fall", "Phoenix Heart", "Inferno Field", "Dragon's Inferno");

        add("terra", "Tierra", "Terra", 0xFFC9A66B, 0xFF6D4C2F,
                "Stone Shard", "Earthen Wall", "Tremor", "Stone Armor", "Spike Eruption",
                "Seismic Slam", "Guardian Golem", "Faultline", "Mountain's Embrace", "Continental Rupture");

        add("storm", "Eléctrico", "Storm", 0xFFF6E27A, 0xFF6B5BD6,
                "Spark Bolt", "Chain Lightning", "Static Field", "Lightning Dash", "Overload",
                "Thunder Spear", "Storm Call", "Thunder Cage", "Tempest Surge", "God of Thunder");

        add("flora", "Planta", "Flora", 0xFF7BE495, 0xFF1B7F4B,
                "Thorn Shot", "Vine Snare", "Spore Cloud", "Blooming Heal", "Seed Trap",
                "Predator Plant", "Pollen Frenzy", "Overgrowth", "Nature's Grasp", "Worldbloom");

        add("radiance", "Luz", "Radiance", 0xFFFFE9A8, 0xFFFFA726,
                "Radiant Bolt", "Dawn Beacon", "Flash Blind", "Purifying Beam", "Shield of Dawn",
                "Solar Lance", "Holy Ground", "Radiant Wings", "Divine Judgment", "Sun's Verdict");

        add("umbra", "Oscuridad", "Umbra", 0xFFB388FF, 0xFF3A1E6E,
                "Shadow Bolt", "Dark Shroud", "Terror Gaze", "Shadow Step", "Soul Siphon",
                "Withering Curse", "Shadow Clones", "Eclipse", "Abyssal Tendrils", "Nightfall Avatar");

        add("zephyr", "Viento", "Zephyr", 0xFFA8E6FF, 0xFF3E8EA8,
                "Wind Gust", "Air Blade", "Lift", "Gale Dash", "Vortex",
                "Pressure Wall", "Sky Tornado", "Featherfall", "Hurricane Wings", "World Hurricane");

        add("void", "Vacío", "Void", 0xFFD0BFFF, 0xFF160B2E,
                "Void Orb", "Rift Step", "Void Pull", "Spatial Silence", "Dimensional Tear",
                "Projectile Erasure", "Black Hole", "Rift Prison", "Reality Collapse", "Singularity");

        add("sylvaris", "Sylvaris", "Ancient Nature", 0xFFB7F28B, 0xFF2F6B3A,
                "Verdant Spark", "Leafguard", "Ancient Roots", "Forest's Blessing", "Fae Step",
                "Spirit Whisper", "Elderwood Armor", "Dryad Summon", "Heart of Nature", "Avatar of Sylvaris");

        add("corrosive", "Ácido", "Corrosive", 0xFFB6FF6B, 0xFF4F8A1F,
                "Acid Spit", "Corrosive Coating", "Acid Pool", "Armor Dissolve", "Toxic Mist",
                "Caustic Rain", "Corrosion Chain", "Alchemical Eruption", "Dissolving Prison", "Abyssal Melt");

        add("blight", "Corrupción", "Blight", 0xFFD98CFF, 0xFF5B1E6B,
                "Blight Bolt", "Rot Mark", "Blight Spread", "Decay Drain", "Mutation",
                "Corrupted Vines", "Plague Burst", "Blighted Shade", "Consume Essence", "Plague Sovereign");

        add("ferrum", "Metal", "Ferrum", 0xFFD6E4F0, 0xFF5A6B7A,
                "Metal Shard", "Iron Skin", "Magnetic Pull", "Orbiting Blades", "Steel Wall",
                "Magnetize", "Metal Storm", "Forge Weapon", "Magnetic Prison", "Iron Colossus");

        add("frost", "Hielo", "Frost", 0xFFB3F0FF, 0xFF2E6FA8,
                "Ice Shard", "Frost Touch", "Ice Wall", "Frozen Armor", "Frost Nova",
                "Glacial Lance", "Blizzard", "Frozen Prison", "Glacial Path", "Absolute Zero");

        add("sanguis", "Sangre", "Sanguis", 0xFFFF6B7A, 0xFF8B0F24,
                "Blood Needle", "Crimson Pact", "Blood Chains", "Sanguine Drain", "Hemorrhage",
                "Blood Armor", "Crimson Rain", "Heartbound", "Blood Reaver", "Crimson Ascension");

        add("gravity", "Gravedad", "Gravity", 0xFFC9B6FF, 0xFF4A3B7A,
                "Gravity Orb", "Heavy Field", "Gravity Pull", "Zero Gravity", "Crushing Force",
                "Orbiting Rocks", "Gravity Well", "Meteoric Drop", "Singular Pressure", "Planetary Collapse");

        add("chronos", "Tiempo", "Chronos", 0xFFE0C88A, 0xFF6B5320,
                "Time Needle", "Accelerate", "Slow Field", "Temporal Shield", "Rewind Step",
                "Haste Zone", "Frozen Moment", "Afterimage", "Time Fracture", "Eternal Moment");

        add("resonance", "Sonido", "Resonance", 0xFFFFB3E6, 0xFF7A2B6B,
                "Sonic Pulse", "Echo Shot", "Silence Breaker", "Resonant Shield", "Sonic Boom",
                "Echo Clones", "Shatter Frequency", "Harmonic Cage", "Cataclysmic Frequency", "World Resonance");
    }

    private static void add(String id, String name, String alias, int primary, int secondary, String... skillNames) {
        String[] descriptions = id.equals("aqua") ? new String[] {
                "Concentra agua en tres lanzas sucesivas que atraviesan el campo y estallan al alcanzar su alcance.",
                "Invoca una corriente restauradora que cura al lanzador y a sus aliados cercanos durante varios pulsos.",
                "Extiende un látigo líquido en un arco frontal que golpea a los enemigos alcanzados.",
                "Crea una esfera de burbujas protectoras que concede absorción y resistencia durante 8 segundos.",
                "Desata una ola frontal que avanza, ensancha su cresta y derriba a los enemigos a su paso.",
                "Se transforma en una corriente veloz que atraviesa el espacio, daña a los enemigos en la trayectoria y culmina en una explosión de agua.",
                "Abre un remolino persistente que atrae y erosiona a los enemigos atrapados en su radio.",
                "Comprime agua a presión y dispara una lanza perforante de largo alcance que impacta a lo largo de su recorrido.",
                "Expande un anillo de tsunami desde el punto de invocación, golpeando y lanzando por los aires a los enemigos.",
                "Invoca el espíritu de un leviatán acuático que serpentea hacia delante y culmina en una devastadora ruptura de marea."
        } : TIER_DESCRIPTIONS;
        List<SkillDefinition> skills = new ArrayList<>(skillNames.length);
        for (int i = 0; i < skillNames.length; i++) {
            String skillName = skillNames[i];
            String skillId = id + "_" + slug(skillName);
            List<String> prerequisites = i == 0
                    ? List.of()
                    : List.of(id + "_" + slug(skillNames[i - 1]));
            String iconTexture = "wildbound:skill/" + skillId;
            SkillDefinition definition = new SkillDefinition(
                    skillId,
                    skillName,
                    descriptions[i].formatted(name),
                    TIER_LEVELS[i],
                    "",
                    TIER_COSTS[i],
                    prerequisites,
                    i == TIER_LEVELS.length - 1 ? SkillCategory.ULTIMATE : SkillCategory.ACTIVE,
                    SkillClass.UNIVERSAL,
                    "elemental",
                    iconTexture
            );
            SkillRegistry.register(definition);
            skills.add(definition);
        }
        SkillElement element = new SkillElement(id, name, alias, primary, secondary, skills);
        BY_ID.put(id, element);
        ORDER.add(element);
    }

    private static String slug(String value) {
        StringBuilder out = new StringBuilder();
        boolean pendingSeparator = false;
        for (char c : value.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                if (pendingSeparator && out.length() > 0) out.append('_');
                pendingSeparator = false;
                out.append(c);
            } else {
                pendingSeparator = true;
            }
        }
        return out.toString();
    }

    /** All elements in catalog order (Agua, Fuego, ... Sonido). */
    public static List<SkillElement> all() {
        initialize();
        return Collections.unmodifiableList(ORDER);
    }

    public static Optional<SkillElement> byId(String id) {
        initialize();
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static Optional<SkillElement> forSkill(String skillId) {
        initialize();
        for (SkillElement element : ORDER) {
            for (SkillDefinition skill : element.skills()) {
                if (skill.id().equals(skillId)) return Optional.of(element);
            }
        }
        return Optional.empty();
    }

    public static SkillDefinition getSkillDefinition(String skillId) {
        initialize();
        for (SkillElement element : ORDER) {
            for (SkillDefinition skill : element.skills()) {
                if (skill.id().equals(skillId)) return skill;
            }
        }
        return null;
    }
}
