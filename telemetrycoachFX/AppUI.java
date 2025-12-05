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
import org.simulator.gemini.GeminiService; // <--- IMPORTANTE
import org.simulator.tracks.SessionPreamble;
import org.simulator.ui.DataController;
import org.simulator.ui.all_laps_view.AllLapsView;
import org.simulator.ui.analysis_view.AnalysisView;
import org.simulator.ui.analysis_view.EngineerChatPane;
import org.simulator.ui.compare_sessions_view.CompareSessionsView;
import org.simulator.ui.help_view.GuideGlossaryView;
import org.simulator.ui.time_line_view.TimeLineView;

import java.io.BufferedReader;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

final class AppUI {

    private final DataController data;
    private final Runnable onGoHome;

    private final BorderPane rootApp = new BorderPane();
    private TabPane tabs;

    // Contenitori per le viste (Lazy Loading)
    private BorderPane analysisPaneContainer;
    private BorderPane allLapsContainer;

    // Riferimenti alle viste
    private AnalysisView analysisView;
    private AllLapsView allLapsView;
    private TimeLineView timelineView;
    private org.simulator.ui.track_view.TrackView trackView;
    private CompareSessionsView compareView;
    private GuideGlossaryView guideView;

    // Riferimenti ai Tab
    private Tab timelineTab;
    private Tab analysisTab;
    private Tab allLapsTab;
    private Tab circuitTab;
    private Tab compareTab;
    private Tab guideTab;

    private Stage stage;

    private HBox top;
    private Label fileLabel;
    private ComboBox<Integer> refLapSelector;
    private ComboBox<Object> ghostSelector;
    private static final String NO_GHOST = "Nessun ghost";
    private CheckBox showDelta;
    private Button openBtn;

    private final Map<Tab, Boolean> tabsDirty = new HashMap<>();

    private Lap currentRefLap;
    private Lap currentGhostLap;
    private boolean currentShowDelta;

    interface InsideImporter { void chooseAndLoadInsideApp(Stage stage); }
    private InsideImporter importer;

    AppUI(DataController data, Runnable onGoHome) {
        this.data = data;
        this.onGoHome = onGoHome;
        buildUI();
    }

    Parent getRoot() { return rootApp; }

    void setImporterAndStage(Stage stage, InsideImporter importer) {
        this.stage = stage;
        this.importer = importer;
        if (openBtn != null) openBtn.setDisable(false);
    }

    private void buildUI() {
        Button homeBtn = new Button("⌂ Home");
        homeBtn.setStyle("-fx-font-size: 14px; -fx-padding: 6 12; -fx-background-color: #3a4a5a; -fx-text-fill: white; -fx-background-radius: 6;");
        homeBtn.setOnAction(e -> {
            fullReset();
            if(onGoHome != null) onGoHome.run();
        });

        openBtn = new Button("Inserisci nuovo CSV…");
        openBtn.setOnAction(e -> {
            if (importer != null && stage != null) importer.chooseAndLoadInsideApp(stage);
        });
        openBtn.setStyle("-fx-font-size: 14px; -fx-padding: 6 12;");

        fileLabel = new Label("Nessun file");

        refLapSelector = new ComboBox<>();
        refLapSelector.setPromptText("Giro corrente");
        refLapSelector.valueProperty().addListener((o, ov, nv) -> {
            rebuildGhostOptions();
            if (ghostSelector.getValue() instanceof Integer gi && Objects.equals(gi, nv)) {
                ghostSelector.getSelectionModel().select(NO_GHOST);
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

        ImageView appLogo = makeLogoFromEither("/assets/app_logo.png", "", 40);
        ImageView dscLogo = makeLogoFromEither("/assets/dsc_lecce.png", "", 40);
        ImageView uniLogo = makeLogoFromEither("/assets/unisalento.png", "", 40);
        HBox logosBox = new HBox(12, appLogo, dscLogo, uniLogo);
        logosBox.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        top = new HBox(10, homeBtn, openBtn, fileLabel, new Label("Giro corrente"), refLapSelector,
                new Label("Ghost"), ghostSelector, showDelta, spacer, logosBox);
        top.setPadding(new Insets(10)); top.setAlignment(Pos.CENTER_LEFT);

        timelineView = new TimeLineView(data);
        timelineTab = new Tab("Timeline", timelineView.getRoot());
        timelineTab.setClosable(false);

        analysisTab = new Tab("Analisi", new Label("Caricamento..."));
        analysisTab.setClosable(false);
        analysisPaneContainer = new BorderPane();

        allLapsTab = new Tab("Tutti i giri", new Label("Caricamento..."));
        allLapsTab.setClosable(false);
        allLapsContainer = new BorderPane();

        circuitTab = new Tab("Circuito", new Label("Caricamento..."));
        circuitTab.setClosable(false);

        compareTab = new Tab("Confronto Sessioni", new Label("Caricamento..."));
        compareTab.setClosable(false);

        guideTab  = new Tab("Guida & Glossario", new Label("Caricamento..."));
        guideTab.setClosable(false);

        tabs = new TabPane();
        tabs.getTabs().setAll(timelineTab, analysisTab, allLapsTab, circuitTab, compareTab, guideTab);

        tabs.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            boolean hideTop = (nv == compareTab) || (nv == guideTab) || (nv == circuitTab);
            rootApp.setTop(hideTop ? null : top);
            ensureViewCreated(nv);
            if (tabsDirty.getOrDefault(nv, false)) {
                refreshViewData(nv);
                tabsDirty.put(nv, false);
            }
        });

        rootApp.setTop(top);
        rootApp.setCenter(tabs);
    }

    private void ensureViewCreated(Tab tab) {
        if (tab == null) return;
        rootApp.setCursor(javafx.scene.Cursor.WAIT);
        try {
            if (tab == timelineTab && timelineView == null) {
                timelineView = new TimeLineView(data);
                tab.setContent(timelineView.getRoot());
            }
            else if (tab == analysisTab && analysisView == null) {
                analysisView = new AnalysisView(data);
                analysisView.clearGhost();
                analysisPaneContainer.setCenter(analysisView.getRoot());
                Node analysisTop = tryBuildAnalysisTop();
                if (analysisTop != null) analysisPaneContainer.setTop(analysisTop);
                tab.setContent(analysisPaneContainer);
            }
            else if (tab == allLapsTab && allLapsView == null) {
                allLapsView = new AllLapsView(data);
                Node allLapsRoot = allLapsView.getRoot();
                ScrollPane lapsScroll = new ScrollPane(allLapsRoot);
                lapsScroll.setFitToWidth(true);
                lapsScroll.setPannable(true);
                lapsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                allLapsContainer.setCenter(lapsScroll);
                tab.setContent(allLapsContainer);
            }
            else if (tab == circuitTab && trackView == null) {
                trackView = new org.simulator.ui.track_view.TrackView(data);
                tab.setContent(trackView.getRoot());
            }
            else if (tab == compareTab && compareView == null) {
                try {
                    compareView = new CompareSessionsView(data);
                    if (stage != null) compareView.setStage(stage);
                    tab.setContent(compareView.getRoot());
                    compareView.onBaseDataChanged();
                } catch (Exception e) {
                    tab.setContent(new Label("Errore: " + e.getMessage()));
                }
            }
            else if (tab == guideTab && guideView == null) {
                guideView = new GuideGlossaryView();
                tab.setContent(guideView.getRoot());
                guideView.rebuildGeneral();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            rootApp.setCursor(javafx.scene.Cursor.DEFAULT);
        }
    }

    private void refreshViewData(Tab tab) {
        if (tab == timelineTab && timelineView != null) {
            timelineView.showLap(currentRefLap);
        }
        else if (tab == analysisTab && analysisView != null) {
            analysisView.showLap(currentRefLap, currentGhostLap, currentShowDelta);
            tryNotifyAnalysisGhost(currentGhostLap);
        }
        else if (tab == allLapsTab && allLapsView != null) {
            allLapsView.render();
            tryNotifyCoachInAllLapsView(currentRefLap, currentGhostLap);
        }
        else if (tab == circuitTab && trackView != null) {
            trackView.refresh();
        }
        else if (tab == compareTab && compareView != null) {
            compareView.onBaseDataChanged();
        }
    }

    private void fullReset() {
        this.timelineView = null;
        this.analysisView = null;
        this.allLapsView = null;
        this.trackView = null;
        this.compareView = null;
        this.tabsDirty.clear();
        if (tabs != null) {
            for (Tab t : tabs.getTabs()) {
                if (t != guideTab) t.setContent(null);
            }
        }
    }

    void populateAfterLoad(File f) {
        fullReset();

        // ⚠️ FIX: RESETTA LA MEMORIA DELL'AI QUANDO CARICHI UN NUOVO FILE
        GeminiService.clearHistory();
        EngineerChatPane.clearGlobalChat();

        fileLabel.setText(f.getName());
        List<Lap> laps = data.getLaps();

        refLapSelector.getItems().setAll(laps.stream().map(l -> l.index).toList());
        if (!laps.isEmpty()) refLapSelector.getSelectionModel().selectFirst();

        rebuildGhostOptions();
        ghostSelector.getSelectionModel().select(NO_GHOST);

        String vehicle = computeVehicleName();
        if (stage != null) stage.setTitle("Telemetry Coach — " + vehicle);

        onSelectionsChanged();

        if (allLapsTab != null) {
            tabsDirty.put(allLapsTab, true);
        }

        tabs.getSelectionModel().select(timelineTab);
        ensureViewCreated(timelineTab);
        refreshViewData(timelineTab);
    }

    private void onSelectionsChanged() {
        List<Lap> laps = data.getLaps();
        if (laps == null || laps.isEmpty()) return;

        Integer refIdx = refLapSelector.getValue();
        Object ghostVal = ghostSelector.getValue();
        boolean useGhost = (ghostVal instanceof Integer);
        Integer ghostIdx = useGhost ? (Integer) ghostVal : null;

        this.currentRefLap = (refIdx != null) ? data.byIndex(refIdx) : null;
        this.currentGhostLap = (useGhost && ghostIdx != null) ? data.byIndex(ghostIdx) : null;
        this.currentShowDelta = showDelta.isSelected();

        if (currentRefLap != null && currentGhostLap != null && Objects.equals(currentRefLap.index, currentGhostLap.index)) {
            currentGhostLap = null;
            ghostSelector.getSelectionModel().select(NO_GHOST);
            this.currentGhostLap = null;
        }

        for (Tab t : tabs.getTabs()) {
            if (t != allLapsTab) {
                tabsDirty.put(t, true);
            }
        }

        Tab currentTab = tabs.getSelectionModel().getSelectedItem();
        if (currentTab != null) {
            ensureViewCreated(currentTab);
            if (tabsDirty.getOrDefault(currentTab, false)) {
                refreshViewData(currentTab);
                tabsDirty.put(currentTab, false);
            }
        }
    }

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

    private void tryNotifyAnalysisGhost(Lap ghostLap) {
        if (analysisView == null) return;
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
        if (allLapsView == null) return;
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
        if (analysisView == null) return null;
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

    private ImageView makeLogoFromEither(String classpath, String absolutePath, double fitHeight) {
        Image img = null;
        URL url = AppUI.class.getResource(classpath);
        if (url != null) {
            img = new Image(url.toExternalForm(), 0, fitHeight, true, true);
        } else {
            File f = new File(absolutePath);
            if (f.exists()) {
                img = new Image(f.toURI().toString(), 0, fitHeight, true, true);
            }
        }
        ImageView iv = (img != null) ? new ImageView(img) : new ImageView();
        iv.setPreserveRatio(true);
        iv.setFitHeight(fitHeight);
        iv.setPickOnBounds(true);
        return iv;
    }

    private String computeVehicleName() {
        try {
            Path csvPath = data.getCsvPath();
            var pre = SessionPreamble.parse(csvPath);
            if (pre != null) {
                String v = null;
                try { v = (String) SessionPreamble.class.getField("car").get(pre); } catch (Throwable __) {}
                if (isNonEmpty(v)) return v;
                try { v = (String) SessionPreamble.class.getMethod("car").invoke(pre); } catch (Throwable __) {}
                if (isNonEmpty(v)) return v;
            }
            String fromCsv = tryReadVehicleFromCsv(csvPath);
            if (isNonEmpty(fromCsv)) return fromCsv;
            String guess = csvPath.getFileName().toString().replace(".csv","").replace('_',' ').trim();
            return guess.isBlank() ? "Veicolo sconosciuto" : guess;
        } catch (Exception e) {
            return "Veicolo sconosciuto";
        }
    }

    private boolean isNonEmpty(String s){ return s != null && !s.isBlank(); }

    private String tryReadVehicleFromCsv(Path csvPath) {
        if (csvPath == null) return null;
        try (BufferedReader br = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String header = br.readLine();
            if (header == null) return null;
            String sep = header.contains(";") ? ";" : ",";
            String[] cols = header.split(sep, -1);
            int idx = -1;
            String[] candidates = { "car", "vehicle", "car_name", "carname", "model", "veicolo", "nomeveicolo" };
            for (int i = 0; i < cols.length && idx < 0; i++) {
                String c = cols[i].trim().replace("\"","").toLowerCase(Locale.ROOT);
                for (String cand : candidates) if (c.equals(cand)) { idx = i; break; }
            }
            String first = br.readLine();
            if (idx >= 0 && first != null) {
                String[] vals = first.split(sep, -1);
                if (idx < vals.length) {
                    String v = vals[idx].replace("\"","").trim();
                    if (!v.isBlank()) return v;
                }
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }
}