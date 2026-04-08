package com.example.javaproject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Represents a single chat message (DM or group). */
public class ChatMessage {

    public enum Type { DM, GROUP }

    private final String        from;
    private final String        to;       // username for DM, roomId for group
    private final String        text;
    private final LocalDateTime timestamp;
    private final Type          type;
    private boolean             read;

    public ChatMessage(String from, String to, String text, Type type) {
        this.from      = from;
        this.to        = to;
        this.text      = text;
        this.type      = type;
        this.timestamp = LocalDateTime.now();
        this.read      = false;
    }

    // Full constructor (for loading from file)
    public ChatMessage(String from, String to, String text, Type type,
                       LocalDateTime timestamp, boolean read) {
        this.from      = from;
        this.to        = to;
        this.text      = text;
        this.type      = type;
        this.timestamp = timestamp;
        this.read      = read;
    }

    public String        getFrom()      { return from; }
    public String        getTo()        { return to; }
    public String        getText()      { return text; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public Type          getType()      { return type; }
    public boolean       isRead()       { return read; }
    public void          setRead(boolean r) { this.read = r; }

    public String getFormattedTime() {
        return timestamp.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    public String getFormattedDate() {
        return timestamp.format(DateTimeFormatter.ofPattern("MMM d"));
    }

    /** Returns the full ISO timestamp string used for server-side message matching. */
    public String getFormattedTimeRaw() {
        return timestamp != null ? timestamp.toString() : "";
    }
}
