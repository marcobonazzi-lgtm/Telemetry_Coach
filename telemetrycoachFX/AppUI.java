package org.simulator.telemetrycoachFX;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.simulator.canale.Lap;
import org.simulator.ui.DataController;
import org.simulator.ui.all_laps_view.AllLapsView;
import org.simulator.ui.analysis_view.AnalysisView;
import org.simulator.ui.compare_sessions_view.CompareSessionsView;
import org.simulator.ui.help_view.GuideGlossaryView;
import org.simulator.ui.time_line_view.TimeLineView;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

final class AppUI {

    private final DataController data;

    // UI principal
    private final BorderPane rootApp = new BorderPane();
    private TabPane tabs;
    private BorderPane analysisPaneContainer;
    private BorderPane allLapsContainer;

    private AnalysisView analysisView;
    private AllLapsView allLapsView;
    private TimeLineView timelineView;

    // nuova vista circuito (statica)
    private org.simulator.ui.track_view.TrackView trackView;
    private Tab circuitTab;

    // confronto sessioni
    private CompareSessionsView compareView;
    private Tab compareTab;

    // guida & glossario
    private GuideGlossaryView guideView;
    private Tab guideTab;

    // top bar
    private HBox top;
    private Label fileLabel;
    private ComboBox<Integer> refLapSelector;
    private ComboBox<Object> ghostSelector;
    private static final String NO_GHOST = "Nessun ghost";
    private CheckBox showDelta;

    // importer callback
    interface InsideImporter { void chooseAndLoadInsideApp(Stage stage); }
    private InsideImporter importer;

    AppUI(DataController data) { this.data = data; }

    Parent getRoot() { return rootApp; }

    void init(Stage stage, InsideImporter importer) {
        this.importer = importer;

        // ---------- TOP BAR ----------
        Button openBtn = new Button("Inserisci nuovo CSV…");
        openBtn.setOnAction(e -> importer.chooseAndLoadInsideApp(stage));
        openBtn.setStyle("-fx-font-size: 14px; -fx-padding: 6 12;");

        fileLabel = new Label("Nessun file");

        refLapSelector = new ComboBox<>();
        refLapSelector.setPromptText("Giro corrente");
        refLapSelector.valueProperty().addListener((o, ov, nv) -> {
            rebuildGhostOptions();
            if (ghostSelector.getValue() instanceof Integer gi && Objects.equals(gi, nv)) {
                ghostSelector.getSelectionModel().select(NO_GHOST);
                analysisView.clearGhost();
            }
            onSelectionsChanged();
        });

        ghostSelector = new ComboBox<>();
        ghostSelector.setPromptText("Ghost");
        ghostSelector.setButtonCell(new ComboBoxListCell<>() {
            @Override public void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setText(""); return; }
                setText(item instanceof Integer idx ? "Lap " + idx : NO_GHOST);
            }
        });
        ghostSelector.setCellFactory(cb -> new ListCell<>() {
            @Override protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setText(""); return; }
                setText(item instanceof Integer idx ? "Lap " + idx : NO_GHOST);
            }
        });
        ghostSelector.valueProperty().addListener((o, ov, nv) -> onSelectionsChanged());

        showDelta = new CheckBox("Mostra Delta");
        showDelta.setSelected(false);
        showDelta.selectedProperty().addListener((o, ov, nv) -> onSelectionsChanged());

        ImageView appLogo = makeLogoFromEither("/assets/app_logo.png",
                "C:/Users/addir/Desktop/TESI/BOZZA_TESI/src/main/resources/assets/app_logo.png", 40);
        ImageView dscLogo = makeLogoFromEither("/assets/dsc_lecce.png",
                "C:/Users/addir/Desktop/TESI/BOZZA_TESI/src/main/resources/assets/dsc_lecce.png", 40);
        ImageView uniLogo = makeLogoFromEither("/assets/unisalento.png",
                "C:/Users/addir/Desktop/TESI/BOZZA_TESI/src/main/resources/assets/unisalento.png", 40);
        HBox logosBox = new HBox(12, appLogo, dscLogo, uniLogo);
        logosBox.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        top = new HBox(10, openBtn, fileLabel, new Label("Giro corrente"), refLapSelector,
                new Label("Ghost"), ghostSelector, showDelta, spacer, logosBox);
        top.setPadding(new Insets(10)); top.setAlignment(Pos.CENTER_LEFT);

        // ---------- VISTE (crea UNA sola volta ciascuna) ----------
        timelineView = new TimeLineView(data);

        analysisView = new AnalysisView(data);
        analysisView.clearGhost();
        analysisPaneContainer = new BorderPane(analysisView.getRoot(), null, null, null, null);

        allLapsView = new AllLapsView(data);
        allLapsContainer = new BorderPane();
        Node allLapsRoot = allLapsView.getRoot();
        ScrollPane lapsScroll = new ScrollPane(allLapsRoot);
        lapsScroll.setFitToWidth(true);
        lapsScroll.setPannable(true);
        lapsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        allLapsContainer.setCenter(lapsScroll);

        // nuova vista: Circuito (statica)
        trackView  = new org.simulator.ui.track_view.TrackView(data);
        circuitTab = new Tab("Circuito", trackView.getRoot());
        circuitTab.setClosable(false);

        // confronto sessioni
        compareView = new CompareSessionsView(data);
        compareView.setStage(stage);
        compareTab = new Tab("Confronto Sessioni", compareView.getRoot());
        compareTab.setClosable(false);

        // guida & glossario
        guideView = new GuideGlossaryView();
        guideTab  = new Tab("Guida & Glossario", guideView.getRoot());
        guideTab.setClosable(false);

        // ---------- TABS ----------
        tabs = new TabPane();

        Tab t1 = new Tab("Timeline", timelineView.getRoot());       t1.setClosable(false);
        Tab t2 = new Tab("Analisi",  analysisPaneContainer);        t2.setClosable(false);
        Tab t3 = new Tab("Tutti i giri", allLapsContainer);         t3.setClosable(false);

        // Ordine: Timeline, Analisi, Tutti i giri, Circuito, Confronto Sessioni, Guida
        tabs.getTabs().setAll(t1, t2, t3, circuitTab, compareTab, guideTab);

        // top bar visibility
        tabs.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            boolean hideTop = (nv == compareTab) || (nv == guideTab) || (nv == circuitTab);
            rootApp.setTop(hideTop ? null : top);
        });

        // analysis top opzionale
        Node analysisTop = tryBuildAnalysisTop();
        if (analysisTop != null) analysisPaneContainer.setTop(analysisTop);

        rootApp.setTop(top);
        rootApp.setCenter(tabs);

        // prima render della pagina circuito (se c'è già un CSV selezionato verrà aggiornata in populateAfterLoad)
        trackView.refresh();
    }

    void populateAfterLoad(File f) {
        fileLabel.setText(f.getName());
        analysisView.showLap(null, null, showDelta.isSelected());
        allLapsView.render();

        List<Lap> laps = data.getLaps();
        refLapSelector.getItems().setAll(laps.stream().map(l -> l.index).toList());
        if (!laps.isEmpty()) refLapSelector.getSelectionModel().selectFirst();
        if (!laps.isEmpty() && timelineView != null) {
            timelineView.showLap(laps.get(0));
        }

        rebuildGhostOptions();
        ghostSelector.getSelectionModel().select(NO_GHOST);
        analysisView.clearGhost();

        // aggiorna confronto (nuovo CSV base)
        if (compareView != null) compareView.onBaseDataChanged();

        // aggiorna guida con quadro generale
        if (guideView != null) {
            guideView.rebuildGeneral();
        }

        // aggiorna la vista circuito (riconosce Venue/Vehicle dal preambolo CSV)
        if (trackView != null) trackView.refresh();

        onSelectionsChanged();

        // seleziona la prima tab in sicurezza (Timeline)
        tabs.getSelectionModel().select(tabs.getTabs().get(0));
    }

    // ---------------- internals ----------------
    private void rebuildGhostOptions() {
        List<Lap> laps = data.getLaps();
        if (laps == null || laps.isEmpty()) {
            ghostSelector.getItems().setAll(List.of(NO_GHOST));
            return;
        }
        Integer refIdx = refLapSelector.getValue();
        List<Object> ghosts = new ArrayList<>();
        ghosts.add(NO_GHOST);
        for (Lap l : laps) if (refIdx == null || !Objects.equals(l.index, refIdx)) ghosts.add(l.index);

        Object prev = ghostSelector.getValue();
        ghostSelector.getItems().setAll(ghosts);

        if (prev instanceof Integer idx && ghosts.contains(idx)) ghostSelector.getSelectionModel().select(idx);
        else ghostSelector.getSelectionModel().select(NO_GHOST);
    }

    private void onSelectionsChanged() {
        List<Lap> laps = data.getLaps();
        if (laps == null || laps.isEmpty()) return;

        Integer refIdx = refLapSelector.getValue();
        Object ghostVal = ghostSelector.getValue();
        boolean useGhost = (ghostVal instanceof Integer);
        Integer ghostIdx = useGhost ? (Integer) ghostVal : null;

        Lap refLap = (refIdx != null) ? data.byIndex(refIdx) : null;
        Lap ghostLap = (useGhost && ghostIdx != null) ? data.byIndex(ghostIdx) : null;

        if (refLap != null && ghostLap != null && Objects.equals(refLap.index, ghostLap.index)) {
            ghostLap = null;
            ghostSelector.getSelectionModel().select(NO_GHOST);
        }
        if (timelineView != null) timelineView.showLap(refLap);

        boolean showDeltaNow = showDelta.isSelected();
        analysisView.showLap(refLap, ghostLap, showDeltaNow);
        tryNotifyAnalysisGhost(ghostLap);

        allLapsView.render();
        tryNotifyCoachInAllLapsView(refLap, ghostLap);
    }

    private void tryNotifyAnalysisGhost(Lap ghostLap) {
        Object target = analysisView;
        Method[] candidates = {
                findMethod(target, "setGhostLap", Lap.class),
                findMethod(target, "setGhost", Lap.class),
                findMethod(target, "selectGhost", Integer.class),
                findMethod(target, "clearGhost")
        };
        for (Method m : candidates) {
            if (m == null) continue;
            try {
                switch (m.getName()) {
                    case "setGhostLap":
                    case "setGhost":   m.invoke(target, ghostLap); return;
                    case "selectGhost":m.invoke(target, ghostLap != null ? ghostLap.index : null); return;
                    case "clearGhost": m.invoke(target); return;
                }
            } catch (Exception ignore) {}
        }
    }

    private void tryNotifyCoachInAllLapsView(Lap refLap, Lap ghostLap) {
        Object target = allLapsView;
        Method[] candidates = {
                findMethod(target, "setReferenceAndGhost", Lap.class, Lap.class),
                findMethod(target, "setCoachInputs", Lap.class, Lap.class),
                findMethod(target, "updateCoach", Lap.class, Lap.class)
        };
        for (Method m : candidates) {
            if (m == null) continue;
            try { m.invoke(target, refLap, ghostLap); return; }
            catch (Exception ignore) {}
        }
    }

    private Method findMethod(Object target, String name, Class<?>... types) {
        try { return target.getClass().getMethod(name, types); }
        catch (Exception e) { return null; }
    }

    private Node tryBuildAnalysisTop() {
        try {
            Method m = analysisView.getClass().getMethod("buildTop");
            Object n = m.invoke(analysisView);
            if (n instanceof Node) return (Node) n;
        } catch (Exception ignore) {}
        try {
            Method m = analysisView.getClass().getMethod("getTop");
            Object n = m.invoke(analysisView);
            if (n instanceof Node) return (Node) n;
        } catch (Exception ignore) {}
        return null;
    }

    // ---------------- helper loghi ----------------
    /**
     * Crea un ImageView tentando prima il classpath (es. "/assets/logo.png") e,
     * se non presente, il percorso assoluto su disco. Mantiene il rapporto, altezza fissa.
     */
    private ImageView makeLogoFromEither(String classpath, String absolutePath, double fitHeight) {
        Image img = null;

        // 1) classpath
        URL url = AppUI.class.getResource(classpath);
        if (url != null) {
            img = new Image(url.toExternalForm(), 0, fitHeight, true, true);
        } else {
            // 2) fallback: file assoluto
            File f = new File(absolutePath);
            if (f.exists()) {
                img = new Image(f.toURI().toString(), 0, fitHeight, true, true);
            }
        }

        // Se ancora null, creo un ImageView vuoto per non rompere il layout
        ImageView iv = (img != null) ? new ImageView(img) : new ImageView();
        iv.setPreserveRatio(true);
        iv.setFitHeight(fitHeight);
        iv.setPickOnBounds(true);
        return iv;
    }
}
