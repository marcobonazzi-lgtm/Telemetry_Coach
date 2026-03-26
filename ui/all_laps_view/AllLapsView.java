package org.simulator.ui.all_laps_view;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.simulator.analisi_base.lap_analysis.LapAnalysis;
import org.simulator.analisi_base.session_analysis.SessionAnalysis;
import org.simulator.canale.Lap;
import org.simulator.tracks.StaticTrackDB;
import org.simulator.tracks.TrackInfo;
import org.simulator.ui.DataController;
import org.simulator.ui.analysis_view.EngineerChatPane;
import org.simulator.ui.settings.UiSettings;
import org.simulator.widget.*;
import org.simulator.ui.asix_pack.AxisChoice;
import org.simulator.ui.asix_pack.AxisPicker;
import org.simulator.ui.ChartManager;
import org.simulator.ui.ChartPane;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class AllLapsView {

    private final DataController data;
    private final ChartManager charts;

    private final StackPane rootStack = new StackPane();
    private final GridPane grid = new GridPane();
    private final Label xAxisLabel = new Label("Asse X: (n/d)");

    private final ComboBox<ChartPane.PlotType> plotSelector = new ComboBox<>();
    private ChartPane.PlotType currentType = ChartPane.PlotType.SPEED_DIST;

    private final HBox lapsRow = new HBox(12);
    private final ScrollPane lapsScroll = new ScrollPane(lapsRow);
    private final Button btnLeft  = new Button("◄");
    private final Button btnRight = new Button("►");
    private final StackPane lapsViewport = new StackPane();
    private static final double CELL_MIN_W = 420;
    private static final double GAP = 12;
    private int currentCols = -1;

    private final VBox loadingOverlay;
    private final AtomicLong renderToken = new AtomicLong(0);

    public AllLapsView(DataController data) {
        this.data = data;
        this.charts = new ChartManager();

        grid.setHgap(12); grid.setVgap(12); grid.setPadding(new Insets(12));

        lapsRow.setAlignment(Pos.TOP_LEFT);
        lapsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        lapsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        lapsScroll.setFitToHeight(true);
        lapsScroll.setPannable(true);

        StackPane.setAlignment(btnLeft, Pos.CENTER_LEFT);
        StackPane.setAlignment(btnRight, Pos.CENTER_RIGHT);
        StackPane.setMargin(btnLeft, new Insets(0,0,0,4));
        StackPane.setMargin(btnRight,new Insets(0,4,0,0));
        btnLeft.setFocusTraversable(false);
        btnRight.setFocusTraversable(false);

        lapsViewport.getChildren().addAll(lapsScroll, btnLeft, btnRight);
        lapsViewport.setMinHeight(340);
        btnLeft.setOnAction(ignored -> pageBy(-1));
        btnRight.setOnAction(ignored -> pageBy(+1));

        lapsScroll.viewportBoundsProperty().addListener((ignoredObs, ov, nv) -> {
            if (nv.getWidth() != ov.getWidth()) sizeCardsToViewport();
        });

        plotSelector.getItems().addAll(
                ChartPane.PlotType.SPEED_DIST,
                ChartPane.PlotType.THR_BRAKE_DIST,
                ChartPane.PlotType.STEERING_DIST,
                ChartPane.PlotType.RPM_TIME,
                ChartPane.PlotType.FFB_FORCE,
                ChartPane.PlotType.PEDAL_FORCE,
                ChartPane.PlotType.SEAT_FORCE
        );
        plotSelector.getSelectionModel().select(currentType);
        plotSelector.valueProperty().addListener((ignoredObs, ignoredOld, nv) -> {
            if (nv != null) {
                currentType = nv;
                render();
            }
        });

        Node top = buildTop();
        VBox contentBox = new VBox();
        contentBox.getChildren().addAll(top, grid);
        VBox.setVgrow(grid, Priority.ALWAYS);

        loadingOverlay = new VBox(15, new ProgressIndicator(), new Label("Analisi sessione completa..."));
        loadingOverlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        loadingOverlay.setFillWidth(true);
        loadingOverlay.getChildren().get(1).setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
        loadingOverlay.getChildren().get(0).setStyle("-fx-progress-color: #0096c9;");
        loadingOverlay.setAlignment(Pos.CENTER);
        loadingOverlay.setVisible(false);
        loadingOverlay.setStyle("-fx-background-color: rgba(20, 24, 30, 0.85); -fx-background-radius: 0;");

        rootStack.getChildren().addAll(contentBox, loadingOverlay);

        UiSettings ui = UiSettings.get();
        ui.wTyreTempProperty().addListener((ignoredObs, ignoredOld, ignoredNew) -> render());
        ui.wBrakesProperty().addListener((ignoredObs, ignoredOld, ignoredNew) -> render());
        ui.wTyrePressProperty().addListener((ignoredObs, ignoredOld, ignoredNew) -> render());
        ui.wDamageProperty().addListener((ignoredObs, ignoredOld, ignoredNew)    -> render());
        ui.wPedalsProperty().addListener((ignoredObs, ignoredOld, ignoredNew)    -> render());
    }

    public Node getRoot() { return rootStack; }
    public GridPane grid() { return grid; }

    public Node buildTop() {
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Label tipoLbl = new Label("Tipo grafico:");
        HBox top = new HBox(12, xAxisLabel, spacer, tipoLbl, plotSelector);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(6, 12, 6, 12));
        return new VBox(top);
    }

    public void render() {
        long myToken = renderToken.incrementAndGet();
        loadingOverlay.setVisible(true);
        grid.getChildren().clear();
        lapsRow.getChildren().clear();

        List<Lap> laps = data.getLaps();
        if (laps == null || laps.isEmpty()) {
            xAxisLabel.setText("Asse X: (n/d)");
            loadingOverlay.setVisible(false);
            return;
        }

        AxisChoice axis = AxisPicker.pick(laps.getFirst());
        xAxisLabel.setText("Asse X: " + axis.label + "  •  Grafici: " + currentType);

        List<VBox> placeholders = preparePlaceholders(laps);
        lapsRow.getChildren().addAll(placeholders);

        grid.add(lapsViewport, 0, 0);

        Platform.runLater(() -> renderLapAsync(0, laps, axis, myToken, placeholders));

        Platform.runLater(() -> {
            if (renderToken.get() != myToken) return;
            try {
                VBox sessionBox = buildSessionSection(laps);
                grid.add(sessionBox, 0, 1);
                GridPane.setHgrow(sessionBox, Priority.ALWAYS);
            } catch (Exception ignored) {}
        });
    }

    private List<VBox> preparePlaceholders(List<Lap> laps) {
        List<VBox> boxes = new ArrayList<>(laps.size());
        for (int i = 0; i < laps.size(); i++) {
            VBox card = new VBox();
            card.setAlignment(Pos.CENTER);
            card.setPadding(new Insets(0));
            card.setMinWidth(Region.USE_PREF_SIZE);
            card.setMaxWidth(Double.MAX_VALUE);
            card.getChildren().add(new Label(""));
            boxes.add(card);
        }
        sizeCardsToViewport();
        return boxes;
    }

    private void renderLapAsync(int index, List<Lap> laps, AxisChoice axis, long token, List<VBox> containers) {
        if (renderToken.get() != token) return;

        if (index >= laps.size()) {
            updateArrowsVisibility();
            loadingOverlay.setVisible(false);
            return;
        }

        int batchSize = 2;
        int endIndex = Math.min(index + batchSize, laps.size());

        for (int i = index; i < endIndex; i++) {
            try {
                Lap lap = laps.get(i);
                VBox container = containers.get(i);
                String yLabel = yLabelFor(currentType);
                String status = lap.validityStatus(laps);
                LineChart<Number, Number> c = charts.buildChart("Lap " + lap.index + " (" + status + ")", axis.label, yLabel);
                charts.renderChart(c, currentType, lap, null, axis);
                container.getChildren().setAll(c);
                refreshCardSize(container);
            } catch (Exception ignored) {}
        }
        Platform.runLater(() -> renderLapAsync(endIndex, laps, axis, token, containers));
    }

    private double calculateCardWidth(double viewportW) {
        int targetCols = Math.max(1, Math.min(5, (int)Math.floor((viewportW + GAP) / (CELL_MIN_W + GAP))));
        return (viewportW - (targetCols - 1) * GAP) / targetCols;
    }

    private void sizeCardsToViewport() {
        double viewportW = lapsScroll.getViewportBounds() == null ? 0 : lapsScroll.getViewportBounds().getWidth();
        if (viewportW <= 0 || lapsRow.getChildren().isEmpty()) return;

        double cardW = calculateCardWidth(viewportW);
        for (Node n : lapsRow.getChildren()) {
            if (n instanceof VBox v) refreshCardSize(v, cardW);
        }
    }

    private void refreshCardSize(VBox v) {
        double viewportW = lapsScroll.getViewportBounds() == null ? 0 : lapsScroll.getViewportBounds().getWidth();
        if (viewportW <= 0) return;
        refreshCardSize(v, calculateCardWidth(viewportW));
    }

    private void refreshCardSize(VBox v, double width) {
        v.setPrefWidth(width);
        if (!v.getChildren().isEmpty() && v.getChildren().getFirst() instanceof Region r) r.setPrefWidth(width);
    }

    private void updateArrowsVisibility() {
        Platform.runLater(() -> {
            double contentW = lapsRow.prefWidth(-1);
            double viewportW = lapsScroll.getViewportBounds() == null ? 0 : lapsScroll.getViewportBounds().getWidth();
            boolean needArrows = contentW > viewportW + 1;
            btnLeft.setVisible(needArrows);
            btnRight.setVisible(needArrows);
        });
    }

    private void pageBy(int pages) {
        double contentW  = lapsRow.getBoundsInLocal().getWidth();
        double viewportW = lapsScroll.getViewportBounds().getWidth();
        if (contentW <= viewportW) return;
        double step = viewportW / contentW;
        double h = lapsScroll.getHvalue() + pages * step * 0.95;
        h = Math.max(0.0, Math.min(1.0, h));
        lapsScroll.setHvalue(h);
    }

    private VBox buildSessionSection(List<Lap> laps) {
        VBox sessionBox = new VBox(12);
        sessionBox.setAlignment(Pos.TOP_LEFT);
        sessionBox.setPadding(new Insets(10));

        Lap best = bestLapOf(laps);
        Node bestNode = null;
        if (best != null) {
            var bestStats = LapAnalysis.basicStats(best);
            String bestTitle = "🏆 Statistiche (Miglior giro: #" + best.index + (Double.isNaN(best.lapTime) ? "" : ", " + TimeUtil.formatLapTime(best.lapTime)) + ")";
            bestNode = titled(bestTitle, UiWidgets.buildStatsAccordion(bestStats));
        }

        var avgBase = SessionAnalysis.averageStats(laps);
        var forceStats = SessionForceStatsAggregator.build(laps);
        var merged = new LinkedHashMap<String, Double>();
        merged.putAll(avgBase);
        merged.putAll(forceStats);

        Node avgAccordion = UiWidgets.buildStatsAccordion(merged);
        // Cambiato da TitledPane a Node per la flessibilità UI
        Node mediaPane = titled("⏱️ Statistiche (Media sessione)", new VBox(8, avgAccordion));

        HBox statsRow = new HBox(15);
        statsRow.setFillHeight(true);
        if (bestNode != null) statsRow.getChildren().add(bestNode);
        statsRow.getChildren().add(mediaPane);
        for (Node n : statsRow.getChildren()) { if (n instanceof Region r) { r.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(r, Priority.ALWAYS); } }
        sessionBox.getChildren().add(statsRow);

        // --- ENGINEER UI ---
        EngineerChatPane engineerUI = new EngineerChatPane();
        engineerUI.setContextMode(false);

        // --- LOGICA PISTA ---
        try {
            if (data.getPreamble() != null && data.getPreamble().venue != null) {
                String trackName = data.getPreamble().venue;
                TrackInfo trackInfo = StaticTrackDB.get(trackName);
                if (trackInfo != null) {
                    engineerUI.setTrackInfo(trackInfo);
                }
            }
        } catch (Exception ignored) { }
        // --------------------

        engineerUI.loadSession(laps);

        Node engineerNode = engineerUI.getRoot();
        VBox.setVgrow(engineerNode, Priority.ALWAYS);
        sessionBox.getChildren().add(engineerNode);

        sessionBox.getChildren().add(buildWidgetsGrid(laps));
        return sessionBox;
    }

    private static Lap bestLapOf(List<Lap> laps) {
        if (laps == null || laps.isEmpty()) return null;
        Optional<Lap> bestByTime = laps.stream().filter(l -> l.isComplete(laps)).min(Comparator.comparingDouble(l -> l.lapTime));
        return bestByTime.orElse(laps.stream().max(Comparator.comparingInt(l -> l.samples == null ? 0 : l.samples.size())).orElse(laps.getFirst()));
    }

    private static Node titled(String title, Node content) {
        VBox card = new VBox();
        card.getStyleClass().add("modern-card");

        Label header = new Label(title.toUpperCase(Locale.ROOT));
        header.getStyleClass().add("modern-card-header");
        header.setMaxWidth(Double.MAX_VALUE);

        VBox contentContainer = new VBox(content);
        contentContainer.setPadding(new Insets(12));
        VBox.setVgrow(contentContainer, Priority.ALWAYS);

        card.getChildren().addAll(header, contentContainer);
        return card;
    }

    private static String yLabelFor(ChartPane.PlotType t) {
        return switch (t) {
            case SPEED_DIST -> "Speed [km/h]";
            case THR_BRAKE_DIST -> "%";
            case STEERING_DIST -> "Angle [deg]";
            case RPM_TIME -> "RPM";
            case FFB_FORCE -> "FFB";
            case PEDAL_FORCE, SEAT_FORCE -> "Force (N)";
        };
    }

    private GridPane buildWidgetsGrid(List<Lap> laps) {
        var ui = org.simulator.ui.settings.UiSettings.get();
        GridPane g = new GridPane();
        g.setHgap(20); g.setVgap(20); g.setPadding(new Insets(15, 20, 15, 20));

        List<Node> activeWidgets = new ArrayList<>();
        if (ui.wTyreTempProperty().get())  activeWidgets.add(TyreThermalWidget.buildFromLaps(laps));
        if (ui.wTyrePressProperty().get()) activeWidgets.add(TyrePressureWidget.buildFromLaps(laps));
        if (ui.wSuspensionProperty().get()) activeWidgets.add(SuspensionWidget.buildFromLaps(laps));
        if (ui.wBrakesProperty().get())    activeWidgets.add(BrakeThermalWidget.buildFromLaps(laps));
        if (ui.wDamageProperty().get())    activeWidgets.add(DamageWidget.buildFromLaps(laps));
        if (ui.wPedalsProperty().get())    activeWidgets.add(PedalWidget.buildFromLaps(laps));

        g.widthProperty().addListener((ignoredObs, ignoredOld, newVal) -> {
            double width = newVal.doubleValue();
            if (width <= 0) return;
            int targetCols = (width > 1600) ? 3 : 2;
            if (targetCols != currentCols) {
                currentCols = targetCols;
                rebuildGridStructure(g, activeWidgets, targetCols);
            }
        });
        rebuildGridStructure(g, activeWidgets, 2);
        return g;
    }

    private void rebuildGridStructure(GridPane g, List<Node> widgets, int cols) {
        g.getChildren().clear();
        g.getColumnConstraints().clear();
        g.getRowConstraints().clear();
        double percent = 100.0 / cols;
        for (int i = 0; i < cols; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(percent);
            g.getColumnConstraints().add(c);
        }
        for (int i = 0; i < widgets.size(); i++) {
            Node n = widgets.get(i);
            if (n instanceof Region r) { r.setMaxWidth(Double.MAX_VALUE); r.setMaxHeight(Double.MAX_VALUE); }
            VBox card = new VBox(n);
            card.setAlignment(Pos.CENTER);
            card.setPadding(new Insets(10));
            card.setStyle("-fx-background-color: -fx-control-inner-background; -fx-border-color: #ccc; -fx-border-radius: 6; -fx-background-radius: 6;");
            VBox.setVgrow(n, Priority.ALWAYS);
            GridPane.setHgrow(card, Priority.ALWAYS);
            GridPane.setVgrow(card, Priority.ALWAYS);
            g.add(card, i % cols, i / cols);
        }
    }
}
