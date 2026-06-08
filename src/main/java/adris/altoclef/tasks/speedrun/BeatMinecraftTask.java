package adris.altoclef.tasks.speedrun;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GetToXZTask;
import adris.altoclef.tasks.resources.CollectFoodTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.trackers.LocateResultTracker;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;

public class BeatMinecraftTask extends Task {
    private boolean requestedStrongholdLocate;

    @Override
    protected void onStart(AltoClef mod) {
        requestedStrongholdLocate = false;
    }

    @Override
    protected Task onTick(AltoClef mod) {
        Dimension dimension = WorldHelper.getCurrentDimension();
        if (dimension == Dimension.END) {
            setDebugState("Kill dragon");
            return new KillEnderDragonTask();
        }
        if (StorageHelper.calculateInventoryFoodScore(mod) < 12) {
            setDebugState("Get food");
            return new CollectFoodTask(16);
        }
        if (!mod.getItemStorage().hasItemInventoryOnly(Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE)) {
            setDebugState("Get pickaxe");
            return new CollectItemTask(new ItemTarget(Items.IRON_PICKAXE, 1));
        }
        if (!mod.getItemStorage().hasItemInventoryOnly(Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD)) {
            setDebugState("Get sword");
            return new CollectItemTask(new ItemTarget(Items.IRON_SWORD, 1));
        }
        if (mod.getItemStorage().getItemCountInventoryOnly(Items.ENDER_EYE) < 12) {
            if (dimension == Dimension.OVERWORLD && mod.getItemStorage().getItemCountInventoryOnly(Items.BLAZE_ROD) < 6) {
                setDebugState("Go Nether for blaze rods");
                return new DefaultGoToDimensionTask(Dimension.NETHER);
            }
            setDebugState("Get eyes of ender");
            return new CollectItemTask(new ItemTarget(Items.ENDER_EYE, 12));
        }
        if (dimension == Dimension.NETHER) {
            setDebugState("Return Overworld");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }
        BlockPos stronghold = LocateResultTracker.lastStructurePos().orElse(null);
        if (stronghold != null && mod.getPlayer() != null && mod.getPlayer().blockPosition().distSqr(stronghold) > 256.0) {
            setDebugState("Travel stronghold " + stronghold.toShortString());
            return new GetToXZTask(stronghold.getX(), stronghold.getZ(), Dimension.OVERWORLD);
        }
        if (stronghold != null) {
            setDebugState("Open End portal");
            return new OpenEndPortalTask();
        }
        if (!requestedStrongholdLocate && Minecraft.getInstance().getConnection() != null) {
            requestedStrongholdLocate = true;
            LocateResultTracker.clear();
            Minecraft.getInstance().getConnection().sendCommand("locate structure minecraft:stronghold");
            mod.log("Stronghold locate sent.");
        }
        setDebugState("Waiting for stronghold locate result");
        return null;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.stopPathing();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof BeatMinecraftTask;
    }

    @Override
    protected String toDebugString() {
        return "Beat Minecraft";
    }
}
