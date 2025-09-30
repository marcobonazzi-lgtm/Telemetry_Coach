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
import org.simulator.ui.all_laps_view.SessionCoachPane;
import org.simulator.ui.settings.UiSettings;
import org.simulator.widget.*;
import org.simulator.ui.*;
import org.simulator.ui.asix_pack.AxisChoice;
import org.simulator.ui.asix_pack.AxisPicker;
import org.simulator.ui.ChartManager;
import org.simulator.ui.ChartPane;


import java.util.*;

/** Pagina "Tutti i giri": carosello orizzontale dei mini-grafici + riepilogo sessione (coach + setup). */
public class AllLapsView {

    private final DataController data;
    private final ChartManager charts;

    // root completo: top + contenuto
    private final VBox root = new VBox();

    private final GridPane grid = new GridPane();
    private final Label xAxisLabel = new Label("Asse X: (n/d)");

    private final ComboBox<ChartPane.PlotType> plotSelector = new ComboBox<>();
    private ChartPane.PlotType currentType = ChartPane.PlotType.SPEED_DIST;

    // --- carosello orizzontale ---
    private final HBox lapsRow = new HBox(12);
    private final ScrollPane lapsScroll = new ScrollPane(lapsRow);
    private final Button btnLeft  = new Button("◄");
    private final Button btnRight = new Button("►");
    private final StackPane lapsViewport = new StackPane();
    private static final double CELL_MIN_W = 420;
    private static final double GAP = 12;
    private final UiSettings ui = UiSettings.get();


    // --- componenti estratti ---
    private final SessionCoachPane coachPane = new SessionCoachPane();
    private final SessionSetupPaneBuilder setupPane = new SessionSetupPaneBuilder();

    public AllLapsView(DataController data) {
        this.data = data;
        this.charts = new ChartManager();

        grid.setHgap(12); grid.setVgap(12); grid.setPadding(new Insets(12));

        // Carosello
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
        btnLeft.setOnAction(e -> pageBy(-1));
        btnRight.setOnAction(e -> pageBy(+1));
        lapsScroll.viewportBoundsProperty().addListener((o, ov, nv) -> sizeCardsToViewport());

        // Top controls
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
        plotSelector.valueProperty().addListener((o, ov, nv) -> { currentType = nv; render(); });

        // monta la top e il root
        Node top = buildTop();
        root.getChildren().addAll(top, grid);
        // Rirender quando cambiano i filtri globali (Altri pannelli)

        ui.wTyreTempProperty().addListener((o,ov,nv) -> render());
        ui.wBrakesProperty().addListener((o,ov,nv) -> render());

// opzionale: se vuoi reagire anche a Pressioni / Danni / Pedali
        ui.wTyrePressProperty().addListener((o,ov,nv) -> render());
        ui.wDamageProperty().addListener((o,ov,nv)    -> render());
        ui.wPedalsProperty().addListener((o,ov,nv)    -> render());


        VBox.setVgrow(grid, Priority.ALWAYS);
    }

    /** Root completo (top + contenuto). */
    public Node getRoot() { return root; }

    /** Mantengo grid() per retrocompatibilità. */
    public GridPane grid() { return grid; }

    public Node buildTop() {
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Label tipoLbl = new Label("Tipo grafico:");
        HBox top = new HBox(12, xAxisLabel, spacer, tipoLbl, plotSelector);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(6, 12, 6, 12));
        return new VBox(top);
    }

    // ---------- render ----------
    public void render() {
        grid.getChildren().clear();
        List<Lap> laps = data.getLaps();
        if (laps == null || laps.isEmpty()) { xAxisLabel.setText("Asse X: (n/d)"); return; }

        AxisChoice axis = AxisPicker.pick(laps.get(0));
        xAxisLabel.setText("Asse X: " + axis.label + "  •  Grafici: " + currentType);

        buildCarousel(laps, axis);
        grid.add(lapsViewport, 0, 0);

        VBox sessionBox = buildSessionSection(laps);
        grid.add(sessionBox, 0, 1);
        GridPane.setHgrow(sessionBox, Priority.ALWAYS);
    }


    // ---------- carosello ----------
    private void buildCarousel(List<Lap> laps, AxisChoice axis) {
        lapsRow.getChildren().clear();

        for (Lap lap : laps) {
            String yLabel = yLabelFor(currentType);
            String status = lap.validityStatus(laps); // ✅ nuovo metodo

            LineChart<Number, Number> c = charts.buildChart(
                    "Lap " + lap.index + " (" + status + ")",
                    axis.label,
                    yLabel
            );

            charts.renderChart(c, currentType, lap, null, axis);

            VBox card = new VBox(5, c);
            card.setAlignment(Pos.CENTER);
            card.setPadding(new Insets(0));
            card.setMinWidth(Region.USE_PREF_SIZE);
            card.setMaxWidth(Double.MAX_VALUE);
            lapsRow.getChildren().add(card);
        }

        sizeCardsToViewport();

        Platform.runLater(() -> {
            double contentW = lapsRow.prefWidth(-1);
            double viewportW = lapsScroll.getViewportBounds() == null ? 0 : lapsScroll.getViewportBounds().getWidth();
            boolean needArrows = contentW > viewportW + 1;
            btnLeft.setVisible(needArrows);
            btnRight.setVisible(needArrows);
        });
    }


    // adatta larghezza card al viewport
    private void sizeCardsToViewport() {
        double viewportW = lapsScroll.getViewportBounds() == null ? 0 : lapsScroll.getViewportBounds().getWidth();
        if (viewportW <= 0 || lapsRow.getChildren().isEmpty()) return;

        int targetCols = Math.max(1, Math.min(5, (int)Math.floor((viewportW + GAP) / (CELL_MIN_W + GAP))));
        double cardW = (viewportW - (targetCols - 1) * GAP) / targetCols;

        for (Node n : lapsRow.getChildren()) {
            if (n instanceof VBox v) {
                v.setPrefWidth(cardW);
                if (!v.getChildren().isEmpty() && v.getChildren().get(0) instanceof Region r) {
                    r.setPrefWidth(cardW);
                }
            }
        }
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

    // ---------- sezione sessione ----------
    private VBox buildSessionSection(List<Lap> laps) {
        VBox sessionBox = new VBox(12);
        sessionBox.setAlignment(Pos.TOP_LEFT);
        sessionBox.setPadding(new Insets(10));

        Lap best = bestLapOf(laps);
        Node bestNode = null;
        if (best != null) {
            var bestStats = LapAnalysis.basicStats(best);
            String bestTitle = "Statistiche (miglior giro: #" + best.index
                    + (Double.isNaN(best.lapTime) ? "" : ", " + TimeUtil.formatLapTime(best.lapTime)) + ")";

            bestNode = titled(bestTitle, UiWidgets.buildStatsAccordion(bestStats));
        }

        // media sessione (base) + forze (aggregatore estratto)
        var avgBase = SessionAnalysis.averageStats(laps);
        var forceStats = SessionForceStatsAggregator.build(laps);
        var merged = new LinkedHashMap<String, Double>();
        merged.putAll(avgBase);
        merged.putAll(forceStats);

        Node avgAccordion = UiWidgets.buildStatsAccordion(merged);
        VBox mediaContent = new VBox(8, avgAccordion);
        TitledPane mediaPane = titled("Statistiche (media sessione)", mediaContent);

        HBox statsRow = new HBox(12);
        statsRow.setFillHeight(true);
        if (bestNode != null) statsRow.getChildren().add(bestNode);
        statsRow.getChildren().add(mediaPane);
        for (Node n : statsRow.getChildren()) {
            if (n instanceof Region r) { r.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(r, Priority.ALWAYS); }
        }
        sessionBox.getChildren().add(statsRow);

        // Coach + Setup (classi estratte)
        sessionBox.getChildren().add((TitledPane) coachPane.build(laps));
        sessionBox.getChildren().add((TitledPane) setupPane.build(laps));

        // Widget termici/danni/attuatori
        sessionBox.getChildren().add(buildWidgetsGrid(laps));


        return sessionBox;
    }

    private static Lap bestLapOf(List<Lap> laps) {
        if (laps == null || laps.isEmpty()) return null;

        // ✅ considera solo i giri validi
        Optional<Lap> bestByTime = laps.stream()
                .filter(l -> l.isComplete(laps))
                .min(Comparator.comparingDouble(l -> l.lapTime));

        if (bestByTime.isPresent()) return bestByTime.get();

        // fallback: nessun giro valido → ritorna il più lungo
        return laps.stream()
                .max(Comparator.comparingInt(l -> l.samples == null ? 0 : l.samples.size()))
                .orElse(laps.get(0));
    }


    private static TitledPane titled(String title, Node content) {
        TitledPane tp = new TitledPane(title, content);
        tp.setCollapsible(false);
        return tp;
    }

    private static String yLabelFor(ChartPane.PlotType t) {
        return switch (t) {
            case SPEED_DIST -> "Speed [km/h]";
            case THR_BRAKE_DIST -> "%";
            case STEERING_DIST -> "Angle [deg]";
            case RPM_TIME -> "RPM";
            case FFB_FORCE -> "FFB";
            case PEDAL_FORCE, SEAT_FORCE -> "Force (N)";
            default -> "Y";
        };
    }
    private GridPane buildWidgetsGrid(List<Lap> laps){
        var ui = org.simulator.ui.settings.UiSettings.get();

        GridPane g = new GridPane();
        g.setHgap(12); g.setVgap(12);
        ColumnConstraints c = new ColumnConstraints(); c.setPercentWidth(33.333);
        g.getColumnConstraints().setAll(c, c, c);

        List<Node> widgets = new ArrayList<>();
        if (ui.wTyreTempProperty().get())  widgets.add(TyreThermalWidget.buildFromLaps(laps));
        if (ui.wTyrePressProperty().get()) widgets.add(TyrePressureWidget.buildFromLaps(laps));
        if (ui.wSuspensionProperty().get()) widgets.add(SuspensionWidget.buildFromLaps(laps));
        if (ui.wBrakesProperty().get())    widgets.add(BrakeThermalWidget.buildFromLaps(laps));
        if (ui.wDamageProperty().get())    widgets.add(DamageWidget.buildFromLaps(laps));
        if (ui.wPedalsProperty().get())    widgets.add(PedalWidget.buildFromLaps(laps));


        for (int i=0;i<widgets.size();i++){
            Node n = widgets.get(i);
            if (n instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow(n, Priority.ALWAYS);
            g.add(n, i % 3, i / 3);
        }
        return g;
    }



}
