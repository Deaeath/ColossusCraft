package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.entity.KillAndLootTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.world.item.Items;

public class CollectBlazeRodsTask extends ResourceItemTask {
    private final int count;
    private int commandCooldown;

    public CollectBlazeRodsTask(int count) {
        super(Items.BLAZE_ROD, count);
        this.count = count;
    }

    @Override
    protected void onResourceStart(AltoClef mod) {
        commandCooldown = 0;
    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        if (WorldHelper.getCurrentDimension() != Dimension.NETHER) {
            return new DefaultGoToDimensionTask(Dimension.NETHER);
        }
        if (commandCooldown-- <= 0) {
            mod.runBaritone("explore");
            commandCooldown = 120;
        }
        return new KillAndLootTask("blaze", new ItemTarget(Items.BLAZE_ROD, count));
    }
}
