package com.heroicpaths.command;

import com.heroicpaths.party.Party;
import com.heroicpaths.party.PartyManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.UUID;

/**
 * /party — состав группы. /party create, invite &lt;игрок&gt;, accept, decline, leave, kick &lt;игрок&gt;, disband.
 * Команды доступны всем игрокам.
 */
public final class PartyCommand {
    private PartyCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("party")
                .executes(ctx -> result(PartyManager.list(player(ctx.getSource()))))
                .then(Commands.literal("list").executes(ctx -> result(PartyManager.list(player(ctx.getSource())))))
                .then(Commands.literal("create").executes(ctx -> result(PartyManager.create(player(ctx.getSource())))))
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> result(PartyManager.invite(player(ctx.getSource()), EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("accept").executes(ctx -> result(PartyManager.accept(player(ctx.getSource())))))
                .then(Commands.literal("decline").executes(ctx -> result(PartyManager.decline(player(ctx.getSource())))))
                .then(Commands.literal("leave").executes(ctx -> result(PartyManager.leave(player(ctx.getSource())))))
                .then(Commands.literal("disband").executes(ctx -> result(PartyManager.disband(player(ctx.getSource())))))
                .then(Commands.literal("kick").then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            Party party = ctx.getSource().getPlayer() == null ? null : PartyManager.partyOf(ctx.getSource().getPlayer());
                            return SharedSuggestionProvider.suggest(party == null ? java.util.List.of()
                                    : party.members().stream().map(party::name).toList(), builder);
                        })
                        .executes(ctx -> kick(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))));
    }

    private static ServerPlayer player(CommandSourceStack source) throws CommandSyntaxException {
        return source.getPlayerOrException();
    }

    private static int result(boolean success) {
        return success ? 1 : 0;
    }

    /** Исключать можно и тех, кто не в сети, поэтому ищем по имени в составе группы. */
    private static int kick(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerPlayer leader = player(source);
        Party party = PartyManager.partyOf(leader);
        UUID target = null;
        if (party != null) {
            for (UUID member : party.members()) {
                if (party.name(member).equalsIgnoreCase(name)) target = member;
            }
        }
        if (target == null) {
            source.sendFailure(Component.translatable("message.heroicpaths.party.not_member"));
            return 0;
        }
        return result(PartyManager.kick(leader, target));
    }
}
