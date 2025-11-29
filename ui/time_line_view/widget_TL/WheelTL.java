package org.simulator.ui.time_line_view.widget_TL;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

import java.util.Locale;

public final class WheelTL {

    private static final double SIZE = 200;

    private final VBox root = new VBox(0);
    private final ImageView imgWheel = new ImageView();
    private final Label angleLbl = new Label("0°");

    public WheelTL(String resourcePng){
        Label title = new Label("Steering Input");
        title.setStyle("-fx-text-fill:#ffffff; -fx-font-weight:800; -fx-font-size:14px;");

        VBox content = new VBox(10);
        content.setAlignment(Pos.CENTER);

        // Image setup
        loadImage(resourcePng);
        imgWheel.setFitWidth(140);
        imgWheel.setFitHeight(140);
        imgWheel.setPreserveRatio(true);

        // Digital Readout
        angleLbl.setStyle("-fx-text-fill:#38bdf8; -fx-font-family:'Monospaced'; -fx-font-weight:bold; -fx-font-size:18px;");

        content.getChildren().addAll(title, imgWheel, angleLbl);
        content.setPadding(new Insets(14));
        content.setStyle("-fx-background-color:#0f172a; -fx-background-radius:12; -fx-border-color: #1e293b; -fx-border-width: 1; -fx-border-radius: 12;");

        // Lock size
        content.setMinSize(SIZE, SIZE + 40);
        content.setMaxSize(SIZE, SIZE + 40);

        root.getChildren().add(content);
    }

    public Node getRoot(){ return root; }

    // CORREZIONE QUI: Aggiunto il segno meno per invertire la rotazione
    public void setAngleDeg(double deg){
        imgWheel.setRotate(-deg); // Invertito per corrispondere alla visuale reale
        angleLbl.setText(String.format(Locale.ROOT, "%.0f°", deg));
    }

    // Metodo di compatibilità/pubblico richiesto
    public void setImageFrom(String resourcePath) {
        loadImage(resourcePath);
    }

    private void loadImage(String path) {
        try {
            String res = (path == null || path.isBlank()) ? "/assets/wheel.png" : path;
            java.net.URL url = getClass().getResource(res);
            // Fallback se inizia con /
            if (url == null && !res.startsWith("/")) url = getClass().getResource("/" + res);
            // Fallback se non inizia con /
            if (url == null && res.startsWith("/")) url = getClass().getResource(res.substring(1));

            if (url != null) {
                imgWheel.setImage(new Image(url.toExternalForm()));
            } else {
                System.err.println("[WheelTL] Image not found: " + res);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}