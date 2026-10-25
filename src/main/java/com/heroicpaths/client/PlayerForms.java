package com.heroicpaths.client;

import com.heroicpaths.network.CombatInputPayload;
import com.heroicpaths.rpg.ability.UniqueAbilities;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Облики уникальных умений на клиенте: волколак (вместо игрока рисуется огромный волк), вихрь клинка
 * («И один в поле воин»: тело крутится, камера от третьего лица) и мгновенные выстрелы «Охотничьего азарта».
 */
public final class PlayerForms {
    private static final float WEREWOLF_SCALE = 2.2f;

    private record Form(String form, long until) {}

    private static final Map<Integer, Form> FORMS = new HashMap<>();
    private static final Map<Integer, Wolf> WOLVES = new HashMap<>();
    @Nullable
    private static CameraType savedCamera;
    private static long cameraUntil;

    private PlayerForms() {}

    public static void set(int entityId, String form, int ticks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        long now = minecraft.level.getGameTime();
        if (form.equals("camera")) {
            if (ticks > 0 && savedCamera == null) {
                savedCamera = minecraft.options.getCameraType();
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            cameraUntil = now + ticks;
            return;
        }
        if (ticks <= 0) FORMS.remove(entityId);
        else FORMS.put(entityId, new Form(form, now + ticks));
    }

    private static boolean has(Player player, String form) {
        Form current = FORMS.get(player.getId());
        return current != null && current.form.equals(form) && current.until > player.level().getGameTime();
    }

    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        PoseStack pose = event.getPoseStack();
        if (has(player, "werewolf")) {
            Wolf wolf = wolf(player);
            if (wolf == null) return;
            event.setCanceled(true);
            float partial = event.getPartialTick();
            wolf.setPos(player.getX(), player.getY(), player.getZ());
            wolf.setYRot(player.getYRot());
            wolf.yRotO = player.yRotO;
            wolf.yBodyRot = player.yBodyRot;
            wolf.yBodyRotO = player.yBodyRotO;
            wolf.yHeadRot = player.yHeadRot;
            wolf.yHeadRotO = player.yHeadRotO;
            wolf.setXRot(player.getXRot());
            wolf.xRotO = player.xRotO;
            pose.pushPose();
            pose.scale(WEREWOLF_SCALE, WEREWOLF_SCALE, WEREWOLF_SCALE);
            Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(wolf)
                    .render(wolf, player.getViewYRot(partial), partial, pose, event.getMultiBufferSource(), event.getPackedLight());
            pose.popPose();
            return;
        }
        if (has(player, "spin")) {
            // Вихрь: тело крутится вокруг своей оси (поза снимается после отрисовки игрока).
            float angle = (player.tickCount + event.getPartialTick()) * 50f;
            pose.mulPose(Axis.YP.rotationDegrees(angle));
        }
    }

    @Nullable
    private static Wolf wolf(Player player) {
        if (!(player.level() instanceof ClientLevel level)) return null;
        Wolf wolf = WOLVES.get(player.getId());
        if (wolf == null || wolf.level() != level) {
            wolf = EntityType.WOLF.create(level);
            if (wolf == null) return null;
            wolf.setRemainingPersistentAngerTime(Integer.MAX_VALUE);
            WOLVES.put(player.getId(), wolf);
        }
        return wolf;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            FORMS.clear();
            WOLVES.clear();
            return;
        }
        long now = minecraft.level.getGameTime();
        FORMS.values().removeIf(form -> form.until <= now);
        WOLVES.keySet().removeIf(id -> !FORMS.containsKey(id));
        // Волк бежит так же, как бежит игрок.
        WOLVES.forEach((id, wolf) -> {
            if (minecraft.level.getEntity(id) instanceof Player player) {
                wolf.tickCount = player.tickCount;
                double speed = player.getDeltaMovement().horizontalDistance();
                wolf.walkAnimation.update((float) Math.min(1.0, speed * 6), 0.4f);
            }
        });
        if (savedCamera != null && now >= cameraUntil) {
            minecraft.options.setCameraType(savedCamera);
            savedCamera = null;
        }
    }

    /** «Охотничий азарт»: щелчок ПКМ с луком — сразу выстрел, без натяжения. */
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || !event.isUseItem() || !(player.getItemInHand(event.getHand()).getItem() instanceof BowItem)) return;
        if (!UniqueAbilities.active(player, "hunting_frenzy")) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        PacketDistributor.sendToServer(new CombatInputPayload(CombatInputPayload.Kind.QUICK_SHOT, 0));
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        FORMS.clear();
        WOLVES.clear();
        if (savedCamera != null) {
            Minecraft.getInstance().options.setCameraType(savedCamera);
            savedCamera = null;
        }
    }
}
