package com.example.javaproject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class Topic {

    // ================================
    // INSTANCE VARIABLES
    // ================================

    private String name;
    private BooleanProperty completed;
    private StringProperty comment;

    // ================================
    // CONSTRUCTORS
    // ================================

    public Topic(String name) {
        this.name      = name;
        this.completed = new SimpleBooleanProperty(false);
        this.comment   = new SimpleStringProperty("");
    }

    public Topic(String name, boolean completed) {
        this.name      = name;
        this.completed = new SimpleBooleanProperty(completed);
        this.comment   = new SimpleStringProperty("");
    }

    public Topic(String name, boolean completed, String comment) {
        this.name      = name;
        this.completed = new SimpleBooleanProperty(completed);
        this.comment   = new SimpleStringProperty(comment != null ? comment : "");
    }

    // ================================
    // GETTERS AND SETTERS
    // ================================

    public String getName()             { return name; }
    public void   setName(String name)  { this.name = name; }

    public boolean isCompleted()                { return completed.get(); }
    public void    setCompleted(boolean v)      { completed.set(v); }
    public BooleanProperty completedProperty()  { return completed; }

    public String getComment()              { return comment.get(); }
    public void   setComment(String c)      { comment.set(c != null ? c : ""); }
    public StringProperty commentProperty() { return comment; }

    // ================================
    // OVERRIDE METHODS
    // ================================

    @Override
    public String toString() {
        return name + (isCompleted() ? " ✓" : "");
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Topic topic = (Topic) obj;
        return name.equals(topic.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
