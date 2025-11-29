package org.simulator.ui.track_view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import org.simulator.tracks.SessionPreamble;
import org.simulator.tracks.StaticTrackDB;
import org.simulator.tracks.TrackInfo;
import org.simulator.setup.setup_advisor.VehicleTraits;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

public final class TrackView {
    // Layout principale
    private final BorderPane root = new BorderPane();

    // Area Mappa (Sinistra/Centro)
    private final StackPane canvasWrap = new StackPane();
    private final Canvas canvas = new Canvas();

    // Area Informazioni (Destra)
    private final VBox right = new VBox(14);
    private final ScrollPane rightScroll = new ScrollPane(right);

    // Componenti UI
    private final Label title = new Label("Circuito");
    private final Label subtitle = new Label();
    private final TextArea notes = new TextArea();
    private final TextArea description = new TextArea();

    // Pulsante "Torna alla Home"
    private final Button backBtn = new Button("← Torna alla Home");

    // Selettore variante
    private final VBox variantCard;
    private final ComboBox<String> variantBox = new ComboBox<>();
    private boolean ignoreListener = false;

    private String currentTrackId;
    private String currentVariantId = null;

    // Stato
    private boolean isPreviewMode = false;
    private String previewCategory = "GT";
    private VBox tipsBox;
    private TrackInfo track;
    private final org.simulator.ui.DataController data;

    private final Button saveNotesBtn = new Button("Salva Note");

    // Scaling immagine
    private double imageScaleFactor = 0.90;

    public TrackView(org.simulator.ui.DataController data){
        this.data = data;

        this.variantCard = card();
        this.tipsBox = card();

        // --- Configurazione Area Mappa ---
        backBtn.setVisible(false);
        // RIMOSSO LO STILE HARDCODED. Usiamo una classe CSS.
        backBtn.getStyleClass().add("action-button");
        StackPane.setAlignment(backBtn, Pos.TOP_LEFT);
        StackPane.setMargin(backBtn, new Insets(10));

        canvas.setManaged(false);

        canvasWrap.getChildren().addAll(canvas, backBtn);
        canvasWrap.setPadding(new Insets(20));

        // RIMOSSO LO STILE BIANCO FISSO.
        // Assegniamo la classe "map-card" per gestirlo da CSS.
        canvasWrap.getStyleClass().add("map-card");

        canvas.widthProperty().bind(canvasWrap.widthProperty());
        canvas.heightProperty().bind(canvasWrap.heightProperty());

        StackPane mapContainer = new StackPane(canvasWrap);
        mapContainer.setPadding(new Insets(16));
        // RIMOSSO LO SFONDO HARDCODED.
        // Sarà il CSS (.root o .split-pane) a decidere il colore di sfondo.

        // --- Configurazione Area Destra (Info) ---
        title.getStyleClass().add("view-title"); // Classe CSS per titolo grande
        title.setWrapText(true);

        subtitle.getStyleClass().add("view-subtitle"); // Classe CSS per sottotitolo
        subtitle.setWrapText(true);

        description.setEditable(false);
        description.setWrapText(true);
        // RIMOSSI COLORI FISSI. TextArea si adatterà al tema automaticamente.
        description.setPrefRowCount(6);

        notes.setPromptText("Note personali...");
        notes.setPrefRowCount(6);

        VBox descCard = card();
        descCard.getChildren().addAll(sectionTitle("Descrizione circuito"), description);

        tipsBox.getChildren().add(sectionTitle("Consigli ideali"));

        // Configurazione Box Varianti
        variantBox.setMaxWidth(Double.MAX_VALUE);
        variantBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (!ignoreListener && newV != null) {
                onVariantSelected(newV);
            }
        });

        variantCard.getChildren().addAll(sectionTitle("Variante"), variantBox);
        variantCard.setVisible(false);
        variantCard.setManaged(false);

        VBox notesCard = card();
        saveNotesBtn.setOnAction(e -> {
            if(isPreviewMode) return;
            var pre = SessionPreamble.parse(data.getCsvPath());
            String catKey = categoryKeyFromData(data, pre.vehicle);
            NotesStore.save(noteKeyWithCategory(catKey), notes.getText());
        });
        notesCard.getChildren().addAll(sectionTitle("Note personali"), notes, saveNotesBtn);

        right.setPadding(new Insets(16));
        right.getChildren().addAll(title, subtitle, variantCard, descCard, tipsBox, notesCard);

        rightScroll.setContent(right);
        rightScroll.setFitToWidth(true);
        rightScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        // RIMOSSO sfondo trasparente forzato.

        rightScroll.setMinWidth(400);
        rightScroll.setPrefWidth(450);
        rightScroll.setMaxWidth(600);

        root.setCenter(mapContainer);
        root.setRight(rightScroll);

        canvas.widthProperty().addListener((o,a,b)->draw());
        canvas.heightProperty().addListener((o,a,b)->draw());
    }

    // --- Metodi Pubblici ---

    public void setOnBackAction(Runnable action) {
        backBtn.setOnAction(e -> action.run());
    }

    public void setPreviewMode(String trackId, String vehicleCategory) {
        this.isPreviewMode = true;
        this.currentTrackId = trackId.toLowerCase(Locale.ROOT);
        this.previewCategory = vehicleCategory;

        backBtn.setVisible(true);

        notes.setEditable(false);
        saveNotesBtn.setVisible(false);
        saveNotesBtn.setManaged(false);

        loadTrackAndRefreshUI(currentTrackId, previewCategory);

        String noteKey = currentTrackId + "__" + (previewCategory == null ? "OTHER" : previewCategory);
        notes.setText(NotesStore.load(noteKey));
    }

    public void refresh(){
        this.isPreviewMode = false;
        backBtn.setVisible(false);

        notes.setEditable(true);
        saveNotesBtn.setVisible(true);
        saveNotesBtn.setManaged(true);

        var pre = SessionPreamble.parse(data.getCsvPath());
        currentTrackId = pre.venue==null? null : pre.venue.toLowerCase(Locale.ROOT);
        String catKey = categoryKeyFromData(data, pre.vehicle);

        loadTrackAndRefreshUI(currentTrackId, catKey);

        notes.setText(NotesStore.load(noteKeyWithCategory(catKey)));
    }

    public Node getRoot(){ return root; }

    // --- Logica Interna ---

    private void loadTrackAndRefreshUI(String trackId, String categoryKey) {
        track = StaticTrackDB.get(trackId);

        if (track == null){
            title.setText(trackId == null ? "Circuito" : trackId);
            subtitle.setText("Dati non disponibili (ID: " + trackId + ")");
            hideVariantUI();
            draw(); return;
        }

        List<String> variants = StaticTrackDB.getVariantIds(trackId);
        String defaultVar = StaticTrackDB.getDefaultVariantId(trackId);

        if (variants.isEmpty()){
            hideVariantUI();
            currentVariantId = null;
        } else {
            String toSelect = defaultVar;
            if (currentVariantId != null && variants.contains(currentVariantId)) {
                toSelect = currentVariantId;
            } else if (!variants.contains(toSelect) && !variants.isEmpty()) {
                toSelect = variants.get(0);
            }
            showVariantUI(variants, toSelect);

            if (toSelect != null) {
                TrackInfo v = StaticTrackDB.getVariant(trackId, toSelect);
                if (v != null) track = v;
            }
        }

        applyTrackToUI(track, categoryKey);
        draw();
    }

    private void showVariantUI(List<String> variants, String selection){
        ignoreListener = true;
        try {
            variantCard.setVisible(true);
            variantCard.setManaged(true);
            variantBox.getItems().setAll(variants);
            if (selection != null) {
                variantBox.getSelectionModel().select(selection);
                currentVariantId = selection;
            }
        } finally {
            ignoreListener = false;
        }
    }

    private void hideVariantUI(){
        variantCard.setVisible(false);
        variantCard.setManaged(false);
        variantBox.getItems().clear();
        currentVariantId = null;
    }

    private void onVariantSelected(String variantId){
        if (variantId == null) return;
        currentVariantId = variantId;
        TrackInfo v = StaticTrackDB.getVariant(currentTrackId, variantId);

        if (v != null){
            this.track = v;
            String cat;
            if (isPreviewMode) {
                cat = previewCategory;
            } else {
                var pre = SessionPreamble.parse(data.getCsvPath());
                cat = categoryKeyFromData(data, pre.vehicle);
            }
            applyTrackToUI(this.track, cat);
            draw();
        }
    }

    private void applyTrackToUI(TrackInfo t, String catKey){
        title.setText(t.displayName);
        subtitle.setText(String.format(Locale.ROOT, "%.3f km — %d settori", t.lengthKm, t.sectorSplits.size()));
        description.setText(t.description==null? "" : t.description.trim());

        tipsBox.getChildren().setAll(sectionTitle("Consigli ideali ("+catKey+")"));

        if (t.turns != null) {
            for (TrackInfo.Turn tu : t.turns){
                TrackInfo.Advice a = tu.adviceByVehicle.get(catKey);
                if (a == null) {
                    if ("OTHER".equals(catKey)) a = tu.adviceByVehicle.get("ROAD");
                    if (a == null && "PROTOTYPE".equals(catKey)) a = tu.adviceByVehicle.get("GT");
                    if (a == null) a = tu.adviceByVehicle.get("FORMULA");
                    if (a == null) a = tu.adviceByVehicle.get("GT");
                    if (a == null) a = tu.adviceByVehicle.get("ROAD");
                }
                String text = a==null ? "—" :
                        ((a.gear!=null? "Marcia "+a.gear+" • " : "") +
                                (a.vMinIdealKmh!=null? "Vel. "+Math.round(a.vMinIdealKmh)+" km/h" : "") +
                                (a.note!=null && !a.note.isBlank()? " — "+a.note : ""));

                Label line = new Label("T"+tu.number+" "+tu.name+" \n"+text);
                // RIMOSSO STILE HARDCODED. Assegnata classe.
                line.getStyleClass().add("tip-label");

                line.setWrapText(true);
                line.setMaxWidth(Double.MAX_VALUE);
                tipsBox.getChildren().add(line);
                tipsBox.getChildren().add(new Separator());
            }
        }
    }

    private void draw(){
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        g.clearRect(0, 0, w, h);

        if (track == null) return;

        if (track.imageResource != null) {
            try (InputStream is = getClass().getResourceAsStream(track.imageResource)) {
                if (is != null) {
                    Image img = new Image(is);
                    double scale = Math.min(w / img.getWidth(), h / img.getHeight()) * imageScaleFactor;
                    double iw = img.getWidth() * scale;
                    double ih = img.getHeight() * scale;
                    double ox = (w - iw) / 2;
                    double oy = (h - ih) / 2;

                    g.drawImage(img, ox, oy, iw, ih);
                }
            } catch (Exception ignore) {}
        }
    }

    // Metodo helper modificato per usare CSS invece di stili hardcoded
    private VBox card(){
        VBox v = new VBox(10);
        v.setPadding(new Insets(12));
        // Assegna la classe CSS "card-panel".
        // I colori saranno definiti nel file .css in base al tema.
        v.getStyleClass().add("card-panel");
        return v;
    }

    private Label sectionTitle(String t){
        Label l = new Label(t);
        // Usa classe CSS
        l.getStyleClass().add("section-title");
        return l;
    }

    private static String categoryKeyFromData(org.simulator.ui.DataController data, String vehicleName){
        try {
            VehicleTraits traits = VehicleTraits.detect(data.getLaps());
            switch (traits.category) {
                case FORMULA:    return "FORMULA";
                case PROTOTYPE:  return "PROTOTYPE";
                case GT:         return "GT";
                case ROAD:       return "ROAD";
                default:         return "OTHER";
            }
        } catch (Throwable ignore) {
            return VehicleKey.fromVehicleName(vehicleName);
        }
    }

    private String noteKeyWithCategory(String catKey){
        var pre = SessionPreamble.parse(data.getCsvPath());
        String id = pre.venue==null? "unknown" : pre.venue.toLowerCase(Locale.ROOT);
        String vehCat = (catKey==null || catKey.isBlank()) ? "OTHER" : catKey;
        return id+"__"+vehCat;
    }

    static final class VehicleKey {
        static String fromVehicleName(String vehicle){
            if (vehicle==null) return "ROAD";
            String v = vehicle.toLowerCase(Locale.ROOT);
            if (v.contains("formula") || v.contains("f1")) return "FORMULA";
            if (v.contains("lmp") || v.contains("prototype")) return "PROTOTYPE";
            if (v.contains("gt")) return "GT";
            return "ROAD";
        }
    }

    private static final class NotesStore {
        static String load(String key){
            try {
                var dir = java.nio.file.Paths.get(System.getProperty("user.home"), ".telemetrycoach", "notes");
                java.nio.file.Files.createDirectories(dir);
                var f = dir.resolve(key.replaceAll("[^a-zA-Z0-9._-]","_") + ".txt");
                if (java.nio.file.Files.exists(f)) return java.nio.file.Files.readString(f);
            } catch (Exception ignore){}
            return "";
        }
        static void save(String key, String text){
            try {
                var dir = java.nio.file.Paths.get(System.getProperty("user.home"), ".telemetrycoach", "notes");
                java.nio.file.Files.createDirectories(dir);
                var f = dir.resolve(key.replaceAll("[^a-zA-Z0-9._-]","_") + ".txt");
                java.nio.file.Files.writeString(f, text==null? "" : text);
            } catch (Exception ignore){}
        }
    }
}