package adris.altoclef.util.slots;

public class SmokerSlot extends FurnaceSlot {
    public static final SmokerSlot INPUT_SLOT_FUEL = new SmokerSlot(1);
    public static final SmokerSlot INPUT_SLOT_MATERIALS = new SmokerSlot(0);
    public static final SmokerSlot OUTPUT_SLOT = new SmokerSlot(2);

    public SmokerSlot(int windowSlot) {
        this(windowSlot, false);
    }

    protected SmokerSlot(int slot, boolean inventory) {
        super(slot, inventory);
    }

    @Override
    protected String getName() {
        return "Smoker";
    }
}
