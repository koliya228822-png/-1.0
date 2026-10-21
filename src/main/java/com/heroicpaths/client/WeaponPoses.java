package com.heroicpaths.client;

import com.heroicpaths.combat.WeaponType;
import com.heroicpaths.item.FocusItem;
import com.heroicpaths.item.HandCrossbowItem;
import com.heroicpaths.item.MeleeWeaponItem;
import com.heroicpaths.item.MusketItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Как персонаж держит оружие и бьёт им.
 * <ul>
 *   <li>Двуручник — обеими руками у пояса; удар — замах над головой и рубящий удар обеими руками.</li>
 *   <li>Копьё и боевой молот — в стойке древко стоит вертикально до пола; копьё колет выпадом вперёд,
 *       молот бьёт сверху вниз обеими руками (положение оружия в руке плавно меняется — PolearmModels).</li>
 *   <li>Посох — вертикально, нижним концом на полу.</li>
 *   <li>Ручной арбалет — одной рукой, как пистолет: в покое стволом вниз-вперёд, заряженный — на прицеле.</li>
 *   <li>Мушкет — как ружьё: обе руки, приклад к плечу.</li>
 * </ul>
 * Новые позы — значения ArmPose через META-INF/enumextensions.json. Слот второй руки при двуручном оружии закрыт.
 */
public final class WeaponPoses {
    public static final EnumProxy<HumanoidModel.ArmPose> TWO_HANDED =
            new EnumProxy<>(HumanoidModel.ArmPose.class, true, (IArmPoseTransformer) WeaponPoses::twoHanded);
    public static final EnumProxy<HumanoidModel.ArmPose> POLEARM =
            new EnumProxy<>(HumanoidModel.ArmPose.class, true, (IArmPoseTransformer) WeaponPoses::polearm);
    public static final EnumProxy<HumanoidModel.ArmPose> STAFF =
            new EnumProxy<>(HumanoidModel.ArmPose.class, false, (IArmPoseTransformer) WeaponPoses::staff);
    public static final EnumProxy<HumanoidModel.ArmPose> PISTOL =
            new EnumProxy<>(HumanoidModel.ArmPose.class, false, (IArmPoseTransformer) WeaponPoses::pistol);
    /** Жезл или посох заряжает магическую стрелу: рука вытянута к цели (у посоха вторая рука поддерживает). */
    public static final EnumProxy<HumanoidModel.ArmPose> CHANNEL =
            new EnumProxy<>(HumanoidModel.ArmPose.class, false, (IArmPoseTransformer) WeaponPoses::channel);

    private WeaponPoses() {}

    enum Grip {
        GREAT, SPEAR, HAMMER
    }

    static Grip grip(ItemStack stack) {
        if (!(stack.getItem() instanceof MeleeWeaponItem weapon) || !weapon.isTwoHanded()) return null;
        return weapon.weaponType() == WeaponType.PIERCING ? Grip.SPEAR : weapon.weaponType() == WeaponType.CRUSHING ? Grip.HAMMER : Grip.GREAT;
    }

    private static float ease(float k) {
        k = Mth.clamp(k, 0, 1);
        return k * k * (3 - 2 * k);
    }

    /** Наклон рук при рубящем ударе: замах над головой, удар вниз, возврат в стойку. */
    static float chop(float p, float base) {
        if (p < 0.3F) return Mth.lerp(ease(p / 0.3F), base, -2.7F);
        if (p < 0.62F) return Mth.lerp(ease((p - 0.3F) / 0.32F), -2.7F, -0.25F);
        return Mth.lerp(ease((p - 0.62F) / 0.38F), -0.25F, base);
    }

    private static ItemStack held(LivingEntity entity, HumanoidArm arm) {
        return entity.getItemInHand(arm == entity.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
    }

    // ---------------------------------------------------------------- позы от третьего лица

    private static void twoHanded(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        float p = model.attackTime;
        // Свой удар двумя руками вместо ванильного взмаха одной.
        model.attackTime = 0;
        boolean right = arm == HumanoidArm.RIGHT;
        ModelPart main = right ? model.rightArm : model.leftArm, off = right ? model.leftArm : model.rightArm;
        float side = right ? 1 : -1, yaw = model.head.yRot * 0.35F;
        float x = p > 0 ? chop(p, -0.6F) : -0.6F + model.head.xRot * 0.3F;
        main.xRot = x;
        main.yRot = -0.38F * side + yaw;
        main.zRot = 0;
        off.xRot = x + 0.05F;
        off.yRot = 0.78F * side + yaw;
        off.zRot = 0;
    }

    /** Насколько копьё или молот в положении удара: быстро входит в удар и плавно возвращается в стойку. */
    static float attackWeight(float p) {
        if (p <= 0) return 0;
        if (p < 0.12F) return ease(p / 0.12F);
        return p > 0.8F ? ease((1 - p) / 0.2F) : 1;
    }

    private static void polearm(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        float p = model.attackTime;
        model.attackTime = 0;
        boolean right = arm == HumanoidArm.RIGHT;
        ModelPart main = right ? model.rightArm : model.leftArm, off = right ? model.leftArm : model.rightArm;
        float side = right ? 1 : -1;
        Grip g = grip(held(entity, arm));
        // Стойка: древко вертикально, нижний конец на полу; рука чуть вперёд, вторая свободна.
        // В удар и обратно руки переходят плавно — вместе с положением оружия в руке (PolearmModels).
        float w = g == null ? 0 : attackWeight(p), mx, my, ox, oy, mz = 0, oz = 0;
        if (g == Grip.SPEAR) {
            // Выпад: обе руки вперёд, копьё уходит вперёд и возвращается.
            float k = Mth.sin(p * Mth.PI), aim = model.head.xRot * 0.6F, yaw = model.head.yRot * 0.6F;
            mx = -1.45F + aim;
            my = -0.12F * side + yaw;
            ox = -1.35F + aim;
            oy = 0.5F * side + yaw;
            mz = -3.5F * k;
            oz = -3.0F * k;
        } else {
            // Молот: замах над головой и удар сверху вниз обеими руками.
            float x = chop(p, -0.35F);
            mx = x;
            my = -0.3F * side;
            ox = x + 0.05F;
            oy = 0.7F * side;
        }
        main.xRot = Mth.lerp(w, -0.35F, mx);
        main.yRot = Mth.lerp(w, 0, my);
        main.zRot = 0;
        main.z = mz * w;
        if (w <= 0) return;
        off.xRot = Mth.lerp(w, off.xRot, ox);
        off.yRot = Mth.lerp(w, off.yRot, oy);
        off.zRot = Mth.lerp(w, off.zRot, 0);
        off.z = oz * w;
    }
    private static void staff(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        // Во время удара — обычный взмах; в покое посох стоит на полу.
        if (model.attackTime > 0) return;
        ModelPart main = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        main.xRot = -0.35F;
        main.yRot = 0;
        main.zRot = 0;
    }

    private static void channel(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        boolean right = arm == HumanoidArm.RIGHT;
        ModelPart main = right ? model.rightArm : model.leftArm, off = right ? model.leftArm : model.rightArm;
        float side = right ? 1 : -1;
        main.xRot = -Mth.HALF_PI + model.head.xRot;
        main.yRot = model.head.yRot - 0.05F * side;
        main.zRot = 0;
        if (held(entity, arm).getItem() instanceof FocusItem focus && focus.isStaff() && held(entity, arm.getOpposite()).isEmpty()) {
            off.xRot = -Mth.HALF_PI + 0.35F + model.head.xRot;
            off.yRot = 0.55F * side + model.head.yRot;
            off.zRot = 0;
        }
    }

    /** Жезл или посох в руке этого существа сейчас заряжается. */
    private static boolean channeling(LivingEntity entity, ItemStack stack) {
        return entity.isUsingItem() && entity.getUseItem() == stack;
    }

    /** От первого лица при зарядке фокус поднимается и выдвигается к прицелу; на полном заряде дрожит от силы. */
    static boolean chargeTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partialTick, float equip) {
        if (!channeling(player, stack)) return false;
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float t = player.getTicksUsingItem() + partialTick, k = ease(t / FocusItem.CHARGE_TICKS);
        pose.translate(side * 0.56F, -0.52F + equip * -0.6F, -0.72F);
        pose.translate(-side * 0.22F * k, 0.18F * k, -0.12F * k);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-28 * k));
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(side * 12 * k));
        if (t >= FocusItem.CHARGE_TICKS) pose.translate(Mth.sin(t * 2.3F) * 0.004F, Mth.cos(t * 1.9F) * 0.004F, 0);
        return true;
    }

    private static void pistol(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        boolean right = arm == HumanoidArm.RIGHT;
        ModelPart main = right ? model.rightArm : model.leftArm;
        float side = right ? 1 : -1;
        if (CrossbowItem.isCharged(held(entity, arm))) {
            main.xRot = -Mth.HALF_PI + model.head.xRot;
            main.yRot = model.head.yRot - 0.05F * side;
        } else {
            main.xRot = -0.85F;
            main.yRot = -0.1F * side;
        }
        main.zRot = 0;
    }

    public static void registerExtensions(RegisterClientExtensionsEvent event) {
        List<Item> twoHanded = new ArrayList<>(), staves = new ArrayList<>(), wands = new ArrayList<>(), pistols = new ArrayList<>(), muskets = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof MeleeWeaponItem weapon && weapon.isTwoHanded()) twoHanded.add(item);
            if (item instanceof FocusItem focus) (focus.isStaff() ? staves : wands).add(item);
            if (item instanceof HandCrossbowItem) pistols.add(item);
            if (item instanceof MusketItem) muskets.add(item);
        }
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                if (hand != InteractionHand.MAIN_HAND) return null;
                return grip(stack) == Grip.GREAT ? TWO_HANDED.getValue() : POLEARM.getValue();
            }

            @Override
            public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                                   float partialTick, float equip, float swing) {
                Hold h = hold(grip(stack), swing, dip(stack, player, equip), arm == HumanoidArm.RIGHT ? 1 : -1);
                pose.translate(h.grip.x, h.grip.y, h.grip.z);
                pose.mulPose(h.rot);
                pose.scale(h.scale, h.scale, h.scale);
                return true;
            }
        }, twoHanded.toArray(Item[]::new));
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return channeling(entity, stack) ? CHANNEL.getValue() : STAFF.getValue();
            }

            @Override
            public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                                   float partialTick, float equip, float swing) {
                return chargeTransform(pose, player, arm, stack, partialTick, equip);
            }
        }, staves.toArray(Item[]::new));
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return channeling(entity, stack) ? CHANNEL.getValue() : null;
            }

            @Override
            public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                                   float partialTick, float equip, float swing) {
                return chargeTransform(pose, player, arm, stack, partialTick, equip);
            }
        }, wands.toArray(Item[]::new));
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                // Пока заряжается — обычная ванильная зарядка арбалета.
                return entity.isUsingItem() && entity.getUseItem() == stack ? null : PISTOL.getValue();
            }
        }, pistols.toArray(Item[]::new));
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }
        }, muskets.toArray(Item[]::new));
    }

    // ---------------------------------------------------------------- от первого лица

    private static final float DEG = Mth.DEG_TO_RAD;

    /**
     * Хват от первого лица (для правой руки; для левой — зеркально): где рукоять относительно камеры, как повёрнуто
     * оружие (+Y модели — к острию), масштаб и где на рукояти кулаки (в пикселях модели от точки хвата).
     * Модель оружия от первого лица лишь приводит точку хвата в начало координат — всё остальное здесь.
     */
    static final class Hold {
        final Vector3f grip = new Vector3f();
        Quaternionf rot = new Quaternionf();
        float scale = 0.85F, mainAt, offAt;

        Vector3f at(float px) {
            return rot.transform(new Vector3f(0, px * scale / 16, 0)).add(grip);
        }
    }

    /** Наклон влево (lean), от себя (forward, назад — меньше нуля) и поворот вокруг своей оси (roll), в градусах. */
    private static Quaternionf tilt(float lean, float forward, float roll) {
        return new Quaternionf().rotationZ(lean * DEG).rotateX(-forward * DEG).rotateY(roll * DEG);
    }

    /** Оружие из точки from остриём в точку to. */
    private static Quaternionf toward(Vector3f from, Vector3f to, float roll) {
        Vector3f d = new Vector3f(to).sub(from).normalize();
        return new Quaternionf().rotationTo(0, 1, 0, d.x, d.y, d.z).rotateY(roll * DEG);
    }

    private static void blend(Hold h, float k, Vector3f p0, Quaternionf q0, Vector3f p1, Quaternionf q1) {
        p0.lerp(p1, k, h.grip);
        h.rot = new Quaternionf(q0).slerp(q1, k);
    }

    private static Vector3f v(double x, double y, double z) {
        return new Vector3f((float) x, (float) y, (float) z);
    }

    /**
     * Стойка и удар: двуручник и молот рубят из-за плеча наискось, копьё колет в прицел.
     * dip — насколько опущено оружие (смена предмета или восстановление после удара).
     */
    static Hold hold(Grip g, float p, float dip, int side) {
        Hold h = new Hold();
        if (g == Grip.SPEAR) {
            // Заднюю часть древка держит основная рука, переднюю — вторая.
            h.scale = 1F;
            h.mainAt = -5F;
            h.offAt = 5F;
            Vector3f idle = v(0.56, -0.5, -1.05), out = v(0.42, -0.42, -1.7);
            Quaternionf qi = toward(idle, v(-1.0, 1.7, -5), 0), qo = toward(out, v(0, 0.1, -8), 0);
            float k = p <= 0 ? 0 : p < 0.3F ? 1 - (1 - p / 0.3F) * (1 - p / 0.3F) : 1 - ease((p - 0.3F) / 0.7F);
            blend(h, k, idle, qi, out, qo);
        } else {
            boolean hammer = g == Grip.HAMMER;
            // Молот держат ближе к середине древка, чтобы руки были видны под бойком.
            // Двуручник — правая рука у гарды, левая у навершия; клинок вперёд-вверх с лёгким наклоном вправо,
            // рукоять ниже и ближе к середине экрана, чтобы оба кулака обхватывали её поперёк.
            h.scale = hammer ? 0.85F : 1.1F;
            h.mainAt = hammer ? 5.5F : 2.6F;
            h.offAt = hammer ? -0.5F : -3.2F;
            float roll = -70;
            // Замах за правое плечо, удар наискось справа налево через весь экран.
            Vector3f idle = hammer ? v(0.58, -0.52, -1.1) : v(0.25, -0.28, -0.95), wind = v(0.7, -0.3, -1.0), hit = v(0.2, -0.45, -1.25);
            Quaternionf qi = hammer ? tilt(25, 18, roll) : tilt(-12, 45, roll), qw = tilt(-25, -40, roll), qh = tilt(100, 30, roll);
            if (p < 0.3F) blend(h, ease(p / 0.3F), idle, qi, wind, qw);
            else if (p < 0.62F) blend(h, ease((p - 0.3F) / 0.32F), wind, qw, hit, qh);
            else blend(h, ease((p - 0.62F) / 0.38F), hit, qh, idle, qi);
        }
        h.grip.y -= dip;
        if (side < 0) {
            h.grip.x = -h.grip.x;
            h.rot = new Quaternionf(h.rot.x, -h.rot.y, -h.rot.z, h.rot.w);
        }
        return h;
    }

    /** Ход удара и опускание основной руки за этот кадр — их видно только в событии для основной руки. */
    private static float mainSwing, mainDip;

    /**
     * На сколько опустить оружие: при смене предмета — полностью, как в ванильной игре; после удара — чуть-чуть
     * (ванильно тяжёлое оружие надолго уходит вниз, а с руками на рукояти это выглядит как сломанная анимация).
     */
    static float dip(ItemStack shown, Player player, float equip) {
        return equip * (ItemStack.isSameItem(shown, player.getMainHandItem()) ? 0.12F : 0.6F);
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /** Вместо пустой второй руки рисуем обе руки на рукояти двуручного оружия — с тем же движением, что у оружия. */
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            mainSwing = event.getSwingProgress();
            if (mc().player != null) mainDip = dip(event.getItemStack(), mc().player, event.getEquipProgress());
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isInvisible() || !event.getItemStack().isEmpty()) return;
        Grip g = grip(player.getMainHandItem());
        if (g == null) return;
        if (!(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;
        HumanoidArm main = player.getMainArm();
        int side = main == HumanoidArm.RIGHT ? 1 : -1;
        Hold h = hold(g, mainSwing, mainDip, side);
        // Плечи — за нижним краем экрана: основное сбоку, второе ближе к середине (рука тянется поперёк тела).
        // У двуручника плечи ближе к камере: предплечья идут почти горизонтально вперёд, и рукоять проходит через кулаки поперёк,
        // а не упирается в кулак снизу.
        Vector3f mainShoulder = g == Grip.GREAT ? v(0.75 * side, -0.55, -0.05) : v(0.7 * side, -0.95, -0.35);
        Vector3f offShoulder = g == Grip.GREAT ? v(-0.3 * side, -0.62, -0.05) : v(-0.35 * side, -1.0, -0.4);
        PoseStack pose = event.getPoseStack();
        arm(pose, event, renderer, player, main, h.at(h.mainAt), mainShoulder);
        arm(pose, event, renderer, player, main.getOpposite(), h.at(h.offAt), offShoulder);
    }

    /** Рука кулаком в точке fist, вытянута от плеча; повёрнута как пустая рука в ванильной игре, чтобы выглядеть привычно. */
    private static void arm(PoseStack pose, RenderHandEvent event, PlayerRenderer renderer, AbstractClientPlayer player, HumanoidArm side,
                            Vector3f fist, Vector3f shoulder) {
        boolean right = side == HumanoidArm.RIGHT;
        float f = right ? 1 : -1;
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        ModelPart part = right ? model.rightArm : model.leftArm, sleeve = right ? model.rightSleeve : model.leftSleeve;
        PartPose partPose = part.storePose(), sleevePose = sleeve.storePose();
        boolean partVisible = part.visible, sleeveVisible = sleeve.visible;
        part.resetPose();
        sleeve.resetPose();
        part.visible = true;
        sleeve.visible = player.isModelPartShown(right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE);
        Quaternionf rot = new Quaternionf().rotationY(f * 45 * DEG).rotateZ(f * 120 * DEG).rotateX(200 * DEG).rotateY(f * -135 * DEG);
        Vector3f along = rot.transform(new Vector3f(0, 1, 0)), want = new Vector3f(fist).sub(shoulder).normalize();
        rot.premul(new Quaternionf().rotationTo(along, want));
        float cx = -f * (player.getSkin().model() == PlayerSkin.Model.SLIM ? 0.5F : 1F);
        pose.pushPose();
        pose.translate(fist.x, fist.y, fist.z);
        pose.mulPose(rot);
        pose.translate(-(part.x + cx) / 16, -(part.y + 8.5F) / 16, -part.z / 16);
        ResourceLocation skin = player.getSkin().texture();
        MultiBufferSource buffers = event.getMultiBufferSource();
        part.render(pose, buffers.getBuffer(RenderType.entitySolid(skin)), event.getPackedLight(), OverlayTexture.NO_OVERLAY);
        if (sleeve.visible)
            sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(skin)), event.getPackedLight(), OverlayTexture.NO_OVERLAY);
        pose.popPose();
        part.loadPose(partPose);
        sleeve.loadPose(sleevePose);
        part.visible = partVisible;
        sleeve.visible = sleeveVisible;
    }
    // ---------------------------------------------------------------- инвентарь

    private static Slot offhandSlot(AbstractContainerScreen<?> screen, Player player) {
        for (Slot slot : screen.getMenu().slots)
            if (slot.container == player.getInventory() && slot.getContainerSlot() == Inventory.SLOT_OFFHAND) return slot;
        return null;
    }

    private static boolean locked(Player player) {
        return player != null && WeaponType.isTwoHanded(player.getMainHandItem());
    }

    /** Замок поверх слота второй руки и подсказка при наведении. */
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        Player player = Minecraft.getInstance().player;
        if (!locked(player)) return;
        Slot slot = offhandSlot(screen, player);
        if (slot == null || !slot.isActive()) return;
        GuiGraphics g = event.getGuiGraphics();
        int x = screen.getGuiLeft() + slot.x, y = screen.getGuiTop() + slot.y;
        g.pose().pushPose();
        g.pose().translate(0, 0, 350);
        g.fill(x, y, x + 16, y + 16, 0xB0201010);
        int gold = 0xFFE8C05A, dark = 0xFF5A3A10;
        g.fill(x + 5, y + 2, x + 11, y + 3, gold);
        g.fill(x + 4, y + 3, x + 5, y + 7, gold);
        g.fill(x + 11, y + 3, x + 12, y + 7, gold);
        g.fill(x + 3, y + 7, x + 13, y + 14, gold);
        g.fill(x + 3, y + 13, x + 13, y + 14, dark);
        g.fill(x + 7, y + 9, x + 9, y + 12, dark);
        g.pose().popPose();
        if (screen.getSlotUnderMouse() == slot)
            g.renderTooltip(Minecraft.getInstance().font, Component.translatable("tooltip.heroicpaths.offhand_locked"), event.getMouseX(), event.getMouseY());
    }

    /** В закрытый слот ничего не положить. */
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        Player player = Minecraft.getInstance().player;
        if (!locked(player)) return;
        Slot slot = offhandSlot(screen, player);
        if (slot != null && screen.getSlotUnderMouse() == slot) event.setCanceled(true);
    }
}
