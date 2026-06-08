package adris.altoclef.ui;

import adris.altoclef.Debug;

public class MessageSender {
    public void enqueue(String message, MessagePriority priority) {
        Debug.logMessage(message);
    }
}
