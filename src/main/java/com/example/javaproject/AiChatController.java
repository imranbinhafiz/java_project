package com.example.javaproject;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiChatController {

    // ── FXML ──────────────────────────────────────────────────────────────────
    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox       chatContainer;
    @FXML private TextField  inputField;
    @FXML private Button     sendButton;
    @FXML private Button     clearButton;
    @FXML private Button     attachButton;
    @FXML private HBox       typingIndicator;
    @FXML private HBox       suggestionChips;
    @FXML private Label      attachedFileLabel;
    @FXML private HBox       attachedFileBar;

    // ── Internals ──────────────────────────────────────────────────────────────
    private final GeminiApiService geminiService = new GeminiApiService();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "gemini-worker");
        t.setDaemon(true);
        return t;
    });

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private File   attachedImage   = null;
    private String lastUserMessage = null;

    // ── Init ───────────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        // Auto-scroll to bottom when chat grows
        chatContainer.heightProperty().addListener((obs, o, n) ->
                chatScrollPane.setVvalue(1.0));

        inputField.setOnAction(e -> handleSend());
        sendButton.setDisable(true);
        inputField.textProperty().addListener((obs, o, n) -> updateSendState());

        typingIndicator.setVisible(false);
        typingIndicator.setManaged(false);
        attachedFileBar.setVisible(false);
        attachedFileBar.setManaged(false);

        setupChips();

//        appendAiBubble(
//            "### 👋 Welcome!\n\n" +
//            "I'm your **AI Study Assistant** powered by Gemini. I can help you with:\n\n" +
//            "- 📚 **Explaining concepts** in any subject\n" +
//            "- 📝 **Creating quizzes** and practice questions\n" +
//            "- 🖼️ **Analyzing images** – just attach a photo!\n" +
//            "- 💡 **Providing examples** and step-by-step solutions\n\n" +
//            "Ask me anything or try the quick actions below! 🚀",
//            false
//        );
    }

    // ── FXML handlers ──────────────────────────────────────────────────────────
    @FXML
    private void handleSend() {
        String text = inputField.getText().trim();
        if (text.isBlank() && attachedImage == null) return;

        inputField.clear();
        sendButton.setDisable(true);

        if (attachedImage != null) appendUserImageBubble(text, attachedImage);
        else appendUserBubble(text);

        lastUserMessage = text;
        final File img = attachedImage;
        clearAttach();
        showTyping(true);
        hideSuggestions();

        executor.submit(() -> {
            String reply = img != null
                ? geminiService.askWithImage(text, img)
                : geminiService.ask(text);
            Platform.runLater(() -> {
                showTyping(false);
                if (reply.startsWith("ERROR:")) appendErrBubble(reply.substring(6).trim());
                else appendAiBubble(reply, true);
                showSuggestions();
                updateSendState();
            });
        });
    }

    @FXML private void handleClear() {
        chatContainer.getChildren().clear();
        geminiService.clearHistory();
        lastUserMessage = null;
        clearAttach();
//        appendAiBubble("### 🔄 Chat Cleared\n\nFresh start! Ask me anything. 🚀", false);
        showSuggestions();
    }

    @FXML private void handleAttach() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select an Image");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Images","*.png","*.jpg","*.jpeg","*.gif","*.webp","*.bmp"),
            new FileChooser.ExtensionFilter("All Files","*.*")
        );
        File f = fc.showOpenDialog(attachButton.getScene().getWindow());
        if (f != null) {
            attachedImage = f;
            attachedFileLabel.setText("📎 " + f.getName());
            attachedFileBar.setVisible(true);
            attachedFileBar.setManaged(true);
            AnimationUtil.fadeIn(attachedFileBar, 200, null);
            updateSendState();
        }
    }

    @FXML private void handleRemoveAttachment() { clearAttach(); }

    // ── User bubble ────────────────────────────────────────────────────────────
    private void appendUserBubble(String message) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setPadding(new Insets(4, 14, 4, 60));

        VBox bubble = new VBox(4);
        bubble.getStyleClass().add("chat-bubble-user");
        bubble.setMaxWidth(560);

        Label txt = new Label(message);
        txt.setWrapText(true);
        txt.getStyleClass().add("chat-bubble-text-user");

        Label time = new Label(LocalTime.now().format(TIME_FMT));
        time.getStyleClass().add("chat-bubble-time-user");
        HBox tr = new HBox(time);
        tr.setAlignment(Pos.CENTER_RIGHT);

        bubble.getChildren().addAll(txt, tr);
        row.getChildren().add(bubble);
        chatContainer.getChildren().add(row);
        AnimationUtil.fadeIn(row, 300, null);
    }

    private void appendUserImageBubble(String message, File imageFile) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setPadding(new Insets(4, 14, 4, 60));

        VBox bubble = new VBox(6);
        bubble.getStyleClass().add("chat-bubble-user");
        bubble.setMaxWidth(560);

        try {
            Image img = new Image(imageFile.toURI().toString(), 300, 200, true, true);
            ImageView iv = new ImageView(img);
            iv.setFitWidth(300);
            iv.setPreserveRatio(true);
            iv.getStyleClass().add("chat-image-preview");
            double h = img.getHeight() > 0 ? img.getHeight() * (300.0 / img.getWidth()) : 180;
            javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(300, Math.max(60, h));
            clip.setArcWidth(14); clip.setArcHeight(14);
            iv.setClip(clip);
            bubble.getChildren().add(iv);
        } catch (Exception e) {
            bubble.getChildren().add(lbl("⚠️ Could not load image", "chat-bubble-text-user"));
        }

        if (message != null && !message.isBlank()) {
            Label txt = new Label(message);
            txt.setWrapText(true);
            txt.getStyleClass().add("chat-bubble-text-user");
            bubble.getChildren().add(txt);
        }

        Label time = new Label(LocalTime.now().format(TIME_FMT));
        time.getStyleClass().add("chat-bubble-time-user");
        HBox tr = new HBox(time);
        tr.setAlignment(Pos.CENTER_RIGHT);
        bubble.getChildren().add(tr);

        row.getChildren().add(bubble);
        chatContainer.getChildren().add(row);
        AnimationUtil.fadeIn(row, 300, null);
    }

    // ── AI bubble ──────────────────────────────────────────────────────────────
    private void appendAiBubble(String message, boolean animate) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(6, 12, 6, 12));
        row.setMaxWidth(Double.MAX_VALUE);

        // Avatar
        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("chat-ai-avatar");
        avatar.setMinSize(36, 36);
        avatar.setMaxSize(36, 36);
        Label av = new Label("✦");
        av.getStyleClass().add("chat-ai-avatar-text");
        avatar.getChildren().add(av);

        // Bubble container
        VBox bubble = new VBox(5);
        bubble.getStyleClass().add("chat-bubble-ai");
        bubble.setMinWidth(0);
        bubble.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(bubble, Priority.ALWAYS);

        Label sender = new Label("AI Assistant");
        sender.getStyleClass().add("chat-bubble-sender");

        // ── WebView ────────────────────────────────────────────────
        WebView wv = HtmlRenderer.createMessageView(message, 600);
        wv.setStyle("-fx-background-color: #0f1117;");
        wv.setMinWidth(80);
        wv.setMaxWidth(Double.MAX_VALUE);
        wv.setMinHeight(40);
        HBox.setHgrow(wv, Priority.ALWAYS);
        VBox.setVgrow(wv, Priority.NEVER);

        // ── FIX: Forward scroll events from WebView up to the outer ScrollPane ──
        // This makes mouse-wheel scrolling work on the chat window even when
        // the mouse is hovering over a WebView bubble.
        wv.addEventFilter(ScrollEvent.SCROLL, event -> {
            // Consume the event for the WebView (it has overflow:auto internally)
            // but also propagate the scroll delta to the outer ScrollPane
            double deltaY = event.getDeltaY();
            double vval   = chatScrollPane.getVvalue();
            double vmax   = chatScrollPane.getVmax();
            double vmin   = chatScrollPane.getVmin();
            double range  = vmax - vmin;
            // Scroll the outer pane by a proportional amount
            double step   = range * 0.06 * (deltaY > 0 ? -1 : 1);
            chatScrollPane.setVvalue(Math.max(vmin, Math.min(vmax, vval + step)));
        });

        // ── Bind width to bubble width ─────────────────────────────
        bubble.widthProperty().addListener((obs, oldW, newW) -> {
            double w = Math.max(80, newW.doubleValue() - 36);
            wv.setPrefWidth(w);
            wv.setMaxWidth(w);
            // Re-measure height after width-driven reflow
            PauseTransition reflow = new PauseTransition(Duration.millis(120));
            reflow.setOnFinished(e -> {
                try {
                    Object h = wv.getEngine().executeScript(
                        "Math.max(document.body.scrollHeight,document.documentElement.scrollHeight)");
                    if (h instanceof Number nn && nn.doubleValue() > 5) {
                        double height = nn.doubleValue() + 16;
                        wv.setPrefHeight(height);
                        wv.setMinHeight(height);
                    }
                } catch (Exception ignored) {}
            });
            reflow.play();
        });

        // Footer
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_LEFT);

        Label time = new Label(LocalTime.now().format(TIME_FMT));
        time.getStyleClass().add("chat-bubble-time-ai");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label copyBtn = new Label("📋");
        copyBtn.getStyleClass().add("chat-action-btn");
        copyBtn.setTooltip(new Tooltip("Copy response"));
        copyBtn.setOnMouseClicked(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(message);
            Clipboard.getSystemClipboard().setContent(cc);
            copyBtn.setText("✓");
            PauseTransition p = new PauseTransition(Duration.seconds(2));
            p.setOnFinished(ev -> copyBtn.setText("📋"));
            p.play();
        });

        footer.getChildren().addAll(time, spacer, copyBtn);
        bubble.getChildren().addAll(sender, wv, footer);
        row.getChildren().addAll(avatar, bubble);
        chatContainer.getChildren().add(row);
        if (animate) AnimationUtil.fadeIn(row, 400, null);
    }

    // ── Error bubble ───────────────────────────────────────────────────────────
    private void appendErrBubble(String message) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(6, 12, 6, 12));
        row.setMaxWidth(Double.MAX_VALUE);

        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("chat-ai-avatar-error");
        avatar.setMinSize(36, 36);
        avatar.setMaxSize(36, 36);
        Label av = new Label("⚠");
        av.getStyleClass().add("chat-ai-avatar-text-error");
        avatar.getChildren().add(av);

        VBox bubble = new VBox(6);
        bubble.getStyleClass().addAll("chat-bubble-ai", "chat-bubble-error");
        bubble.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(bubble, Priority.ALWAYS);

        Label errTxt = new Label("Something went wrong: " + message);
        errTxt.setWrapText(true);
        errTxt.getStyleClass().add("chat-error-text");

        Label retryBtn = new Label("🔄 Retry");
        retryBtn.getStyleClass().add("chat-retry-btn");
        retryBtn.setOnMouseClicked(e -> {
            if (lastUserMessage != null && !lastUserMessage.isBlank()) {
                chatContainer.getChildren().remove(row);
                inputField.setText(lastUserMessage);
                handleSend();
            }
        });

        bubble.getChildren().addAll(errTxt, new HBox(retryBtn));
        row.getChildren().addAll(avatar, bubble);
        chatContainer.getChildren().add(row);
        AnimationUtil.fadeIn(row, 300, null);
    }

    // ── Typing indicator ───────────────────────────────────────────────────────
    private void showTyping(boolean v) {
        typingIndicator.setVisible(v);
        typingIndicator.setManaged(v);
        if (v) AnimationUtil.fadeIn(typingIndicator, 200, null);
    }

    // ── Suggestion chips ───────────────────────────────────────────────────────
    private void setupChips() {
        String[][] chips = {
            {"💡","Explain a concept"},
            {"📝","Create a quiz"},
            {"🔍","Summarize a topic"},
            {"💻","Help with code"}
        };
        suggestionChips.getChildren().clear();
        for (String[] c : chips) {
            Label chip = new Label(c[0] + " " + c[1]);
            chip.getStyleClass().add("suggestion-chip");
            chip.setOnMouseClicked(e -> {
                inputField.setText(c[1] + ": ");
                inputField.requestFocus();
                inputField.positionCaret(inputField.getText().length());
            });
            suggestionChips.getChildren().add(chip);
        }
    }

    private void hideSuggestions() { suggestionChips.setVisible(false); suggestionChips.setManaged(false); }
    private void showSuggestions() { suggestionChips.setVisible(true);  suggestionChips.setManaged(true); }

    // ── Helpers ────────────────────────────────────────────────────────────────
    private void clearAttach() {
        attachedImage = null;
        attachedFileBar.setVisible(false);
        attachedFileBar.setManaged(false);
        attachedFileLabel.setText("");
        updateSendState();
    }

    private void updateSendState() {
        boolean ok = (inputField.getText() != null && !inputField.getText().isBlank())
                     || attachedImage != null;
        sendButton.setDisable(!ok);
    }

    private static Label lbl(String text, String style) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.getStyleClass().add(style);
        return l;
    }
}
