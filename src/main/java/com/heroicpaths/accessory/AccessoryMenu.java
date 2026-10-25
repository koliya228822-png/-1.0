package com.heroicpaths.accessory;

import com.heroicpaths.registry.ModMenus;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Окно аксессуаров: 8 слотов (перчатки, пояс, наручи, ожерелье, две серьги, два кольца) и инвентарь игрока.
 * На сервере слоты — сами аксессуары игрока, на клиенте — копия, которую заполняет синхронизация окна.
 */
public class AccessoryMenu extends AbstractContainerMenu {
    /** Где стоят слоты аксессуаров в окне: две колонки по четыре. */
    public static final int[][] SLOT_POS = {{26, 18}, {26, 36}, {26, 54}, {26, 72}, {134, 18}, {134, 36}, {134, 54}, {134, 72}};
    private final Player player;

    /** Клиент. */
    public AccessoryMenu(int id, Inventory inventory) {
        this(id, inventory, new ItemStackHandler(Accessories.SIZE));
    }

    private AccessoryMenu(int id, Inventory inventory, IItemHandler accessories) {
        super(ModMenus.ACCESSORIES.get(), id);
        this.player = inventory.player;
        for (int i = 0; i < Accessories.SIZE; i++) {
            int slot = i;
            addSlot(new SlotItemHandler(accessories, i, SLOT_POS[i][0], SLOT_POS[i][1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return Accessories.fits(slot, stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public void setChanged() {
                    super.setChanged();
                    if (player instanceof ServerPlayer serverPlayer) AccessoryEvents.changed(serverPlayer);
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 102 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 160));
    }

    public static void open(ServerPlayer player) {
        Accessories accessories = RpgManager.get(player).accessories();
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new AccessoryMenu(id, inventory, accessories.handler()),
                Component.translatable("screen.heroicpaths.accessories")));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < Accessories.SIZE) {
            if (!moveItemStackTo(stack, Accessories.SIZE, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < Accessories.SIZE && !moved; i++) {
                Slot target = slots.get(i);
                if (!target.hasItem() && target.mayPlace(stack)) {
                    target.setByPlayer(stack.split(1));
                    moved = true;
                }
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }
}
