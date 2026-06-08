package adris.altoclef.util.slots;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.stream.IntStream;

public class PlayerSlot extends Slot {
    public static final PlayerSlot CRAFT_OUTPUT_SLOT = new PlayerSlot(0);
    public static final PlayerSlot ARMOR_HELMET_SLOT = new PlayerSlot(5);
    public static final PlayerSlot ARMOR_CHESTPLATE_SLOT = new PlayerSlot(6);
    public static final PlayerSlot ARMOR_LEGGINGS_SLOT = new PlayerSlot(7);
    public static final PlayerSlot ARMOR_BOOTS_SLOT = new PlayerSlot(8);
    public static final PlayerSlot[] ARMOR_SLOTS = new PlayerSlot[]{
            ARMOR_HELMET_SLOT,
            ARMOR_CHESTPLATE_SLOT,
            ARMOR_LEGGINGS_SLOT,
            ARMOR_BOOTS_SLOT
    };
    public static final PlayerSlot OFFHAND_SLOT = new PlayerSlot(45);
    public static final PlayerSlot[] CRAFT_INPUT_SLOTS =
            IntStream.range(0, 4).mapToObj(PlayerSlot::getCraftInputSlot).toArray(PlayerSlot[]::new);

    public PlayerSlot(int windowSlot) {
        this(windowSlot, false);
    }

    protected PlayerSlot(int slot, boolean inventory) {
        super(slot, inventory);
    }

    public static PlayerSlot getCraftInputSlot(int x, int y) {
        return getCraftInputSlot(y * 2 + x);
    }

    public static PlayerSlot getCraftInputSlot(int index) {
        return new PlayerSlot(index + 1);
    }

    public static Slot getEquipSlot(EquipmentSlot equipSlot) {
        return switch (equipSlot) {
            case MAINHAND -> Slot.getFromCurrentScreenInventory(Minecraft.getInstance().player.getInventory().selected);
            case OFFHAND -> OFFHAND_SLOT;
            case FEET -> ARMOR_BOOTS_SLOT;
            case LEGS -> ARMOR_LEGGINGS_SLOT;
            case CHEST -> ARMOR_CHESTPLATE_SLOT;
            case HEAD -> ARMOR_HELMET_SLOT;
            case BODY -> ARMOR_CHESTPLATE_SLOT;
        };
    }

    public static Slot getEquipSlot() {
        return getEquipSlot(EquipmentSlot.MAINHAND);
    }

    @Override
    public int inventorySlotToWindowSlot(int inventorySlot) {
        if (inventorySlot < 9) {
            return inventorySlot + 36;
        }
        return inventorySlot;
    }

    @Override
    protected int windowSlotToInventorySlot(int windowSlot) {
        if (windowSlot >= 36) {
            return windowSlot - 36;
        }
        return windowSlot;
    }

    @Override
    protected String getName() {
        return "Player";
    }
}
