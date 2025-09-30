package org.simulator.ui.analysis_view;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
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

/** Prima pagina: 3 grafici + Delta. Colonna destra: Stats, Widget, Coach (con TTS), Consigli Setup. */
public class AnalysisView {

    private final DataController data;
    private final ChartManager charts;

    private final BorderPane root = new BorderPane();
    private SplitPane split;
    private ScrollPane rightPane;

    private final MiniChartBox chartA = new MiniChartBox("Grafico A", ChartPane.PlotType.SPEED_DIST);
    private final MiniChartBox chartB = new MiniChartBox("Grafico B", ChartPane.PlotType.THR_BRAKE_DIST);
    private final MiniChartBox chartC = new MiniChartBox("Grafico C", ChartPane.PlotType.STEERING_DIST);
    private final LineChart<Number, Number> deltaChart;
    private final UiSettings ui = UiSettings.get();
    private Lap refLap;
    private Lap ghostLap;           // null = nessun ghost
    private boolean showDelta = false;

    // componenti estratti
    private final LapCoachPane coachPane = new LapCoachPane();
    private final LapSetupPaneBuilder setupPane = new LapSetupPaneBuilder();

    public AnalysisView(DataController data) {
        this.data = data;
        this.charts = new ChartManager();

        // griglia grafici sinistra
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(10));
        grid.add(chartA, 0, 0);
        grid.add(chartB, 1, 0);
        grid.add(chartC, 0, 1);

        deltaChart = charts.buildChart("Delta Lap Time", "X", "Δt [s]");
        grid.add(deltaChart, 1, 1);

        // split: grafici + pannello destro
        split = new SplitPane();
        split.setDividerPositions(0.58);
        split.getItems().add(grid);

        rightPane = new ScrollPane();
        rightPane.setFitToWidth(true);
        rightPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rightPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        rightPane.setPrefWidth(560);
        rightPane.setMinWidth(420);
        split.getItems().add(rightPane);

        root.setPadding(new Insets(10));
        root.setCenter(split);
        // Rirender del pannello destro quando cambiano i filtri globali

        ui.wTyreTempProperty().addListener((o,ov,nv) -> renderRightPanel());
        ui.wBrakesProperty().addListener((o,ov,nv) -> renderRightPanel());

// opzionale
        ui.wTyrePressProperty().addListener((o,ov,nv) -> renderRightPanel());
        ui.wDamageProperty().addListener((o,ov,nv)    -> renderRightPanel());
        ui.wPedalsProperty().addListener((o,ov,nv)    -> renderRightPanel());



        // ridisegna alla variazione dei plot type
        chartA.selector.valueProperty().addListener((o, ov, nv) -> { renderCharts(); renderRightPanel(); });
        chartB.selector.valueProperty().addListener((o, ov, nv) -> { renderCharts(); renderRightPanel(); });
        chartC.selector.valueProperty().addListener((o, ov, nv) -> { renderCharts(); renderRightPanel(); });
    }

    public Node getRoot() { return root; }

    /** Imposta giro corrente, ghost (può essere null = nessuno) e visibilità del delta. */
    public void showLap(Lap current, Lap ghost, boolean showDelta) {
        this.refLap = current;
        this.ghostLap = ghost;               // null = "Nessun ghost"
        this.showDelta = showDelta;
        renderCharts();
        renderRightPanel();
    }

    /** Hook per impostare/azzerare il ghost dall’esterno. Passa null per "Nessun ghost". */
    public void setGhostLap(Lap ghost) { this.ghostLap = ghost; renderCharts(); }

    /** Reset esplicito al default (nessun ghost). */
    public void clearGhost() { setGhostLap(null); }

    // ============= Grafici =============
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

        AxisChoice axis = AxisPicker.pick(refLap);

        charts.renderChart(chartA.chart, chartA.type(), refLap, ghostLap, axis);
        charts.renderChart(chartB.chart, chartB.type(), refLap, ghostLap, axis);
        charts.renderChart(chartC.chart, chartC.type(), refLap, ghostLap, axis);

        deltaChart.getData().clear();
        if (showDelta && ghostLap != null) charts.renderDelta(deltaChart, refLap, ghostLap, axis);
    }

    // ============= Colonna destra =============
    private void renderRightPanel() {
        if (refLap == null) { rightPane.setContent(null); return; }

        GridPane topGrid = new GridPane();
        topGrid.setHgap(12); topGrid.setVgap(12); topGrid.setPadding(new Insets(8));
        ColumnConstraints c1 = new ColumnConstraints(); c1.setPercentWidth(44);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setPercentWidth(56);
        topGrid.getColumnConstraints().setAll(c1, c2);

        Map<String, Double> lapStats = LapAnalysis.basicStats(refLap);

        // Distribuzione sedile (lasciata qui, come nel tuo codice)
        ForceStats.SeatDistribution dist = ForceStats.distribution(refLap);
        lapStats.put("Seat dist SX [%]",   dist.left  * 100.0);
        lapStats.put("Seat dist POST [%]", dist.rear  * 100.0);
        lapStats.put("Seat dist DX [%]",   dist.right * 100.0);

        // Forze/FFB/Seat roughness aggregate (estratto)
        Map<String, Double> forceStats = LapForceStatsAggregator.build(refLap);

        // Accordion con statistiche
        Map<String, Double> merged = new LinkedHashMap<>(lapStats);
        merged.putAll(forceStats);
        TitledPane statsPane = titled("Statistiche (giro)", UiWidgets.buildStatsAccordion(merged));
        topGrid.add(statsPane, 0, 0);

        // Widget switcher
// Widget switcher (rispetta i filtri globali completi)
        var ui = org.simulator.ui.settings.UiSettings.get();
        ComboBox<String> widgetSelector = new ComboBox<>();
        List<String> items = new ArrayList<>();
        if (ui.wTyreTempProperty().get())  items.add("Pneumatici (°C)");
        if (ui.wTyrePressProperty().get()) items.add("Pressioni (psi)");
        if(ui.wSuspensionProperty().get()) items.add("Sospensioni (mm)");
        if (ui.wBrakesProperty().get())    items.add("Freni (°C)");
        if (ui.wDamageProperty().get())    items.add("Danni (%)");
        if (ui.wPedalsProperty().get())    items.add("Pedali (%)");

        if (items.isEmpty()) items.add("Pneumatici (T)");
        widgetSelector.getItems().setAll(items);
        widgetSelector.getSelectionModel().selectFirst();

        StackPane widgetHolder = new StackPane();
        widgetHolder.setPadding(new Insets(6));
        widgetHolder.getChildren().setAll(selectWidgetContent(widgetSelector.getValue(), refLap));

        widgetSelector.valueProperty().addListener((o, ov, nv) ->
                widgetHolder.getChildren().setAll(selectWidgetContent(nv, refLap)));

        TitledPane widgetPane = titled("Dettagli (seleziona widget)",
                new VBox(6, widgetSelector, widgetHolder));
        topGrid.add(widgetPane, 1, 0);



        TitledPane detailsPane = titled("Dettagli (seleziona widget)", new VBox(6, widgetSelector, widgetHolder));
        topGrid.add(detailsPane, 1, 0);


        // Coach (giro) + Setup consigli (sottoclassi estratte)
        GridPane bottomGrid = new GridPane();
        bottomGrid.setHgap(12); bottomGrid.setVgap(12); bottomGrid.setPadding(new Insets(8));
        bottomGrid.getColumnConstraints().setAll(c1, c2);

        TitledPane coach = (TitledPane) new LapCoachPane().build(refLap);
        bottomGrid.add(coach, 0, 0, 2, 1);

        TitledPane setup = (TitledPane) new LapSetupPaneBuilder().build(refLap, data.getLaps());
        bottomGrid.add(setup, 0, 1, 2, 1);

        VBox right = new VBox(10, topGrid, bottomGrid);
        right.setPadding(new Insets(6));
        right.setFillWidth(true);

        rightPane.setContent(right);
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
            default          -> TyreThermalWidget.build(lap); // "Pneumatici (T)"
        };
    }

}
