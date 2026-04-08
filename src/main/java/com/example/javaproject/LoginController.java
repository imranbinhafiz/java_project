package com.example.javaproject;

import java.io.IOException;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class LoginController {

    // ============================================================
    // FXML INJECTED FIELDS
    // ============================================================

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField serverIpField;

    /** Card VBox — used for entrance / exit animations */
    @FXML
    private VBox loginCard;

    // ============================================================
    // INITIALISATION
    // ============================================================

    @FXML
    public void initialize() {
        String preferredLanIp = NetworkUtil.getPreferredLanIpv4();
        if (preferredLanIp != null && !preferredLanIp.isBlank()) {
            serverIpField.setPromptText("LAN IP e.g. " + preferredLanIp);
        } else {
            serverIpField.setPromptText("Server LAN IP");
        }

        // Restore previously saved server host from session
        String host = UserSession.getInstance().getServerHost();
        if (host != null && !host.trim().isEmpty() && !"localhost".equalsIgnoreCase(host.trim())) {
            serverIpField.setText(host.trim());
        } else {
            serverIpField.clear();
        }

        // ── Entrance: slide up + fade in ──
        if (loginCard != null) {
            loginCard.setOpacity(0);
            loginCard.setTranslateY(28);

            FadeTransition fade = new FadeTransition(Duration.millis(500), loginCard);
            fade.setFromValue(0.0);
            fade.setToValue(1.0);
            fade.setInterpolator(Interpolator.EASE_OUT);

            TranslateTransition slide = new TranslateTransition(Duration.millis(500), loginCard);
            slide.setFromY(28);
            slide.setToY(0);
            slide.setInterpolator(Interpolator.EASE_OUT);

            ParallelTransition entrance = new ParallelTransition(fade, slide);
            entrance.setDelay(Duration.millis(60));
            entrance.play();
        }

        // ── Focus lift on all input fields ──
        attachFocusScale(usernameField);
        attachFocusScale(passwordField);
        attachFocusScale(serverIpField);
    }

    // ============================================================
    // HANDLERS (logic identical to source)
    // ============================================================

    @FXML
    public void onLoginButtonClick(ActionEvent event) throws IOException {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();
        String serverHost = NetworkUtil.normalizeHostInput(serverIpField.getText());

        if (!NetworkUtil.isValidHostValue(serverHost)) {
            shakeField(serverIpField);
            showAlert(Alert.AlertType.WARNING, "Login Failed", "Please enter a valid server IP/host.");
            return;
        }
        if (username.isEmpty() || password.isEmpty()) {
            shakeField(username.isEmpty() ? usernameField : passwordField);
            showAlert(Alert.AlertType.WARNING, "Login Failed", "Please enter both username and password.");
            return;
        }
        if (!UserFileManager.isUsernameTaken(username)) {
            shakeField(usernameField);
            showAlert(Alert.AlertType.ERROR, "Login Failed", "Username not found!");
        } else if (!UserFileManager.validateLogin(username, password)) {
            shakeField(passwordField);
            showAlert(Alert.AlertType.ERROR, "Login Failed", "Password incorrect!");
        } else {
            UserSession.getInstance().setUsername(username);
            UserSession.getInstance().setServerHost(serverHost);
            ExamServerRuntime.ensureRunningForLocalTarget(serverHost);
            if (!ExamServerRuntime.waitForServer(serverHost, ExamServer.PORT, 8, 350)) {
                showAlert(
                        Alert.AlertType.ERROR,
                        "Server Unreachable",
                        "Could not connect to exam server at " + serverHost + ":" + ExamServer.PORT
                                + ".\nMake sure the host device is running and firewall allows port 9090.");
                return;
            }
            Main.changeScene(event, "fxml files/dashboard.fxml");
        }
    }

    @FXML
    public void onSignUpButtonClick(ActionEvent event) throws IOException {
        Main.changeScene(event, "fxml files/signup.fxml");
    }

    // ============================================================
    // ANIMATION HELPERS
    // ============================================================

    private void attachFocusScale(javafx.scene.control.Control field) {
        if (field == null)
            return;
        field.focusedProperty().addListener((obs, was, isFocused) -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(160), field);
            st.setInterpolator(Interpolator.EASE_BOTH);
            st.setToX(isFocused ? 1.018 : 1.0);
            st.setToY(isFocused ? 1.018 : 1.0);
            st.play();
        });
    }

    private void shakeField(javafx.scene.Node field) {
        if (field == null)
            return;
        TranslateTransition shake = new TranslateTransition(Duration.millis(55), field);
        shake.setByX(9);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.setOnFinished(e -> field.setTranslateX(0));
        shake.play();
    }

    // ============================================================
    // ALERT HELPER
    // ============================================================

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.getDialogPane().setPrefWidth(390);
        var cssUrl = getClass().getResource("css/alert.css");
        if (cssUrl != null)
            alert.getDialogPane().getStylesheets().add(cssUrl.toExternalForm());
        alert.showAndWait();
    }
}
