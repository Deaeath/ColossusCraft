package adris.altoclef.tasks.resources;

import adris.altoclef.util.ItemTarget;
import net.minecraft.world.entity.Entity;

public class KillAndLootTask extends adris.altoclef.tasks.entity.KillAndLootTask {
    public KillAndLootTask(String entityId, ItemTarget loot) {
        super(entityId, loot);
    }

    public KillAndLootTask(Class<? extends Entity> entityClass, ItemTarget loot) {
        super(entityClass.getSimpleName().toLowerCase(java.util.Locale.ROOT), loot);
    }
}
