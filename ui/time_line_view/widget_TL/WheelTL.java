package org.simulator.ui.time_line_view.widget_TL;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.simulator.ui.time_line_view.TimeLineView;

import java.util.Locale;

public final class WheelTL {
    private final VBox root = new VBox(6);
    private final StackPane wheelPane = new StackPane();
    private final ImageView img = new ImageView();
    private final Region fallback = new Region();
    private final Label angleLbl = new Label("0°");

    public WheelTL(String resourcePng){
        root.setPadding(new Insets(8));
        root.setStyle("-fx-background-color:#0f172a; -fx-background-radius:10;");
        Label title = new Label("Volante");
        title.setStyle("-fx-text-fill:#e5e7eb; -fx-font-weight:bold;");

        wheelPane.setMinSize(180, 180);
        wheelPane.setPrefSize(180, 180);
        img.setPreserveRatio(true);
        img.setFitWidth(180);
        img.setFitHeight(180);

        fallback.setMinSize(160,160);
        fallback.setMaxSize(160,160);
        fallback.setStyle(
                "-fx-background-radius:100;" +
                        "-fx-background-color: linear-gradient(#1f2937,#0b1220);" +
                        "-fx-border-color:#ff6b6b; -fx-border-radius:100; -fx-border-width:2;"
        );

        Image im = null;
        java.net.URL url = null;

        String res = (resourcePng == null || resourcePng.isBlank()) ? "/assets/wheel.png" : resourcePng;
        url = WheelTL.class.getResource(res);
        if (url == null) {
            String alt = res.startsWith("/") ? res.substring(1) : ("/" + res);
            url = WheelTL.class.getResource(alt);
        }
        if (url == null) {
            String clKey = res.startsWith("/") ? res.substring(1) : res;
            url = Thread.currentThread().getContextClassLoader().getResource(clKey);
        }
        if (url != null) {
            im = new Image(url.toExternalForm(), 180, 180, true, true);
            System.out.println("[WheelTL] Caricata immagine volante da classpath: " + url);
        }

        if (im == null || im.isError()) {
            try {
                java.nio.file.Path p1 = java.nio.file.Paths.get("src/main/resources/assets/wheel.png");
                java.nio.file.Path p2 = java.nio.file.Paths.get("target/classes/assets/wheel.png");
                java.nio.file.Path use = null;
                if (java.nio.file.Files.exists(p1)) use = p1;
                else if (java.nio.file.Files.exists(p2)) use = p2;

                if (use != null) {
                    java.net.URL fileUrl = use.toUri().toURL();
                    im = new Image(fileUrl.toExternalForm(), 180, 180, true, true);
                    System.out.println("[WheelTL] Caricata immagine volante da file: " + use.toAbsolutePath());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (im != null) {
            if (im.isError()) {
                System.out.println("[WheelTL] ERRORE nel decodificare l'immagine.");
                Throwable ex = im.getException();
                if (ex != null) ex.printStackTrace();
                else System.out.println("[WheelTL] (nessuna eccezione disponibile da Image.getException())");
                wheelPane.getChildren().setAll(fallback);
            } else {
                img.setImage(im);
                wheelPane.getChildren().setAll(img);
            }
        } else {
            System.out.println("[WheelTL] Immagine nulla, uso fallback.");
            wheelPane.getChildren().setAll(fallback);
        }

        angleLbl.setStyle("-fx-text-fill:#e5e7eb; -fx-opacity:.8;");
        root.getChildren().setAll(title, wheelPane, angleLbl);
        root.setAlignment(Pos.CENTER_LEFT);
    }

    public void setAngleDeg(double deg){
        if (img.getImage()!=null) img.setRotate(-deg);
        else fallback.setRotate(-deg);
        angleLbl.setText(String.format(Locale.ITALIAN, "%.1f°", deg));
    }
    public Node getRoot(){ return root; }
    public void setImageFrom(String pathOrResource){
        try {
            Image im = null;
            if (pathOrResource!=null && (pathOrResource.startsWith("file:") || new java.io.File(pathOrResource).exists())){
                java.net.URI uri = pathOrResource.startsWith("file:") ? java.net.URI.create(pathOrResource)
                        : new java.io.File(pathOrResource).toURI();
                im = new Image(uri.toString(), 180,180,true,true);
            } else {
                java.net.URL url = TimeLineView.class.getResource(pathOrResource);
                if (url!=null) im = new Image(url.toExternalForm(), 180,180,true,true);
            }
            if (im!=null && !im.isError()){ img.setImage(im); wheelPane.getChildren().setAll(img); }
        } catch (Exception ignored) {}
    }

}
