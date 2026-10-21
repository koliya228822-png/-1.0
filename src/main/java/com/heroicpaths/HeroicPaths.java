package com.heroicpaths;

import com.heroicpaths.combat.WeaponCombat;
import com.heroicpaths.command.PartyCommand;
import com.heroicpaths.registry.ModEffects;
import com.heroicpaths.command.RpgCommand;
import com.heroicpaths.gear.GearEvents;
import com.heroicpaths.network.ModNetwork;
import com.heroicpaths.dungeon.DungeonManager;
import com.heroicpaths.dungeon.DungeonRules;
import com.heroicpaths.party.PartyManager;
import com.heroicpaths.registry.ModLoot;
import com.heroicpaths.registry.ModRecipes;
import com.heroicpaths.item.ItemRarity;
import com.heroicpaths.registry.ModBlockEntities;
import com.heroicpaths.registry.ModArmorMaterials;
import com.heroicpaths.registry.ModAttachments;
import com.heroicpaths.registry.ModComponents;
import com.heroicpaths.registry.ModBlocks;
import com.heroicpaths.registry.ModCreativeTabs;
import com.heroicpaths.registry.ModEntities;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.registry.ModVillagers;
import com.heroicpaths.rpg.RpgEvents;
import com.heroicpaths.rpg.ability.AbilityLogic;
import com.heroicpaths.rpg.ability.DelayedTasks;
import com.heroicpaths.village.MentorEvents;
import com.heroicpaths.village.MentorTrades;
import com.heroicpaths.village.VillageInjector;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(HeroicPaths.MODID)
public class HeroicPaths {
    public static final String MODID = "heroicpaths";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HeroicPaths(IEventBus modBus) {
        ModBlocks.BLOCKS.register(modBus);
        ModComponents.COMPONENTS.register(modBus);
        ModArmorMaterials.ARMOR_MATERIALS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModEntities.ENTITY_TYPES.register(modBus);
        com.heroicpaths.registry.ModParticles.PARTICLES.register(modBus);
        ModAttachments.ATTACHMENT_TYPES.register(modBus);
        ModVillagers.POI_TYPES.register(modBus);
        ModVillagers.PROFESSIONS.register(modBus);
        ModCreativeTabs.CREATIVE_TABS.register(modBus);
        ModRecipes.SERIALIZERS.register(modBus);
        ModRecipes.TYPES.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        ModLoot.FUNCTIONS.register(modBus);
        modBus.addListener(ModNetwork::registerPayloads);
        modBus.addListener(ModEntities::registerAttributes);

        NeoForge.EVENT_BUS.register(RpgEvents.class);
        NeoForge.EVENT_BUS.register(MentorEvents.class);
        NeoForge.EVENT_BUS.register(ItemRarity.class);
        NeoForge.EVENT_BUS.register(com.heroicpaths.craft.CraftEvents.class);
        NeoForge.EVENT_BUS.register(com.heroicpaths.craft.SoulStones.class);
        NeoForge.EVENT_BUS.register(DungeonManager.class);
        NeoForge.EVENT_BUS.register(DungeonRules.class);
        NeoForge.EVENT_BUS.register(PartyManager.class);
        NeoForge.EVENT_BUS.register(GearEvents.class);
        NeoForge.EVENT_BUS.register(WeaponCombat.class);
        NeoForge.EVENT_BUS.register(com.heroicpaths.combat.TwoHandedGrip.class);
        NeoForge.EVENT_BUS.register(com.heroicpaths.rpg.ClassTraits.class);
        NeoForge.EVENT_BUS.register(com.heroicpaths.guild.GuildOrders.class);
        NeoForge.EVENT_BUS.register(com.heroicpaths.combat.Finishers.class);
        NeoForge.EVENT_BUS.register(com.heroicpaths.minion.CompanionManager.class);
        NeoForge.EVENT_BUS.register(com.heroicpaths.minion.MinionEvents.class);
        NeoForge.EVENT_BUS.addListener(RpgCommand::register);
        NeoForge.EVENT_BUS.addListener(PartyCommand::register);
        NeoForge.EVENT_BUS.addListener(MentorTrades::onVillagerTrades);
        NeoForge.EVENT_BUS.addListener(VillageInjector::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(DelayedTasks::onServerTick);
        NeoForge.EVENT_BUS.addListener(DelayedTasks::onServerStopped);
        // Касты сильных умений и метки ударов по площади.
        NeoForge.EVENT_BUS.addListener(com.heroicpaths.rpg.ability.Casting::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.heroicpaths.rpg.ability.Casting::onServerStopped);
        NeoForge.EVENT_BUS.addListener(com.heroicpaths.rpg.ability.Zones::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.heroicpaths.rpg.ability.Zones::onServerStopped);
        NeoForge.EVENT_BUS.addListener(AbilityLogic::onProjectileImpact);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
