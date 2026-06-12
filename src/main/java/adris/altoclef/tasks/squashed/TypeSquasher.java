package adris.altoclef.tasks.squashed;

import adris.altoclef.tasks.ResourceTask;

import java.util.ArrayList;
import java.util.List;

public class TypeSquasher {
    protected final List<ResourceTask> tasks = new ArrayList<>();

    public void add(ResourceTask task) {
        tasks.add(task);
    }

    public List<ResourceTask> getSquashed() {
        return new ArrayList<>(tasks);
    }
}
