package adris.altoclef.chains;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.TaskRunner;

public class FoodChain extends SingleTaskChain {
    public FoodChain(TaskRunner runner) {
        super(runner);
    }

    @Override
    protected void onTaskFinish(AltoClef mod) {
    }

    @Override
    public float getPriority(AltoClef mod) {
        return Float.NEGATIVE_INFINITY;
    }

    @Override
    public String getName() {
        return "Food";
    }
}
