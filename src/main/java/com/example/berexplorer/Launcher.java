package com.example.berexplorer;

import javafx.application.Application;

/** Classpath entry point for the shaded JAR. */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        Application.launch(Main.class, args);
    }
}
