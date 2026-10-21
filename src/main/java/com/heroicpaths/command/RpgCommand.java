package com.heroicpaths.command;

import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /rpg info — свой класс и уровень.
 * /rpg addxp, /rpg setlevel, /rpg reset — для операторов (тесты и администрирование).
 */
public final class RpgCommand {
    private RpgCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rpg")
                .then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
                .then(Commands.literal("addxp").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1_000_000))
                                .executes(ctx -> {
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    RpgManager.addXp(ctx.getSource().getPlayerOrException(), amount);
                                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.heroicpaths.addxp", amount), false);
                                    return 1;
                                })))
                .then(Commands.literal("setlevel").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, RpgData.MAX_LEVEL))
                                .executes(ctx -> {
                                    int level = IntegerArgumentType.getInteger(ctx, "level");
                                    RpgManager.setLevel(ctx.getSource().getPlayerOrException(), level);
                                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.heroicpaths.setlevel", level), false);
                                    return 1;
                                })))
                .then(Commands.literal("reset").requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            RpgManager.resetClass(ctx.getSource().getPlayerOrException());
                            ctx.getSource().sendSuccess(() -> Component.translatable("commands.heroicpaths.reset"), false);
                            return 1;
                        })));
    }

    private static int info(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        RpgData data = RpgManager.get(player);
        Component spec = data.getSubclass() == null ? Component.literal("-") : data.getSubclass().displayName();
        source.sendSuccess(() -> Component.translatable("commands.heroicpaths.info",
                data.getPlayerClass().displayName(), spec, data.getLevel(), data.getXp(),
                RpgData.xpToNextLevel(data.getLevel()), data.treePoints()), false);
        return 1;
    }
}
