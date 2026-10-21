package com.heroicpaths.client.armor;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.item.ClassArmorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Объёмная классовая броня: для каждого комплекта и слота — своя модель из деталей ArmorShapes
 * (колпак мага, капюшон лучника, шлем с гребнем мечника и т. д.) вместо плоских слоёв ванильной брони.
 */
public final class ArmorModels {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Map<String, HumanoidModel<LivingEntity>> CACHE = new HashMap<>();

    private ArmorModels() {}

    private static String slotName(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "head";
            case CHEST -> "chest";
            case LEGS -> "legs";
            default -> "feet";
        };
    }

    public static ModelLayerLocation layer(String set, EquipmentSlot slot) {
        return new ModelLayerLocation(HeroicPaths.id("armor3d/" + set), slotName(slot));
    }

    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (String set : ArmorShapes.SETS)
            for (EquipmentSlot slot : SLOTS)
                if (ArmorShapes.get(set + "/" + slotName(slot)) != null)
                    event.registerLayerDefinition(layer(set, slot), () -> create(set, slot));
    }

    /** Модель брони подменяется для всей классовой брони: у игроков, мобов и стоек. */
    public static void registerExtensions(RegisterClientExtensionsEvent event) {
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) if (item instanceof ClassArmorItem) items.add(item);
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                if (!(stack.getItem() instanceof ClassArmorItem armor)) return original;
                HumanoidModel<?> model = model(armor.setName(), slot);
                return model == null ? original : model;
            }

            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public Model getGenericArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                HumanoidModel<?> model = getHumanoidArmorModel(entity, stack, slot, original);
                if (model == original) return original;
                ClientHooks.copyModelProperties((HumanoidModel) original, model);
                // Когда рука согнута в локте, рукава брони рисует слой согнутых рук — здесь они прямые и не нужны.
                if (com.heroicpaths.client.BentArms.active(entity)) {
                    model.rightArm.visible = false;
                    model.leftArm.visible = false;
                }
                return model;
            }
        }, items.toArray(Item[]::new));
    }

    /** Детали брони на руке (прямые потомки кости руки) и высота их середины от плеча — для согнутой руки. */
    public static List<Map.Entry<ModelPart, Float>> armPieces(ClassArmorItem armor, boolean right) {
        float[][] data = ArmorShapes.get(armor.setName() + "/chest");
        HumanoidModel<?> model = model(armor.setName(), EquipmentSlot.CHEST);
        List<Map.Entry<ModelPart, Float>> out = new ArrayList<>();
        if (data == null || model == null) return out;
        ModelPart bone = right ? model.rightArm : model.leftArm;
        for (int i = 0; i < data.length; i++)
            if (data[i][1] < 0 && (int) data[i][0] == (right ? 2 : 3)) out.add(Map.entry(bone.getChild("p" + i), data[i][3] + data[i][11] + data[i][14] / 2));
        return out;
    }

    private static HumanoidModel<?> model(String set, EquipmentSlot slot) {
        String key = set + "/" + slotName(slot);
        if (ArmorShapes.get(key) == null) return null;
        return CACHE.computeIfAbsent(key, k -> new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(layer(set, slot))));
    }

    /**
     * Сетка модели: шесть костей гуманоида с обычными точками поворота, к ним — детали из ArmorShapes.
     * У каждой детали своя часть с поворотом; детали можно вешать друг на друга (загнутый кончик колпака).
     */
    static LayerDefinition create(String set, EquipmentSlot slot) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition[] bones = {
                root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO),
                root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO),
                root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5, 2, 0)),
                root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5, 2, 0)),
                root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12, 0)),
                root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12, 0))};
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        float[][] data = ArmorShapes.get(set + "/" + slotName(slot));
        List<PartDefinition> parts = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            float[] e = data[i];
            PartDefinition parent = e[1] < 0 ? bones[(int) e[0]] : parts.get((int) e[1]);
            CubeListBuilder cube = CubeListBuilder.create().texOffs((int) e[8], (int) e[9]);
            if (e[17] > 0) cube.mirror();
            cube.addBox(e[10], e[11], e[12], e[13], e[14], e[15], new CubeDeformation(e[16]));
            parts.add(parent.addOrReplaceChild("p" + i, cube, PartPose.offsetAndRotation(e[2], e[3], e[4], e[5], e[6], e[7])));
        }
        return LayerDefinition.create(mesh, ArmorShapes.TEX_W, ArmorShapes.TEX_H);
    }
}
