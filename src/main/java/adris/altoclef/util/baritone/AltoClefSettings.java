package adris.altoclef.util.baritone;

/**
 * AltoClef-side Baritone interaction flags. Currently tracks whether Baritone interactions
 * (block break/place) should be paused — e.g. while shielding in combat so the bot doesn't
 * try to mine while blocking. Pathing continues regardless.
 */
public class AltoClefSettings {
    private boolean interactionPaused = false;
    private boolean canWalkOnEndPortal = false;

    public void setInteractionPaused(boolean paused) {
        interactionPaused = paused;
    }

    public boolean isInteractionPaused() {
        return interactionPaused;
    }

    public void canWalkOnEndPortal(boolean value) {
        canWalkOnEndPortal = value;
    }

    public boolean isCanWalkOnEndPortal() {
        return canWalkOnEndPortal;
    }
}
