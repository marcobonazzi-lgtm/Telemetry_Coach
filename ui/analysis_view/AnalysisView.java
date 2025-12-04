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
    private final VBox content = new VBox(12);

    private final GridPane chartsGrid = new GridPane();
    private final VBox body = new VBox(12);

    private final MiniChartBox chartA = new MiniChartBox("Grafico A", ChartPane.PlotType.SPEED_DIST);
    private final MiniChartBox chartB = new MiniChartBox("Grafico B", ChartPane.PlotType.THR_BRAKE_DIST);
    private final MiniChartBox chartC = new MiniChartBox("Grafico C", ChartPane.PlotType.STEERING_DIST);
    private final LineChart<Number, Number> deltaChart;
    private final UiSettings ui = UiSettings.get();

    // Stato volatile per gestire la concorrenza
    private volatile Lap refLap;
    private volatile Lap ghostLap;
    private boolean showDelta = false;

    private final PauseTransition updateDebouncer;

    public AnalysisView(DataController data) {
        this.data = data;
        this.charts = new ChartManager();

        chartsGrid.setHgap(10); chartsGrid.setVgap(10); chartsGrid.setPadding(new Insets(10));
        chartsGrid.add(chartA, 0, 0);
        chartsGrid.add(chartB, 1, 0);
        chartsGrid.add(chartC, 0, 1);

        deltaChart = charts.buildChart("Delta Lap Time", "X", "Δt [s]");
        deltaChart.setAnimated(false);
        deltaChart.setCreateSymbols(false);
        // Cache anche per il delta chart
        deltaChart.setCache(true);
        deltaChart.setCacheHint(CacheHint.SPEED);

        chartsGrid.add(deltaChart, 1, 1);

        ColumnConstraints col = new ColumnConstraints(); col.setPercentWidth(50);
        chartsGrid.getColumnConstraints().setAll(col, col);

        content.getChildren().setAll(chartsGrid, body);
        content.setFillWidth(true);

        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setContent(content);
        scroll.setPannable(true);

        // Ottimizzazione scrolling
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
        updateDebouncer.setOnFinished(e -> performRenderBodyAsync());

        ui.wTyreTempProperty().addListener((o,ov,nv) -> triggerRenderBody());
        ui.wBrakesProperty().addListener((o,ov,nv) -> triggerRenderBody());
        ui.wTyrePressProperty().addListener((o,ov,nv) -> triggerRenderBody());
        ui.wDamageProperty().addListener((o,ov,nv)    -> triggerRenderBody());
        ui.wPedalsProperty().addListener((o,ov,nv)    -> triggerRenderBody());

        chartA.selector.valueProperty().addListener((o, ov, nv) -> { renderCharts(); triggerRenderBody(); });
        chartB.selector.valueProperty().addListener((o, ov, nv) -> { renderCharts(); triggerRenderBody(); });
        chartC.selector.valueProperty().addListener((o, ov, nv) -> { renderCharts(); triggerRenderBody(); });
    }

    public Node getRoot() { return root; }

    public void showLap(Lap current, Lap ghost, boolean showDelta) {
        this.refLap = current;
        this.ghostLap = ghost;
        this.showDelta = showDelta;

        // Puliamo subito per dare feedback visivo
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
            if (laps != null && !laps.isEmpty()) refLap = laps.get(0);
        }
        if (refLap == null) {
            chartA.chart.getData().clear();
            chartB.chart.getData().clear();
            chartC.chart.getData().clear();
            deltaChart.getData().clear();
            return;
        }

        // I grafici devono essere aggiornati nel thread UI, ma usiamo runLater a bassa priorità
        Platform.runLater(() -> {
            AxisChoice axis = AxisPicker.pick(refLap);
            charts.renderChart(chartA.chart, chartA.type(), refLap, ghostLap, axis);
            charts.renderChart(chartB.chart, chartB.type(), refLap, ghostLap, axis);
            charts.renderChart(chartC.chart, chartC.type(), refLap, ghostLap, axis);

            deltaChart.getData().clear();
            if (showDelta && ghostLap != null) charts.renderDelta(deltaChart, refLap, ghostLap, axis);
        });
    }

    //Calcoli in background:
    private void performRenderBodyAsync() {
        final Lap targetLap = refLap;
        if (targetLap == null) {
            body.getChildren().clear();
            return;
        }

        // 1. Avvia il calcolo pesante su un thread non-UI
        CompletableFuture.supplyAsync(() -> {
            // --- INIZIO BACKGROUND THREAD ---
            // Calcolo Stats
            Map<String, Double> lapStats = LapAnalysis.basicStats(targetLap);
            ForceStats.SeatDistribution dist = ForceStats.distribution(targetLap);
            lapStats.put("Seat dist SX [%]",   dist.left  * 100.0);
            lapStats.put("Seat dist POST [%]", dist.rear  * 100.0);
            lapStats.put("Seat dist DX [%]",   dist.right * 100.0);

            Map<String, Double> forceStats = LapForceStatsAggregator.build(targetLap);
            Map<String, Double> merged = new LinkedHashMap<>(lapStats);
            merged.putAll(forceStats);

            return merged;
            // --- FINE BACKGROUND THREAD ---
        }).thenAcceptAsync(stats -> {
            // --- RITORNO AL UI THREAD (Safe) ---
            if (refLap != targetLap) return; // L'utente ha cambiato giro nel frattempo?

            TitledPane statsPane = titled("Statistiche (giro)", UiWidgets.buildStatsAccordion(stats));

            // Widget Selector
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

            StackPane widgetHolder = new StackPane();
            widgetHolder.setPadding(new Insets(6));
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
            widgetSelector.valueProperty().addListener((o, ov, nv) -> updateWidget.run());

            TitledPane widgetPane = titled("Dettagli (seleziona widget)", new VBox(6, widgetSelector, widgetHolder));

            GridPane row = new GridPane();
            row.setAlignment(Pos.TOP_LEFT);
            row.setHgap(12); row.setVgap(12);
            row.setPadding(new Insets(8));
            ColumnConstraints left = new ColumnConstraints();  left.setPercentWidth(50);
            ColumnConstraints right= new ColumnConstraints();  right.setPercentWidth(50);
            row.getColumnConstraints().setAll(left, right);
            GridPane.setValignment(statsPane, VPos.TOP);
            GridPane.setValignment(widgetPane, VPos.TOP);
            row.add(statsPane, 0, 0);
            row.add(widgetPane, 1, 0);

            // --- INTEGRAZIONE GEMINI ENGINEER UI ---
            // Sostituisce coachPane e setupPane
            EngineerChatPane engineerUI = new EngineerChatPane();

            // >>> MODIFICA QUI: Attiva modalità SINGOLO GIRO <<<
            engineerUI.setContextMode(true);

            // Carica il giro corrente e la lista di giri (per il contesto sessione/vehicle traits)
            engineerUI.loadLap(targetLap, data.getLaps());

            Node engineerNode = engineerUI.getRoot();
            VBox.setVgrow(engineerNode, Priority.ALWAYS); // Fa espandere la chat

            body.getChildren().setAll(row, engineerNode);
            body.setPadding(new Insets(4,10,10,10));
            body.setFillWidth(true);

        }, Platform::runLater);
    }

    private static TitledPane titled(String title, Node content) {
        TitledPane tp = new TitledPane(title, content);
        tp.setCollapsible(false);
        return tp;
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