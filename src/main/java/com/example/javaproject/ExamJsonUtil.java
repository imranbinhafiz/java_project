package com.example.javaproject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight JSON builder/parser for the Exam module network protocol.
 *
 * ═══════════════════════════════════════════════════════════════
 * CHANGES FROM ORIGINAL:
 *   [ADDED] examToJsonWithParticipantCount() – includes participantCount field
 *           used by the publisher dashboard GET_PUBLISHED_EXAMS response.
 *   [ADDED] resultToJson() now serializes full QuestionResult breakdown array
 *           so it persists across server restarts and is available for View Result.
 *   [ADDED] resultFromJson() now restores the QuestionResult breakdown array.
 *   [ADDED] parseResultList() overload that reads from "participants" array key.
 *   [ADDED] parseInt() for "examCounter" / "resultCounter" in state file.
 * ═══════════════════════════════════════════════════════════════
 */
public class ExamJsonUtil {

    private static final DateTimeFormatter DT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    // ============================================================
    // EXAM  →  JSON
    // ============================================================

    public static String examToJson(Exam exam) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        appendString(sb, "examId", exam.getExamId()); sb.append(",");
        appendString(sb, "title", exam.getTitle()); sb.append(",");
        appendString(sb, "description", exam.getDescription()); sb.append(",");
        appendInt(sb, "durationMinutes", exam.getDurationMinutes()); sb.append(",");
        appendInt(sb, "totalMarks", exam.getTotalMarks()); sb.append(",");
        appendBool(sb, "negativeMarking", exam.isNegativeMarking()); sb.append(",");
        appendBool(sb, "shuffleOptions", exam.isShuffleOptions()); sb.append(",");
        appendString(sb, "examType", exam.getExamType() != null ? exam.getExamType().name() : "PRACTICE"); sb.append(",");
        appendString(sb, "publisherUsername", exam.getPublisherUsername()); sb.append(",");
        appendBool(sb, "isPublic", exam.isPublic()); sb.append(",");
        appendBool(sb, "isProtected", exam.isProtected()); sb.append(",");
        appendString(sb, "password", exam.getPassword()); sb.append(",");
        appendString(sb, "startTime",
                exam.getStartTime() != null ? exam.getStartTime().format(DT_FORMAT) : ""); sb.append(",");
        appendString(sb, "endTime",
                exam.getEndTime() != null ? exam.getEndTime().format(DT_FORMAT) : ""); sb.append(",");
        // [ADDED] statusLabel so clients know SCHEDULED / Live / Completed without recalculating
        appendString(sb, "statusLabel", exam.getStatusLabel()); sb.append(",");
        // Assigned users array
        sb.append("\"assignedUsers\":[");
        List<String> au = exam.getAssignedUsers();
        for (int i = 0; i < au.size(); i++) {
            sb.append("\"").append(escape(au.get(i))).append("\"");
            if (i < au.size() - 1) sb.append(",");
        }
        sb.append("],");
        // questions array
        sb.append("\"questions\":[");
        List<Question> qs = exam.getQuestions();
        for (int i = 0; i < qs.size(); i++) {
            sb.append(questionToJson(qs.get(i)));
            if (i < qs.size() - 1) sb.append(",");
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    /**
     * [ADDED] Same as examToJson but appends a participantCount field.
     * Used by GET_PUBLISHED_EXAMS so the publisher dashboard can show counts.
     */
    public static String examToJsonWithParticipantCount(Exam exam, int participantCount) {
        // Strip the trailing "}" and inject the extra field
        String base = examToJson(exam);
        return base.substring(0, base.length() - 1)
                + ",\"participantCount\":" + participantCount + "}";
    }

    public static String questionToJson(Question q) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        appendString(sb, "questionId", q.getQuestionId()); sb.append(",");
        appendString(sb, "questionText", q.getQuestionText()); sb.append(",");
        appendInt(sb, "correctOptionIndex", q.getCorrectOptionIndex()); sb.append(",");
        appendInt(sb, "marks", q.getMarks()); sb.append(",");
        if (q.getImageBase64() != null) {
            appendString(sb, "imageBase64", q.getImageBase64()); sb.append(",");
        }
        sb.append("\"options\":[");
        List<String> opts = q.getOptions();
        for (int i = 0; i < opts.size(); i++) {
            sb.append("\"").append(escape(opts.get(i))).append("\"");
            if (i < opts.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    // ============================================================
    // JSON  →  EXAM
    // ============================================================

    public static Exam examFromJson(String json) {
        Exam exam = new Exam();
        exam.setExamId(parseString(json, "examId"));
        exam.setTitle(parseString(json, "title"));
        exam.setDescription(parseString(json, "description"));
        exam.setDurationMinutes(parseInt(json, "durationMinutes"));
        exam.setTotalMarks(parseInt(json, "totalMarks"));
        exam.setNegativeMarking(parseBool(json, "negativeMarking"));
        exam.setShuffleOptions(parseBool(json, "shuffleOptions"));
        String typeStr = parseString(json, "examType");
        exam.setExamType(typeStr.isEmpty() ? Exam.ExamType.PRACTICE : Exam.ExamType.valueOf(typeStr));
        exam.setPublisherUsername(parseString(json, "publisherUsername"));
        exam.setPublic(parseBool(json, "isPublic"));
        exam.setProtected(parseBool(json, "isProtected"));
        exam.setPassword(parseString(json, "password"));
        String st = parseString(json, "startTime");
        if (!st.isEmpty()) exam.setStartTime(LocalDateTime.parse(st, DT_FORMAT));
        String et = parseString(json, "endTime");
        if (!et.isEmpty()) exam.setEndTime(LocalDateTime.parse(et, DT_FORMAT));
        // Present in GET_PUBLISHED_EXAMS payloads.
        exam.setParticipantCount(parseInt(json, "participantCount"));

        // [ADDED] Restore assignedUsers
        String auArr = extractArray(json, "assignedUsers");
        if (!auArr.isEmpty()) {
            exam.getAssignedUsers().addAll(parseStringArray(auArr));
        }

        String questionsJson = extractArray(json, "questions");
        if (!questionsJson.isEmpty()) {
            List<String> questionObjects = splitObjectArray(questionsJson);
            List<Question> questions = new ArrayList<>();
            for (String qJson : questionObjects) {
                questions.add(questionFromJson(qJson));
            }
            exam.setQuestions(questions);
        }
        return exam;
    }

    public static Question questionFromJson(String json) {
        Question q = new Question();
        q.setQuestionId(parseString(json, "questionId"));
        q.setQuestionText(parseString(json, "questionText"));
        q.setCorrectOptionIndex(parseInt(json, "correctOptionIndex"));
        q.setMarks(parseInt(json, "marks"));
        q.setImageBase64(parseString(json, "imageBase64"));
        String optsJson = extractArray(json, "options");
        q.setOptions(parseStringArray(optsJson));
        return q;
    }

    // ============================================================
    // EXAM RESULT  →  JSON  and back
    // ============================================================

    /**
     * [CHANGED] Now serializes the full QuestionResult breakdown array
     * so results survive server restarts and "View Result" always works.
     */
    public static String resultToJson(ExamResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        appendString(sb, "resultId", r.getResultId()); sb.append(",");
        appendString(sb, "examId", r.getExamId()); sb.append(",");
        appendString(sb, "examTitle", r.getExamTitle()); sb.append(",");
        appendString(sb, "publisherUsername", r.getPublisherUsername()); sb.append(",");
        appendString(sb, "studentUsername", r.getStudentUsername()); sb.append(",");
        appendInt(sb, "score", r.getScore()); sb.append(",");
        appendInt(sb, "totalMarks", r.getTotalMarks()); sb.append(",");
        appendInt(sb, "correctCount", r.getCorrectCount()); sb.append(",");
        appendInt(sb, "wrongCount", r.getWrongCount()); sb.append(",");
        appendInt(sb, "totalQuestions", r.getTotalQuestions()); sb.append(",");
        appendString(sb, "attemptedAt",
                r.getAttemptedAt() != null ? r.getAttemptedAt().format(DT_FORMAT) : ""); sb.append(",");
        appendString(sb, "examType", r.getExamType() != null ? r.getExamType().name() : ""); sb.append(",");

        // [ADDED] Serialize questionResults breakdown
        sb.append("\"questionResults\":[");
        List<ExamResult.QuestionResult> qrs = r.getQuestionResults();
        if (qrs != null) {
            for (int i = 0; i < qrs.size(); i++) {
                ExamResult.QuestionResult qr = qrs.get(i);
                sb.append("{");
                appendString(sb, "questionText",   qr.getQuestionText());   sb.append(",");
                appendString(sb, "selectedOption", qr.getSelectedOption()); sb.append(",");
                appendString(sb, "correctOption",  qr.getCorrectOption());  sb.append(",");
                appendBool(sb, "correct", qr.isCorrect());
                sb.append("}");
                if (i < qrs.size() - 1) sb.append(",");
            }
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    /**
     * [CHANGED] Now restores the QuestionResult breakdown array from JSON.
     */
    public static ExamResult resultFromJson(String json) {
        ExamResult r = new ExamResult();
        r.setResultId(parseString(json, "resultId"));
        r.setExamId(parseString(json, "examId"));
        r.setExamTitle(parseString(json, "examTitle"));
        r.setPublisherUsername(parseString(json, "publisherUsername"));
        r.setStudentUsername(parseString(json, "studentUsername"));
        r.setScore(parseInt(json, "score"));
        r.setTotalMarks(parseInt(json, "totalMarks"));
        r.setCorrectCount(parseInt(json, "correctCount"));
        r.setWrongCount(parseInt(json, "wrongCount"));
        r.setTotalQuestions(parseInt(json, "totalQuestions"));
        String at = parseString(json, "attemptedAt");
        if (!at.isEmpty()) r.setAttemptedAt(LocalDateTime.parse(at, DT_FORMAT));
        String typeStr = parseString(json, "examType");
        if (!typeStr.isEmpty()) r.setExamType(Exam.ExamType.valueOf(typeStr));

        // [ADDED] Restore questionResults breakdown
        String qrsArr = extractArray(json, "questionResults");
        if (!qrsArr.isEmpty()) {
            List<ExamResult.QuestionResult> breakdown = new ArrayList<>();
            for (String qrJson : splitObjectArray(qrsArr)) {
                String qText    = parseString(qrJson, "questionText");
                String selected = parseString(qrJson, "selectedOption");
                String correct  = parseString(qrJson, "correctOption");
                boolean isOk    = parseBool(qrJson, "correct");
                breakdown.add(new ExamResult.QuestionResult(qText, selected, correct, isOk));
            }
            r.setQuestionResults(breakdown);
        }
        return r;
    }

    // ============================================================
    // ASSIGNMENT  →  JSON
    // ============================================================

    public static String assignmentToJson(String examId, List<String> usernames) {
        return assignmentToJson(examId, usernames, false, false, "");
    }

    public static String assignmentToJson(String examId, List<String> usernames, boolean isPublic, boolean isProtected, String password) {
        StringBuilder sb = new StringBuilder("{");
        appendString(sb, "examId", examId);
        sb.append(",");
        appendBool(sb, "isPublic", isPublic);
        sb.append(",");
        appendBool(sb, "isProtected", isProtected);
        sb.append(",");
        appendString(sb, "password", password);
        sb.append(",\"assignedUsers\":[");
        for (int i = 0; i < usernames.size(); i++) {
            sb.append("\"").append(escape(usernames.get(i))).append("\"");
            if (i < usernames.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    public static String submitPayloadToJson(String examId, List<Integer> answers) {
        StringBuilder sb = new StringBuilder("{");
        appendString(sb, "examId", examId);
        sb.append(",\"answers\":[");
        for (int i = 0; i < answers.size(); i++) {
            sb.append(answers.get(i));
            if (i < answers.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    public static List<Integer> answersFromJson(String json) {
        String arr = extractArray(json, "answers");
        List<Integer> result = new ArrayList<>();
        if (arr.isEmpty()) return result;
        for (String s : arr.split(",")) {
            s = s.trim();
            if (!s.isEmpty()) result.add(Integer.parseInt(s));
        }
        return result;
    }

    // ============================================================
    // NETWORK PROTOCOL HELPERS
    // ============================================================

    public static String buildMessage(String command, String payload) {
        return command + " " + payload + "\n";
    }

    public static String parseCommand(String message) {
        if (message == null || message.isBlank()) return "";
        int space = message.indexOf(' ');
        return space == -1 ? message.trim() : message.substring(0, space).trim();
    }

    public static String parsePayload(String message) {
        if (message == null || message.isBlank()) return "{}";
        int space = message.indexOf(' ');
        return space == -1 ? "{}" : message.substring(space + 1).trim();
    }

    // ============================================================
    // LOW-LEVEL HELPERS
    // ============================================================

    static void appendString(StringBuilder sb, String key, String value) {
        sb.append("\"").append(key).append("\":\"").append(escape(value == null ? "" : value)).append("\"");
    }

    static void appendInt(StringBuilder sb, String key, int value) {
        sb.append("\"").append(key).append("\":").append(value);
    }

    static void appendBool(StringBuilder sb, String key, boolean value) {
        sb.append("\"").append(key).append("\":").append(value);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    public static String parseString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return "";
        start += pattern.length();
        int end = start;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\') break;
            end++;
        }
        return json.substring(start, end)
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\\\", "\\");
    }

    public static int parseInt(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return 0;
        start += pattern.length();
        while (start < json.length() && json.charAt(start) == ' ') start++;
        // Skip over a leading '"' if this is a string-valued int (shouldn't happen but guard)
        if (start < json.length() && json.charAt(start) == '"') return 0;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        try { return Integer.parseInt(json.substring(start, end)); } catch (Exception e) { return 0; }
    }

    public static boolean parseBool(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return false;
        start += pattern.length();
        while (start < json.length() && json.charAt(start) == ' ') start++;
        return json.startsWith("true", start);
    }

    public static String extractArray(String json, String key) {
        String pattern = "\"" + key + "\":[";
        int start = json.indexOf(pattern);
        if (start == -1) return "";
        start += pattern.length();
        int depth = 1;
        int end = start;
        while (end < json.length() && depth > 0) {
            char c = json.charAt(end);
            if (c == '[') depth++;
            else if (c == ']') depth--;
            if (depth > 0) end++;
        }
        return json.substring(start, end);
    }

    public static List<String> splitObjectArray(String arrayContent) {
        List<String> items = new ArrayList<>();
        int i = 0;
        while (i < arrayContent.length()) {
            if (arrayContent.charAt(i) == '{') {
                int depth = 0, start = i;
                while (i < arrayContent.length()) {
                    char c = arrayContent.charAt(i);
                    if (c == '{') depth++;
                    else if (c == '}') { depth--; if (depth == 0) { i++; break; } }
                    i++;
                }
                items.add(arrayContent.substring(start, i));
            } else {
                i++;
            }
        }
        return items;
    }

    public static List<String> parseStringArray(String arrayContent) {
        List<String> result = new ArrayList<>();
        int i = 0;
        while (i < arrayContent.length()) {
            if (arrayContent.charAt(i) == '"') {
                i++;
                StringBuilder sb = new StringBuilder();
                while (i < arrayContent.length()) {
                    char c = arrayContent.charAt(i);
                    if (c == '"' && arrayContent.charAt(i - 1) != '\\') break;
                    sb.append(c);
                    i++;
                }
                result.add(sb.toString().replace("\\\"", "\"").replace("\\n", "\n"));
            }
            i++;
        }
        return result;
    }

    public static List<String> parseUsernameList(String json) {
        String arr = extractArray(json, "users");
        if (arr.isEmpty()) return parseStringArray(json);
        List<String> names = new ArrayList<>();
        for (String obj : splitObjectArray(arr)) {
            String u = parseString(obj, "username");
            if (!u.isEmpty()) names.add(u);
        }
        return names;
    }

    public static List<Exam> parseExamList(String json) {
        String arr = extractArray(json, "exams");
        List<Exam> exams = new ArrayList<>();
        for (String obj : splitObjectArray(arr)) {
            exams.add(examFromJson(obj));
        }
        return exams;
    }

    public static List<ExamResult> parseResultList(String json) {
        String arr = extractArray(json, "results");
        List<ExamResult> results = new ArrayList<>();
        for (String obj : splitObjectArray(arr)) {
            results.add(resultFromJson(obj));
        }
        return results;
    }

    /**
     * [ADDED] Parses participants list from GET_EXAM_PARTICIPANTS response.
     */
    public static List<ExamResult> parseParticipantList(String json) {
        String arr = extractArray(json, "participants");
        List<ExamResult> results = new ArrayList<>();
        for (String obj : splitObjectArray(arr)) {
            results.add(resultFromJson(obj));
        }
        return results;
    }
}
