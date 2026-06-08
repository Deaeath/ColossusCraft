package adris.altoclef.control;

import baritone.api.utils.input.Input;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

public class InputControls {

    private final Queue<Input> toUnpress = new ArrayDeque<>();
    private final Set<Input> waitForRelease = new HashSet<>();

    private static KeyMapping inputToKeyBinding(Input input) {
        Options o = Minecraft.getInstance().options;
        return switch (input) {
            case MOVE_FORWARD -> o.keyUp;
            case MOVE_BACK -> o.keyDown;
            case MOVE_LEFT -> o.keyLeft;
            case MOVE_RIGHT -> o.keyRight;
            case CLICK_LEFT -> o.keyAttack;
            case CLICK_RIGHT -> o.keyUse;
            case JUMP -> o.keyJump;
            case SNEAK -> o.keyShift;
            case SPRINT -> o.keySprint;
        };
    }

    public void tryPress(Input input) {
        if (waitForRelease.contains(input)) {
            return;
        }
        KeyMapping key = inputToKeyBinding(input);
        key.setDown(true);
        KeyMapping.click(key.getDefaultKey());
        toUnpress.add(input);
        waitForRelease.add(input);
    }

    public void hold(Input input) {
        KeyMapping key = inputToKeyBinding(input);
        if (!key.isDown()) {
            KeyMapping.click(key.getDefaultKey());
        }
        key.setDown(true);
    }

    public void release(Input input) {
        inputToKeyBinding(input).setDown(false);
    }

    public boolean isHeldDown(Input input) {
        return inputToKeyBinding(input).isDown();
    }

    public void forceLook(float yaw, float pitch) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.setYRot(yaw);
            Minecraft.getInstance().player.setXRot(pitch);
        }
    }

    public void onTickPre() {
        while (!toUnpress.isEmpty()) {
            inputToKeyBinding(toUnpress.remove()).setDown(false);
        }
    }

    public void onTickPost() {
        waitForRelease.clear();
    }
}
