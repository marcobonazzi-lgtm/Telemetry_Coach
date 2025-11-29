package org.simulator.telemetrycoachFX;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
import org.simulator.importCSVFW.converter.ConverterProfile;
import org.simulator.importCSVFW.converter.CsvStandardizer;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class WelcomeView {

    interface ImportHandler { void importFile(File f); }
    interface PreviewHandler { void onPreview(String trackId, String category); }

    private final Stage stage;
    private final ImportHandler onImport;
    private final Runnable onExit;
    private final PreviewHandler onPreview;

    private Menu personalizzaMenuRef;
    private Menu esportamenu;
    private final StackPane rootWelcome = new StackPane();
    private VBox loadingOverlay;

    WelcomeView(Stage stage, ImportHandler onImport, Runnable onExit, PreviewHandler onPreview) {
        this.stage = stage;
        this.onImport = onImport;
        this.onExit = onExit;
        this.onPreview = onPreview;
        build();
    }

    Parent getRoot() { return rootWelcome; }

    void showLoading(boolean vis) {
        if (loadingOverlay != null) loadingOverlay.setVisible(vis);
    }

    private void build() {
        // Assegnazione Classe CSS radice
        rootWelcome.getStyleClass().add("root-welcome");

        // Sfondo (Immagine opzionale, fallback gestito dal CSS)
        Image bg = safeImage("/assets/assetto_corsa_bg.png");
        if (bg != null) {
            ImageView bgView = new ImageView(bg);
            bgView.setPreserveRatio(false);
            bgView.fitWidthProperty().bind(rootWelcome.widthProperty());
            bgView.fitHeightProperty().bind(rootWelcome.heightProperty());
            bgView.setOpacity(0.92);
            bgView.setMouseTransparent(true);
            rootWelcome.getChildren().add(bgView);
        }

        Region leftOverlay = new Region();
        leftOverlay.setMaxWidth(820);
        leftOverlay.setMouseTransparent(true);
        StackPane.setAlignment(leftOverlay, Pos.CENTER_LEFT);
        // Questo gradiente serve per leggibilità sull'immagine, lo lascio hardcoded
        // ma uso rgba per fondersi col tema se necessario.
        leftOverlay.setStyle("-fx-background-color: linear-gradient(to right, rgba(8,10,14,0.85) 0%, rgba(8,10,14,0.78) 45%, rgba(8,10,14,0.30) 95%, rgba(8,10,14,0.00) 100%);");
        rootWelcome.getChildren().add(leftOverlay);

        // Titoli
        // Carichiamo il font solo per la famiglia, il colore è nel CSS
        Font titleFont = loadFirstAvailableFont(new String[]{"/assets/fonts/Orbitron-Bold.ttf", "/assets/fonts/Montserrat-ExtraBold.ttf"}, 56, "Segoe UI Black", "Arial Black");

        Label title = new Label("Telemetry Coach");
        title.setFont(titleFont);
        title.getStyleClass().add("welcome-title"); // CSS gestisce colore e ombra

        Label subtitle = new Label("Analisi telemetria per Assetto Corsa");
        subtitle.getStyleClass().add("welcome-subtitle"); // CSS gestisce colore

        // Pulsanti Principali (Usa classi CSS)
        Button chooseBtn = new Button("Inserisci nuovo CSV…");
        chooseBtn.setDefaultButton(true);
        chooseBtn.getStyleClass().add("button-primary"); // CLASSE CSS
        chooseBtn.setMinHeight(46);
        chooseBtn.setPadding(new Insets(10, 18, 10, 18));
        chooseBtn.setOnAction(e -> openChooser());

        Button convertBtn = new Button("Converti il tuo file CSV");
        convertBtn.getStyleClass().add("button-primary"); // CLASSE CSS
        convertBtn.setMinHeight(46);
        convertBtn.setPadding(new Insets(10, 18, 10, 18));
        convertBtn.setOnAction(e -> openCsvConverterDialog());

        Button exitBtn = new Button("Esci");
        exitBtn.getStyleClass().add("button-secondary"); // CLASSE CSS
        exitBtn.setMinHeight(46);
        exitBtn.setPadding(new Insets(10, 18, 10, 18));
        exitBtn.setOnAction(e -> onExit.run());

        HBox actions = new HBox(14, chooseBtn, convertBtn, exitBtn);
        actions.setAlignment(Pos.CENTER);

        // ---------------------------------------------------------
        // SEZIONE ANTEPRIMA
        // ---------------------------------------------------------
        Label previewTitle = new Label("Oppure visualizza anteprima circuito");
        previewTitle.getStyleClass().add("welcome-label-muted"); // CSS

        ComboBox<String> trackSelector = new ComboBox<>();
        trackSelector.getItems().addAll(
                "imola", "ks_laguna_seca", "ks_redbullring", "ks_silverstone",
                "ks_silverstone1967", "ks_vallelunga", "ks_zandvoort", "magione",
                "misano", "monza", "mugello", "spa"
        );
        trackSelector.setPromptText("Seleziona Circuito");
        trackSelector.setPrefWidth(180);

        ComboBox<String> catSelector = new ComboBox<>();
        catSelector.getItems().addAll("GT", "FORMULA", "PROTOTYPE", "ROAD");
        catSelector.setValue("GT");
        catSelector.setPrefWidth(120);

        Button previewBtn = new Button("Anteprima \u2192");
        previewBtn.getStyleClass().add("button-preview"); // CSS
        previewBtn.setOnAction(e -> {
            String t = trackSelector.getValue();
            String c = catSelector.getValue();
            if (t != null && !t.isBlank() && onPreview != null) {
                onPreview.onPreview(t, c);
            } else {
                trackSelector.requestFocus();
                trackSelector.show();
            }
        });

        HBox previewBox = new HBox(10, trackSelector, catSelector, previewBtn);
        previewBox.setAlignment(Pos.CENTER);
        previewBox.setPadding(new Insets(10));
        previewBox.getStyleClass().add("preview-box"); // CSS gestisce sfondo e bordi
        previewBox.setMaxWidth(450);
        // ---------------------------------------------------------

        VBox centerBlock = new VBox(16, title, subtitle, actions, spacer(20), previewTitle, previewBox);
        centerBlock.setAlignment(Pos.CENTER);
        centerBlock.setPadding(new Insets(24, 24, 24, 36));
        centerBlock.setMaxWidth(820);
        centerBlock.setTranslateY(-80);
        StackPane.setAlignment(centerBlock, Pos.CENTER);
        rootWelcome.getChildren().add(centerBlock);

        // Credits & Footer
        Label credits = new Label("Sviluppo: MARCO BONAZZI · NOEMI CUCURACHI · Prof. LUCA MAINETTI");
        credits.setStyle("-fx-text-fill: #c7cdd6; -fx-font-size: 13px;"); // Lascio questo fisso per leggibilità su img
        credits.setMouseTransparent(true);
        StackPane.setAlignment(credits, Pos.TOP_LEFT);
        StackPane.setMargin(credits, new Insets(0, 0, 18, 18));
        rootWelcome.getChildren().add(credits);

        ImageView uniLogo = logo("/assets/unisalento.png", 64);
        ImageView dscLogo = logo("/assets/dsc_lecce.png", 64);
        ImageView appMark = logo("/assets/app_logo.png", 64);
        HBox cornerLogos = new HBox(16, uniLogo, dscLogo, appMark);
        cornerLogos.setPadding(new Insets(0, 18, 18, 0));
        cornerLogos.setAlignment(Pos.TOP_RIGHT);
        cornerLogos.setMouseTransparent(true);
        StackPane.setAlignment(cornerLogos, Pos.TOP_RIGHT);
        rootWelcome.getChildren().add(cornerLogos);

        // Drop Zone
        VBox dropZone = new VBox();
        dropZone.setVisible(false);
        dropZone.setMouseTransparent(true);
        dropZone.setAlignment(Pos.CENTER);
        dropZone.setStyle("-fx-background-color: rgba(0,0,0,0.35); -fx-border-color: #6aa5ff; -fx-border-width: 3; -fx-border-style: dashed; -fx-border-radius: 12; -fx-background-radius: 12;");
        Label dzTitle = new Label("Rilascia qui il file CSV");
        dzTitle.setStyle("-fx-text-fill: white; -fx-font-size: 26px; -fx-font-weight: bold;");
        Label dzHint  = new Label("Oppure clicca su \"Inserisci nuovo CSV…\"");
        dzHint.setStyle("-fx-text-fill: #e6edf3; -fx-font-size: 14px;");
        dropZone.getChildren().addAll(spacer(10), dzTitle, spacer(10), dzHint);

        // Loading Overlay
        loadingOverlay = new VBox(8, new ProgressIndicator(), new Label("Caricamento file in corso…"));
        ((Label) loadingOverlay.getChildren().get(1)).setStyle("-fx-text-fill: white; -fx-font-size: 12px;");
        loadingOverlay.setAlignment(Pos.CENTER);
        loadingOverlay.setVisible(false);
        loadingOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.55); -fx-padding: 20; -fx-background-radius: 12;");

        // Listeners menu
        rootWelcome.sceneProperty().addListener((o, oldSc, newSc) -> { if (newSc != null) javafx.application.Platform.runLater(() -> hidePersonalizzaMenu(newSc)); });
        rootWelcome.sceneProperty().addListener((o, oldSc, newSc) -> { if (newSc != null) javafx.application.Platform.runLater(() -> hideesportamenu(newSc)); });
        rootWelcome.visibleProperty().addListener((o, ov, nv) -> { if (!nv) javafx.application.Platform.runLater(this::restorePersonalizzaMenu); });
        rootWelcome.parentProperty().addListener((o, ov, nv) -> { if (ov != null && nv == null) javafx.application.Platform.runLater(this::restorePersonalizzaMenu); });
        rootWelcome.visibleProperty().addListener((o, ov, nv) -> { if (!nv) javafx.application.Platform.runLater(this::restoreesportamenu); });
        rootWelcome.parentProperty().addListener((o, ov, nv) -> { if (ov != null && nv == null) javafx.application.Platform.runLater(this::restoreesportamenu); });
        if (rootWelcome.getScene() != null) javafx.application.Platform.runLater(() -> hidePersonalizzaMenu(rootWelcome.getScene()));

        rootWelcome.getChildren().addAll(dropZone, loadingOverlay);
        StackPane.setAlignment(dropZone, Pos.CENTER);
        StackPane.setAlignment(loadingOverlay, Pos.CENTER);

        // Drag & Drop
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

    // Helpers standard
    private void openCsvConverterDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Conversione CSV verso formato Assetto Corsa");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        ComboBox<String> simChoice = new ComboBox<>();
        simChoice.getItems().addAll("Le Mans Ultimate (MoTeC)", "Assetto Corsa Competizione (MoTeC)");
        simChoice.setPromptText("Seleziona il simulatore di origine");

        TextField inPath = new TextField(); inPath.setPromptText("File CSV da convertire…"); inPath.setEditable(false);
        Button pickIn = new Button("Importa CSV da convertire");
        pickIn.setOnAction(ev -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
            File f = fc.showOpenDialog(stage);
            if (f != null) inPath.setText(f.getAbsolutePath());
        });

        TextField outPath = new TextField(); outPath.setPromptText("Percorso di esportazione (CSV AC) …"); outPath.setEditable(false);
        Button pickOut = new Button("Esporta…");
        pickOut.setOnAction(ev -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
            fc.setInitialFileName("converted_assetto_corsa.csv");
            File f = fc.showSaveDialog(stage);
            if (f != null) outPath.setText(f.getAbsolutePath());
        });

        CheckBox stripExtras = new CheckBox("Rimuovi colonne non riconosciute (consigliato)");
        stripExtras.setSelected(true);

        Button run = new Button("Converti");
        run.setDefaultButton(true);
        // Uso la classe CSS anche qui per coerenza
        run.getStyleClass().add("button-primary");
        Label status = new Label();

        run.setOnAction(ev -> {
            String sim = simChoice.getValue();
            String in  = inPath.getText();
            String out = outPath.getText();
            if (sim == null || sim.isBlank()) { status.setText("Seleziona il simulatore."); return; }
            if (in == null || in.isBlank())   { status.setText("Scegli il CSV di input."); return; }
            if (out == null || out.isBlank()) { status.setText("Scegli il file di output."); return; }
            ConverterProfile profile;
            if (sim.startsWith("Le Mans")) profile = ConverterProfile.LMU_MOTEC;
            else if (sim.startsWith("Assetto Corsa Competizione")) profile = ConverterProfile.ACC_MOTEC;
            else profile = ConverterProfile.ASSETTO_CORSA_PASS_THROUGH;
            try {
                CsvStandardizer.convertToAssettoCorsa(Path.of(in), Path.of(out), profile, stripExtras.isSelected());
                status.setText("Conversione completata ✔");
            } catch (Exception ex) {
                ex.printStackTrace();
                status.setText("Errore: " + ex.getMessage());
            }
        });

        GridPane gp = new GridPane();
        gp.setVgap(10); gp.setHgap(10); gp.setPadding(new Insets(10));
        gp.add(new Label("Simulatore di origine"), 0,0); gp.add(simChoice, 1,0);
        gp.add(pickIn, 0,1); gp.add(inPath, 1,1);
        gp.add(pickOut,0,2); gp.add(outPath,1,2);
        gp.add(stripExtras, 1,3);
        gp.add(run,   0,4); gp.add(status, 1,4);
        dialog.getDialogPane().setContent(gp);
        dialog.showAndWait();
    }

    private void openChooser() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File f = fc.showOpenDialog(stage);
        if (f != null) onImport.importFile(f);
    }

    private static Region spacer(double h){ Region r = new Region(); r.setMinHeight(h); return r; }
    private boolean containsCsv(List<File> files) { return files.stream().anyMatch(this::isCsv); }
    private boolean isCsv(File f) { return f.getName().toLowerCase(Locale.ITALIAN).endsWith(".csv"); }

    private Font loadFirstAvailableFont(String[] resourcePaths, double size, String... fallbacks) {
        for (String p : resourcePaths) {
            try (InputStream in = getClass().getResourceAsStream(p)) {
                if (in != null) { Font f = Font.loadFont(in, size); if (f != null) return f; }
            } catch (Exception ignored) {}
        }
        for (String fam : fallbacks) { try { return Font.font(fam, size); } catch (Exception ignored) {} }
        return Font.font(size);
    }
    private Image safeImage(String path) {
        try { var url = getClass().getResource(path); if (url == null) return null; return new Image(url.toExternalForm()); } catch (Exception ex) { return null; }
    }
    private ImageView logo(String path, double h) {
        Image img = safeImage(path);
        ImageView iv = new ImageView();
        if (img != null) { iv.setImage(img); iv.setPreserveRatio(true); iv.setFitHeight(h); }
        return iv;
    }

    private void hidePersonalizzaMenu(Scene sc) {
        if (sc == null) return;
        var node = sc.lookup(".menu-bar");
        if (!(node instanceof MenuBar mb)) return;
        if (personalizzaMenuRef == null) {
            for (Menu m : mb.getMenus()) {
                String t = (m.getText() == null) ? "" : m.getText().trim();
                if (t.equalsIgnoreCase("Personalizza")) { personalizzaMenuRef = m; break; }
            }
        }
        if (personalizzaMenuRef != null) { personalizzaMenuRef.setDisable(true); personalizzaMenuRef.setVisible(false); }
    }
    private void restorePersonalizzaMenu() {
        if (personalizzaMenuRef != null) { personalizzaMenuRef.setDisable(false); personalizzaMenuRef.setVisible(true); personalizzaMenuRef = null; }
    }
    private void hideesportamenu(Scene sc) {
        if (sc == null) return;
        var node = sc.lookup(".menu-bar");
        if (!(node instanceof MenuBar mb)) return;
        if (esportamenu == null) {
            for (Menu m : mb.getMenus()) {
                String t = (m.getText() == null) ? "" : m.getText().trim();
                if (t.equalsIgnoreCase("Esporta in PDF")) { esportamenu = m; break; }
            }
        }
        if (esportamenu != null) { esportamenu.setDisable(true); esportamenu.setVisible(false); }
    }
    private void restoreesportamenu() {
        if (esportamenu != null) { esportamenu.setDisable(false); esportamenu.setVisible(true); esportamenu = null; }
    }
}