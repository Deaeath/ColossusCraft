package adris.altoclef;

public class Settings {
    private String commandPrefix = "@";
    private String chatLogPrefix = "[Alto Clef] ";
    private boolean hideAllWarningLogs;
    private double entityReachRange = 4.5;
    private double containerItemMoveDelay = 0.12;
    private int blockScanHorizontalRange = 48;
    private int blockScanVerticalRange = 32;

    public String getCommandPrefix() {
        return commandPrefix;
    }

    public String getChatLogPrefix() {
        return chatLogPrefix;
    }

    public boolean shouldHideAllWarningLogs() {
        return hideAllWarningLogs;
    }

    public double getEntityReachRange() {
        return entityReachRange;
    }

    public double getContainerItemMoveDelay() {
        return containerItemMoveDelay;
    }

    public int getBlockScanHorizontalRange() {
        return blockScanHorizontalRange;
    }

    public int getBlockScanVerticalRange() {
        return blockScanVerticalRange;
    }
}
