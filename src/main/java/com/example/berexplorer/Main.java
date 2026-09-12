package com.example.berexplorer;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage stage) {
        MainView view = new MainView(stage);
        Scene scene = new Scene(view.root(), 1400, 850);
        scene.getStylesheets().add(getClass().getResource("/com/example/berexplorer/app.css").toExternalForm());
        stage.setTitle("ASN.1 BER TLV Explorer");
        stage.setMinWidth(1000);
        stage.setMinHeight(650);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
