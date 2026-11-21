package com.wildbound.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wildbound.api.ExperienceSource;
import com.wildbound.attributes.AttributeService;
import com.wildbound.attributes.DerivedStats;
import com.wildbound.attributes.DerivedStatsCalculator;
import com.wildbound.attributes.RpgAttribute;
import com.wildbound.core.WildboundConfig;
import com.wildbound.core.WildboundConstants;
import com.wildbound.player.PlayerRpgProfile;
import com.wildbound.player.PlayerRpgProvider;
import com.wildbound.player.RpgAttachments;
import com.wildbound.progression.ExperienceService;
import com.wildbound.realms.RealmProgressionService;
import com.wildbound.resources.ResourceService;
import com.wildbound.resources.ResourceType;
import com.wildbound.skills.SkillProgressionService;
import com.wildbound.skills.SkillUnlockResult;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.Locale;

/** Administrative commands, restricted to server operators. */
public final class RpgCommands {
    private RpgCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register(RpgCommands::build);
    }

    private static void build(CommandDispatcher<CommandSourceStack> dispatcher,
                              CommandBuildContext context, Commands.CommandSelection selection) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("wildbound")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN));

        root.then(Commands.literal("profile").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PlayerRpgProfile p = PlayerRpgProvider.getOrCreate(player);
            send(ctx.getSource(), "Level " + p.level() + " | XP " + p.experience()
                    + "/" + ExperienceService.getExperienceRequired(player)
                    + " | AP " + p.attributePoints() + " | SP " + p.skillPoints());
            return 1;
        }));

        root.then(Commands.literal("stats").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            DerivedStats s = DerivedStatsCalculator.calculate(player);
            send(ctx.getSource(), "HP " + fmt(s.maxHealth()) + " MP " + fmt(s.maxMana())
                    + " SP " + fmt(s.maxStamina()) + " MELEE " + fmt(s.meleePower())
                    + " SPELL " + fmt(s.spellPower()) + " CRIT " + fmt(s.criticalChance() * 100f) + "%");
            return 1;
        }));

        addXpCommands(root);
        addLevelCommands(root);
        addAttributeCommands(root);
        addRealmCommands(root);
        addSkillCommands(root);
        addResourceCommands(root);
        addDebugCommands(root);

        dispatcher.register(root);
    }

    private static void addXpCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> xp = Commands.literal("xp");
        xp.then(Commands.literal("add")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(0L))
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    long amount = LongArgumentType.getLong(ctx, "amount");
                                    ExperienceService.grantExperience(target, amount, ExperienceSource.ADMIN);
                                    send(ctx.getSource(), "Granted " + amount + " XP to " + target.getName().getString());
                                    return 1;
                                }))));
        xp.then(Commands.literal("set")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(0L))
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    ExperienceService.setExperience(target, LongArgumentType.getLong(ctx, "amount"));
                                    send(ctx.getSource(), "XP updated for " + target.getName().getString());
                                    return 1;
                                }))));
        root.then(xp);
    }

    private static void addLevelCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> level = Commands.literal("level");
        level.then(Commands.literal("set")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, WildboundConfig.maxLevel))
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    setLevel(target, IntegerArgumentType.getInteger(ctx, "level"));
                                    send(ctx.getSource(), "Level updated for " + target.getName().getString());
                                    return 1;
                                }))));
        root.then(level);
    }

    private static void addAttributeCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> attribute = Commands.literal("attribute");
        attribute.then(Commands.literal("add")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("attribute", StringArgumentType.word())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1000))
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            RpgAttribute attr = parseAttribute(ctx.getArgument("attribute", String.class));
                                            if (attr == null) return 0;
                                            boolean ok = AttributeService.increaseAttribute(target, attr,
                                                    IntegerArgumentType.getInteger(ctx, "amount"));
                                            send(ctx.getSource(), ok ? "Attribute increased" : "Not enough points");
                                            return ok ? 1 : 0;
                                        })))));
        root.then(attribute);
    }

    private static void addRealmCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> realm = Commands.literal("realm");
        realm.then(Commands.literal("unlock")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("realm", StringArgumentType.string())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    boolean ok = RealmProgressionService.unlock(target, ctx.getArgument("realm", String.class));
                                    send(ctx.getSource(), ok ? "Realm unlocked" : "Cannot unlock that realm");
                                    return ok ? 1 : 0;
                                }))));
        realm.then(Commands.literal("list")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            send(ctx.getSource(), String.valueOf(RealmProgressionService.getUnlockedRealms(target)));
                            return 1;
                        })));
        root.then(realm);
    }

    private static void addSkillCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> skill = Commands.literal("skill");
        skill.then(Commands.literal("points")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, 9999))
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    PlayerRpgProfile p = PlayerRpgProvider.getOrCreate(target);
                                    PlayerRpgProvider.set(target, new PlayerRpgProfile(p.schemaVersion(), p.uuid(),
                                            p.level(), p.experience(), p.totalExperience(), p.attributePoints(),
                                            p.skillPoints() + amount, p.attributes(), p.mana(), p.stamina(),
                                            p.unlockedSkills(), p.unlockedRealms(), p.radialSkills(), p.stats()));
                                    send(ctx.getSource(), "Skill points: " + (p.skillPoints() + amount)
                                            + " for " + target.getName().getString());
                                    return 1;
                                }))));
        skill.then(Commands.literal("unlock")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("skill", StringArgumentType.string())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    String skillId = ctx.getArgument("skill", String.class);
                                    SkillUnlockResult result = SkillProgressionService.unlock(target, skillId);
                                    send(ctx.getSource(), result.success()
                                            ? "Skill unlocked: " + skillId
                                            : "Cannot unlock " + skillId + ": " + result.reason());
                                    return result.success() ? 1 : 0;
                                }))));
        skill.then(Commands.literal("list")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            PlayerRpgProfile p = PlayerRpgProvider.getOrCreate(target);
                            String details = p.unlockedSkills().stream()
                                    .map(id -> id + " [R" + p.getSkillProgress(id).rank() + " "
                                            + p.getSkillProgress(id).experience() + "/"
                                            + p.getSkillProgress(id).experienceRequired() + " XP]")
                                    .collect(java.util.stream.Collectors.joining(", "));
                            send(ctx.getSource(), "Skills (" + p.unlockedSkills().size() + "): " + details);
                            return 1;
                        })));
        root.then(skill);
    }

    private static void addResourceCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(resourceCommand("mana", ResourceType.MANA));
        root.then(resourceCommand("stamina", ResourceType.STAMINA));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> resourceCommand(String name, ResourceType type) {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(name);
        node.then(Commands.literal("fill")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            ResourceService.set(target, type, ResourceService.getMax(target, type));
                            send(ctx.getSource(), capitalize(name) + " filled for "
                                    + target.getName().getString());
                            return 1;
                        })));
        node.then(Commands.literal("set")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, 100000))
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    float amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    ResourceService.set(target, type, amount);
                                    send(ctx.getSource(), capitalize(name) + " set to "
                                            + fmt(ResourceService.get(target, type))
                                            + " for " + target.getName().getString());
                                    return 1;
                                }))));
        return node;
    }

    private static String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static void addDebugCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> debug = Commands.literal("debug");
        debug.then(Commands.literal("info").executes(ctx -> {
            send(ctx.getSource(), "WILDBOUND core | max level " + WildboundConfig.maxLevel
                    + " | schema " + PlayerRpgProfile.CURRENT_SCHEMA);
            return 1;
        }));
        debug.then(Commands.literal("reset")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            PlayerRpgProvider.set(target, PlayerRpgProfile.create(target.getUUID(),
                                    WildboundConstants.REALM_EMERALD_VALE));
                            send(ctx.getSource(), "Profile reset for " + target.getName().getString());
                            return 1;
                        })));
        debug.then(Commands.literal("attachment").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            boolean attached = player.hasAttached(RpgAttachments.PROFILE);
            PlayerRpgProfile profile = player.getAttached(RpgAttachments.PROFILE);
            if (profile == null) {
                send(ctx.getSource(), "Attachment present=false | profile has not been initialized");
                return 1;
            }

            boolean uuidMatches = player.getUUID().toString().equals(profile.uuid());
            send(ctx.getSource(), "Attachment present=" + attached
                    + " | UUID matches=" + uuidMatches
                    + " | schema=" + profile.schemaVersion()
                    + " | level=" + profile.level()
                    + " | XP=" + profile.experience()
                    + " | attribute points=" + profile.attributePoints()
                    + " | skill points=" + profile.skillPoints()
                    + " | skills=" + profile.unlockedSkills().size()
                    + " | realms=" + profile.unlockedRealms().size()
                    + " | radial=" + profile.radialSkills());
            return 1;
        }));
        root.then(debug);
    }

    private static void setLevel(ServerPlayer player, int level) {
        PlayerRpgProfile p = PlayerRpgProvider.getOrCreate(player);
        PlayerRpgProvider.set(player, new PlayerRpgProfile(p.schemaVersion(), p.uuid(), level, 0L,
                p.totalExperience(), p.attributePoints(), p.skillPoints(), p.attributes(),
                p.mana(), p.stamina(), p.unlockedSkills(), p.unlockedRealms(), p.radialSkills(), p.stats()));
    }

    private static RpgAttribute parseAttribute(String value) {
        try {
            return RpgAttribute.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String fmt(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static void send(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }
}
