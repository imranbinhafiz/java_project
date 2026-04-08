package com.example.javaproject;

import okhttp3.*;
import com.google.gson.*;
import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Service class that communicates with Google Gemini API.
 * Supports text chat with conversation history, image input, and unlimited response length.
 */
public class GeminiApiService {

    private static final String GEMINI_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private final String apiKey;
    private final String model;
    private final OkHttpClient httpClient;
    private final Gson gson;

    /** Conversation history for multi-turn context */
    private final List<JsonObject> conversationHistory = new ArrayList<>();

    /** System instruction to make the AI respond with well-structured markdown */
    private static final String SYSTEM_INSTRUCTION =
            "You are a helpful, knowledgeable AI study assistant. " +
            "Always format your responses using Markdown for readability. " +
            "Structure responses like ChatGPT: start with a brief summary, then clear section headers, " +
            "then bullet or numbered lists and step-by-step guidance when helpful. " +
            "Use **bold** for emphasis, `code` for inline code, ```language for code blocks, " +
            "- or * for bullet lists, 1. for numbered lists, and ### for section headers. " +
            "Provide well-structured, detailed, and comprehensive answers. " +
            "Do not truncate or shorten your responses. Give complete answers.";

    public GeminiApiService() {
        Properties props = loadConfig();
        this.apiKey = props.getProperty("GEMINI_API_KEY", "").trim();
        String rawModel = props.getProperty("GEMINI_MODEL", "gemini-3.1-flash-lite-preview");
        this.model = normalizeModelId(rawModel, "gemini-3.1-flash-lite-preview");

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)   // Increased for longer responses
                .writeTimeout(60, TimeUnit.SECONDS)    // Increased for image uploads
                .build();

        this.gson = new Gson();
    }

    // ─── Public API ────────────────────────────────────────────────────────────

    /**
     * Sends a user text message to Gemini with full conversation history.
     * Blocking – call from a background thread.
     *
     * @param userMessage the question typed by the user
     * @return the model's reply text, or an error string prefixed with "ERROR:"
     */
    public String ask(String userMessage) {
        if (apiKey.isBlank()) {
            return "ERROR: API key is missing. Please check data/api_config.properties.";
        }

        // Build user content with text only
        JsonObject userContent = buildTextContent("user", userMessage);
        conversationHistory.add(userContent);

        String url  = String.format(GEMINI_BASE_URL, model, apiKey);
        String body = buildRequestBody();

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body, MediaType.get("application/json; charset=utf-8")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                // Remove the failed user message from history
                conversationHistory.remove(conversationHistory.size() - 1);
                return parseErrorMessage(responseBody, response.code());
            }

            String reply = parseSuccessResponse(responseBody);
            if (reply.startsWith("ERROR:")) {
                conversationHistory.remove(conversationHistory.size() - 1);
                return reply;
            }

            // Add model response to history for context
            JsonObject modelContent = buildTextContent("model", reply);
            conversationHistory.add(modelContent);

            return reply;

        } catch (IOException e) {
            conversationHistory.remove(conversationHistory.size() - 1);
            return "ERROR: Network error – " + e.getMessage();
        }
    }

    /**
     * Sends a user message with an attached image to Gemini.
     * Blocking – call from a background thread.
     *
     * @param userMessage the text message (can be empty for image-only)
     * @param imageFile   the image file to send
     * @return the model's reply text, or an error string prefixed with "ERROR:"
     */
    public String askWithImage(String userMessage, File imageFile) {
        if (apiKey.isBlank()) {
            return "ERROR: API key is missing. Please check data/api_config.properties.";
        }

        if (imageFile == null || !imageFile.exists()) {
            return "ERROR: Image file not found.";
        }

        try {
            // Build user content with text + image
            JsonObject userContent = buildImageContent(userMessage, imageFile);
            conversationHistory.add(userContent);

            String url  = String.format(GEMINI_BASE_URL, model, apiKey);
            String body = buildRequestBody();

            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body, MediaType.get("application/json; charset=utf-8")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    conversationHistory.remove(conversationHistory.size() - 1);
                    return parseErrorMessage(responseBody, response.code());
                }

                String reply = parseSuccessResponse(responseBody);
                if (reply.startsWith("ERROR:")) {
                    conversationHistory.remove(conversationHistory.size() - 1);
                    return reply;
                }

                // Add model response to history
                JsonObject modelContent = buildTextContent("model", reply);
                conversationHistory.add(modelContent);

                return reply;
            }

        } catch (IOException e) {
            if (!conversationHistory.isEmpty()) {
                conversationHistory.remove(conversationHistory.size() - 1);
            }
            return "ERROR: Failed to process image – " + e.getMessage();
        }
    }

    /**
     * Clears the conversation history for a fresh start.
     */
    public void clearHistory() {
        conversationHistory.clear();
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

    private JsonObject buildTextContent(String role, String text) {
        JsonObject part = new JsonObject();
        part.addProperty("text", text);

        JsonArray parts = new JsonArray();
        parts.add(part);

        JsonObject content = new JsonObject();
        content.addProperty("role", role);
        content.add("parts", parts);
        return content;
    }

    private JsonObject buildImageContent(String text, File imageFile) throws IOException {
        JsonArray parts = new JsonArray();

        // Add text part if present
        if (text != null && !text.isBlank()) {
            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", text);
            parts.add(textPart);
        } else {
            // Default prompt for image-only
            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", "Describe and analyze this image in detail.");
            parts.add(textPart);
        }

        // Add image part as inline base64
        byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
        String base64Data = Base64.getEncoder().encodeToString(imageBytes);
        String mimeType = detectMimeType(imageFile);

        JsonObject inlineData = new JsonObject();
        inlineData.addProperty("mimeType", mimeType);
        inlineData.addProperty("data", base64Data);

        JsonObject imagePart = new JsonObject();
        imagePart.add("inlineData", inlineData);
        parts.add(imagePart);

        JsonObject content = new JsonObject();
        content.addProperty("role", "user");
        content.add("parts", parts);
        return content;
    }

    private String buildRequestBody() {
        JsonObject root = new JsonObject();

        // Add system instruction
        JsonObject systemInstruction = new JsonObject();
        JsonArray systemParts = new JsonArray();
        JsonObject systemPart = new JsonObject();
        systemPart.addProperty("text", SYSTEM_INSTRUCTION);
        systemParts.add(systemPart);
        systemInstruction.add("parts", systemParts);
        root.add("systemInstruction", systemInstruction);

        // Add full conversation history
        JsonArray contents = new JsonArray();
        for (JsonObject content : conversationHistory) {
            contents.add(content);
        }
        root.add("contents", contents);

        // Generation config – NO token limit for full responses
        JsonObject genConfig = new JsonObject();
        genConfig.addProperty("temperature", 0.7);
        genConfig.addProperty("topP", 0.95);
        genConfig.addProperty("topK", 40);
        // maxOutputTokens intentionally omitted to allow maximum response length
        root.add("generationConfig", genConfig);

        return gson.toJson(root);
    }

    private String parseSuccessResponse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return "ERROR: No response generated. Try rephrasing your question.";
            }
            JsonObject candidate = candidates.get(0).getAsJsonObject();
            JsonObject contentObj = candidate.getAsJsonObject("content");
            if (contentObj == null) return "ERROR: Empty response from API.";
            JsonArray parts = contentObj.getAsJsonArray("parts");
            if (parts == null || parts.isEmpty()) return "ERROR: Empty parts in response.";

            // Concatenate all text parts (for multi-part responses)
            StringBuilder fullText = new StringBuilder();
            for (JsonElement partEl : parts) {
                JsonObject partObj = partEl.getAsJsonObject();
                if (partObj.has("text")) {
                    fullText.append(partObj.get("text").getAsString());
                }
            }
            return fullText.toString().trim();
        } catch (Exception e) {
            return "ERROR: Could not parse API response – " + e.getMessage();
        }
    }

    private String parseErrorMessage(String json, int httpCode) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject errorObj = root.getAsJsonObject("error");
            String msg = errorObj != null && errorObj.has("message")
                    ? errorObj.get("message").getAsString()
                    : null;
            if (msg != null) {
                if (httpCode == 404 && msg.toLowerCase().contains("models/")) {
                    return "ERROR: [" + httpCode + "] " + msg
                            + " (Check GEMINI_MODEL; try a current model like gemini-3.1-flash-lite-preview.)";
                }
                return "ERROR: [" + httpCode + "] " + msg;
            }
        } catch (Exception ignored) {}
        return "ERROR: HTTP " + httpCode + " - " + json;
    }

    private static String normalizeModelId(String raw, String fallback) {
        if (raw == null) return fallback;
        String m = raw.trim();
        if (m.startsWith("models/")) {
            m = m.substring("models/".length());
        }
        return m.isBlank() ? fallback : m;
    }

    private String detectMimeType(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".bmp")) return "image/bmp";
        // Default to jpeg
        return "image/jpeg";
    }

    private Properties loadConfig() {
        Properties props = new Properties();
        // Try loading from the data/ directory next to the project root
        File configFile = new File("data/api_config.properties");
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                return props;
            } catch (IOException ignored) {}
        }
        // Fallback: classpath resource
        try (InputStream is = getClass().getResourceAsStream("/api_config.properties")) {
            if (is != null) props.load(is);
        } catch (IOException ignored) {}
        return props;
    }
}
