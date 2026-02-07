package com.example.javaproject;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Utility class for reusable JavaFX animations in the dashboard application.
 * Provides centralized animation management for consistent behavior across the UI.
 */
public class AnimationUtil {

    // Animation durations (in milliseconds)
    public static final int SIDEBAR_COLLAPSE_DURATION = 280;
    public static final int FADE_IN_DURATION = 500;
    public static final int HOVER_SCALE_DURATION = 150;
    public static final int CONTENT_TRANSITION_DURATION = 300;

    /**
     * Animates the sidebar collapse/expand with smooth TranslateTransition.
     * @param sideNav The sidebar VBox to animate
     * @param isCollapsing true to collapse, false to expand
     * @param onFinished Runnable to execute when animation completes
     */
    public static void animateSidebarCollapse(VBox sideNav, boolean isCollapsing, Runnable onFinished) {
        double targetTranslateX = isCollapsing ? -170 : 0;
        double targetWidth = isCollapsing ? 60 : 230;

        // Animate sidebar width
        Timeline widthAnimation = new Timeline();
        widthAnimation.getKeyFrames().add(
            new KeyFrame(Duration.millis(SIDEBAR_COLLAPSE_DURATION),
                new KeyValue(sideNav.prefWidthProperty(), targetWidth, Interpolator.EASE_BOTH))
        );

        // Animate sidebar position
        TranslateTransition slideAnimation = new TranslateTransition(Duration.millis(SIDEBAR_COLLAPSE_DURATION), sideNav);
        slideAnimation.setToX(targetTranslateX);
        slideAnimation.setInterpolator(Interpolator.EASE_BOTH);

        // Play both animations together
        ParallelTransition parallelTransition = new ParallelTransition(widthAnimation, slideAnimation);
        parallelTransition.setOnFinished(e -> {
            if (onFinished != null) {
                onFinished.run();
            }
        });
        parallelTransition.play();
    }

    /**
     * Fades in a node from invisible to fully visible.
     * @param node The node to fade in
     */
    public static void fadeIn(Node node) {
        fadeIn(node, FADE_IN_DURATION, null);
    }

    /**
     * Fades in a node from invisible to fully visible with custom duration and callback.
     * @param node The node to fade in
     * @param durationMillis Duration in milliseconds
     * @param onFinished Runnable to execute when animation completes
     */
    public static void fadeIn(Node node, int durationMillis, Runnable onFinished) {
        node.setOpacity(0.0);
        FadeTransition fadeTransition = new FadeTransition(Duration.millis(durationMillis), node);
        fadeTransition.setFromValue(0.0);
        fadeTransition.setToValue(1.0);
        fadeTransition.setInterpolator(Interpolator.EASE_OUT);

        if (onFinished != null) {
            fadeTransition.setOnFinished(e -> onFinished.run());
        }

        fadeTransition.play();
    }

    /**
     * Creates a subtle scale animation for hover effects.
     * @param node The node to scale
     * @param scaleTo Target scale value (e.g., 1.03 for slight growth)
     * @param durationMillis Duration in milliseconds
     */
    public static void scaleHover(Node node, double scaleTo, int durationMillis) {
        ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(durationMillis), node);
        scaleTransition.setToX(scaleTo);
        scaleTransition.setToY(scaleTo);
        scaleTransition.setInterpolator(Interpolator.EASE_BOTH);
        scaleTransition.play();
    }

    /**
     * Resets a node to its original scale (for hover exit).
     * @param node The node to reset
     * @param durationMillis Duration in milliseconds
     */
    public static void scaleReset(Node node, int durationMillis) {
        ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(durationMillis), node);
        scaleTransition.setToX(1.0);
        scaleTransition.setToY(1.0);
        scaleTransition.setInterpolator(Interpolator.EASE_BOTH);
        scaleTransition.play();
    }

    /**
     * Animates content transition when switching between different views.
     * @param oldContent The content to fade out
     * @param newContent The content to fade in
     * @param onFinished Runnable to execute when transition completes
     */
    public static void contentTransition(Node oldContent, Node newContent, Runnable onFinished) {
        if (oldContent != null) {
            // Fade out old content
            FadeTransition fadeOut = new FadeTransition(Duration.millis(CONTENT_TRANSITION_DURATION / 2), oldContent);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setInterpolator(Interpolator.EASE_OUT);

            fadeOut.setOnFinished(e -> {
                oldContent.setVisible(false);
                newContent.setOpacity(0.0);
                newContent.setVisible(true);

                // Fade in new content
                FadeTransition fadeIn = new FadeTransition(Duration.millis(CONTENT_TRANSITION_DURATION / 2), newContent);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.setInterpolator(Interpolator.EASE_IN);

                if (onFinished != null) {
                    fadeIn.setOnFinished(f -> onFinished.run());
                }

                fadeIn.play();
            });

            fadeOut.play();
        } else {
            // No old content, just fade in new content
            fadeIn(newContent, CONTENT_TRANSITION_DURATION, onFinished);
        }
    }

    /**
     * Applies a gentle pulse animation to draw attention to an element.
     * @param node The node to pulse
     * @param intensity How much to scale (e.g., 1.05)
     */
    public static void pulse(Node node, double intensity) {
        ScaleTransition scaleUp = new ScaleTransition(Duration.millis(150), node);
        scaleUp.setToX(intensity);
        scaleUp.setToY(intensity);

        ScaleTransition scaleDown = new ScaleTransition(Duration.millis(150), node);
        scaleDown.setToX(1.0);
        scaleDown.setToY(1.0);

        SequentialTransition pulseSequence = new SequentialTransition(scaleUp, scaleDown);
        pulseSequence.play();
    }
}