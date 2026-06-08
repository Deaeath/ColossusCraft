package adris.altoclef.tasks.entity;

import adris.altoclef.tasksystem.Task;
import net.minecraft.world.entity.Entity;

public abstract class AbstractDoToEntityTask extends Task {
    protected final Entity entity;

    protected AbstractDoToEntityTask(Entity entity) {
        this.entity = entity;
    }
}
