package com.heroicpaths.village;

import com.heroicpaths.registry.ModVillagers;
import com.heroicpaths.rpg.PlayerClass;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.List;

/**
 * Связь между профессиями наставников и RPG-классами. Наставник мечников учит воинов и разбойников,
 * наставник лучников — лучников и охотников, архимаг — магов.
 */
public final class ClassMentors {
    private ClassMentors() {}

    /** Классы, которым учит наставник этой профессии; пусто — это не наставник. */
    public static List<PlayerClass> classesOf(VillagerProfession profession) {
        if (profession == ModVillagers.SWORDMASTER.get()) return List.of(PlayerClass.WARRIOR, PlayerClass.ROGUE);
        if (profession == ModVillagers.MASTER_ARCHER.get()) return List.of(PlayerClass.ARCHER, PlayerClass.HUNTER);
        if (profession == ModVillagers.ARCHMAGE.get()) return List.of(PlayerClass.MAGE);
        return List.of();
    }

    public static List<PlayerClass> classesOf(AbstractVillager villager) {
        return villager instanceof Villager v ? classesOf(v.getVillagerData().getProfession()) : List.of();
    }

    /** Житель — наставник этого класса. */
    public static boolean teaches(AbstractVillager villager, PlayerClass playerClass) {
        return playerClass != PlayerClass.NONE && classesOf(villager).contains(playerClass);
    }
}
