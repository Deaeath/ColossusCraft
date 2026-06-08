package adris.altoclef.tasks.entity;

import net.minecraft.world.entity.Entity;

import java.util.function.Predicate;

public abstract class AbstractKillEntityTask extends KillEntitiesTask {
    @SafeVarargs
    public AbstractKillEntityTask(Predicate<Entity> accept, Class<? extends Entity>... entityClasses) {
        super(accept, entityClasses);
    }

    @SafeVarargs
    public AbstractKillEntityTask(Class<? extends Entity>... entityClasses) {
        super(entityClasses);
    }
}
