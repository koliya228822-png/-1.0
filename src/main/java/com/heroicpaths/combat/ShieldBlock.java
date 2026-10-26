package com.heroicpaths.combat;

import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Щит больше не останавливает удар целиком.
 * <ul>
 *     <li>щит гасит 60% урона, 40% проходит (удары, снаряды и взрывы — одинаково);</li>
 *     <li>бойцу с выносливостью каждый принятый на щит удар стоит 2 выносливости за единицу погашенного урона, но не больше 25;</li>
 *     <li>если выносливости не хватает, щит гасит лишь 30% урона;</li>
 *     <li>игрок без класса и монстры получают те же 60%, но без затрат;</li>
 *     <li>маг не меняется: его щит, как в ванилле, блокирует удар полностью.</li>
 * </ul>
 */
public final class ShieldBlock {
    /** Доля урона, которую гасит щит. */
    public static final float BLOCK_FACTOR = 0.6f;
    /** Доля урона, которую гасит щит, если выносливости не хватило. */
    public static final float TIRED_BLOCK_FACTOR = 0.3f;
    /** Выносливость за единицу погашенного урона. */
    public static final float STAMINA_PER_DAMAGE = 2f;
    /** Предел выносливости за один удар по щиту. */
    public static final float MAX_STAMINA_COST = 25f;

    private ShieldBlock() {}

    /** Маг держит щит по-старому: полный блок. */
    public static boolean blocksFully(LivingEntity entity) {
        return entity instanceof Player player && RpgManager.get(player).getPlayerClass() == PlayerClass.MAGE;
    }

    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (!event.getBlocked() || event.getEntity().level().isClientSide() || blocksFully(event.getEntity())) return;
        float damage = event.getOriginalBlockedDamage();
        float factor = BLOCK_FACTOR;
        if (event.getEntity() instanceof ServerPlayer player && Stamina.uses(player)) {
            float cost = Math.min(MAX_STAMINA_COST, damage * BLOCK_FACTOR * STAMINA_PER_DAMAGE);
            if (!Stamina.spend(player, cost)) factor = TIRED_BLOCK_FACTOR;
        }
        event.setBlockedDamage(damage * factor);
    }
}
