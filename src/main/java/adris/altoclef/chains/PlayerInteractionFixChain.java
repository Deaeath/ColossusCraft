package adris.altoclef.chains;

import adris.altoclef.tasksystem.TaskRunner;

public class PlayerInteractionFixChain extends FoodChain {
    public PlayerInteractionFixChain(TaskRunner runner) {
        super(runner);
    }

    @Override
    public String getName() {
        return "Player Interaction Fix";
    }
}
