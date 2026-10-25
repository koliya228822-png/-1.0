package com.heroicpaths.network;

import com.heroicpaths.craft.StationCrafting;
import com.heroicpaths.guild.GuildOrders;
import com.heroicpaths.dungeon.ContractService;
import com.heroicpaths.dungeon.DungeonType;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.CoreStat;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.Subclass;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("10");

        // Клиент хранит копию данных прямо в attachment своего игрока: HUD и меню читают её оттуда.
        registrar.playToClient(SyncRpgDataPayload.TYPE, SyncRpgDataPayload.STREAM_CODEC, (payload, context) ->
                RpgManager.get(context.player()).deserializeNBT(context.player().registryAccess(), payload.data()));

        registrar.playToServer(ChooseClassPayload.TYPE, ChooseClassPayload.STREAM_CODEC, (payload, context) -> {
            Subclass subclass = Subclass.byId(payload.subclass());
            if (context.player() instanceof ServerPlayer player && subclass != null) {
                RpgManager.chooseClass(player, PlayerClass.byOrdinal(payload.classId()), subclass);
            }
        });
        registrar.playToServer(UseAbilityPayload.TYPE, UseAbilityPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                RpgManager.useAbility(player, payload.slot());
            }
        });
        // Лямбда, а не ссылка на метод: клиентский класс загрузится только на клиенте при получении пакета.
        registrar.playToClient(OpenQuestBoardPayload.TYPE, OpenQuestBoardPayload.STREAM_CODEC, (payload, context) ->
                com.heroicpaths.client.ClientHooks.openQuestBoard());
        registrar.playToServer(TakeContractPayload.TYPE, TakeContractPayload.STREAM_CODEC, (payload, context) -> {
            DungeonType type = DungeonType.byId(payload.dungeon());
            if (context.player() instanceof ServerPlayer player && type != null) {
                ContractService.takeFromBoard(player, type);
            }
        });
        registrar.playToServer(OpenAccessoriesPayload.TYPE, OpenAccessoriesPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) com.heroicpaths.accessory.AccessoryMenu.open(player);
        });
        registrar.playToClient(OpenStationPayload.TYPE, OpenStationPayload.STREAM_CODEC, (payload, context) ->
                com.heroicpaths.client.ClientHooks.openStation(payload.station(), payload.pos()));
        registrar.playToServer(StationCraftPayload.TYPE, StationCraftPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                StationCrafting.craft(player, payload.recipe(), payload.pos(), payload.all());
            }
        });
        registrar.playToServer(RuneEnchantPayload.TYPE, RuneEnchantPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                com.heroicpaths.craft.RuneEnchanting.enchant(player, payload.enchantment(), payload.pos());
            }
        });
        registrar.playToClient(OpenOrderBoardPayload.TYPE, OpenOrderBoardPayload.STREAM_CODEC, (payload, context) ->
                com.heroicpaths.client.ClientHooks.openOrderBoard(payload.pos(), payload.offers()));
        registrar.playToServer(OrderActionPayload.TYPE, OrderActionPayload.STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            switch (payload.action()) {
                case OrderActionPayload.TAKE -> GuildOrders.take(player, payload.pos(), payload.index());
                case OrderActionPayload.TURN_IN -> GuildOrders.turnIn(player, payload.pos(), payload.index());
                case OrderActionPayload.ABANDON -> GuildOrders.abandon(player, payload.index());
                default -> { }
            }
        });
        registrar.playToClient(StaminaPayload.TYPE, StaminaPayload.STREAM_CODEC, (payload, context) ->
                com.heroicpaths.combat.Stamina.set(context.player(), payload.stamina()));
        registrar.playToClient(CastBarPayload.TYPE, CastBarPayload.STREAM_CODEC, (payload, context) ->
                com.heroicpaths.client.CastBarLayer.set(payload.ability(), payload.ticks()));
        registrar.playToClient(PlayerFormPayload.TYPE, PlayerFormPayload.STREAM_CODEC, (payload, context) ->
                com.heroicpaths.client.PlayerForms.set(payload.entityId(), payload.form(), payload.ticks()));
        registrar.playToClient(MobLevelPayload.TYPE, MobLevelPayload.STREAM_CODEC, (payload, context) -> {
            net.minecraft.world.entity.Entity entity = context.player().level().getEntity(payload.entityId());
            if (entity != null) com.heroicpaths.combat.MobLevels.applyOnClient(entity, payload);
        });
        registrar.playToClient(PartySyncPayload.TYPE, PartySyncPayload.STREAM_CODEC, (payload, context) ->
                com.heroicpaths.client.ClientHooks.setParty(payload.members()));
        registrar.playToServer(CombatInputPayload.TYPE, CombatInputPayload.STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            switch (payload.kind()) {
                case POWER_ATTACK -> com.heroicpaths.combat.PowerAttack.perform(player, payload.value());
                case CANCEL_CAST -> com.heroicpaths.rpg.ability.Casting.cancel(player);
                case QUICK_SHOT -> com.heroicpaths.rpg.ability.UniqueAbilities.quickShot(player);
            }
        });
        registrar.playToServer(TreeActionPayload.TYPE, TreeActionPayload.STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            switch (payload.action()) {
                case ALLOCATE -> RpgManager.allocateNode(player, payload.value());
                case REFUND -> RpgManager.refundNode(player, payload.value());
                case CHOOSE_SUBCLASS -> {
                    Subclass subclass = Subclass.byId(payload.text());
                    if (subclass != null) RpgManager.chooseSubclass(player, subclass);
                }
                case ADD_STAT -> RpgManager.addStat(player, CoreStat.byOrdinal(payload.value()));
                case RANK_UP -> RpgManager.rankUp(player, payload.text());
                case BIND -> RpgManager.bindAbility(player, payload.text(), payload.value());
                case CONVERT_XP -> com.heroicpaths.rpg.Experience.convertVanilla(player,
                        payload.text().isEmpty() ? null : com.heroicpaths.craft.Profession.byId(payload.text()));
            }
        });
    }
}
