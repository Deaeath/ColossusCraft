package adris.altoclef.chains;

import adris.altoclef.tasksystem.TaskRunner;

public class MLGBucketFallChain extends FoodChain {
    public MLGBucketFallChain(TaskRunner runner) {
        super(runner);
    }

    @Override
    public String getName() {
        return "MLG Bucket Fall";
    }
}
