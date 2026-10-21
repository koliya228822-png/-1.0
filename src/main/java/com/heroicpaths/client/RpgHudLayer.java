package com.heroicpaths.client;

import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.PlayerStats;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.ability.AbilityDef;
import com.heroicpaths.rpg.ability.AbilityRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import com.heroicpaths.network.PartySyncPayload;

import java.util.List;
import java.util.Locale;

/** Панель в левом верхнем углу: класс, уровень, ресурс, опыт и 4 слота способностей. */
public class RpgHudLayer implements LayeredDraw.Layer {
    private static final int WIDTH = 140;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || player.isSpectator()
                || minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }
        // Размер панели задаётся в настройках мода.
        float scale = ClientConfig.hudScale();
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1f);
        try {
            draw(graphics, minecraft, player);
        } finally {
            graphics.pose().popPose();
        }
    }

    private void draw(GuiGraphics graphics, Minecraft minecraft, LocalPlayer player) {
        Font font = minecraft.font;
        RpgData data = RpgManager.get(player);
        PlayerClass playerClass = data.getPlayerClass();
        int x = 6;
        int y = 6;

        if (playerClass == PlayerClass.NONE) {
            graphics.drawString(font, Component.translatable("hud.heroicpaths.choose_class",
                    ModKeys.OPEN_SKILLS.getTranslatedKeyMessage()), x, y, 0xFFD27F, true);
            renderParty(graphics, font, x, y + 16);
            return;
        }

        boolean hasContract = !data.getContractDungeon().isEmpty();
        graphics.fill(x - 3, y - 3, x + WIDTH + 3, y + 30 + RpgData.ABILITY_SLOTS * 10 + (hasContract ? 10 : 0), 0x80000000);
        if (hasContract) {
            com.heroicpaths.dungeon.DungeonType type = com.heroicpaths.dungeon.DungeonType.byId(data.getContractDungeon());
            Component contract = Component.translatable("hud.heroicpaths.contract",
                    type == null ? Component.literal("?") : type.displayName(), data.getContractParty());
            graphics.drawString(font, contract, x, y + 30 + RpgData.ABILITY_SLOTS * 10, 0xD0A0FF, true);
        }

        MutableComponent header = Component.empty()
                .append(data.getSubclass() != null ? data.getSubclass().displayName() : playerClass.displayName())
                .append(" ")
                .append(Component.translatable("hud.heroicpaths.level", data.getLevel()));
        graphics.drawString(font, header, x, y, playerClass.color(), true);
        int points = Math.max(0, data.treePoints()) + Math.max(0, data.statPoints()) + Math.max(0, data.abilityPoints());
        if (points > 0) {
            Component pointsText = Component.translatable("hud.heroicpaths.points", points);
            graphics.drawString(font, pointsText, x + WIDTH - font.width(pointsText), y, 0x7FFF7F, true);
        }
        y += 12;

        // Маг крови платит здоровьем: вместо полоски ресурса — полоска крови.
        boolean blood = data.getSubclass() != null && data.getSubclass().usesHealth();
        float max = blood ? player.getMaxHealth() : PlayerStats.maxResource(player);
        float current = blood ? player.getHealth() : Math.min(data.getResource(), max);
        graphics.fill(x, y, x + WIDTH, y + 10, 0xFF1A1A1A);
        graphics.fill(x, y, x + Math.round(WIDTH * current / max), y + 10, blood ? 0xFFB01020 : 0xFF000000 | playerClass.resourceColor());
        Component resourceName = blood ? Component.translatable("resource.heroicpaths.blood") : playerClass.resourceName();
        String resourceText = resourceName.getString() + " " + (int) current + "/" + (int) max;
        graphics.drawCenteredString(font, resourceText, x + WIDTH / 2, y + 1, 0xFFFFFF);
        y += 12;

        float progress = data.getLevel() >= RpgData.MAX_LEVEL
                ? 1f
                : (float) data.getXp() / RpgData.xpToNextLevel(data.getLevel());
        graphics.fill(x, y, x + WIDTH, y + 3, 0xFF1A1A1A);
        graphics.fill(x, y, x + Math.round(WIDTH * progress), y + 3, 0xFF7FD23C);
        y += 6;

        long gameTime = player.level().getGameTime();
        for (int slot = 0; slot < RpgData.ABILITY_SLOTS; slot++) {
            String key = ModKeys.ability(slot).getTranslatedKeyMessage().getString();
            AbilityDef def = AbilityRegistry.get(data.getSlot(slot));
            String name;
            String status;
            int color;
            if (def == null) {
                name = Component.translatable("hud.heroicpaths.empty_slot").getString();
                status = "";
                color = 0x666666;
            } else {
                name = def.name().getString();
                long ticksLeft = data.getCooldownEnd(def.id()) - gameTime;
                int cost = RpgManager.cost(player, def);
                if (ticksLeft > 0) {
                    status = Component.translatable("hud.heroicpaths.seconds", (ticksLeft + 19) / 20).getString();
                    color = 0x999999;
                } else {
                    float price = blood ? PlayerStats.healthCost(cost) : cost;
                    status = blood ? String.format(java.util.Locale.ROOT, "%.1f♥", price) : String.valueOf(cost);
                    boolean weaponOk = def.weapon().isMet(player) && com.heroicpaths.rpg.ArmorProficiency.blockReason(player, def) == null;
                    color = !weaponOk ? 0x888888 : current < price ? 0xFF6060 : 0xFFFFFF;
                }
            }
            graphics.drawString(font, "[" + key + "] " + name, x, y, color, true);
            graphics.drawString(font, status, x + WIDTH - font.width(status), y, color, true);
            y += 10;
        }
        y = renderCompanion(graphics, minecraft, data, font, x, y + (hasContract ? 18 : 8));
        y = renderOrders(graphics, player, data, font, x, y);
        renderParty(graphics, font, x, y);
    }

    /** Взятые заказы гильдии: название и прогресс; выполненные — зелёным. */
    private static int renderOrders(GuiGraphics graphics, LocalPlayer player, RpgData data, Font font, int x, int y) {
        List<com.heroicpaths.guild.Order> orders = data.getOrders();
        if (orders.isEmpty() || !ClientConfig.showOrders()) return y;
        graphics.fill(x - 3, y - 3, x + WIDTH + 3, y + 9 + orders.size() * 10, 0x80000000);
        graphics.drawString(font, Component.translatable("hud.heroicpaths.orders"), x, y, 0xFFD27F, true);
        y += 10;
        for (com.heroicpaths.guild.Order order : orders) {
            boolean delivery = order.type() == com.heroicpaths.guild.OrderType.DELIVERY;
            int progress = delivery ? Math.min(order.amount(), com.heroicpaths.guild.GuildOrders.countItems(player, order)) : order.progress();
            boolean ready = progress >= order.amount();
            String count = progress + "/" + order.amount();
            String name = Component.translatable(order.targetKey()).getString();
            int room = WIDTH - font.width(count) - 6;
            if (font.width(name) > room) name = font.plainSubstrByWidth(name, room - font.width("…")) + "…";
            int color = ready ? 0x7FFF7F : 0xDDDDDD;
            graphics.drawString(font, name, x, y, color, true);
            graphics.drawString(font, count, x + WIDTH - font.width(count), y, color, true);
            y += 10;
        }
        return y + 8;
    }

    /**
     * Зверь охотника: вид, полоска и число здоровья. Если зверь погиб — сколько ждать его возвращения.
     * Возвращает высоту, с которой рисовать следующий блок.
     */
    private static int renderCompanion(GuiGraphics graphics, Minecraft minecraft, RpgData data, Font font, int x, int y) {
        if (data.getPlayerClass() != PlayerClass.HUNTER || !ClientConfig.showCompanion() || minecraft.level == null) return y;
        long now = minecraft.level.getGameTime();
        Component line;
        float fraction = -1;
        String healthText = "";
        LivingEntity companion = null;
        if (data.getCompanion() != null) {
            for (Entity entity : minecraft.level.entitiesForRendering()) {
                if (data.getCompanion().equals(entity.getUUID()) && entity instanceof LivingEntity living) companion = living;
            }
        }
        if (companion != null) {
            line = companion.getName();
            fraction = Math.min(1f, companion.getHealth() / companion.getMaxHealth());
            healthText = Math.round(companion.getHealth()) + "/" + Math.round(companion.getMaxHealth());
        } else if (data.getCompanionDownUntil() > now) {
            line = Component.translatable("hud.heroicpaths.companion_down", (data.getCompanionDownUntil() - now + 19) / 20);
        } else if (data.getCompanion() != null) {
            line = Component.translatable("hud.heroicpaths.companion_far");
        } else {
            line = Component.translatable("hud.heroicpaths.companion_none");
        }
        graphics.fill(x - 3, y - 3, x + WIDTH + 3, y + (fraction >= 0 ? 17 : 11), 0x80000000);
        graphics.drawString(font, line, x, y, 0xE0B070, true);
        if (fraction >= 0) {
            graphics.drawString(font, healthText, x + WIDTH - font.width(healthText), y, 0xFFFFFF, true);
            graphics.fill(x, y + 10, x + WIDTH, y + 13, 0xFF1A1A1A);
            graphics.fill(x, y + 10, x + Math.round(WIDTH * fraction), y + 13, fraction > 0.3f ? 0xFF4CC24C : 0xFFD23C3C);
        }
        return y + (fraction >= 0 ? 24 : 18);
    }

    /** Состав группы: имя, уровень и полоска здоровья каждого участника. */
    private static void renderParty(GuiGraphics graphics, Font font, int x, int y) {
        List<PartySyncPayload.Member> party = ClientHooks.party();
        if (party.isEmpty() || !ClientConfig.showParty()) return;
        graphics.fill(x - 3, y - 3, x + WIDTH + 3, y + 10 + party.size() * 14, 0x80000000);
        graphics.drawString(font, Component.translatable("hud.heroicpaths.party", party.size(),
                String.format(Locale.ROOT, "%.1f", ClientHooks.partyAverageLevel(1))), x, y, 0xFFD27F, true);
        y += 11;
        for (PartySyncPayload.Member member : party) {
            String label = (member.leader() ? "★ " : "") + member.name();
            String level = Component.translatable("hud.heroicpaths.level", member.level()).getString();
            int textColor = member.online() ? 0xFFFFFF : 0x777777;
            graphics.drawString(font, label, x, y, member.online() ? member.color() : textColor, true);
            graphics.drawString(font, level, x + WIDTH - font.width(level), y, textColor, true);
            float fraction = member.online() && member.maxHealth() > 0 ? Math.min(1f, member.health() / member.maxHealth()) : 0f;
            graphics.fill(x, y + 9, x + WIDTH, y + 11, 0xFF1A1A1A);
            graphics.fill(x, y + 9, x + Math.round(WIDTH * fraction), y + 11, 0xFFD23C3C);
            y += 14;
        }
    }
}
