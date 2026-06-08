package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.CollectItemTask;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.helpers.StorageHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.Arrays;

public class MineAndCollectTask extends ResourceTask {
    private final Block[] blocksToMine;
    private final MiningRequirement requirement;
    private int commandCooldown;

    public MineAndCollectTask(ItemTarget[] itemTargets, Block[] blocksToMine, MiningRequirement requirement) {
        super(itemTargets);
        this.blocksToMine = blocksToMine;
        this.requirement = requirement;
    }

    public MineAndCollectTask(ItemTarget[] blocksToMine, MiningRequirement requirement) {
        this(blocksToMine, itemTargetToBlockList(blocksToMine), requirement);
    }

    public MineAndCollectTask(ItemTarget target, Block[] blocksToMine, MiningRequirement requirement) {
        this(new ItemTarget[]{target}, blocksToMine, requirement);
    }

    public MineAndCollectTask(Item item, int count, Block[] blocksToMine, MiningRequirement requirement) {
        this(new ItemTarget(item, count), blocksToMine, requirement);
    }

    public static Block[] itemTargetToBlockList(ItemTarget[] targets) {
        return Arrays.stream(ItemTarget.getMatches(targets))
                .map(Block::byItem)
                .filter(block -> block != null && !block.defaultBlockState().isAir())
                .distinct()
                .toArray(Block[]::new);
    }

    @Override
    protected void onResourceStart(AltoClef mod) {
        commandCooldown = 0;
    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        if (!StorageHelper.miningRequirementMet(mod, requirement)) {
            return new CollectItemTask(new ItemTarget(requirement.getMinimumPickaxe(), 1));
        }
        if (commandCooldown-- <= 0) {
            String ids = Arrays.stream(blocksToMine)
                    .map(BuiltInRegistries.BLOCK::getKey)
                    .map(ResourceLocation::toString)
                    .reduce((a, b) -> a + " " + b)
                    .orElse("");
            if (!ids.isBlank()) {
                mod.runBaritone("mine " + ids);
            }
            commandCooldown = 80;
        }
        setDebugState("Mine " + Arrays.toString(blocksToMine));
        return collectMissingItemsTask(mod);
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {
        mod.stopPathing();
    }
}
