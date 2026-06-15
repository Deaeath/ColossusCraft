package adris.altoclef.tasks.construction;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.time.TimerGame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

public class PlaceBlockTask extends Task {
    private static final Item[] STRUCTURE_MATERIALS = new Item[]{
            Items.DIRT, Items.COBBLESTONE, Items.NETHERRACK, Items.COBBLED_DEEPSLATE,
            Items.STONE, Items.DEEPSLATE, Items.ANDESITE, Items.DIORITE, Items.GRANITE,
            Items.TUFF, Items.CALCITE, Items.BASALT, Items.BLACKSTONE
    };

    private final BlockPos target;
    private final Block[] toPlace;
    private final boolean useThrowaways;
    private final boolean autoCollectStructureBlocks;
    private int useCooldown;
    private final TimerGame _stuckTimer = new TimerGame(3);

    public PlaceBlockTask(BlockPos target, Block[] toPlace, boolean useThrowaways, boolean autoCollectStructureBlocks) {
        this.target = target;
        this.toPlace = toPlace;
        this.useThrowaways = useThrowaways;
        this.autoCollectStructureBlocks = autoCollectStructureBlocks;
    }

    public PlaceBlockTask(BlockPos target, Block... toPlace) {
        this(target, toPlace, false, false);
    }

    public static int getMaterialCount(AltoClef mod) {
        return mod.getItemStorage().getItemCount(STRUCTURE_MATERIALS);
    }

    public static Item[] getStructureMaterials() {
        return STRUCTURE_MATERIALS;
    }

    public static Task getMaterialTask(int count) {
        return new CollectItemTask(new ItemTarget(STRUCTURE_MATERIALS, count));
    }

    @Override
    protected void onStart(AltoClef mod) {
        useCooldown = 0;
        _stuckTimer.reset();
    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (isFinished(mod)) return null;
        Item[] items = Arrays.stream(toPlace).map(Block::asItem).filter(item -> item != Items.AIR).toArray(Item[]::new);
        if (items.length == 0 && autoCollectStructureBlocks) {
            items = STRUCTURE_MATERIALS;
        }
        if (items.length != 0 && !mod.getSlotHandler().forceEquipItem(items)) {
            _stuckTimer.reset();
            return new CollectItemTask(new ItemTarget(items, 1));
        }
        if (mod.getPlayer() != null && mod.getPlayer().blockPosition().distSqr(target) > 25) {
            _stuckTimer.reset();
            return new GetToBlockTask(target);
        }
        if (_stuckTimer.elapsed()) {
            // Can't reach target to place — give up so the caller can try elsewhere.
            return null;
        }
        if (useCooldown-- <= 0 && mod.getController() != null && mod.getPlayer() != null) {
            useCooldown = 8;
            BlockPos support = target.below();
            LookHelper.lookAt(mod, support);
            mod.getController().useItemOn(mod.getPlayer(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false));
        }
        setDebugState("Place " + target.toShortString() + " throwaways=" + useThrowaways);
        return null;
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return mod.getWorld() != null && !mod.getWorld().getBlockState(target).isAir();
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof PlaceBlockTask task && task.target.equals(target) && Arrays.equals(task.toPlace, toPlace);
    }

    @Override
    protected String toDebugString() {
        return "Place block";
    }
}
