package org.simulator.ui.analysis_view;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.input.ScrollEvent;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.layout.Region;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.simulator.analisi_base.force_stats.ForceStats;
import org.simulator.analisi_base.lap_analysis.LapAnalysis;
import org.simulator.canale.Lap;
import org.simulator.tracks.StaticTrackDB;
import org.simulator.tracks.TrackInfo;
import org.simulator.widget.*;
import org.simulator.ui.*;
import org.simulator.ui.asix_pack.AxisChoice;
import org.simulator.ui.asix_pack.AxisPicker;
import org.simulator.ui.ChartManager;
import org.simulator.ui.ChartPane;
import org.simulator.ui.settings.UiSettings;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class AnalysisView {

    private final DataController data;
    private final ChartManager charts;

    private final BorderPane root = new BorderPane();
    private final ScrollPane scroll = new ScrollPane();

    private final VBox body = new VBox(12);

    private final MiniChartBox chartA = new MiniChartBox("Grafico A", ChartPane.PlotType.SPEED_DIST);
    private final MiniChartBox chartB = new MiniChartBox("Grafico B", ChartPane.PlotType.THR_BRAKE_DIST);
    private final MiniChartBox chartC = new MiniChartBox("Grafico C", ChartPane.PlotType.STEERING_DIST);
    private final LineChart<Number, Number> deltaChart;
    private final UiSettings ui = UiSettings.get();

    private volatile Lap refLap;
    private volatile Lap ghostLap;
    private boolean showDelta = false;

    private final PauseTransition updateDebouncer;

    public AnalysisView(DataController data) {
        this.data = data;
        this.charts = new ChartManager();

        GridPane chartsGrid = new GridPane();
        chartsGrid.setHgap(10); chartsGrid.setVgap(10); chartsGrid.setPadding(new Insets(10));
        chartsGrid.add(chartA, 0, 0);
        chartsGrid.add(chartB, 1, 0);
        chartsGrid.add(chartC, 0, 1);

        deltaChart = charts.buildChart("Delta Lap Time", "X", "Δt [s]");
        deltaChart.setAnimated(false);
        deltaChart.setCreateSymbols(false);
        deltaChart.setCache(true);
        deltaChart.setCacheHint(CacheHint.SPEED);

        chartsGrid.add(deltaChart, 1, 1);

        ColumnConstraints col = new ColumnConstraints(); col.setPercentWidth(50);
        chartsGrid.getColumnConstraints().setAll(col, col);

        VBox content = new VBox(12);
        content.getChildren().setAll(chartsGrid, body);
        content.setFillWidth(true);

        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setContent(content);
        scroll.setPannable(true);

        root.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.isControlDown()) return;
            double dy = e.getDeltaY();
            if (dy != 0) {
                double v = scroll.getVvalue();
                scroll.setVvalue(Math.max(0, Math.min(1, v - dy/1200.0)));
                e.consume();
            }
        });

        root.setPadding(new Insets(8));
        root.setCenter(scroll);

        updateDebouncer = new PauseTransition(Duration.millis(150));
        updateDebouncer.setOnFinished(ignored -> performRenderBodyAsync());

        ui.wTyreTempProperty().addListener((ignoredObs, ignoredOld, ignoredNew) -> triggerRenderBody());
        ui.wBrakesProperty().addListener((ignoredObs, ignoredOld, ignoredNew) -> triggerRenderBody());
        ui.wTyrePressProperty().addListener((ignoredObs, ignoredOld, ignoredNew) -> triggerRenderBody());
        ui.wDamageProperty().addListener((ignoredObs, ignoredOld, ignoredNew)    -> triggerRenderBody());
        ui.wPedalsProperty().addListener((ignoredObs, ignoredOld, ignoredNew)    -> triggerRenderBody());

        chartA.selector.valueProperty().addListener((ignoredObs, ignoredOld, ignoredNew) -> { renderCharts(); triggerRenderBody(); });
        chartB.selector.valueProperty().addListener((ignoredObs, ignoredOld, ignoredNew) -> { renderCharts(); triggerRenderBody(); });
        chartC.selector.valueProperty().addListener((ignoredObs, ignoredOld, ignoredNew) -> { renderCharts(); triggerRenderBody(); });
    }

    public Node getRoot() { return root; }

    public void showLap(Lap current, Lap ghost, boolean showDelta) {
        this.refLap = current;
        this.ghostLap = ghost;
        this.showDelta = showDelta;

        Platform.runLater(() -> {
            body.getChildren().clear();
            Label loading = new Label("Caricamento dati in corso...");
            loading.setStyle("-fx-font-size: 14px; -fx-text-fill: gray;");
            loading.setPadding(new Insets(20));
            body.getChildren().add(loading);

            renderCharts();
            triggerRenderBody();
        });
    }

    public void setGhostLap(Lap ghost) { this.ghostLap = ghost; renderCharts(); }
    public void clearGhost() { setGhostLap(null); }

    private void triggerRenderBody() {
        updateDebouncer.playFromStart();
    }

    private void renderCharts() {
        if (refLap == null) {
            List<Lap> laps = data.getLaps();
            if (laps != null && !laps.isEmpty()) refLap = laps.getFirst();
        }
        if (refLap == null) {
            chartA.chart.getData().clear();
            chartB.chart.getData().clear();
            chartC.chart.getData().clear();
            deltaChart.getData().clear();
            return;
        }

        Platform.runLater(() -> {
            AxisChoice axis = AxisPicker.pick(refLap);
            charts.renderChart(chartA.chart, chartA.type(), refLap, ghostLap, axis);
            charts.renderChart(chartB.chart, chartB.type(), refLap, ghostLap, axis);
            charts.renderChart(chartC.chart, chartC.type(), refLap, ghostLap, axis);

            deltaChart.getData().clear();
            if (showDelta && ghostLap != null) charts.renderDelta(deltaChart, refLap, ghostLap, axis);
        });
    }

    private void performRenderBodyAsync() {
        final Lap targetLap = refLap;
        if (targetLap == null) {
            body.getChildren().clear();
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            Map<String, Double> lapStats = LapAnalysis.basicStats(targetLap);
            ForceStats.SeatDistribution dist = ForceStats.distribution(targetLap);
            lapStats.put("Seat dist SX [%]",   dist.left  * 100.0);
            lapStats.put("Seat dist POST [%]", dist.rear  * 100.0);
            lapStats.put("Seat dist DX [%]",   dist.right * 100.0);

            Map<String, Double> forceStats = LapForceStatsAggregator.build(targetLap);
            Map<String, Double> merged = new LinkedHashMap<>(lapStats);
            merged.putAll(forceStats);
            return merged;
        }).thenAcceptAsync(stats -> {
            if (refLap != targetLap) return;

            // Cambiato il tipo da TitledPane a Node per la flessibilità della nuova UI
            Node statsPane = titled("📊 Statistiche (giro)", UiWidgets.buildStatsAccordion(stats));

            ComboBox<String> widgetSelector = new ComboBox<>();
            List<String> items = new ArrayList<>();
            if (ui.wTyreTempProperty().get())  items.add("Pneumatici (°C)");
            if (ui.wTyrePressProperty().get()) items.add("Pressioni (psi)");
            if (ui.wSuspensionProperty().get()) items.add("Sospensioni (mm)");
            if (ui.wBrakesProperty().get())    items.add("Freni (°C)");
            if (ui.wDamageProperty().get())    items.add("Danni (%)");
            if (ui.wPedalsProperty().get())    items.add("Pedali (%)");
            if (items.isEmpty()) items.add("Pneumatici (T)");
            widgetSelector.getItems().setAll(items);
            widgetSelector.getSelectionModel().selectFirst();
            widgetSelector.setStyle("-fx-background-radius: 4; -fx-border-radius: 4; -fx-padding: 2;");

            StackPane widgetHolder = new StackPane();
            widgetHolder.setPadding(new Insets(6, 0, 0, 0));
            double WIDGET_HEIGHT = 350.0;
            widgetHolder.setMinHeight(WIDGET_HEIGHT);
            widgetHolder.setPrefHeight(WIDGET_HEIGHT);

            Runnable updateWidget = () -> {
                Node n = selectWidgetContent(widgetSelector.getValue(), targetLap);
                if (n instanceof Region r) {
                    r.setMaxWidth(Double.MAX_VALUE);
                    r.setMaxHeight(Double.MAX_VALUE);
                    r.setPrefHeight(WIDGET_HEIGHT);
                }
                widgetHolder.getChildren().setAll(n);
            };
            updateWidget.run();
            widgetSelector.valueProperty().addListener((ignoredObs, ignoredOld, ignoredNew) -> updateWidget.run());

            // Cambiato il tipo da TitledPane a Node
            Node widgetPane = titled("⚙️ Dettagli (seleziona widget)", new VBox(8, widgetSelector, widgetHolder));

            GridPane row = new GridPane();
            row.setAlignment(Pos.TOP_LEFT);
            row.setHgap(15); row.setVgap(15);
            row.setPadding(new Insets(8));
            ColumnConstraints left = new ColumnConstraints();  left.setPercentWidth(50);
            ColumnConstraints right= new ColumnConstraints();  right.setPercentWidth(50);
            row.getColumnConstraints().setAll(left, right);
            GridPane.setValignment(statsPane, VPos.TOP);
            GridPane.setValignment(widgetPane, VPos.TOP);
            row.add(statsPane, 0, 0);
            row.add(widgetPane, 1, 0);

            // --- ENGINEER UI ---
            EngineerChatPane engineerUI = new EngineerChatPane();
            engineerUI.setContextMode(true);

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

            engineerUI.loadLap(targetLap, data.getLaps());

            Node engineerNode = engineerUI.getRoot();
            VBox.setVgrow(engineerNode, Priority.ALWAYS);

            if (!org.simulator.gemini.GeminiService.isPremium()) {
                Button btnPremium = new Button("Sblocca Premium 🏆 (Analisi Illimitate)");
                btnPremium.setStyle("-fx-background-color: #ffd700; -fx-text-fill: black; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 8 16; -fx-font-size: 14px;");

                btnPremium.setOnAction(e -> {
                    org.simulator.gemini.GeminiService.unlockPremium(); // Sblocca il back-end

                    // --- QUESTA È LA RIGA MAGICA CHE MANCAVA ---
                    engineerUI.unlockUI();
                    // -------------------------------------------

                    btnPremium.setText("Premium Attivo ✅");
                    btnPremium.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 16; -fx-font-size: 14px;");
                    btnPremium.setDisable(true);
                });
                body.getChildren().setAll(row, btnPremium, engineerNode);
            } else {
                body.getChildren().setAll(row, engineerNode);
            }
            body.setPadding(new Insets(4,10,10,10));
            body.setFillWidth(true);

        }, Platform::runLater);
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

    private Node selectWidgetContent(String name, Lap lap){
        if (name == null) return TyreThermalWidget.build(lap);
        return switch (name) {
            case "Pressioni (psi)" -> TyrePressureWidget.build(lap);
            case "Freni (°C)"     -> BrakeThermalWidget.build(lap);
            case "Sospensioni (mm)" -> SuspensionWidget.build(lap);
            case "Danni (%)"     -> DamageWidget.build(lap);
            case "Pedali (%)"    -> PedalWidget.build(lap);
            default          -> TyreThermalWidget.build(lap);
        };
    }
}
