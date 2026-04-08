package com.example.javaproject;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Model class representing a single Exam.
 *
 * ═══════════════════════════════════════════════════════════════
 * CHANGES FROM ORIGINAL:
 *   [ADDED] participantCount field – populated from server for publisher view.
 *   [ADDED] ExamStatus enum extended: SCHEDULED, LIVE, COMPLETED, PRACTICE, EXPIRED.
 *   [ADDED] getComputedStatus() – returns the correct ExamStatus for the current time.
 * ═══════════════════════════════════════════════════════════════
 */
public class Exam {

    public enum ExamType   { REAL_TIME, PRACTICE }

    // [ADDED] Full lifecycle status enum
    public enum ExamStatus { SCHEDULED, LIVE, COMPLETED, PRACTICE, EXPIRED, AVAILABLE, ATTEMPTED }

    private String examId;
    private String title;
    private String description;
    private int durationMinutes;
    private int totalMarks;
    private boolean negativeMarking;
    private boolean shuffleOptions;
    private ExamType examType;
    private String publisherUsername;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private boolean isPublic;
    private boolean isProtected;
    private String password;
    private List<String> assignedUsers;
    private List<Question> questions;

    // [ADDED] Populated when fetched from GET_PUBLISHED_EXAMS
    private int participantCount;

    public Exam() {
        this.assignedUsers = new ArrayList<>();
        this.questions     = new ArrayList<>();
    }

    public Exam(String examId, String title, String description, int durationMinutes,
                int totalMarks, boolean negativeMarking, boolean shuffleOptions,
                ExamType examType, String publisherUsername) {
        this();
        this.examId            = examId;
        this.title             = title;
        this.description       = description;
        this.durationMinutes   = durationMinutes;
        this.totalMarks        = totalMarks;
        this.negativeMarking   = negativeMarking;
        this.shuffleOptions    = shuffleOptions;
        this.examType          = examType;
        this.publisherUsername = publisherUsername;
    }

    // ─── BUSINESS LOGIC ───────────────────────────────────────────────

    public boolean isAccessible() {
        if (examType == ExamType.PRACTICE) return true;
        LocalDateTime now = LocalDateTime.now();
        return startTime != null && endTime != null
                && now.isAfter(startTime) && now.isBefore(endTime);
    }

    public boolean isExpired() {
        if (examType == ExamType.PRACTICE) return false;
        return endTime != null && LocalDateTime.now().isAfter(endTime);
    }

    /**
     * [ADDED] Returns a precise lifecycle status for the publisher dashboard.
     */
    public ExamStatus getComputedStatus() {
        if (examType == ExamType.PRACTICE) return ExamStatus.PRACTICE;
        LocalDateTime now = LocalDateTime.now();
        if (startTime != null && now.isBefore(startTime)) return ExamStatus.SCHEDULED;
        if (isAccessible()) return ExamStatus.LIVE;
        if (isExpired())    return ExamStatus.COMPLETED;
        return ExamStatus.SCHEDULED;
    }

    public String getStatusLabel() {
        switch (getComputedStatus()) {
            case PRACTICE:  return "Practice";
            case SCHEDULED: return "Scheduled";
            case LIVE:      return "🔴 Live";
            case COMPLETED: return "Completed";
            default:        return "Unknown";
        }
    }

    // ─── GETTERS & SETTERS ────────────────────────────────────────────

    public String getExamId()                        { return examId; }
    public void   setExamId(String examId)           { this.examId = examId; }

    public String getTitle()                         { return title; }
    public void   setTitle(String title)             { this.title = title; }

    public String getDescription()                   { return description; }
    public void   setDescription(String d)           { this.description = d; }

    public int  getDurationMinutes()                 { return durationMinutes; }
    public void setDurationMinutes(int d)            { this.durationMinutes = d; }

    public int  getTotalMarks()                      { return totalMarks; }
    public void setTotalMarks(int t)                 { this.totalMarks = t; }

    public boolean isNegativeMarking()               { return negativeMarking; }
    public void    setNegativeMarking(boolean n)     { this.negativeMarking = n; }

    public boolean isShuffleOptions()                { return shuffleOptions; }
    public void    setShuffleOptions(boolean s)      { this.shuffleOptions = s; }

    public ExamType getExamType()                    { return examType; }
    public void     setExamType(ExamType t)          { this.examType = t; }

    public String getPublisherUsername()             { return publisherUsername; }
    public void   setPublisherUsername(String p)     { this.publisherUsername = p; }

    public LocalDateTime getStartTime()              { return startTime; }
    public void          setStartTime(LocalDateTime t){ this.startTime = t; }

    public LocalDateTime getEndTime()                { return endTime; }
    public void          setEndTime(LocalDateTime t) { this.endTime = t; }

    public boolean isPublic()                        { return isPublic; }
    public void    setPublic(boolean p)              { this.isPublic = p; }

    public boolean isProtected()                     { return isProtected; }
    public void    setProtected(boolean pr)          { this.isProtected = pr; }

    public String getPassword()                      { return password; }
    public void   setPassword(String pwd)            { this.password = pwd; }

    public List<String>   getAssignedUsers()         { return assignedUsers; }
    public void           setAssignedUsers(List<String> u) { this.assignedUsers = u; }

    public List<Question> getQuestions()             { return questions; }
    public void           setQuestions(List<Question> q)   { this.questions = q; }

    // [ADDED]
    public int  getParticipantCount()                { return participantCount; }
    public void setParticipantCount(int c)           { this.participantCount = c; }

    @Override
    public String toString() {
        return "Exam{id='" + examId + "', title='" + title + "', type=" + examType + "}";
    }
}
