package org.simulator.ui.track_view;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority; // NEW
import javafx.scene.paint.Color;
import javafx.stage.Window;
import org.simulator.tracks.SessionPreamble;
import org.simulator.tracks.StaticTrackDB;
import org.simulator.tracks.TrackInfo;
import org.simulator.setup.setup_advisor.VehicleTraits;  // <-- usa VehicleTraits

import java.io.InputStream;
import java.util.Locale;

/** Vista "Circuito" STATICA con UI leggibile (tema chiaro) e consigli per categoria. */
public final class TrackView {
    private final BorderPane root = new BorderPane();
    private final StackPane canvasWrap = new StackPane();
    private final Canvas canvas = new Canvas(980, 640);

    private final VBox right = new VBox(14);
    private final ScrollPane rightScroll = new ScrollPane(right);

    private final Label title = new Label("Circuito");
    private final Label subtitle = new Label();
    private final TextArea notes = new TextArea();
    private final TextArea description = new TextArea();

    // --- Consigli: card + scroll + contenitore righe
    private VBox tipsBox;                 // solo le righe
    private ScrollPane tipsScroll;        // scroll verticale per i consigli  // NEW
    private VBox tipsCard;                // la card che contiene titolo + scroll // NEW

    private TrackInfo track;
    private final org.simulator.ui.DataController data;

    // valori di stile adattivi
    private double basePadding = 16;
    private double cardPadding = 12;
    private double canvasCardPadding = 20;

    // UI dinamica
    private SplitPane splitPane;
    private double dividerTarget = 0.55;       // si aggiorna in applyResponsive
    private double imageScaleFactor = 0.90;    // si aggiorna in applyResponsive

    public TrackView(org.simulator.ui.DataController data){
        this.data = data;

        // centro: sfondo bianco
        StackPane centerBg = new StackPane(canvasWrap);
        centerBg.setPadding(new Insets(basePadding));
        centerBg.setStyle("-fx-background-color: #ffffff;");

        // canvas: card bianca con bordo leggero
        canvasWrap.getChildren().add(canvas);
        canvasWrap.setPadding(new Insets(canvasCardPadding));
        canvasWrap.setStyle(
                "-fx-background-color: #ffffff;" +
                        "-fx-background-radius: 16;" +
                        "-fx-border-color: #dfe6ee;" +
                        "-fx-border-radius: 16;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 14, 0.2, 0, 6);"
        );

        // canvas responsivo (padding aggiornato da applyResponsive)
        canvas.widthProperty().bind(centerBg.widthProperty().subtract(canvasCardPadding*2));
        canvas.heightProperty().bind(centerBg.heightProperty().subtract(canvasCardPadding*2));

        // colonna destra (tema chiaro)
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1b1f24;");
        subtitle.setStyle("-fx-text-fill: #3a5a78; -fx-font-size: 12px;");

        description.setEditable(false);
        description.setWrapText(true);
        description.setStyle("-fx-control-inner-background: #f7f9fb; -fx-text-fill: #1a1a1a; -fx-background-radius: 10;");
        description.setPrefRowCount(6);

        notes.setPromptText("Note personali per questo circuito/veicolo…");
        notes.setPrefRowCount(6);
        notes.setStyle("-fx-control-inner-background: #f7f9fb; -fx-text-fill: #1a1a1a; -fx-background-radius: 10;");

        VBox descCard = card();
        descCard.getChildren().addAll(sectionTitle("Descrizione circuito"), description);

        // --- Consigli: card con scroll interno ---------------------------------
        tipsBox = new VBox(6);                          // CHANGED: non è più la card
        tipsBox.setFillWidth(true);                     // NEW: fa rispettare la larghezza
        tipsScroll = new ScrollPane(tipsBox);           // NEW
        tipsScroll.setFitToWidth(true);                 // NEW
        tipsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);   // NEW
        tipsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED); // NEW
        tipsScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;"); // NEW
        tipsScroll.setMinViewportHeight(120);           // NEW: altezza minima gradevole

        tipsCard = card();                              // NEW: la card vera
        tipsCard.getChildren().addAll(sectionTitle("Consigli ideali"), tipsScroll); // NEW
        VBox.setVgrow(tipsScroll, Priority.ALWAYS);     // NEW: lo scroll prende lo spazio
        VBox.setVgrow(tipsCard, Priority.ALWAYS);       // NEW: la card può espandersi
        // -----------------------------------------------------------------------

        VBox notesCard = card();
        Button save = new Button("Salva");
        save.setOnAction(e -> {
            var pre = SessionPreamble.parse(data.getCsvPath());
            String catKey = categoryKeyFromData(data, pre.vehicle);
            NotesStore.save(noteKeyWithCategory(catKey), notes.getText());
        });
        notesCard.getChildren().addAll(sectionTitle("Note personali"), notes, save);

        right.setPadding(new Insets(basePadding));
        right.setFillWidth(true); // NEW: per far wrappare bene le label
        right.getChildren().addAll(title, subtitle, descCard, tipsCard, notesCard); // CHANGED: tipsCard (non tipsBox)

        // ScrollPane per la colonna destra
        rightScroll.setFitToWidth(true);
        rightScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rightScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        rightScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        // ===== SplitPane responsivo: centro + destra =====
        splitPane = new SplitPane(centerBg, rightScroll);
        splitPane.setDividerPositions(dividerTarget);     // iniziale 55% / 45%

        // Pannello destro responsivo con limiti sensati
        right.setMinWidth(320);
        right.setMaxWidth(Double.MAX_VALUE);
        right.prefWidthProperty().bind(root.widthProperty().multiply(0.45));

        // Metti lo SplitPane al centro del BorderPane
        root.setCenter(splitPane);

        // >>> BLOCCO DEL DIVIDER
        Platform.runLater(() -> {
            forceDividerTo(dividerTarget);
            for (Node d : splitPane.lookupAll(".split-pane-divider")) {
                d.setMouseTransparent(true);
                d.setStyle("-fx-padding: 0;");
            }
            splitPane.getDividers().forEach(div ->
                    div.positionProperty().addListener((obs, oldV, newV) -> div.setPosition(dividerTarget))
            );
        });

        // ridisegna quando cambia misura
        canvas.widthProperty().addListener((o,a,b)->draw());
        canvas.heightProperty().addListener((o,a,b)->draw());

        // Responsività
        root.widthProperty().addListener((obs, oldW, newW) -> applyResponsive(newW.doubleValue(), root.getHeight(), splitPane, centerBg));
        root.heightProperty().addListener((obs, oldH, newH) -> applyResponsive(root.getWidth(), newH.doubleValue(), splitPane, centerBg));

        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                addWindowSizeListeners(newScene, splitPane, centerBg);
                Platform.runLater(() -> applyResponsive(root.getWidth(), root.getHeight(), splitPane, centerBg));
            }
        });

        Platform.runLater(() -> applyResponsive(root.getWidth(), root.getHeight(), splitPane, centerBg));
    }

    /** Aggancia listener anche alla Window per reagire a cambi "globali". */
    private void addWindowSizeListeners(Scene scene, SplitPane sp, StackPane centerBg) {
        Window w = scene.getWindow();
        if (w == null) return;
        w.widthProperty().addListener((o, a, b) -> applyResponsive(root.getWidth(), root.getHeight(), sp, centerBg));
        w.heightProperty().addListener((o, a, b) -> applyResponsive(root.getWidth(), root.getHeight(), sp, centerBg));
    }

    /** Applica stili e misure in base alle dimensioni correnti della finestra. */
    private void applyResponsive(double w, double h, SplitPane sp, StackPane centerBg) {
        final boolean narrowWidth = w < 1200;
        final boolean lowHeight  = h < 700;
        final boolean small = narrowWidth || lowHeight;
        final boolean large = w >= 1400 && h >= 850;

        basePadding = small ? 10 : (large ? 20 : 16);
        cardPadding = small ? 8  : (large ? 14 : 12);
        canvasCardPadding = small ? 14 : (large ? 24 : 20);

        right.setSpacing(small ? 10 : (large ? 16 : 14));
        right.setPadding(new Insets(basePadding));
        canvasWrap.setPadding(new Insets(canvasCardPadding));
        centerBg.setPadding(new Insets(basePadding));

        canvas.widthProperty().unbind();
        canvas.heightProperty().unbind();
        canvas.widthProperty().bind(centerBg.widthProperty().subtract(canvasCardPadding*2));
        canvas.heightProperty().bind(centerBg.heightProperty().subtract(canvasCardPadding*2));

        title.setStyle(String.format("-fx-font-size: %dpx; -fx-font-weight: bold; -fx-text-fill: #1b1f24;",
                small ? 18 : (large ? 22 : 20)));
        subtitle.setStyle(String.format("-fx-text-fill: #3a5a78; -fx-font-size: %dpx;",
                small ? 11 : (large ? 13 : 12)));

        description.setPrefRowCount(small ? 4 : (large ? 7 : 6));
        notes.setPrefRowCount(small ? 4 : (large ? 7 : 6));

        Orientation desired = small ? Orientation.VERTICAL : Orientation.HORIZONTAL;
        if (sp.getOrientation() != desired) sp.setOrientation(desired);

        dividerTarget = (desired == Orientation.VERTICAL) ? 0.60 : 0.55;
        forceDividerTo(dividerTarget);
        sp.getDividers().forEach(div ->
                div.positionProperty().addListener((obs, oldV, newV) -> div.setPosition(dividerTarget))
        );

        right.prefWidthProperty().unbind();
        if (desired == Orientation.HORIZONTAL) {
            right.prefWidthProperty().bind(root.widthProperty().multiply(0.45));
        }

        imageScaleFactor = small ? (lowHeight ? 0.86 : 0.88) : 0.90;

        draw();
    }

    private void forceDividerTo(double p) {
        splitPane.setDividerPositions(p);
        Platform.runLater(() -> splitPane.setDividerPositions(p));
    }

    private VBox card(){
        VBox v = new VBox(8);
        v.setPadding(new Insets(cardPadding));
        v.setStyle("-fx-background-color: #e9edf1; -fx-background-radius: 12; -fx-border-color: #d3dde6; -fx-border-radius: 12;");
        return v;
    }

    private Label sectionTitle(String t){
        Label l = new Label(t);
        l.setStyle("-fx-font-weight: bold; -fx-text-fill: #0f1419;");
        return l;
    }

    public Node getRoot(){ return root; }

    public void refresh(){
        var pre = SessionPreamble.parse(data.getCsvPath());
        String id = pre.venue==null? null : pre.venue.toLowerCase(Locale.ROOT);
        track = StaticTrackDB.get(id);

        if (track == null){
            title.setText(pre.venue==null? "Circuito" : pre.venue);
            subtitle.setText("Profilo non trovato. Aggiungi resources/assets/tracks/"+id+".json");
            draw(); return;
        }

        title.setText(track.displayName);
        subtitle.setText(String.format(Locale.ROOT, "Lunghezza %.3f km — %d settori", track.lengthKm, track.sectorSplits.size()));

        // descrizione
        description.setText(track.description==null? "" : track.description.trim());

        // categoria veicolo come nel resto dell'app
        String catKey = categoryKeyFromData(data, pre.vehicle);

        // Consigli (label, nessun link) -> popoliamo SOLO tipsBox
        // aggiorna il titolo nella card
        ((Label) tipsCard.getChildren().get(0)).setText("Consigli ideali ("+catKey+")"); // NEW
        tipsBox.getChildren().clear(); // CHANGED

        for (TrackInfo.Turn t : track.turns){
            TrackInfo.Advice a = t.adviceByVehicle.get(catKey);
            if (a == null) {
                if ("OTHER".equals(catKey)) a = t.adviceByVehicle.get("ROAD");
                if (a == null && "PROTOTYPE".equals(catKey)) a = t.adviceByVehicle.get("GT");
                if (a == null) a = t.adviceByVehicle.get("FORMULA");
                if (a == null) a = t.adviceByVehicle.get("GT");
                if (a == null) a = t.adviceByVehicle.get("ROAD");
            }
            String text = a==null ? "—" :
                    ((a.gear!=null? "Marcia "+a.gear+" • " : "") +
                            (a.vMinIdealKmh!=null? "Vel. "+Math.round(a.vMinIdealKmh)+"±"+Math.round(a.vRangeKmh==null?10:a.vRangeKmh)+" km/h" : "") +
                            (a.note!=null && !a.note.isBlank()? " — "+a.note : ""));
            Label line = new Label("T"+t.number+" "+t.name+" — "+text);
            line.setStyle("-fx-text-fill: #0f1419;");
            line.setWrapText(true);                    // NEW: niente taglio orizzontale
            line.setMaxWidth(Double.MAX_VALUE);        // NEW
            tipsBox.getChildren().add(line);
        }

        // note per coppia circuito+categoria
        notes.setText(NotesStore.load(noteKeyWithCategory(catKey)));

        draw();
    }

    private void draw(){
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth(), h = canvas.getHeight();

        g.setFill(Color.WHITE);
        g.fillRect(0, 0, w, h);

        if (track == null) return;

        if (track.imageResource != null) {
            try (InputStream is = getClass().getResourceAsStream(track.imageResource)) {
                if (is != null) {
                    Image img = new Image(is);

                    double scale = Math.min(w / img.getWidth(), h / img.getHeight()) * imageScaleFactor;
                    double iw = img.getWidth() * scale, ih = img.getHeight() * scale;
                    double ox = (w - iw) / 2, oy = (h - ih) / 2;

                    g.setFill(Color.web("#f3f6f9"));
                    g.fillRoundRect(ox - 10, oy - 10, iw + 20, ih + 20, 18, 18);
                    g.setStroke(Color.web("#dfe6ee"));
                    g.setLineWidth(1.2);
                    g.strokeRoundRect(ox - 10, oy - 10, iw + 20, ih + 20, 18, 18);

                    g.drawImage(img, ox, oy, iw, ih);
                }
            } catch (Exception ignore) {}
        }
    }

    // ===== Categoria veicolo dalla telemetria (VehicleTraits) =====
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

    // chiave note circuito+categoria (non il nome veicolo)
    private String noteKeyWithCategory(String catKey){
        var pre = SessionPreamble.parse(data.getCsvPath());
        String id = pre.venue==null? "unknown" : pre.venue.toLowerCase(Locale.ROOT);
        String vehCat = (catKey==null || catKey.isBlank()) ? "OTHER" : catKey;
        return id+"__"+vehCat;
    }

    private String noteKey(){
        var pre = SessionPreamble.parse(data.getCsvPath());
        String catKey = categoryKeyFromData(data, pre.vehicle);
        return noteKeyWithCategory(catKey);
    }

    static final class VehicleKey {
        static String fromVehicleName(String vehicle){
            if (vehicle==null) return "ROAD";
            String v = vehicle.toLowerCase(Locale.ROOT);
            if (v.contains("formula") || v.contains("f1") || v.contains("single")) return "FORMULA";
            if (v.contains("lmp") || v.contains("prototype") || v.contains("hypercar")) return "PROTOTYPE";
            if (v.contains("gt")) return "GT";
            if (v.contains("road") || v.contains("street")) return "ROAD";
            return "OTHER";
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
