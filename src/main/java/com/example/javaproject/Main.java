package com.example.javaproject;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {
    static Stage stage;
    @Override
    public void start(Stage pstage) throws IOException {
        stage=pstage;
        Parent root=FXMLLoader.load(getClass().getResource("fxml files/login.fxml"));
        Scene scene = new Scene(root);
        stage.setTitle("Hello!");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }

    public static void changeScene(ActionEvent event, String fxmlFile) throws IOException {
        Parent root = FXMLLoader.load(Main.class.getResource(fxmlFile));
//        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        Scene scene = new Scene(root);
        stage.setMaximized(false);
        stage.setScene(scene);
        stage.setMaximized(true);
    }
}
