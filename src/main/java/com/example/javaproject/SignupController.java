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

public class SignupController {

    // ============================================================
    // FXML INJECTED FIELDS
    // ============================================================

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;

    /** Card VBox — used for entrance / exit animations */
    @FXML
    private VBox signupCard;

    // ============================================================
    // INITIALISATION
    // ============================================================

    @FXML
    public void initialize() {
        // ── Entrance: slide up + fade in ──
        if (signupCard != null) {
            signupCard.setOpacity(0);
            signupCard.setTranslateY(28);

            FadeTransition fade = new FadeTransition(Duration.millis(500), signupCard);
            fade.setFromValue(0.0);
            fade.setToValue(1.0);
            fade.setInterpolator(Interpolator.EASE_OUT);

            TranslateTransition slide = new TranslateTransition(Duration.millis(500), signupCard);
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
    }

    // ============================================================
    // HANDLERS (logic identical to source)
    // ============================================================

    @FXML
    private void handleSignupButtonClick(ActionEvent event) throws IOException {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            shakeField(username.isEmpty() ? usernameField : passwordField);
            showAlert(Alert.AlertType.WARNING, "Signup Failed", "Please fill in all fields.");
            return;
        }
        if (UserFileManager.isUsernameTaken(username)) {
            shakeField(usernameField);
            showAlert(Alert.AlertType.ERROR, "Signup Failed", "Username already exists!");
        } else {
            UserFileManager.addUser(username, password);
            UserFileManager.createUserFiles(username);
            showAlert(Alert.AlertType.INFORMATION, "Signup Successful", "Account created! Please log in.");
            Main.changeScene(event, "fxml files/login.fxml");
        }
    }

    @FXML
    private void handleLoginButtonClick(ActionEvent event) throws IOException {
        Main.changeScene(event, "fxml files/login.fxml");
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
