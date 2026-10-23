package com.heroicpaths.rpg;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.minion.CompanionManager;
import com.heroicpaths.network.SyncRpgDataPayload;
import com.heroicpaths.registry.ModAttachments;
import com.heroicpaths.registry.ModItems;
import net.minecraft.world.item.ItemStack;
import com.heroicpaths.rpg.ability.AbilityDef;
import com.heroicpaths.rpg.ability.AbilityLogic;
import com.heroicpaths.rpg.ability.AbilityRegistry;
import com.heroicpaths.rpg.stat.Stat;
import com.heroicpaths.rpg.stat.StatBlock;
import com.heroicpaths.rpg.tree.PassiveTree;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Серверная логика прокачки: опыт, уровни, класс и подкласс, характеристики, дерево, умения и книги. */
public final class RpgManager {
    private static final ResourceLocation HEALTH_FLAT = HeroicPaths.id("tree_health");
    private static final ResourceLocation HEALTH_PCT = HeroicPaths.id("tree_health_pct");
    private static final ResourceLocation ARMOR = HeroicPaths.id("tree_armor");
    private static final ResourceLocation SPEED = HeroicPaths.id("tree_speed");
    private static final ResourceLocation ATTACK_SPEED = HeroicPaths.id("tree_attack_speed");
    /** Модификаторы первой версии мода: снимаются при входе. */
    private static final ResourceLocation LEGACY_TOUGHNESS = HeroicPaths.id("toughness");
    private static final ResourceLocation LEGACY_AGILITY = HeroicPaths.id("agility");
    /** Ранг умения: +25% силы и −10% перезарядки за каждый ранг выше первого. */
    public static final float POWER_PER_RANK = 0.25f;
    public static final float COOLDOWN_PER_RANK = 0.1f;

    private RpgManager() {}

    public static RpgData get(Player player) {
        return player.getData(ModAttachments.RPG_DATA);
    }

    public static void sync(ServerPlayer player) {
        // У клиента без мода (и у фейковых игроков) канала нет — отправка бросила бы исключение.
        if (player.connection == null || !player.connection.hasChannel(SyncRpgDataPayload.TYPE)) return;
        PacketDistributor.sendToPlayer(player, new SyncRpgDataPayload(get(player).serializeNBT(player.registryAccess())));
    }

    // ================================================================ опыт и уровни

    public static void addXp(ServerPlayer player, int amount) {
        RpgData data = get(player);
        if (amount <= 0 || data.getLevel() >= RpgData.MAX_LEVEL) return;
        int level = data.getLevel();
        long xp = (long) data.getXp() + amount; // на высоких уровнях порог близок к пределу int
        int previous = level;
        while (level < RpgData.MAX_LEVEL && xp >= RpgData.xpToNextLevel(level)) {
            xp -= RpgData.xpToNextLevel(level);
            level++;
        }
        if (level >= RpgData.MAX_LEVEL) xp = 0;
        data.setLevel(level);
        data.setXp((int) Math.min(Integer.MAX_VALUE, xp));
        if (level > previous) {
            onLevelUp(player, previous, level);
            autoFillSlots(data);
        }
        sync(player);
    }

    private static void onLevelUp(ServerPlayer player, int previous, int level) {
        player.sendSystemMessage(Component.translatable("message.heroicpaths.level_up", level).withStyle(ChatFormatting.GOLD));
        int abilityPoints = level / RpgData.LEVELS_PER_ABILITY_POINT - previous / RpgData.LEVELS_PER_ABILITY_POINT;
        player.sendSystemMessage(Component.translatable(abilityPoints > 0 ? "message.heroicpaths.points_gained_ability"
                : "message.heroicpaths.points_gained", level - previous, abilityPoints).withStyle(ChatFormatting.YELLOW));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1.2f);
        player.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                player.getX(), player.getY() + 1, player.getZ(), 40, 0.4, 0.8, 0.4, 0.25);
        CompanionManager.onOwnerLevelChanged(player);
    }

    /** Ставит уровень (команда). Если очков стало меньше, чем потрачено, прогресс сбрасывается. */
    public static void setLevel(ServerPlayer player, int level) {
        RpgData data = get(player);
        data.setLevel(Mth.clamp(level, 1, RpgData.MAX_LEVEL));
        data.setXp(0);
        if (data.treePoints() < 0 || data.statPoints() < 0 || data.abilityPoints() < 0) data.resetProgress();
        autoFillSlots(data);
        applyPassives(player);
        CompanionManager.onOwnerLevelChanged(player);
        sync(player);
    }

    // ================================================================ класс и подкласс

    /** Выбор класса с первым подклассом по умолчанию (для команд и тестов). */
    public static void chooseClass(ServerPlayer player, PlayerClass playerClass) {
        List<Subclass> subclasses = Subclass.forClass(playerClass);
        if (!subclasses.isEmpty()) chooseClass(player, playerClass, subclasses.get(0));
    }

    /** Жёсткий выбор класса и подкласса без смешивания. Сменить можно только полным сбросом. */
    public static boolean chooseClass(ServerPlayer player, PlayerClass playerClass, Subclass subclass) {
        RpgData data = get(player);
        if (playerClass == PlayerClass.NONE || subclass.playerClass() != playerClass || data.getPlayerClass() != PlayerClass.NONE) {
            return false;
        }
        data.resetProgress();
        data.setPlayerClass(playerClass);
        data.setSubclass(subclass);
        autoFillSlots(data);
        applyPassives(player);
        data.setResource(PlayerStats.maxResource(player));
        sync(player);
        // Урон мага зависит от жезла или посоха: новичку выдаётся жезл ученика.
        if (playerClass == PlayerClass.MAGE) {
            ItemStack wand = new ItemStack(ModItems.APPRENTICE_WAND.get());
            if (!player.getInventory().add(wand)) player.drop(wand, false);
            player.sendSystemMessage(Component.translatable("message.heroicpaths.mage_wand_given").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        player.sendSystemMessage(Component.translatable("message.heroicpaths.class_chosen",
                playerClass.displayName().withColor(playerClass.color()), subclass.displayName().withStyle(ChatFormatting.LIGHT_PURPLE)));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1f);
        return true;
    }

    /** Подкласс для персонажа, у которого класс уже есть, а подкласса нет (перенос со старой версии). */
    public static boolean chooseSubclass(ServerPlayer player, Subclass subclass) {
        RpgData data = get(player);
        if (data.getSubclass() != null || subclass.playerClass() != data.getPlayerClass()) return false;
        data.setSubclass(subclass);
        autoFillSlots(data);
        applyPassives(player);
        sync(player);
        player.sendSystemMessage(Component.translatable("message.heroicpaths.subclass_chosen", subclass.displayName())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        return true;
    }

    public static void resetClass(ServerPlayer player) {
        CompanionManager.dismiss(player);
        get(player).resetClass();
        applyPassives(player);
        sync(player);
    }

    // ================================================================ характеристики

    public static boolean addStat(ServerPlayer player, CoreStat stat) {
        RpgData data = get(player);
        if (data.getPlayerClass() == PlayerClass.NONE || data.statPoints() <= 0 || data.getStat(stat) >= CoreStat.MAX) return false;
        data.addStat(stat);
        applyPassives(player);
        CompanionManager.onOwnerLevelChanged(player);
        sync(player);
        return true;
    }

    // ================================================================ дерево

    public static boolean allocateNode(ServerPlayer player, int nodeId) {
        RpgData data = get(player);
        if (data.treePoints() <= 0 || !PassiveTree.canAllocate(data.getAllocatedNodes(), data.getPlayerClass(), nodeId)) return false;
        data.allocateNode(nodeId);
        applyPassives(player);
        sync(player);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.5f, 1.4f);
        return true;
    }

    public static boolean refundNode(ServerPlayer player, int nodeId) {
        RpgData data = get(player);
        if (!PassiveTree.canRefund(data.getAllocatedNodes(), data.getPlayerClass(), nodeId)) return false;
        data.refundNode(nodeId);
        applyPassives(player);
        sync(player);
        return true;
    }

    // ================================================================ умения и книги

    /** Почему умение нельзя выучить по книге, или null, если можно. */
    @Nullable
    public static String learnProblem(RpgData data, AbilityDef def) {
        if (data.knows(def.id())) return "message.heroicpaths.book_known";
        if (!AbilityRegistry.isLearnable(data.getSubclass(), def)) return "message.heroicpaths.book_wrong_class";
        if (data.getLevel() < def.unlockLevel()) return "message.heroicpaths.book_level";
        if (data.abilityPoints() <= 0) return "message.heroicpaths.no_ability_points";
        return null;
    }

    /** Чтение книги умения: умение выучено за одно очко умений. */
    public static boolean learnAbility(ServerPlayer player, AbilityDef def) {
        RpgData data = get(player);
        String problem = learnProblem(data, def);
        if (problem != null) {
            player.displayClientMessage(Component.translatable(problem, def.name(), def.unlockLevel()).withStyle(ChatFormatting.RED), true);
            return false;
        }
        data.learn(def.id());
        autoFillSlots(data);
        sync(player);
        player.sendSystemMessage(Component.translatable("message.heroicpaths.learned_ability", def.name()).withStyle(ChatFormatting.AQUA));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1.2f);
        return true;
    }

    /** Повышение ранга известного умения за одно очко умений (до 3 ранга). */
    public static boolean rankUp(ServerPlayer player, String abilityId) {
        RpgData data = get(player);
        AbilityDef def = AbilityRegistry.get(abilityId);
        if (def == null || !data.knows(abilityId) || data.getRank(abilityId) >= AbilityDef.MAX_RANK || data.abilityPoints() <= 0) return false;
        data.setRank(abilityId, data.getRank(abilityId) + 1);
        sync(player);
        player.sendSystemMessage(Component.translatable("message.heroicpaths.rank_up", def.name(), data.getRank(abilityId))
                .withStyle(ChatFormatting.AQUA));
        return true;
    }

    /** Множитель силы от ранга умения. */
    public static float rankPower(Player player, AbilityDef def) {
        return 1f + POWER_PER_RANK * (get(player).getRank(def.id()) - 1);
    }

    /** Известные умения занимают свободные слоты; забытые освобождают их. */
    public static void autoFillSlots(RpgData data) {
        for (int i = 0; i < RpgData.ABILITY_SLOTS; i++) {
            AbilityDef def = AbilityRegistry.get(data.getSlot(i));
            if (def != null && !AbilityRegistry.isLearned(data, def)) data.setSlot(i, null);
        }
        for (AbilityDef def : AbilityRegistry.learned(data)) {
            if (slotOf(data, def.id()) >= 0) continue;
            for (int i = 0; i < RpgData.ABILITY_SLOTS; i++) {
                if (data.getSlot(i) == null) {
                    data.setSlot(i, def.id());
                    break;
                }
            }
        }
    }

    public static int slotOf(RpgData data, String abilityId) {
        for (int i = 0; i < RpgData.ABILITY_SLOTS; i++) {
            if (abilityId.equals(data.getSlot(i))) return i;
        }
        return -1;
    }

    public static boolean bindAbility(ServerPlayer player, String abilityId, int slot) {
        RpgData data = get(player);
        AbilityDef def = AbilityRegistry.get(abilityId);
        if (def == null || slot < 0 || slot >= RpgData.ABILITY_SLOTS || !AbilityRegistry.isLearned(data, def)) return false;
        int previous = slotOf(data, abilityId);
        String displaced = data.getSlot(slot);
        data.setSlot(slot, abilityId);
        if (previous >= 0 && previous != slot) data.setSlot(previous, displaced);
        sync(player);
        return true;
    }

    public static int cost(Player player, AbilityDef def) {
        return Math.max(0, Math.round(def.cost() * PlayerStats.costFactor(player) - PlayerStats.get(player).get(Stat.FLAT_COST_REDUCTION)));
    }

    /** Заклинания магов перезаряжаются в полтора раза дольше остальных умений. */
    public static final float MAGE_COOLDOWN_FACTOR = 1.5f;

    public static int cooldown(Player player, AbilityDef def) {
        RpgData data = get(player);
        float rankFactor = 1f - COOLDOWN_PER_RANK * (data.getRank(def.id()) - 1);
        float classFactor = data.getPlayerClass() == PlayerClass.MAGE ? MAGE_COOLDOWN_FACTOR : 1f;
        return Math.max(10, Math.round(def.cooldownTicks() * PlayerStats.cooldownFactor(player) * rankFactor * classFactor));
    }

    public static void useAbility(ServerPlayer player, int slot) {
        if (player.isSpectator() || !player.isAlive()) return;
        RpgData data = get(player);
        AbilityDef def = AbilityRegistry.get(data.getSlot(slot));
        if (def == null) return;
        if (!AbilityRegistry.isLearned(data, def)) {
            player.displayClientMessage(Component.translatable("message.heroicpaths.not_learned", def.name())
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (com.heroicpaths.rpg.ability.Casting.isCasting(player)) {
            player.displayClientMessage(Component.translatable("message.heroicpaths.already_casting").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!usable(player, def)) return;
        // Сильные умения сначала произносятся: эффект, расход и перезарядка — в конце каста.
        if (def.castTicks() > 0) {
            com.heroicpaths.rpg.ability.Casting.begin(player, def, def.castTicks());
            return;
        }
        perform(player, def);
    }

    /** Конец каста: если умение всё ещё можно применить — применить. */
    public static void finishCast(ServerPlayer player, AbilityDef def) {
        if (!AbilityRegistry.isLearned(get(player), def) || !usable(player, def)) return;
        perform(player, def);
    }

    /** Можно ли сейчас применить умение (перезарядка, оружие, броня, ресурс); если нет — говорит игроку почему. */
    private static boolean usable(ServerPlayer player, AbilityDef def) {
        RpgData data = get(player);
        long now = player.level().getGameTime();
        long readyAt = data.getCooldownEnd(def.id());
        if (now < readyAt) {
            player.displayClientMessage(Component.translatable("message.heroicpaths.on_cooldown", def.name(),
                    (readyAt - now + 19) / 20).withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (!def.weapon().isMet(player)) {
            player.displayClientMessage(Component.translatable("message.heroicpaths.weapon_required", def.name(),
                    def.weapon().displayName()).withStyle(ChatFormatting.RED), true);
            return false;
        }
        Component armorBlock = ArmorProficiency.blockReason(player, def);
        if (armorBlock != null) {
            player.displayClientMessage(armorBlock.copy().withStyle(ChatFormatting.RED), true);
            return false;
        }
        int cost = cost(player, def);
        if (PlayerStats.paysWithHealth(player)) {
            if (player.getHealth() <= PlayerStats.healthCost(cost) + 1) {
                player.displayClientMessage(Component.translatable("message.heroicpaths.too_weak").withStyle(ChatFormatting.RED), true);
                return false;
            }
        } else if (data.getResource() < cost) {
            player.displayClientMessage(Component.translatable("message.heroicpaths.not_enough",
                    data.getPlayerClass().resourceName()).withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    /** Применить умение: эффект, затем расход ресурса и перезарядка. */
    private static void perform(ServerPlayer player, AbilityDef def) {
        RpgData data = get(player);
        long now = player.level().getGameTime();
        int cost = cost(player, def);
        boolean healthCost = PlayerStats.paysWithHealth(player);
        ClassTraits.beforeCast(player, def);
        if (!AbilityLogic.cast(player, def)) return;

        if (healthCost) player.setHealth(player.getHealth() - PlayerStats.healthCost(cost));
        else data.setResource(data.getResource() - cost);
        // Выносливость после умения, как и после удара, начинает копиться через секунду.
        if (com.heroicpaths.combat.Stamina.uses(data.getPlayerClass())) data.setStaminaUsedAt(now);
        data.setCooldownEnd(def.id(), now + cooldown(player, def));
        sync(player);
    }

    public static void restoreResource(ServerPlayer player, float amount) {
        RpgData data = get(player);
        data.setResource(Math.min(PlayerStats.maxResource(player), data.getResource() + amount));
        sync(player);
    }

    /** Вызывается раз в секунду: ресурс и (раз в 5 секунд) здоровье. */
    public static void tickRegen(ServerPlayer player) {
        RpgData data = get(player);
        if (data.getPlayerClass() == PlayerClass.NONE) return;
        float healthRegen = PlayerStats.get(player).get(Stat.HEALTH_REGEN);
        if (healthRegen > 0 && player.tickCount % 100 < 20 && player.getHealth() < player.getMaxHealth()) {
            player.heal(healthRegen);
        }
        // Выносливость бойцов копится каждый тик в Stamina.
        if (com.heroicpaths.combat.Stamina.uses(data.getPlayerClass())) return;
        float max = PlayerStats.maxResource(player);
        float current = data.getResource();
        if (current == max) return;
        data.setResource(current > max ? max : Math.min(max, current + PlayerStats.resourceRegen(player)));
        sync(player);
    }

    // ================================================================ атрибуты

    /** Переносит характеристики в атрибуты игрока: здоровье, броня, скорость, скорость атаки. */
    public static void applyPassives(ServerPlayer player) {
        RpgData data = get(player);
        data.invalidateStats();
        StatBlock stats = PlayerStats.get(player);
        setModifier(player, Attributes.MAX_HEALTH, HEALTH_FLAT, stats.get(Stat.MAX_HEALTH), AttributeModifier.Operation.ADD_VALUE);
        setModifier(player, Attributes.MAX_HEALTH, HEALTH_PCT, stats.get(Stat.MAX_HEALTH_PCT) / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setModifier(player, Attributes.ARMOR, ARMOR, stats.get(Stat.ARMOR), AttributeModifier.Operation.ADD_VALUE);
        setModifier(player, Attributes.MOVEMENT_SPEED, SPEED, stats.get(Stat.MOVE_SPEED) / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setModifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED, stats.get(Stat.ATTACK_SPEED) / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        for (ResourceLocation legacy : List.of(LEGACY_TOUGHNESS)) removeModifier(player, Attributes.MAX_HEALTH, legacy);
        removeModifier(player, Attributes.MOVEMENT_SPEED, LEGACY_AGILITY);

        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
        float maxResource = PlayerStats.maxResource(player);
        if (data.getResource() > maxResource) data.setResource(maxResource);
    }

    private static void setModifier(Player player, Holder<Attribute> attribute, ResourceLocation id,
                                    double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        if (amount != 0) instance.addPermanentModifier(new AttributeModifier(id, amount, operation));
    }

    private static void removeModifier(Player player, Holder<Attribute> attribute, ResourceLocation id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) instance.removeModifier(id);
    }
}
