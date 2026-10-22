package com.heroicpaths.client.armor;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.client.BentArms;
import com.heroicpaths.item.ClassArmorItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import java.util.Set;

/**
 * Светящиеся руны на броне: поверх обычной брони та же модель ещё раз рисуется с текстурой
 * &lt;комплект&gt;_glow.png (всё чёрное, кроме рун) режимом свечения — без теней, с лёгкой пульсацией.
 */
public final class ArmorGlow {
    /** Комплекты, у которых есть текстура свечения (её пишет tools/ArmorGen.java). */
    private static final Set<String> GLOWING = Set.of("mage_wool", "mage_astral");
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private ArmorGlow() {}

    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins())
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new Layer<>(renderer));
        if (event.getRenderer(EntityType.ARMOR_STAND) instanceof LivingEntityRenderer<?, ?> stand) addTo(stand);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addTo(LivingEntityRenderer renderer) {
        if (renderer.getModel() instanceof HumanoidModel) renderer.addLayer(new Layer(renderer));
    }

    static final class Layer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
        Layer(RenderLayerParent<T, M> parent) {
            super(parent);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible()) return;
            // Руны чуть «дышат»: яркость плавно ходит между 70% и 100%.
            float pulse = 0.85F + 0.15F * Mth.sin(ageInTicks * 0.08F);
            int c = (int) (255 * pulse);
            int color = 0xFF000000 | c << 16 | c << 8 | c;
            for (EquipmentSlot slot : SLOTS) {
                ItemStack stack = entity.getItemBySlot(slot);
                if (!(stack.getItem() instanceof ClassArmorItem armor) || !GLOWING.contains(armor.setName())) continue;
                HumanoidModel model = ArmorModels.model(armor.setName(), slot);
                if (model == null) continue;
                getParentModel().copyPropertiesTo(model);
                model.setAllVisible(false);
                switch (slot) {
                    case HEAD -> {
                        model.head.visible = true;
                        model.hat.visible = true;
                    }
                    case CHEST -> {
                        model.body.visible = true;
                        // Согнутые руки рисует BentArms — прямые светящиеся руны там были бы не на месте.
                        boolean bent = BentArms.active(entity);
                        model.rightArm.visible = !bent;
                        model.leftArm.visible = !bent;
                    }
                    case LEGS -> {
                        model.body.visible = true;
                        model.rightLeg.visible = true;
                        model.leftLeg.visible = true;
                    }
                    default -> {
                        model.rightLeg.visible = true;
                        model.leftLeg.visible = true;
                    }
                }
                ResourceLocation tex = HeroicPaths.id("textures/models/armor3d/" + armor.setName() + "_glow.png");
                model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(tex)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
            }
        }
    }
}
