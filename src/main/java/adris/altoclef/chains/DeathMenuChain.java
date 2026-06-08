package adris.altoclef.chains;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.TaskRunner;

public class DeathMenuChain extends FoodChain {
    public DeathMenuChain(TaskRunner runner) {
        super(runner);
    }

    @Override
    public String getName() {
        return "Death Menu";
    }
}
