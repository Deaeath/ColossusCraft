package adris.altoclef.eventbus.events;

public class ChatMessageEvent {
    public final String message;

    public ChatMessageEvent(String message) {
        this.message = message;
    }
}
