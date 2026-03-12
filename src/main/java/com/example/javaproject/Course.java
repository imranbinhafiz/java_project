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

    // Extended fields (backward-compatible: defaults to empty string / indigo)
    private String instructor;
    private String schedule;
    private String room;
    private String color;       // hex, e.g. "#6366f1"
    private String description;

    // ================================
    // CONSTRUCTORS
    // ================================

    /** Legacy constructor – keeps existing code working */
    public Course(String name, String code, double credits) {
        this.name        = name;
        this.code        = code;
        this.credits     = credits;
        this.topics      = FXCollections.observableArrayList();
        this.instructor  = "";
        this.schedule    = "";
        this.room        = "";
        this.color       = "#6366f1";
        this.description = "";
    }

    /** Full constructor */
    public Course(String name, String code, double credits,
                  String instructor, String schedule, String room,
                  String color, String description) {
        this(name, code, credits);
        this.instructor  = instructor  != null ? instructor  : "";
        this.schedule    = schedule    != null ? schedule    : "";
        this.room        = room        != null ? room        : "";
        this.color       = (color != null && !color.isBlank()) ? color : "#6366f1";
        this.description = description != null ? description : "";
    }

    // ================================
    // GETTERS AND SETTERS
    // ================================

    public String getName()               { return name; }
    public void   setName(String name)    { this.name = name; }

    public String getCode()               { return code; }
    public void   setCode(String code)    { this.code = code; }

    public double getCredits()            { return credits; }
    public void   setCredits(double c)    { this.credits = c; }

    public ObservableList<Topic> getTopics()                { return topics; }
    public void setTopics(ObservableList<Topic> topics)     { this.topics = topics; }

    public String getInstructor()                   { return instructor; }
    public void   setInstructor(String instructor)  { this.instructor = instructor != null ? instructor : ""; }

    public String getSchedule()                 { return schedule; }
    public void   setSchedule(String schedule)  { this.schedule = schedule != null ? schedule : ""; }

    public String getRoom()             { return room; }
    public void   setRoom(String room)  { this.room = room != null ? room : ""; }

    public String getColor()                { return color; }
    public void   setColor(String color)    { this.color = (color != null && !color.isBlank()) ? color : "#6366f1"; }

    public String getDescription()                  { return description; }
    public void   setDescription(String description){ this.description = description != null ? description : ""; }

    // ================================
    // COMPUTED / UTILITY
    // ================================

    /** 0.0 – 1.0 progress based on completed topics */
    public double getProgress() {
        if (topics == null || topics.isEmpty()) return 0.0;
        long done = topics.stream().filter(Topic::isCompleted).count();
        return (double) done / topics.size();
    }

    public int getCompletedTopicCount() {
        if (topics == null) return 0;
        return (int) topics.stream().filter(Topic::isCompleted).count();
    }

    public int getTotalTopicCount() {
        return topics == null ? 0 : topics.size();
    }

    /** "Not Started" | "In Progress" | "Completed" */
    public String getStatus() {
        if (topics == null || topics.isEmpty()) return "Not Started";
        double p = getProgress();
        if (p >= 1.0) return "Completed";
        if (p > 0.0)  return "In Progress";
        return "Not Started";
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
