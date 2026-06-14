package adris.altoclef.tasksystem;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import baritone.api.BaritoneAPI;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;

import java.util.ArrayList;

public class TaskRunner {

    private final ArrayList<TaskChain> _chains = new ArrayList<>();
    private final AltoClef _mod;
    private boolean _active;
    private boolean _pauseBaritone;
    private boolean _pauseProcessRegistered;

    private TaskChain _cachedCurrentTaskChain = null;
    private TaskChain _currentActiveChain = null;
    private TaskChain _currentPassiveChain = null;
    private final ArrayList<String> _lastQueuedKeys = new ArrayList<>();
    private long _tickCount;

    public TaskRunner(AltoClef mod) {
        _mod = mod;
        _active = false;
    }

    public void tick() {
        if (!_active) return;
        ensurePauseProcessRegistered();
        _tickCount++;

        TaskChain maxActiveChain = null;
        float maxActivePriority = Float.NEGATIVE_INFINITY;
        TaskChain maxPassiveChain = null;
        float maxPassivePriority = Float.NEGATIVE_INFINITY;
        ArrayList<ChainPriority> queued = new ArrayList<>();
        for (TaskChain chain : _chains) {
            if (!chain.isActive()) continue;
            float priority = chain.getPriority(_mod);
            queued.add(new ChainPriority(chain, priority));
            if (chain.isPassive()) {
                if (priority > maxPassivePriority) {
                    maxPassivePriority = priority;
                    maxPassiveChain = chain;
                }
            } else if (priority > maxActivePriority) {
                maxActivePriority = priority;
                maxActiveChain = chain;
            }
        }

        TaskChain maxChain = maxPassivePriority > maxActivePriority ? maxPassiveChain : maxActiveChain;
        float maxPriority = maxChain == maxPassiveChain ? maxPassivePriority : maxActivePriority;
        TaskChain selectedPassiveChain = maxChain != null && maxChain.isPassive() ? maxChain : null;

        logQueue(queued, maxActiveChain, maxActivePriority, maxPassiveChain, maxPassivePriority, maxChain, maxPriority);
        if (_cachedCurrentTaskChain != maxChain) {
            Debug.logInternal("[TaskRunner] switch: "
                    + (_cachedCurrentTaskChain != null ? _cachedCurrentTaskChain.getName() : "none")
                    + " -> " + (maxChain != null ? maxChain.getName() + " pri=" + maxPriority : "none"));
        }
        if (_currentPassiveChain != selectedPassiveChain) {
            Debug.logInternal("[TaskRunner] passive-lane: " + laneName(_currentPassiveChain)
                    + " -> " + laneName(selectedPassiveChain));
        }
        if (_currentActiveChain != maxActiveChain) {
            Debug.logInternal("[TaskRunner] active-lane: " + laneName(_currentActiveChain)
                    + " -> " + laneName(maxActiveChain));
        }
        if (_currentPassiveChain != null && _currentPassiveChain != selectedPassiveChain) {
            _currentPassiveChain.onInterrupt(_mod, maxChain);
        }
        if (_currentActiveChain != null && _currentActiveChain != maxActiveChain) {
            _currentActiveChain.onInterrupt(_mod, maxActiveChain);
        }
        _currentActiveChain = maxActiveChain;
        _currentPassiveChain = selectedPassiveChain;
        _cachedCurrentTaskChain = maxChain;
        _pauseBaritone = maxChain != null && maxChain.pausesBaritone();
        if (maxChain != null) {
            maxChain.tick(_mod);
        }
    }

    public void addTaskChain(TaskChain chain) {
        _chains.add(chain);
    }

    public void enable() {
        if (!_active) {
            _mod.getBehaviour().push();
            _mod.getBehaviour().setPauseOnLostFocus(false);
        }
        _active = true;
    }

    public void disable() {
        if (_active) {
            _mod.getBehaviour().pop();
        }
        for (TaskChain chain : _chains) {
            chain.stop(_mod);
        }
        _active = false;
        _pauseBaritone = false;
        _cachedCurrentTaskChain = null;
        _currentActiveChain = null;
        _currentPassiveChain = null;
        _lastQueuedKeys.clear();

        Debug.logMessage("Stopped");
    }

    public TaskChain getCurrentTaskChain() {
        return _cachedCurrentTaskChain;
    }

    // Kinda jank ngl
    public AltoClef getMod() {
        return _mod;
    }

    private void ensurePauseProcessRegistered() {
        if (_pauseProcessRegistered) {
            return;
        }
        _pauseProcessRegistered = true;
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingControlManager().registerProcess(new IBaritoneProcess() {
            @Override
            public boolean isActive() {
                return _active && _pauseBaritone;
            }

            @Override
            public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            @Override
            public boolean isTemporary() {
                return true;
            }

            @Override
            public void onLostControl() {
            }

            @Override
            public double priority() {
                return 1000000.0;
            }

            @Override
            public String displayName0() {
                return "ColossusCraft passive safety pause";
            }
        });
    }

    private void logQueue(ArrayList<ChainPriority> queued, TaskChain activeChain, float activePriority,
                          TaskChain passiveChain, float passivePriority, TaskChain selectedChain, float selectedPriority) {
        queued.sort((a, b) -> Float.compare(b.priority, a.priority));
        ArrayList<String> currentKeys = new ArrayList<>();
        for (ChainPriority entry : queued) {
            currentKeys.add(queueKey(entry.chain));
        }
        for (String previousKey : _lastQueuedKeys) {
            if (!currentKeys.contains(previousKey)) {
                Debug.logInternal("[TaskRunner] DEQUEUED: " + previousKey);
            }
        }
        _lastQueuedKeys.clear();
        _lastQueuedKeys.addAll(currentKeys);

        StringBuilder message = new StringBuilder();
        message.append("[TaskRunner] tick=").append(_tickCount)
                .append(" selected=").append(describeSelection(selectedChain, selectedPriority))
                .append(" activeTop=").append(describeSelection(activeChain, activePriority))
                .append(" passiveTop=").append(describeSelection(passiveChain, passivePriority))
                .append(" pauseBaritone=").append(selectedChain != null && selectedChain.pausesBaritone())
                .append(" baritone=").append(describeBaritone())
                .append(" queue=[");
        for (int i = 0; i < queued.size(); i++) {
            if (i > 0) message.append(" | ");
            ChainPriority entry = queued.get(i);
            message.append(entry.chain.isPassive() ? "P:" : "A:")
                    .append(entry.chain.getName())
                    .append("@").append(formatPriority(entry.priority));
            String task = entry.chain.describeCurrentTask();
            if (!task.isEmpty()) {
                message.append("{").append(task).append("}");
            }
        }
        message.append("]");
        Debug.logInternal(message.toString());
    }

    private String describeSelection(TaskChain chain, float priority) {
        if (chain == null) {
            return "none";
        }
        String task = chain.describeCurrentTask();
        return chain.getName() + "@" + formatPriority(priority) + (task.isEmpty() ? "" : "{" + task + "}");
    }

    private String laneName(TaskChain chain) {
        return chain == null ? "none" : chain.getName() + "{" + chain.describeCurrentTask() + "}";
    }

    private String queueKey(TaskChain chain) {
        return (chain.isPassive() ? "P:" : "A:") + chain.getName();
    }

    private String describeBaritone() {
        try {
            var baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            return "mine=" + baritone.getMineProcess().isActive()
                    + ",path=" + baritone.getPathingBehavior().isPathing()
                    + ",goal=" + baritone.getPathingBehavior().getGoal();
        } catch (Throwable e) {
            return "unavailable";
        }
    }

    private String formatPriority(float priority) {
        if (priority == Float.POSITIVE_INFINITY) return "+INF";
        if (priority == Float.NEGATIVE_INFINITY) return "-INF";
        return String.format(java.util.Locale.ROOT, "%.1f", priority);
    }

    private static final class ChainPriority {
        private final TaskChain chain;
        private final float priority;

        private ChainPriority(TaskChain chain, float priority) {
            this.chain = chain;
            this.priority = priority;
        }
    }
}
