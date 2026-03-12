package com.example.javaproject;

import java.util.ArrayList;
import java.util.List;

/**
 * Model class representing a single MCQ Question inside an Exam.
 */
public class Question {

    private String questionId;
    private String questionText;
    private List<String> options;       // exactly 4 options
    private int correctOptionIndex;     // 0-based index of correct answer
    private int marks;
    private String imageBase64;

    public Question() {
        this.options = new ArrayList<>();
    }

    public Question(String questionId, String questionText, List<String> options,
                    int correctOptionIndex, int marks, String imageBase64) {
        this.questionId = questionId;
        this.questionText = questionText;
        this.options = options;
        this.correctOptionIndex = correctOptionIndex;
        this.marks = marks;
        this.imageBase64 = imageBase64;
    }

    public Question(String questionId, String questionText, List<String> options,
                    int correctOptionIndex, int marks) {
        this(questionId, questionText, options, correctOptionIndex, marks, null);
    }

    // ============================================
    // GETTERS & SETTERS
    // ============================================

    public String getQuestionId() { return questionId; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }

    public int getCorrectOptionIndex() { return correctOptionIndex; }
    public void setCorrectOptionIndex(int correctOptionIndex) { this.correctOptionIndex = correctOptionIndex; }

    public int getMarks() { return marks; }
    public void setMarks(int marks) { this.marks = marks; }

    public String getImageBase64() { return imageBase64; }
    public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }

    @Override
    public String toString() {
        return "Question{id='" + questionId + "', text='" + questionText + "'}";
    }
}
