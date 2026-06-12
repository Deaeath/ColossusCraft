package adris.altoclef.tasks;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Optional;

public abstract class AbstractDoToClosestObjectTask<T> extends Task {
    private final HashMap<T, CachedHeuristic> _heuristicMap = new HashMap<>();
    private T _currentlyPursuing = null;
    private boolean _wasWandering;
    private Task _goalTask = null;

    protected abstract Vec3 getPos(AltoClef mod, T obj);

    protected abstract Optional<T> getClosestTo(AltoClef mod, Vec3 pos);

    protected abstract Vec3 getOriginPos(AltoClef mod);

    protected abstract Task getGoalTask(T obj);

    protected abstract boolean isValid(AltoClef mod, T obj);

    protected Task getWanderTask(AltoClef mod) {
        return new TimeoutWanderTask(true);
    }

    public void resetSearch() {
        _currentlyPursuing = null;
        _heuristicMap.clear();
        _goalTask = null;
    }

    public boolean wasWandering() {
        return _wasWandering;
    }

    private double getCurrentCalculatedHeuristic(AltoClef mod) {
        Optional<Double> ticksRemainingOp = mod.getClientBaritone().getPathingBehavior().ticksRemainingInSegment();
        return ticksRemainingOp.orElse(Double.POSITIVE_INFINITY);
    }

    private boolean isMovingToClosestPos(AltoClef mod) {
        return _goalTask != null;
    }

    @Override
    protected void onStart(AltoClef mod) {
    }

    @Override
    protected Task onTick(AltoClef mod) {
        _wasWandering = false;

        if (_currentlyPursuing != null && !isValid(mod, _currentlyPursuing)) {
            _heuristicMap.remove(_currentlyPursuing);
            _currentlyPursuing = null;
        }

        Optional<T> checkNewClosest = getClosestTo(mod, getOriginPos(mod));

        if (checkNewClosest.isPresent() && !checkNewClosest.get().equals(_currentlyPursuing)) {
            T newClosest = checkNewClosest.get();
            if (_currentlyPursuing == null) {
                _currentlyPursuing = newClosest;
            } else if (isMovingToClosestPos(mod)) {
                setDebugState("Moving towards closest...");
                double currentHeuristic = getCurrentCalculatedHeuristic(mod);
                double closestDistanceSqr = getPos(mod, _currentlyPursuing).distanceToSqr(mod.getPlayer().position());
                int lastTick = WorldHelper.getTicks();

                _heuristicMap.computeIfAbsent(_currentlyPursuing, ignored -> new CachedHeuristic());
                CachedHeuristic h = _heuristicMap.get(_currentlyPursuing);
                h.updateHeuristic(currentHeuristic);
                h.updateDistance(closestDistanceSqr);
                h.setTickAttempted(lastTick);
                if (_heuristicMap.containsKey(newClosest)) {
                    CachedHeuristic maybeReAttempt = _heuristicMap.get(newClosest);
                    double maybeClosestDistance = getPos(mod, newClosest).distanceToSqr(mod.getPlayer().position());
                    if (maybeReAttempt.getHeuristicValue() < h.getHeuristicValue() || maybeClosestDistance < maybeReAttempt.getClosestDistanceSqr() / 4) {
                        setDebugState("Retrying old heuristic");
                        _currentlyPursuing = newClosest;
                        maybeReAttempt.updateDistance(maybeClosestDistance);
                    }
                } else {
                    setDebugState("Trying new pursuit");
                    _currentlyPursuing = newClosest;
                }
            } else {
                setDebugState("Waiting for move task");
            }
        }

        if (_currentlyPursuing != null) {
            _goalTask = getGoalTask(_currentlyPursuing);
            return _goalTask;
        }

        _goalTask = null;
        if (checkNewClosest.isEmpty()) {
            setDebugState("Wandering");
            _wasWandering = true;
            return getWanderTask(mod);
        }

        setDebugState("Waiting");
        return null;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
    }

    private static class CachedHeuristic {
        private double _closestDistanceSqr = Double.POSITIVE_INFINITY;
        private int _tickAttempted;
        private double _heuristicValue = Double.POSITIVE_INFINITY;

        public double getHeuristicValue() {
            return _heuristicValue;
        }

        public void updateHeuristic(double heuristicValue) {
            _heuristicValue = Math.min(_heuristicValue, heuristicValue);
        }

        public double getClosestDistanceSqr() {
            return _closestDistanceSqr;
        }

        public void updateDistance(double closestDistanceSqr) {
            _closestDistanceSqr = Math.min(_closestDistanceSqr, closestDistanceSqr);
        }

        public void setTickAttempted(int tickAttempted) {
            _tickAttempted = tickAttempted;
        }
    }
}
