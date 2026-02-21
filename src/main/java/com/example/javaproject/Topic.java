package com.example.javaproject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

public class Topic {

    // ================================
    // INSTANCE VARIABLES
    // ================================

    private String name;
    private BooleanProperty completed;

    // ================================
    // CONSTRUCTORS
    // ================================

    public Topic(String name) {
        this.name = name;
        this.completed = new SimpleBooleanProperty(false);
    }

    public Topic(String name, boolean completed) {
        this.name = name;
        this.completed = new SimpleBooleanProperty(completed);
    }

    // ================================
    // GETTERS AND SETTERS
    // ================================

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isCompleted() {
        return completed.get();
    }

    public void setCompleted(boolean completed) {
        this.completed.set(completed);
    }

    public BooleanProperty completedProperty() {
        return completed;
    }

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