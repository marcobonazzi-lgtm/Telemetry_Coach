package org.simulator.telemetrycoachFX;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;

import java.util.Objects;

public final class IntroVideoView {
    private final StackPane root = new StackPane();
    private final MediaPlayer player;

    // riferimento temporaneo al menu "Personalizza" per poterlo ripristinare
    private Menu personalizzaMenuRef;

    public IntroVideoView(Stage stage, String resourcePath, Runnable onFinish) {
        String url = Objects.requireNonNull(
                getClass().getResource(resourcePath),
                "Intro video non trovato in resources: " + resourcePath
        ).toExternalForm();

        Media media = new Media(url);
        player = new MediaPlayer(media);
        player.setAutoPlay(true);
        player.setCycleCount(1);
        // se vuoi muto: player.setMute(true);

        MediaView mv = new MediaView(player);
        mv.setPreserveRatio(true);
        mv.fitWidthProperty().bind(stage.widthProperty());
        mv.fitHeightProperty().bind(stage.heightProperty());

        root.setStyle("-fx-background-color:black;");
        root.getChildren().add(mv);

        // Hint “doppio clic per saltare”
        Label hint = new Label("Doppio clic per saltare  ⟶");
        hint.setStyle("""
            -fx-text-fill: white;
            -fx-font-size: 14px;
            -fx-background-color: rgba(0,0,0,0.35);
            -fx-padding: 6 10;
            -fx-background-radius: 6;
        """);
        StackPane.setAlignment(hint, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(hint, new Insets(10));
        root.getChildren().add(hint);

        // Skip: doppio click ovunque o ESC
        root.setOnMouseClicked(ev -> { if (ev.getClickCount() == 2) stopAndFinish(onFinish); });
        root.setOnKeyPressed(ev -> { if (ev.getCode() == KeyCode.ESCAPE) stopAndFinish(onFinish); });

        // Fine o errore => vai al welcome
        player.setOnEndOfMedia(() -> stopAndFinish(onFinish));
        player.setOnError(() -> stopAndFinish(onFinish));

        // Nascondi "Personalizza" quando l'intro è visibile
        root.sceneProperty().addListener((o, oldSc, newSc) -> {
            if (newSc != null) Platform.runLater(() -> hidePersonalizzaMenu(newSc));
        });
        // se la Scene è già presente
        if (root.getScene() != null) {
            Platform.runLater(() -> hidePersonalizzaMenu(root.getScene()));
        }
        // ripristina quando l'intro sparisce
        root.visibleProperty().addListener((o, ov, nv) -> { if (!nv) Platform.runLater(this::restorePersonalizzaMenu); });
        root.parentProperty().addListener((o, ov, nv) -> { if (ov != null && nv == null) Platform.runLater(this::restorePersonalizzaMenu); });

        Platform.runLater(root::requestFocus);
    }

    private void stopAndFinish(Runnable onFinish) {
        try { player.stop(); } catch (Exception ignored) {}
        // prima di uscire, ripristina il menu (il Welcome poi lo nasconderà di nuovo se necessario)
        restorePersonalizzaMenu();
        if (onFinish != null) Platform.runLater(onFinish);
    }

    public Parent getRoot() { return root; }

    // --------- helper: nascondi/ripristina menu "Personalizza" ---------
    private void hidePersonalizzaMenu(Scene sc) {
        if (sc == null) return;
        var node = sc.lookup(".menu-bar");
        if (!(node instanceof MenuBar mb)) return;

        if (personalizzaMenuRef == null) {
            for (Menu m : mb.getMenus()) {
                String t = (m.getText() == null) ? "" : m.getText().trim();
                if (t.equalsIgnoreCase("Personalizza")) {
                    personalizzaMenuRef = m;
                    break;
                }
            }
        }
        if (personalizzaMenuRef != null) {
            personalizzaMenuRef.setDisable(true);
            personalizzaMenuRef.setVisible(false); // solo nascosto, non rimosso
        }
    }

    private void restorePersonalizzaMenu() {
        if (personalizzaMenuRef != null) {
            personalizzaMenuRef.setDisable(false);
            personalizzaMenuRef.setVisible(true);
            personalizzaMenuRef = null; // rilascia: se torniamo all'intro lo ricercheremo
        }
    }
}
