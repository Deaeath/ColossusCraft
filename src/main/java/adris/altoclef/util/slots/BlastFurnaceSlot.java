package adris.altoclef.util.slots;

public class BlastFurnaceSlot extends FurnaceSlot {
    public static final BlastFurnaceSlot INPUT_SLOT_FUEL = new BlastFurnaceSlot(1);
    public static final BlastFurnaceSlot INPUT_SLOT_MATERIALS = new BlastFurnaceSlot(0);
    public static final BlastFurnaceSlot OUTPUT_SLOT = new BlastFurnaceSlot(2);

    public BlastFurnaceSlot(int windowSlot) {
        this(windowSlot, false);
    }

    protected BlastFurnaceSlot(int slot, boolean inventory) {
        super(slot, inventory);
    }

    @Override
    protected String getName() {
        return "Blast Furnace";
    }
}
