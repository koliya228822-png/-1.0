package com.heroicpaths.item;

import com.heroicpaths.entity.MagicBoltEntity;
import com.heroicpaths.fx.Fx;
import com.heroicpaths.fx.Palette;
import com.heroicpaths.registry.ModParticles;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.PlayerStats;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.stat.Element;
import com.heroicpaths.rpg.stat.Stat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Жезл или посох мага. Урон заклинаний мага рассчитывается от силы магии фокуса в руке:
 * без жезла или посоха маг не наносит урона. Посох сильнее, но держится двумя руками
 * (с предметом во второй руке его сила вдвое меньше); жезл слабее и оставляет вторую руку свободной.
 * Рунные фокусы привязаны к стихии своей руны и сильнее бьют заклинаниями этой стихии.
 * <p>
 * Ещё фокус стреляет как лук: зажать ПКМ — зарядить (на 20% дольше лука), отпустить — выпустить магическую стрелу
 * стихии фокуса за 5% маны. Урон стрелы — 100% силы магии фокуса плюс 25% силы заклинаний мага.
 */
public class FocusItem extends Item {
    /** Сила магии, при которой урон заклинания равен его базовому урону. */
    public static final float REFERENCE_POWER = 5f;
    /** Полная зарядка: лук натягивается за 20 тиков, фокус — на 20% дольше. */
    public static final int CHARGE_TICKS = 24;
    /** Посох заряжается на 20% быстрее жезла. */
    public static final int STAFF_CHARGE_TICKS = 20;
    /** Выстрел стоит 5% маны (у мага крови — 5% здоровья). */
    public static final float SHOT_COST = 0.05f;
    /** Какая доля силы заклинаний мага добавляется к урону стрелы. */
    public static final float SPELL_POWER_SHARE = 0.25f;

    private final float spellPower;
    private final boolean staff;
    private final int grade;
    @Nullable
    private final Element element;
    private final int elementBonusPercent;

    public FocusItem(float spellPower, boolean staff, Properties properties) {
        this(spellPower, staff, 1, null, 0, properties);
    }

    public FocusItem(float spellPower, boolean staff, int grade, @Nullable Element element, int elementBonusPercent, Properties properties) {
        super(properties);
        this.spellPower = spellPower;
        this.staff = staff;
        this.grade = grade;
        this.element = element;
        this.elementBonusPercent = elementBonusPercent;
    }

    public float spellPower() {
        return spellPower;
    }

    public boolean isStaff() {
        return staff;
    }

    public int grade() {
        return grade;
    }

    @Nullable
    public Element element() {
        return element;
    }

    /** Фокус, которым маг сейчас колдует: главная рука, а если там нет фокуса — жезл во второй руке. */
    @Nullable
    private static ItemStack activeFocus(Player player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        float mainPower = main.getItem() instanceof FocusItem ? stackPower(main, off) : 0;
        float offPower = off.getItem() instanceof FocusItem focus && !focus.staff ? stackPower(off, ItemStack.EMPTY) : 0;
        if (mainPower <= 0 && offPower <= 0) return null;
        return mainPower >= offPower ? main : off;
    }

    private static float stackPower(ItemStack stack, ItemStack otherHand) {
        FocusItem focus = (FocusItem) stack.getItem();
        float power = focus.spellPower * (1f + ItemRarity.bonusPercent(stack) / 100f);
        if (focus.staff && !otherHand.isEmpty()) power *= 0.5f;
        return power;
    }

    /** Гамма магической стрелы: стихия фокуса, у фокусов без стихии — тайная магия. */
    public Palette boltPalette() {
        if (element == null) return Palette.ARCANE;
        return switch (element) {
            case FIRE -> Palette.FIRE;
            case WATER -> Palette.FROST;
            case EARTH -> Palette.AMBER;
            case AIR -> Palette.STORM;
            default -> Palette.ARCANE;
        };
    }

    /** Урон стрелы этого фокуса в руках игрока: сила магии × (1 + 25% силы заклинаний мага). */
    public static float boltDamage(Player player, ItemStack stack) {
        ItemStack other = stack == player.getMainHandItem() ? player.getOffhandItem() : player.getMainHandItem();
        float spell = PlayerStats.get(player).get(Stat.SPELL_DAMAGE);
        return stackPower(stack, other) * Math.max(0.1f, 1f + SPELL_POWER_SHARE * spell / 100f)
                + SPELL_POWER_SHARE * PlayerStats.get(player).get(Stat.FLAT_SPELL_DAMAGE);
    }

    /** Насколько заряжен выстрел после ticks тиков: как у лука, но полная зарядка за chargeTicks. */
    public static float charge(ItemStack stack, int ticks) {
        float k = Math.min(1f, ticks / (float) chargeTicks(stack));
        return (k * k + k * 2f) / 3f;
    }

    /** За сколько тиков фокус заряжается полностью: посох быстрее жезла. */
    public static int chargeTicks(ItemStack stack) {
        return stack.getItem() instanceof FocusItem focus && focus.isStaff() ? STAFF_CHARGE_TICKS : CHARGE_TICKS;
    }

    /** Почему маг не может выстрелить (null — может). */
    @Nullable
    private static Component shotProblem(Player player) {
        RpgData data = RpgManager.get(player);
        if (data.getPlayerClass() != PlayerClass.MAGE) return Component.translatable("message.heroicpaths.focus_mage_only");
        if (PlayerStats.paysWithHealth(player)) {
            if (player.getHealth() <= player.getMaxHealth() * SHOT_COST + 1) return Component.translatable("message.heroicpaths.too_weak");
        } else if (data.getResource() < PlayerStats.maxResource(player) * SHOT_COST) {
            return Component.translatable("message.heroicpaths.not_enough", data.getPlayerClass().resourceName());
        }
        return null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Component problem = shotProblem(player);
        if (problem != null) {
            if (!level.isClientSide) player.displayClientMessage(problem.copy().withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    /** Пока заряжается: сила стягивается к навершию, при полной зарядке — звон и вспышка искр. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!level.isClientSide) return;
        int used = getUseDuration(stack, entity) - remaining;
        Palette palette = boltPalette();
        Vec3 look = entity.getLookAngle(), right = new Vec3(-look.z, 0, look.x).normalize();
        Vec3 tip = entity.getEyePosition().add(look.scale(1.1)).add(right.scale(0.3)).add(0, -0.1, 0);
        for (int i = 0; i < 2; i++) {
            Vec3 from = tip.add((level.random.nextDouble() - 0.5) * 1.2, (level.random.nextDouble() - 0.5) * 1.2, (level.random.nextDouble() - 0.5) * 1.2);
            Vec3 v = tip.subtract(from).scale(0.2);
            level.addParticle(ModParticles.ember(palette), from.x, from.y, from.z, v.x, v.y, v.z);
        }
        if (used == chargeTicks(stack)) {
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2 * i / 12;
                Vec3 v = right.scale(Math.cos(a) * 0.08).add(0, Math.sin(a) * 0.08, 0);
                level.addParticle(ModParticles.ember(palette), tip.x, tip.y, tip.z, v.x, v.y, v.z);
            }
            entity.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.8f);
        }
    }

    /** Отпустил ПКМ: если зарядка хоть немного набрана — выпустить стрелу и заплатить за неё. */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (!(entity instanceof ServerPlayer player)) return;
        float charge = charge(stack, getUseDuration(stack, entity) - remaining);
        if (charge < 0.1f) return;
        Component problem = shotProblem(player);
        if (problem != null) {
            player.displayClientMessage(problem.copy().withStyle(ChatFormatting.RED), true);
            return;
        }
        if (PlayerStats.paysWithHealth(player)) player.setHealth(player.getHealth() - player.getMaxHealth() * SHOT_COST);
        else {
            RpgData data = RpgManager.get(player);
            data.setResource(data.getResource() - PlayerStats.maxResource(player) * SHOT_COST);
            RpgManager.sync(player);
        }
        Palette palette = boltPalette();
        MagicBoltEntity bolt = new MagicBoltEntity(level, player, boltDamage(player, stack) * charge, palette);
        bolt.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, MagicBoltEntity.SPEED, 0f);
        level.addFreshEntity(bolt);
        ServerLevel server = player.serverLevel();
        Vec3 tip = player.getEyePosition().add(player.getLookAngle().scale(1.1)).add(0, -0.1, 0);
        Fx.embers(server, palette, tip, 10, 0.05, 0.12);
        Fx.wisps(server, palette, player.getEyePosition().add(player.getLookAngle().scale(2)), 2, 0.05, 0.02);
        server.playSound(null, player.getX(), player.getY(), player.getZ(), switch (palette) {
            case FIRE -> SoundEvents.BLAZE_SHOOT;
            case FROST -> SoundEvents.PLAYER_HURT_FREEZE;
            case STORM -> SoundEvents.TRIDENT_RIPTIDE_1.value();
            case AMBER -> SoundEvents.BASALT_BREAK;
            default -> SoundEvents.AMETHYST_BLOCK_CHIME;
        }, SoundSource.PLAYERS, 0.7f, 1.5f);
        player.awardStat(Stats.ITEM_USED.get(this));
        // Взмах выстрела видят все: у посоха это одно из трёх движений (WeaponMoves на клиентах).
        if (isStaff()) player.swing(player.getUsedItemHand(), true);
    }

    /** Сила магии фокуса в руках игрока с учётом редкости; 0 — фокуса нет. */
    public static float power(Player player) {
        ItemStack focus = activeFocus(player);
        if (focus == null) return 0;
        return focus == player.getMainHandItem() ? stackPower(focus, player.getOffhandItem()) : stackPower(focus, ItemStack.EMPTY);
    }

    /** Множитель урона заклинаний мага от фокуса: 1 при силе магии 5. */
    public static float damageFactor(Player player) {
        return power(player) / REFERENCE_POWER;
    }

    /** Бонус рунного фокуса к заклинаниям его стихии: 1.15 или 1.3; для чужой стихии — 1. */
    public static float elementFactor(Player player, Element spellElement) {
        ItemStack stack = activeFocus(player);
        if (stack == null) return 1f;
        FocusItem focus = (FocusItem) stack.getItem();
        return focus.element != null && focus.element == spellElement ? 1f + focus.elementBonusPercent / 100f : 1f;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.heroicpaths.focus.power", String.format(Locale.ROOT, "%.1f",
                spellPower * (1f + ItemRarity.bonusPercent(stack) / 100f))).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (element != null) {
            tooltip.add(Component.translatable("item.heroicpaths.focus.element",
                    Component.translatable("element.heroicpaths." + element.name().toLowerCase(Locale.ROOT)), elementBonusPercent)
                    .withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable(staff ? "item.heroicpaths.focus.staff_hint" : "item.heroicpaths.focus.wand_hint")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.heroicpaths.focus.bolt", Component.translatable("item.heroicpaths.focus.bolt." + boltPalette().id()))
                .withStyle(ChatFormatting.AQUA));
    }
}
