package org.simulator.telemetrycoachFX;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.Scene;


import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class WelcomeView {

    interface ImportHandler {
        void importFile(File f);
    }

    private final Stage stage;
    private final ImportHandler onImport;
    private final Runnable onExit;
    private Menu personalizzaMenuRef; // riferimento temporaneo al menu "Personalizza"

    private final StackPane rootWelcome = new StackPane();
    private VBox loadingOverlay;

    WelcomeView(Stage stage, ImportHandler onImport, Runnable onExit) {
        this.stage = stage;
        this.onImport = onImport;
        this.onExit = onExit;
        build();
    }

    Parent getRoot() { return rootWelcome; }

    void showLoading(boolean vis) {
        if (loadingOverlay != null) loadingOverlay.setVisible(vis);
    }

    // ---------------- UI build ----------------
    private void build() {
        // Sfondo full-screen
        Image bg = safeImage("/assets/assetto_corsa_bg.png");
        if (bg != null) {
            ImageView bgView = new ImageView(bg);
            bgView.setPreserveRatio(false);
            bgView.fitWidthProperty().bind(rootWelcome.widthProperty());
            bgView.fitHeightProperty().bind(rootWelcome.heightProperty());
            bgView.setOpacity(0.92);
            bgView.setMouseTransparent(true);
            rootWelcome.getChildren().add(bgView);
        } else {
            rootWelcome.setStyle("-fx-background-color: linear-gradient(#0d1117,#111827);");
        }

        // overlay sinistro
        Region leftOverlay = new Region();
        leftOverlay.setMaxWidth(820);
        leftOverlay.setMouseTransparent(true);
        StackPane.setAlignment(leftOverlay, Pos.CENTER_LEFT);
        leftOverlay.setStyle("""
            -fx-background-color: linear-gradient(to right,
                rgba(8,10,14,0.85) 0%,
                rgba(8,10,14,0.78) 45%,
                rgba(8,10,14,0.30) 95%,
                rgba(8,10,14,0.00) 100%);
            """);
        rootWelcome.getChildren().add(leftOverlay);

        // Titolo / sottotitolo
        Font titleFont = loadFirstAvailableFont(
                new String[]{"/assets/fonts/Orbitron-Bold.ttf", "/assets/fonts/Montserrat-ExtraBold.ttf"},
                56, "Segoe UI Black", "Arial Black", "Segoe UI", "Arial"
        );
        Label title = new Label("Telemetry Coach");
        title.setFont(titleFont);
        title.setStyle("-fx-text-fill: white; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.55), 12, 0.2, 0, 2);");

        Label subtitle = new Label("Analisi telemetria per Assetto Corsa");
        subtitle.setStyle("-fx-font-size: 18px; -fx-text-fill: #d0d7de;");

        // Pulsanti
        Button chooseBtn = bigPrimary("Inserisci nuovo CSV…");
        chooseBtn.setDefaultButton(true);
        chooseBtn.setOnAction(e -> openChooser());

        Button exitBtn = bigSecondary("Esci");
        exitBtn.setOnAction(e -> onExit.run());

        HBox actions = new HBox(14, chooseBtn, exitBtn);
        actions.setAlignment(Pos.CENTER);
        VBox.setMargin(actions, new Insets(28, 0, 0, 0));
        actions.setTranslateY(80);

        // Centro
        VBox centerBlock = new VBox(16, title, subtitle, actions);
        centerBlock.setAlignment(Pos.CENTER);
        centerBlock.setPadding(new Insets(24, 24, 24, 36));
        centerBlock.setMaxWidth(820);
        centerBlock.setTranslateY(-160);
        StackPane.setAlignment(centerBlock, Pos.CENTER);
        rootWelcome.getChildren().add(centerBlock);

        // Crediti
        Label credits = new Label(
                "Sviluppo: MARCO BONAZZI · NOEMI CUCURACHI · Prof. LUCA MAINETTI — Università del Salento · " +
                        "Contatti: marco.bonazzi@studenti.unisalento.it · noemi.cucurachi@studenti.unisalento.it");
        credits.setStyle("-fx-text-fill: #c7cdd6; -fx-font-size: 13px;");
        credits.setMouseTransparent(true);
        StackPane.setAlignment(credits, Pos.TOP_LEFT);
        StackPane.setMargin(credits, new Insets(0, 0, 18, 18));
        rootWelcome.getChildren().add(credits);

        // Loghi
        ImageView uniLogo = logo("/assets/unisalento.png", 64);
        ImageView dscLogo = logo("/assets/dsc_lecce.png", 64);
        ImageView appMark = logo("/assets/app_logo.png", 64);
        HBox cornerLogos = new HBox(16, uniLogo, dscLogo, appMark);
        cornerLogos.setPadding(new Insets(0, 18, 18, 0));
        cornerLogos.setAlignment(Pos.TOP_RIGHT);
        cornerLogos.setMouseTransparent(true);
        StackPane.setAlignment(cornerLogos, Pos.TOP_RIGHT);
        rootWelcome.getChildren().add(cornerLogos);

        // Drop zone (overlay)
        VBox dropZone = new VBox();
        dropZone.setVisible(false);
        dropZone.setMouseTransparent(true);
        dropZone.setAlignment(Pos.CENTER);
        dropZone.setStyle("""
            -fx-background-color: rgba(0,0,0,0.35);
            -fx-border-color: #6aa5ff;
            -fx-border-width: 3;
            -fx-border-style: dashed;
            -fx-border-radius: 12;
            -fx-background-radius: 12;
            """);
        Label dzTitle = new Label("Rilascia qui il file CSV");
        dzTitle.setStyle("-fx-text-fill: white; -fx-font-size: 26px; -fx-font-weight: bold;");
        Label dzHint  = new Label("Oppure clicca su \"Inserisci nuovo CSV…\"");
        dzHint.setStyle("-fx-text-fill: #e6edf3; -fx-font-size: 14px;");
        dropZone.getChildren().addAll(spacer(10), dzTitle, spacer(10), dzHint);

        // Loading overlay
        loadingOverlay = new VBox(8, new ProgressIndicator(), new Label("Caricamento file in corso…"));
        ((Label) loadingOverlay.getChildren().get(1)).setStyle("-fx-text-fill: white; -fx-font-size: 12px;");
        loadingOverlay.setAlignment(Pos.CENTER);
        loadingOverlay.setVisible(false);
        loadingOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.55); -fx-padding: 20; -fx-background-radius: 12;");
// Nascondi "Personalizza" quando la Welcome è mostrata…

        rootWelcome.sceneProperty().addListener((o, oldSc, newSc) -> {
            if (newSc != null) javafx.application.Platform.runLater(() -> hidePersonalizzaMenu(newSc));
        });
// …e ripristina quando la Welcome non è più visibile / viene rimossa dal scene graph
        rootWelcome.visibleProperty().addListener((o, ov, nv) -> {
            if (!nv) javafx.application.Platform.runLater(this::restorePersonalizzaMenu);
        });
        rootWelcome.parentProperty().addListener((o, ov, nv) -> {
            if (ov != null && nv == null) javafx.application.Platform.runLater(this::restorePersonalizzaMenu);
        });
// Se la Scene è già presente
        if (rootWelcome.getScene() != null) {
            javafx.application.Platform.runLater(() -> hidePersonalizzaMenu(rootWelcome.getScene()));
        }


        rootWelcome.getChildren().addAll(dropZone, loadingOverlay);
        StackPane.setAlignment(dropZone, Pos.CENTER);
        StackPane.setAlignment(loadingOverlay, Pos.CENTER);

        // Drag&drop sul root
        rootWelcome.setOnDragOver(ev -> {
            Dragboard db = ev.getDragboard();
            if (db.hasFiles() && containsCsv(db.getFiles())) {
                ev.acceptTransferModes(TransferMode.COPY);
                dropZone.setVisible(true);
                dropZone.setPrefSize(rootWelcome.getWidth(), rootWelcome.getHeight());
            }
            ev.consume();
        });
        rootWelcome.setOnDragExited(ev -> dropZone.setVisible(false));
        rootWelcome.setOnDragDropped((DragEvent ev) -> {
            Dragboard db = ev.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                Optional<File> csv = db.getFiles().stream().filter(this::isCsv).findFirst();
                if (csv.isPresent()) {
                    success = true;
                    onImport.importFile(csv.get());
                }
            }
            ev.setDropCompleted(success);
            ev.consume();
            dropZone.setVisible(false);
        });
    }

    private void openChooser() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File f = fc.showOpenDialog(stage);
        if (f != null) onImport.importFile(f);
    }

    // ---- helpers ----
    private static Region spacer(double h){ Region r = new Region(); r.setMinHeight(h); return r; }

    private boolean containsCsv(List<File> files) { return files.stream().anyMatch(this::isCsv); }
    private boolean isCsv(File f) { return f.getName().toLowerCase(Locale.ITALIAN).endsWith(".csv"); }

    private Font loadFirstAvailableFont(String[] resourcePaths, double size, String... fallbacks) {
        for (String p : resourcePaths) {
            try (InputStream in = getClass().getResourceAsStream(p)) {
                if (in != null) {
                    Font f = Font.loadFont(in, size);
                    if (f != null) return f;
                }
            } catch (Exception ignored) {}
        }
        for (String fam : fallbacks) {
            try { return Font.font(fam, size); } catch (Exception ignored) {}
        }
        return Font.font(size);
    }
    private Image safeImage(String path) {
        try {
            var url = getClass().getResource(path);
            if (url == null) return null;
            return new Image(url.toExternalForm());
        } catch (Exception ex) { return null; }
    }
    private ImageView logo(String path, double h) {
        Image img = safeImage(path);
        ImageView iv = new ImageView();
        if (img != null) { iv.setImage(img); iv.setPreserveRatio(true); iv.setFitHeight(h); }
        return iv;
    }
    private Button bigPrimary(String text){
        Button b = new Button(text);
        b.setMinHeight(46);
        b.setStyle("""
                -fx-font-size: 16px; -fx-font-weight: 700;
                -fx-background-color: linear-gradient(#409cff,#1f6feb);
                -fx-text-fill: white; -fx-background-radius: 10;
                -fx-padding: 10 18 10 18;
                """);
        return b;
    }
    private Button bigSecondary(String text){
        Button b = new Button(text);
        b.setMinHeight(46);
        b.setStyle("""
                -fx-font-size: 16px; -fx-font-weight: 600;
                -fx-background-color: rgba(255,255,255,0.10);
                -fx-text-fill: #e6edf3; -fx-background-radius: 10;
                -fx-padding: 10 18 10 18;
                -fx-border-color: rgba(255,255,255,0.25); -fx-border-radius: 10;
                """);
        return b;
    }
    // Nasconde/rimuove il menu "Personalizza" dal MenuBar della scena (solo in questa schermata)
    private void removePersonalizzaMenu(Scene sc) {
        if (sc == null) return;
        var node = sc.lookup(".menu-bar");
        if (node instanceof MenuBar mb) {
            // rimuovi il menu con testo "Personalizza" (case-insensitive, con trim)
            mb.getMenus().removeIf(m -> {
                String t = (m.getText() == null) ? "" : m.getText().trim();
                return t.equalsIgnoreCase("Personalizza");
            });
            // se non restano menu, nascondi completamente la barra
            if (mb.getMenus().isEmpty()) {
                mb.setManaged(false);
                mb.setVisible(false);
            }
        }
    }
    // Nasconde il menu "Personalizza" (non lo rimuove) SOLO nella schermata Welcome
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
            personalizzaMenuRef.setVisible(false); // << non rimuove, solo nasconde
        }
    }

    // Ripristina il menu quando si esce dalla Welcome
    private void restorePersonalizzaMenu() {
        if (personalizzaMenuRef != null) {
            personalizzaMenuRef.setDisable(false);
            personalizzaMenuRef.setVisible(true);
            personalizzaMenuRef = null; // rilascia il riferimento; verrà ri-trovato alla prossima Welcome
        }
    }


}
