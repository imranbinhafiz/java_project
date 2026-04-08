package com.example.javaproject;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * TCP Client for the Smart Exam System.
 *
 * ═══════════════════════════════════════════════════════════════
 * CHANGES FROM ORIGINAL:
 *   [ADDED] getPublishedExams(String publisher) – fetches publisher's own exams
 *   [ADDED] getExamParticipants(String examId)  – fetches participant result list
 *   [ADDED] getExamQuestions(String examId, String publisher) – full questions w/ answers
 * ═══════════════════════════════════════════════════════════════
 */
public class ExamClient {

    private final String host;
    private final int    port;

    private Socket         socket;
    private BufferedReader reader;
    private PrintWriter    writer;

    private Consumer<String> debugListener;

    public ExamClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // ─── CONNECTION ────────────────────────────────────────────────────

    public void connect() throws ExamClientException {
        try {
            socket = new Socket(host, port);
            socket.setSoTimeout(10_000);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
        } catch (IOException e) {
            throw new ExamClientException("Cannot connect to exam server (" + host + ":" + port + "): " + e.getMessage(), e);
        }
    }

    public void disconnect() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed();
    }

    // ─── ORIGINAL API ─────────────────────────────────────────────────

    public String createExam(Exam exam) throws ExamClientException {
        String response = send("CREATE_EXAM", ExamJsonUtil.examToJson(exam));
        requireCommand(response, "CREATE_EXAM_SUCCESS", "Failed to create exam");
        return ExamJsonUtil.parseString(ExamJsonUtil.parsePayload(response), "examId");
    }

    public List<String> getAllUsers() throws ExamClientException {
        String response = send("GET_ALL_USERS", "{}");
        requireCommand(response, "USERS_LIST", "Failed to fetch user list");
        return ExamJsonUtil.parseUsernameList(ExamJsonUtil.parsePayload(response));
    }

    public void assignExam(String examId, List<String> usernames) throws ExamClientException {
        assignExam(examId, usernames, false);
    }

    public void assignExam(String examId, List<String> usernames, boolean isPublic) throws ExamClientException {
        assignExam(examId, usernames, isPublic, false, "");
    }

    public void assignExam(String examId, List<String> usernames, boolean isPublic, boolean isProtected, String password) throws ExamClientException {
        String payload  = ExamJsonUtil.assignmentToJson(examId, usernames, isPublic, isProtected, password);
        String response = send("ASSIGN_EXAM", payload);
        requireCommand(response, "ASSIGN_SUCCESS", "Failed to assign exam");
    }

    public List<Exam> getAvailableExams(String username) throws ExamClientException {
        String payload  = "{\"username\":\"" + username + "\"}";
        String response = send("GET_AVAILABLE_EXAMS", payload);
        requireCommand(response, "EXAMS_LIST", "Failed to fetch available exams");
        return ExamJsonUtil.parseExamList(ExamJsonUtil.parsePayload(response));
    }

    public List<ExamResult> getPreviousExams(String username) throws ExamClientException {
        String payload  = "{\"username\":\"" + username + "\"}";
        String response = send("GET_PREVIOUS_EXAMS", payload);
        requireCommand(response, "PREVIOUS_LIST", "Failed to fetch previous exams");
        return ExamJsonUtil.parseResultList(ExamJsonUtil.parsePayload(response));
    }

    public Exam startExam(String examId, String username) throws ExamClientException {
        String payload  = "{\"examId\":\"" + examId + "\",\"username\":\"" + username + "\"}";
        String response = send("START_EXAM", payload);
        requireCommand(response, "EXAM_QUESTIONS", "Failed to start exam");
        return ExamJsonUtil.examFromJson(ExamJsonUtil.parsePayload(response));
    }

    public ExamResult submitExam(String examId, String username, List<Integer> answers)
            throws ExamClientException {
        String answerJson = ExamJsonUtil.submitPayloadToJson(examId, answers);
        String payload    = answerJson.substring(0, answerJson.length() - 1)
                + ",\"username\":\"" + username + "\"}";
        String response = send("SUBMIT_EXAM", payload);
        requireCommand(response, "RESULT", "Failed to submit exam");
        return ExamJsonUtil.resultFromJson(ExamJsonUtil.parsePayload(response));
    }

    // ─── [ADDED] PUBLISHER API ────────────────────────────────────────

    /**
     * [ADDED] Returns all exams published by the given username.
     * Each returned Exam includes a participantCount field.
     */
    public List<Exam> getPublishedExams(String publisher) throws ExamClientException {
        String payload  = "{\"username\":\"" + publisher + "\"}";
        String response = send("GET_PUBLISHED_EXAMS", payload);
        requireCommand(response, "PUBLISHED_LIST", "Failed to fetch published exams");
        return ExamJsonUtil.parseExamList(ExamJsonUtil.parsePayload(response));
    }

    /**
     * [ADDED] Returns all participant results for a given exam.
     * Used by the publisher's "View Participants" button.
     */
    public List<ExamResult> getExamParticipants(String examId) throws ExamClientException {
        String payload  = "{\"examId\":\"" + examId + "\"}";
        String response = send("GET_EXAM_PARTICIPANTS", payload);
        requireCommand(response, "PARTICIPANTS_LIST", "Failed to fetch participants");
        return ExamJsonUtil.parseParticipantList(ExamJsonUtil.parsePayload(response));
    }

    /**
     * [ADDED] Returns the full exam including correct answers (publisher only).
     */
    public Exam getExamQuestions(String examId, String publisher) throws ExamClientException {
        String payload  = "{\"examId\":\"" + examId + "\",\"username\":\"" + publisher + "\"}";
        String response = send("GET_EXAM_QUESTIONS", payload);
        requireCommand(response, "EXAM_QUESTIONS_FULL", "Failed to fetch exam questions");
        return ExamJsonUtil.examFromJson(ExamJsonUtil.parsePayload(response));
    }

    // ─── INTERNAL ─────────────────────────────────────────────────────

    private synchronized String send(String command, String payload) throws ExamClientException {
        ensureConnected();
        try {
            writer.println(command + " " + payload);
            String response = reader.readLine();
            if (response == null) throw new ExamClientException("Server closed connection");
            if (debugListener != null) debugListener.accept("← " + response);
            return response;
        } catch (SocketTimeoutException e) {
            throw new ExamClientException("Server timed out");
        } catch (IOException e) {
            throw new ExamClientException("Network error: " + e.getMessage(), e);
        }
    }

    private void ensureConnected() throws ExamClientException {
        if (!isConnected()) connect();
    }

    private void requireCommand(String response, String expected, String errorMsg)
            throws ExamClientException {
        String cmd = ExamJsonUtil.parseCommand(response);
        if (!expected.equals(cmd)) {
            String serverMsg = ExamJsonUtil.parseString(ExamJsonUtil.parsePayload(response), "message");
            throw new ExamClientException(errorMsg + (serverMsg.isEmpty() ? "" : ": " + serverMsg));
        }
    }

    public void setDebugListener(Consumer<String> listener) {
        this.debugListener = listener;
    }

    // ─── EXCEPTION ────────────────────────────────────────────────────

    public static class ExamClientException extends Exception {
        public ExamClientException(String message) { super(message); }
        public ExamClientException(String message, Throwable cause) { super(message, cause); }
    }
}
