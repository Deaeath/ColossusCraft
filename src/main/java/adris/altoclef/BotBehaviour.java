package adris.altoclef;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class BotBehaviour {
    private final Deque<State> states = new ArrayDeque<>();
    private State current = new State();

    public void avoidBlockBreaking(BlockPos pos) {
    }

    public void avoidBlockBreaking(Predicate<BlockPos> pred) {
    }

    public void avoidBlockPlacing(Predicate<BlockPos> pred) {
    }

    public void forceUseTool(BiPredicate<BlockState, ItemStack> pred) {
    }

    public boolean exclusivelyMineLogs() {
        return false;
    }

    public void allowWalkingOn(Predicate<BlockPos> pred) {
    }

    public void avoidWalkingThrough(Predicate<BlockPos> pred) {
    }

    public void setBlockBreakAdditionalPenalty(double penalty) {
    }

    public void push() {
        states.push(current.copy());
    }

    public boolean shouldExcludeFromForcefield(Entity entity) {
        for (Predicate<Entity> pred : current.excludeFromForceField) {
            if (pred.test(entity)) return true;
        }
        return false;
    }

    public void setPreferredStairs(boolean preferred) {
    }

    public void removeProtectedItems(Item... items) {
    }

    public void addForceFieldExclusion(Predicate<Entity> pred) {
        current.excludeFromForceField.add(pred);
    }

    public boolean shouldForceFieldPlayers() {
        return current.forceFieldPlayers;
    }

    public boolean shouldEscapeLava() {
        return current.escapeLava;
    }

    public void setRayTracingFluidHandling(net.minecraft.world.level.ClipContext.Fluid fluidHandling) {
        // Upstream toggles a raytrace mixin to allow targeting fluids; baritone's own raycast already
        // handles the MLG/extinguish look checks here, so this is a no-op holder for API parity.
    }

    public void pop() {
        if (!states.isEmpty()) {
            current = states.pop();
        }
    }

    public void setPauseOnLostFocus(boolean value) {
    }

    public boolean isProtected(Item item) {
        return current.protectedItems.contains(item);
    }

    public void addProtectedItems(Item... items) {
        Collections.addAll(current.protectedItems, items);
    }

    public boolean shouldPreserveFortune() {
        return current.preserveFortune;
    }

    public void setPreserveFortune(boolean value) {
        current.preserveFortune = value;
    }

    private static class State {
        private final List<Predicate<Entity>> excludeFromForceField = new ArrayList<>();
        private final Set<Item> protectedItems = new HashSet<>();
        private boolean forceFieldPlayers;
        private boolean escapeLava = true;
        private boolean preserveFortune = false;

        private State copy() {
            State result = new State();
            result.excludeFromForceField.addAll(excludeFromForceField);
            result.protectedItems.addAll(protectedItems);
            result.forceFieldPlayers = forceFieldPlayers;
            result.escapeLava = escapeLava;
            result.preserveFortune = preserveFortune;
            return result;
        }
    }
}
