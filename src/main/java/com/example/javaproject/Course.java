package com.example.javaproject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class Course {

    // ================================
    // INSTANCE VARIABLES
    // ================================

    private String name;
    private String code;
    private double credits;
    private ObservableList<Topic> topics;

    // ================================
    // CONSTRUCTORS
    // ================================

    public Course(String name, String code, double credits) {
        this.name = name;
        this.code = code;
        this.credits = credits;
        this.topics = FXCollections.observableArrayList();
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public double getCredits() {
        return credits;
    }

    public void setCredits(double credits) {
        this.credits = credits;
    }

    public ObservableList<Topic> getTopics() {
        return topics;
    }

    public void setTopics(ObservableList<Topic> topics) {
        this.topics = topics;
    }

    // ================================
    // OVERRIDE METHODS
    // ================================

    @Override
    public String toString() {
        return name + " (" + code + ") - " + credits + " credits";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Course course = (Course) obj;
        return code.equals(course.code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }
}