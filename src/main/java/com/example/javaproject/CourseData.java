package com.example.javaproject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class CourseData {
    // A single, static list that stays in memory for the whole app session
    private static final ObservableList<Course> instance = FXCollections.observableArrayList();

    public static ObservableList<Course> getCourses() {
        return instance;
    }
}
