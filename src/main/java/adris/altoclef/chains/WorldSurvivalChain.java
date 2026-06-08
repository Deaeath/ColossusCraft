package adris.altoclef.chains;

import adris.altoclef.tasksystem.TaskRunner;

public class WorldSurvivalChain extends FoodChain {
    public WorldSurvivalChain(TaskRunner runner) {
        super(runner);
    }

    @Override
    public String getName() {
        return "World Survival";
    }
}
