package com.example.javaproject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.fxml.FXMLLoader;

import java.io.IOException;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    public void onLoginButtonClick(ActionEvent event) throws IOException {
        String username = usernameField.getText();
        String password = passwordField.getText();

        // For demonstration purposes, let's assume successful login
//        if ("user".equals(username) && "pass".equals(password)) {
//            Main.changeScene(event, "fxml files/dashboard.fxml");
//        } else {
//            System.out.println("Invalid credentials");
//        }
        Main.changeScene(event, "fxml files/dashboard.fxml");
    }
}