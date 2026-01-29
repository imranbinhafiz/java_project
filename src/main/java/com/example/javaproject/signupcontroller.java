package com.example.javaproject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;

import java.io.IOException;

public class signupcontroller {

    @FXML
    private Button loginButton;

    @FXML
    private void handleLoginButtonClick(ActionEvent event) throws IOException {
        Main.changeScene(event,"fxml files/login.fxml");
    }
}
