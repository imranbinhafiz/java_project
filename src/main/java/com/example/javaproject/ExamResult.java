package com.example.javaproject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Model class representing the result of a completed exam attempt.
 */
public class ExamResult {

    private String resultId;
    private String examId;
    private String examTitle;
    private String publisherUsername;
    private String studentUsername;
    private int score;
    private int totalMarks;
    private int correctCount;
    private int wrongCount;
    private int totalQuestions;
    private LocalDateTime attemptedAt;
    private Exam.ExamType examType;

    // Per-question breakdown for the Results screen
    private List<QuestionResult> questionResults;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("MMM dd, yyyy  HH:mm");

    public ExamResult() {
        this.questionResults = new ArrayList<>();
    }

    // ============================================
    // DERIVED DISPLAY PROPERTIES
    // ============================================

    public double getPercentage() {
        if (totalMarks == 0) return 0.0;
        return (score / (double) totalMarks) * 100.0;
    }

    public String getFormattedPercentage() {
        return String.format("%.1f%%", getPercentage());
    }

    public String getFormattedAttemptDate() {
        return attemptedAt != null ? attemptedAt.format(FORMATTER) : "—";
    }

    public String getGrade() {
        double pct = getPercentage();
        if (pct >= 90) return "A+";
        if (pct >= 80) return "A";
        if (pct >= 70) return "B";
        if (pct >= 60) return "C";
        if (pct >= 50) return "D";
        return "F";
    }

    // ============================================
    // GETTERS & SETTERS
    // ============================================

    public String getResultId() { return resultId; }
    public void setResultId(String resultId) { this.resultId = resultId; }

    public String getExamId() { return examId; }
    public void setExamId(String examId) { this.examId = examId; }

    public String getExamTitle() { return examTitle; }
    public void setExamTitle(String examTitle) { this.examTitle = examTitle; }

    public String getPublisherUsername() { return publisherUsername; }
    public void setPublisherUsername(String publisherUsername) { this.publisherUsername = publisherUsername; }

    public String getStudentUsername() { return studentUsername; }
    public void setStudentUsername(String studentUsername) { this.studentUsername = studentUsername; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public int getTotalMarks() { return totalMarks; }
    public void setTotalMarks(int totalMarks) { this.totalMarks = totalMarks; }

    public int getCorrectCount() { return correctCount; }
    public void setCorrectCount(int correctCount) { this.correctCount = correctCount; }

    public int getWrongCount() { return wrongCount; }
    public void setWrongCount(int wrongCount) { this.wrongCount = wrongCount; }

    public int getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(int totalQuestions) { this.totalQuestions = totalQuestions; }

    public LocalDateTime getAttemptedAt() { return attemptedAt; }
    public void setAttemptedAt(LocalDateTime attemptedAt) { this.attemptedAt = attemptedAt; }

    public Exam.ExamType getExamType() { return examType; }
    public void setExamType(Exam.ExamType examType) { this.examType = examType; }

    public List<QuestionResult> getQuestionResults() { return questionResults; }
    public void setQuestionResults(List<QuestionResult> questionResults) { this.questionResults = questionResults; }

    // ============================================
    // INNER CLASS: per-question breakdown
    // ============================================

    public static class QuestionResult {
        private String questionText;
        private String selectedOption;   // may be null if unanswered
        private String correctOption;
        private boolean correct;

        public QuestionResult(String questionText, String selectedOption,
                              String correctOption, boolean correct) {
            this.questionText = questionText;
            this.selectedOption = selectedOption;
            this.correctOption = correctOption;
            this.correct = correct;
        }

        public String getQuestionText() { return questionText; }
        public String getSelectedOption() { return selectedOption; }
        public String getCorrectOption() { return correctOption; }
        public boolean isCorrect() { return correct; }
    }
}
