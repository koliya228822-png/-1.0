package com.heroicpaths.client;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.combat.WeaponType;
import com.heroicpaths.item.Grades;
import net.minecraft.world.item.ItemStack;
import com.heroicpaths.item.ItemRarity;
import com.heroicpaths.registry.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.core.component.DataComponents;
import com.heroicpaths.item.HandCrossbowItem;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import com.heroicpaths.network.UseAbilityPayload;
import com.heroicpaths.registry.ModEntities;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.HoglinRenderer;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.client.renderer.entity.PolarBearRenderer;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** Клиентская точка входа. Не загружается на выделенном сервере. */
@Mod(value = HeroicPaths.MODID, dist = Dist.CLIENT)
public class HeroicPathsClient {
    public HeroicPathsClient(IEventBus modBus, ModContainer container) {
        // Настройки мода: размер панели и клавиши; открываются из меню K и из списка модов.
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));
        modBus.addListener(HeroicPathsClient::onRegisterKeys);
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) ->
                event.register(com.heroicpaths.registry.ModMenus.ACCESSORIES.get(), AccessoryScreen::new));
        modBus.addListener(HeroicPathsClient::onRegisterGuiLayers);
        modBus.addListener(HeroicPathsClient::onRegisterRenderers);
        modBus.addListener(HeroicPathsClient::onClientSetup);
        modBus.addListener(HeroicPathsClient::onRegisterDecorations);
        modBus.addListener(HeroicPathsClient::onRegisterParticles);
        // Объёмная классовая броня.
        modBus.addListener(com.heroicpaths.client.armor.ArmorModels::registerLayers);
        modBus.addListener(com.heroicpaths.client.clay.ClayRenderers::registerLayers);
        modBus.addListener(com.heroicpaths.client.clay.ClayRenderers::registerRenderers);
        modBus.addListener(com.heroicpaths.client.spider.SpiderRenderers::registerLayers);
        modBus.addListener(com.heroicpaths.client.spider.SpiderRenderers::registerRenderers);
        modBus.addListener(GuardRenderer::registerLayers);
        modBus.addListener(GuardRenderer::registerRenderers);
        modBus.addListener(com.heroicpaths.client.armor.ArmorModels::registerExtensions);
        // Двуручное оружие: поза обеих рук, руки на рукояти от первого лица, закрытый слот второй руки.
        modBus.addListener(WeaponPoses::registerExtensions);
        // Копьё и молот плавно переходят из стойки (древко на полу) в удар и обратно.
        modBus.addListener(PolearmModels::registerAdditional);
        modBus.addListener(PolearmModels::modifyBaking);
        // Длинный меч и парные кинжалы: стойка, второй кинжал и три вида ударов вперемешку.
        modBus.addListener(WeaponMoves::modifyBaking);
        modBus.addListener(WeaponMoves::addLayers);
        // Руки сгибаются в локтях, пока в руке оружие с особыми ударами.
        modBus.addListener(BentArms::addLayers);
        // Светящиеся руны на броне мага III.
        modBus.addListener(com.heroicpaths.client.armor.ArmorGlow::addLayers);
        NeoForge.EVENT_BUS.addListener(BentArms::onRenderPre);
        NeoForge.EVENT_BUS.addListener(WeaponMoves::onEntityTick);
        NeoForge.EVENT_BUS.addListener(WeaponMoves::onRenderPre);
        NeoForge.EVENT_BUS.addListener(WeaponMoves::onRenderPost);
        NeoForge.EVENT_BUS.addListener(WeaponPoses::onRenderHand);
        NeoForge.EVENT_BUS.addListener(WeaponPoses::onScreenRender);
        NeoForge.EVENT_BUS.addListener(WeaponPoses::onMouseClick);
        NeoForge.EVENT_BUS.addListener(HeroicPathsClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(MobPlates::onRenderLiving);
        NeoForge.EVENT_BUS.addListener(HeroicPathsClient::onTooltip);
    }

    private static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        // Под названием по порядку: редкость и её бонусы, грейд, тип урона и хват.
        int index = 1 + ItemRarity.addTooltip(stack, event.getToolTip());
        index += Grades.addTooltip(stack, event.getToolTip(), index);
        // Броня: вес и подходит ли она классу.
        index += com.heroicpaths.rpg.ArmorProficiency.addTooltip(stack, event.getToolTip(), index, event.getEntity());
        WeaponType.addTooltip(stack, event.getToolTip(), index);
    }

    /** Рамка редкости — на всех нескладываемых предметах (редкость бывает только у них). */
    private static void onRegisterDecorations(RegisterItemDecorationsEvent event) {
        for (Item item : BuiltInRegistries.ITEM) {
            if (item.getDefaultMaxStackSize() == 1) event.register(item, RarityDecorator.INSTANCE);
        }
    }

    /** Новые луки натягиваются так же, как обычный: те же свойства модели pull и pulling. */
    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (Item bow : BuiltInRegistries.ITEM.stream().filter(item -> item instanceof com.heroicpaths.item.GradedBowItem).toList()) {
                ItemProperties.register(bow, ResourceLocation.withDefaultNamespace("pull"), (stack, level, entity, seed) ->
                        entity == null || entity.getUseItem() != stack ? 0f
                                : (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20f);
                ItemProperties.register(bow, ResourceLocation.withDefaultNamespace("pulling"), (stack, level, entity, seed) ->
                        entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1f : 0f);
            }
            // Щиты с грейдом поднимаются при блоке, как обычный щит.
            for (Item item : BuiltInRegistries.ITEM)
                if (item instanceof com.heroicpaths.item.GradedShieldItem)
                    ItemProperties.register(item, ResourceLocation.withDefaultNamespace("blocking"), (stack, level, entity, seed) ->
                            entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1f : 0f);
            // Камни души светятся, когда в них есть души.
            for (Item stone : new Item[]{ModItems.LESSER_SOUL_STONE.get(), ModItems.GREATER_SOUL_STONE.get(), ModItems.ANCIENT_SOUL_STONE.get()}) {
                ItemProperties.register(stone, HeroicPaths.id("charged"), (stack, level, entity, seed) ->
                        com.heroicpaths.item.SoulStoneItem.souls(stack) > 0 ? 1f : 0f);
            }
            // Ручные арбалеты: те же свойства модели, что у обычного арбалета, но со своим временем зарядки.
            for (Item crossbow : BuiltInRegistries.ITEM.stream().filter(item -> item instanceof HandCrossbowItem).toList()) {
                ItemProperties.register(crossbow, ResourceLocation.withDefaultNamespace("pull"), (stack, level, entity, seed) ->
                        entity == null || CrossbowItem.isCharged(stack) || !(stack.getItem() instanceof HandCrossbowItem hand) ? 0f
                                : (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / (float) hand.chargeDuration(stack, entity));
                ItemProperties.register(crossbow, ResourceLocation.withDefaultNamespace("pulling"), (stack, level, entity, seed) ->
                        entity != null && entity.isUsingItem() && entity.getUseItem() == stack && !CrossbowItem.isCharged(stack) ? 1f : 0f);
                ItemProperties.register(crossbow, ResourceLocation.withDefaultNamespace("charged"), (stack, level, entity, seed) ->
                        CrossbowItem.isCharged(stack) ? 1f : 0f);
                ItemProperties.register(crossbow, ResourceLocation.withDefaultNamespace("firework"), (stack, level, entity, seed) -> {
                    ChargedProjectiles projectiles = stack.get(DataComponents.CHARGED_PROJECTILES);
                    return projectiles != null && projectiles.contains(Items.FIREWORK_ROCKET) ? 1f : 0f;
                });
            }
        });
    }

    /** Частицы умений: облачко, искра и вспышка в каждой цветовой гамме. */
    private static void onRegisterParticles(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        for (com.heroicpaths.fx.Palette palette : com.heroicpaths.fx.Palette.values())
            for (com.heroicpaths.registry.ModParticles.Kind kind : com.heroicpaths.registry.ModParticles.Kind.values())
                event.registerSpriteSet(com.heroicpaths.registry.ModParticles.get(palette, kind), sprites -> new GlowParticle.Provider(sprites, palette, kind));
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(ModKeys.OPEN_SKILLS);
        event.register(ModKeys.OPEN_ACCESSORIES);
        for (KeyMapping key : ModKeys.ABILITIES) event.register(key);
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        // Свои полоски здоровья и стамины вместо ванильных сердечек.
        event.replaceLayer(net.neoforged.neoforge.client.gui.VanillaGuiLayers.PLAYER_HEALTH, HudBars::renderHealth);
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.FOOD_LEVEL, HeroicPaths.id("stamina_bar"), HudBars::renderStamina);
        event.registerAboveAll(HeroicPaths.id("rpg_hud"), new RpgHudLayer());
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR, HeroicPaths.id("cast_bar"), new CastBarLayer());
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SPELL_PROJECTILE.get(), SpellProjectileRenderer::new);
        event.registerEntityRenderer(ModEntities.METEOR.get(), MeteorRenderer::new);
        event.registerEntityRenderer(ModEntities.MAGIC_BOLT.get(), MagicBoltRenderer::new);
        event.registerEntityRenderer(ModEntities.CRYPT_KING.get(), CryptKingRenderer::new);
        event.registerEntityRenderer(ModEntities.FLAME_TYRANT.get(), BossRenderers.FlameTyrantRenderer::new);
        event.registerEntityRenderer(ModEntities.FROST_LORD.get(), BossRenderers.FrostLordRenderer::new);
        event.registerEntityRenderer(ModEntities.DROWNED_PROPHET.get(), BossRenderers.DrownedProphetRenderer::new);
        event.registerEntityRenderer(ModEntities.LICH.get(), BossRenderers.LichRenderer::new);
        event.registerEntityRenderer(ModEntities.COMPANION_DOG.get(), WolfRenderer::new);
        event.registerEntityRenderer(ModEntities.COMPANION_WOLF.get(), WolfRenderer::new);
        event.registerEntityRenderer(ModEntities.SPIRIT_WOLF.get(), WolfRenderer::new);
        event.registerEntityRenderer(ModEntities.COMPANION_BOAR.get(), HoglinRenderer::new);
        event.registerEntityRenderer(ModEntities.COMPANION_BEAR.get(), PolarBearRenderer::new);
        event.registerEntityRenderer(ModEntities.COMPANION_OWLBEAR.get(), OwlbearRenderer::new);
        event.registerEntityRenderer(ModEntities.SUMMONED_SKELETON.get(), SkeletonRenderer::new);
        event.registerEntityRenderer(ModEntities.SUMMONED_GOLEM.get(), IronGolemRenderer::new);
        event.registerEntityRenderer(ModEntities.MUSKET_BALL.get(), ThrownItemRenderer::new);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        while (ModKeys.OPEN_SKILLS.consumeClick()) {
            minecraft.setScreen(new RpgScreen());
        }
        while (ModKeys.OPEN_ACCESSORIES.consumeClick()) {
            PacketDistributor.sendToServer(com.heroicpaths.network.OpenAccessoriesPayload.INSTANCE);
        }
        for (int slot = 0; slot < ModKeys.ABILITIES.length; slot++) {
            while (ModKeys.ABILITIES[slot].consumeClick()) {
                PacketDistributor.sendToServer(new UseAbilityPayload(slot));
            }
        }
    }
}
