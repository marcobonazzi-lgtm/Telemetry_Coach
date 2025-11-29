package org.simulator.ui.time_line_view;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import org.simulator.setup.setup_advisor.VehicleTraits;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.util.Duration;
import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;
import org.simulator.importCSVFW.CsvVenueExtractor;
import org.simulator.ui.ChartInteractions;
import org.simulator.ui.ChartManager;
import org.simulator.ui.ChartPane;
import org.simulator.ui.SeriesBundle;
import org.simulator.ui.asix_pack.AxisChoice;
import org.simulator.ui.asix_pack.AxisPicker;
import org.simulator.ui.settings.UiSettings;
import org.simulator.ui.time_line_view.widget_TL.*;

// Import Circuit
import org.simulator.ui.time_line_view.Track.Circuit.CircuitController;
import org.simulator.ui.time_line_view.Track.Circuit.CircuitView;
import org.simulator.ui.time_line_view.Track.AC.AcSectionsLoader;
import org.simulator.ui.time_line_view.Track.TrackLayout;

import java.util.*;
import java.util.Locale;
import java.util.Optional;

public final class TimeLineView {

    // -------------------- Campi Principali --------------------
    private final DataControllerLike data;
    private final org.simulator.ui.DataController dataController;

    private final ChartManager charts = new ChartManager();
    private final BorderPane root = new BorderPane();
    private final ComboBox<ChartPane.PlotType> plotSelector = new ComboBox<>();
    private final LineChart<Number, Number> chart;
    private final StackPane chartStack = new StackPane();
    private final Pane overlay = new Pane();
    private final Line vline = new Line();
    private final UiSettings ui = UiSettings.get();
    // ... altri campi esistenti ...

    // --- OTTIMIZZAZIONE: Cache Geometria ---
    private double cachedPlotX = 0;
    private double cachedPlotY = 0;
    private double cachedPlotW = 1; // 1 per evitare div/0
    private double cachedPlotH = 1;
    // ---------------------------------------

    // Slider
    private final Slider xSlider = new Slider();
    private final Pane sliderPane = new Pane();
    private final DoubleProperty cursorX = new SimpleDoubleProperty(Double.NaN);

    // Playback
    private final Button playBtn = new Button("▶ Play");
    private final Button pauseBtn = new Button("⏸ Pause");
    private final Button speedBtn = new Button("×1");

    // Waypoint
    private final Group wpGroup = new Group();
    private final Button addWpBtn     = new Button("🏁");
    private final Button delLastWpBtn = new Button("⌫");
    private final Button clearWpBtn   = new Button("🗑");
    private final ComboBox<Double> wpCombo = new ComboBox<>();

    // Step frame
    private final Button stepBackBtn = new Button("◀");
    private final Button stepFwdBtn  = new Button("▶");
    private final CheckBox showDrsCheck = new CheckBox("DRS Zones");
    private final CheckBox showIdealLineCheck = new CheckBox("Ideal Line");
    private final double[] speedSteps = new double[]{0.5, 1, 2, 3, 4, 5};
    private int  speedIdx   = 1;
    private boolean isPlaying = false;
    private final AnimationTimer player = new AnimationTimer() {
        private long lastNs = -1;
        @Override public void handle(long now) {
            if (lastNs < 0) { lastNs = now; return; }
            double dt = (now - lastNs) / 1e9;
            lastNs = now;
            advanceByTime(dt * playbackSpeed());
        }
        @Override public void start() { lastNs = -1; super.start(); }
    };

    // Readout
    private final ReadoutBox readout = new ReadoutBox();

    // Widget
    private final WheelTL      wheel      = new WheelTL("/assets/wheel.png");
    private final SpeedGaugeTL speedGauge = new SpeedGaugeTL();
    private final RpmGaugeTL   rpmGauge   = new RpmGaugeTL();
    private final PedalBarsTL  pedals     = new PedalBarsTL();
    private final FFBBarTL     ffbBar     = new FFBBarTL();
    private final TyreWearTL   tyreWear   = new TyreWearTL();
    private final GForceTL     gForceTL   = new GForceTL();
    private final SeatActuatorsTL seatTL  = new SeatActuatorsTL();
    private final CoachTL      coach      = new CoachTL();
    private final SuspensionsTL suspTL    = new SuspensionsTL();

    private WidgetValueOverlay wheelCard;
    private WidgetValueOverlay speedCard;
    private WidgetValueOverlay rpmCard;

    private Lap currentLap;
    private AxisChoice axis;
    private SeriesBundle sb;
    private Signals signals;

    private final List<Double> xPF = new ArrayList<>();
    private final List<Double> thrForce = new ArrayList<>();
    private final List<Double> brkForce = new ArrayList<>();
    private final List<Double> cluForce = new ArrayList<>();

    private static final double WIDGETS_H = 260;

    private final Map<Lap, Double>       cursorPosByLap  = new HashMap<>();
    private final Map<Lap, List<Double>> waypointsByLap  = new HashMap<>();

    // === Circuito Realtime ===
    private final ToggleButton circuitToggle = new ToggleButton("Circuito in tempo reale");
    private final CircuitView circuitView = new CircuitView();
    private final CircuitController circuitCtl = new CircuitController(circuitView);


    // -------------------- Costruttore --------------------
    public TimeLineView(org.simulator.ui.DataController dataController) {
        this.data = dataController::getLaps;
        this.dataController = dataController;

        circuitCtl.setCurveInfoSink((title, dataMap) -> readout.showCurveDetails(title, dataMap));

        // TOP Toolbar
        plotSelector.getItems().setAll(
                ChartPane.PlotType.SPEED_DIST,
                ChartPane.PlotType.THR_BRAKE_DIST,
                ChartPane.PlotType.STEERING_DIST,
                ChartPane.PlotType.RPM_TIME,
                ChartPane.PlotType.FFB_FORCE,
                ChartPane.PlotType.PEDAL_FORCE,
                ChartPane.PlotType.SEAT_FORCE
        );
        plotSelector.getSelectionModel().select(ChartPane.PlotType.SPEED_DIST);
        plotSelector.valueProperty().addListener((o, ov, nv) -> { if (!circuitView.isVisible()) renderChart(); });

        var laps = Optional.ofNullable(this.data.getLaps()).orElseGet(List::of);
        Label comboInfo = new Label("(il giro si sceglie dalla barra in alto)");
        comboInfo.setStyle("-fx-opacity:.7; -fx-font-size:11px;");

        circuitToggle.setVisible(false);
        circuitToggle.setManaged(false);

        circuitToggle.setOnAction(e -> {
            boolean show = circuitToggle.isSelected();
            if (show) {
                toggleCircuit(true);
                updateCircuitDataWithLayoutCheck();
            } else {
                toggleCircuit(false);
            }
        });

        Button changeLayoutBtn = new Button("Cambia Variante");
        changeLayoutBtn.setStyle("-fx-font-size: 11px;");
        changeLayoutBtn.visibleProperty().bind(circuitToggle.selectedProperty());
        changeLayoutBtn.managedProperty().bind(circuitToggle.selectedProperty());
        changeLayoutBtn.setOnAction(e -> promptForLayoutSelection());

        showDrsCheck.setStyle("-fx-font-size: 11px; -fx-text-fill: #aaaaaa;");
        showDrsCheck.setVisible(false);
        showDrsCheck.setSelected(true);

        showDrsCheck.selectedProperty().addListener((o, ov, nv) -> {
            if (circuitView.isVisible()) {
                circuitView.setDrsVisible(nv);
            }
        });
        showIdealLineCheck.setStyle("-fx-font-size: 11px; -fx-text-fill: #aaaaaa;");
        showIdealLineCheck.setVisible(false);
        showIdealLineCheck.setSelected(true);

        showIdealLineCheck.selectedProperty().addListener((o, ov, nv) -> {
            if (circuitView.isVisible()) {
                circuitView.setIdealLineVisible(nv);
            }
        });
        HBox top = new HBox(12,
                new Label("Grafico:"), plotSelector,
                new Separator(),
                circuitToggle, changeLayoutBtn,
                showDrsCheck,
                showIdealLineCheck,
                comboInfo
        );
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(8));

        chart = charts.buildChart("Timeline", "X", "Y");

        // --- OTTIMIZZAZIONE GRAFICA AGGIUNTA ---
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        // ---------------------------------------

        overlay.setMouseTransparent(true);
        vline.setStrokeWidth(1.3);
        vline.getStyleClass().add("timeline-vline");
        overlay.getChildren().addAll(wpGroup, vline);
        chartStack.getChildren().addAll(chart, overlay);
        StackPane.setAlignment(overlay, Pos.CENTER);
        ChartInteractions.applyDataBoundsFromSeries(chart);

        circuitView.setVisible(false);
        circuitView.setManaged(false);
        chartStack.getChildren().add(circuitView);
        StackPane.setAlignment(circuitView, Pos.CENTER);

        // SLIDER
        xSlider.setBlockIncrement(0.02);
        xSlider.setPadding(Insets.EMPTY);
        xSlider.valueProperty().bindBidirectional(cursorX);
        cursorX.addListener((o, ov, nv) -> {
            double x = nv.doubleValue();
            updateCursorAndWidgets(x);
            if (currentLap != null && Double.isFinite(x)) cursorPosByLap.put(currentLap, x);
            if (circuitView.isVisible()) circuitCtl.updateCarAtX(x);
        });

        sliderPane.setMinHeight(28);
        sliderPane.setPrefHeight(28);
        sliderPane.setMaxHeight(28);

        stepBackBtn.setFocusTraversable(false);
        stepFwdBtn.setFocusTraversable(false);
        stepBackBtn.setMinWidth(28);
        stepFwdBtn.setMinWidth(28);
        stepBackBtn.setOnAction(e -> stepBySamples(-1));
        stepFwdBtn.setOnAction(e -> stepBySamples(+1));
        stepBackBtn.setTooltip(new Tooltip("Frame indietro (Shift+←)"));
        stepFwdBtn.setTooltip(new Tooltip("Frame avanti (Shift+→)"));
        sliderPane.getChildren().setAll(xSlider, stepBackBtn, stepFwdBtn);

        // CONTROLS
        playBtn.setOnAction(e -> startPlayback());
        pauseBtn.setOnAction(e -> pausePlayback());
        speedBtn.setOnAction(e -> cycleSpeed());

        addWpBtn.setTooltip(new Tooltip("Aggiungi waypoint (W)"));
        addWpBtn.setOnAction(e -> addWaypointAtCurrent());
        delLastWpBtn.setTooltip(new Tooltip("Rimuovi waypoint (Canc)"));
        delLastWpBtn.setOnAction(e -> removeSelectedOrLastWaypoint());
        clearWpBtn.setTooltip(new Tooltip("Rimuovi tutti"));
        clearWpBtn.setOnAction(e -> clearWaypoints());

        wpCombo.setPromptText("Waypoints");
        wpCombo.setPrefWidth(180);
        wpCombo.setCellFactory(cb -> WaypointListCellFactory.forAxis(() -> axis));
        wpCombo.setButtonCell(WaypointListCellFactory.forAxis(() -> axis));
        wpCombo.setOnAction(e -> {
            Double x = wpCombo.getValue();
            if (x != null && Double.isFinite(x)) cursorX.set(x);
        });
        Label posLabel = new Label("Posizione:");
        Label keyHint  = new Label("(Shift+←/→)");
        keyHint.setStyle("-fx-opacity:.7; -fx-font-size:11px;");

        Button toggleWidgetsBtn = new Button("▼ Widget");
        toggleWidgetsBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 8;");
        toggleWidgetsBtn.setTooltip(new Tooltip("Nascondi widget"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox controls = new HBox(8,
                playBtn, pauseBtn, speedBtn,
                posLabel, keyHint,
                addWpBtn, delLastWpBtn, clearWpBtn,
                new Label("Waypoints:"), wpCombo,
                spacer,
                toggleWidgetsBtn
        );
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(4, 10, 6, 10));

        // WIDGETS ROW
        wheelCard = new WidgetValueOverlay(wheel.getRoot(), Pos.BOTTOM_CENTER, new Insets(0, 0, 8, 8));
        speedCard = new WidgetValueOverlay(speedGauge.getRoot(), Pos.BOTTOM_RIGHT, new Insets(0, 8, 8, 0));
        rpmCard   = new WidgetValueOverlay(rpmGauge.getRoot(),   Pos.BOTTOM_RIGHT, new Insets(0, 8, 8, 0));

        HBox widgetsRow = new HBox(16,
                coach.getRoot(), wheelCard.getRoot(), speedCard.getRoot(), rpmCard.getRoot(),gForceTL.getRoot(),
                pedals.getRoot(), tyreWear.getRoot(),suspTL.getRoot(),
                ffbBar.getRoot(), seatTL.getRoot()

        );
        widgetsRow.setAlignment(Pos.CENTER_LEFT);
        lockCardSize(wheel.getRoot()); lockCardSize(speedGauge.getRoot()); lockCardSize(rpmGauge.getRoot());
        lockCardSize(pedals.getRoot()); lockCardSize(ffbBar.getRoot()); lockCardSize(tyreWear.getRoot());
        lockCardSize(seatTL.getRoot()); lockCardSize(coach.getRoot());
        lockCardSize(wheelCard.getRoot()); lockCardSize(speedCard.getRoot()); lockCardSize(rpmCard.getRoot());
        lockCardSize(gForceTL.getRoot());

        widgetsRow.setMinWidth(Region.USE_PREF_SIZE);
        widgetsRow.setPrefWidth(Region.USE_COMPUTED_SIZE);
        widgetsRow.setPrefHeight(WIDGETS_H - 14);
        widgetsRow.setMinHeight(Region.USE_PREF_SIZE);
        widgetsRow.setMaxHeight(Region.USE_PREF_SIZE);

        ScrollPane widgetsScroll = new ScrollPane(widgetsRow);
        widgetsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        widgetsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        widgetsScroll.setPannable(true);
        widgetsScroll.setFitToWidth(false);
        widgetsScroll.setFitToHeight(false);
        widgetsScroll.setPrefViewportHeight(WIDGETS_H);
        widgetsScroll.setPrefHeight(WIDGETS_H + 12);
        widgetsScroll.setPadding(new Insets(6, 10, 10, 10));

        final double targetHeight = WIDGETS_H + 12;
        toggleWidgetsBtn.setOnAction(e -> {
            boolean isClosing = widgetsScroll.isVisible();
            Timeline animation = new Timeline();
            if (isClosing) {
                toggleWidgetsBtn.setText("▲ Widget");
                animation.getKeyFrames().add(new KeyFrame(Duration.millis(350),
                        new KeyValue(widgetsScroll.prefHeightProperty(), 0),
                        new KeyValue(widgetsScroll.minHeightProperty(), 0),
                        new KeyValue(widgetsScroll.maxHeightProperty(), 0),
                        new KeyValue(widgetsScroll.opacityProperty(), 0.0)
                ));
                animation.setOnFinished(ev -> {
                    widgetsScroll.setVisible(false); widgetsScroll.setManaged(false);
                    if(circuitView.isVisible()) Platform.runLater(circuitView::fitToTrack);
                });
            } else {
                widgetsScroll.setVisible(true); widgetsScroll.setManaged(true);
                toggleWidgetsBtn.setText("▼ Widget");
                animation.getKeyFrames().add(new KeyFrame(Duration.millis(350),
                        new KeyValue(widgetsScroll.prefHeightProperty(), targetHeight),
                        new KeyValue(widgetsScroll.minHeightProperty(), targetHeight),
                        new KeyValue(widgetsScroll.maxHeightProperty(), targetHeight),
                        new KeyValue(widgetsScroll.opacityProperty(), 1.0)
                ));
                if(circuitView.isVisible()) Platform.runLater(circuitView::fitToTrack);
            }
            animation.play();
        });

        root.setTop(top);
        BorderPane center = new BorderPane(chartStack);
        center.setRight(readout.getRoot());

        VBox bottom = new VBox(sliderPane, controls, widgetsScroll);
        root.setCenter(center);
        root.setBottom(bottom);

        Runnable updateGeometry = () -> {
            cachePlotGeometry(); // Calcola la geometria pesante una volta sola
            updateCursorVisual();
            updateSliderGeometry();
            updateWaypointVisual();
        };

        chart.widthProperty().addListener((a, b, c) -> updateGeometry.run());
        chart.heightProperty().addListener((a, b, c) -> updateGeometry.run());

        NumberAxis xa = (NumberAxis) chart.getXAxis();
        NumberAxis ya = (NumberAxis) chart.getYAxis();
        xa.lowerBoundProperty().addListener((a, b, c) -> updateGeometry.run());
        xa.upperBoundProperty().addListener((a, b, c) -> updateGeometry.run());
        ya.lowerBoundProperty().addListener((a, b, c) -> updateGeometry.run());
        ya.upperBoundProperty().addListener((a, b, c) -> updateGeometry.run());

        root.widthProperty().addListener((a, b, c) -> Platform.runLater(updateGeometry));
        root.heightProperty().addListener((a, b, c) -> Platform.runLater(updateGeometry));
        wheel.setImageFrom(ui.wheelImagePathProperty().get());
        ui.wheelImagePathProperty().addListener((o, ov, nv) -> wheel.setImageFrom(nv));
        attachVisibilityBindings();
        Platform.runLater(() -> WidgetNumbersBinder.bindToPedalsFont(pedals.getRoot(), wheelCard, speedCard, rpmCard));

        root.sceneProperty().addListener((o, oldSc, newSc) -> { if (newSc != null) installShortcuts(newSc); });
        if (root.getScene() != null) installShortcuts(root.getScene());

        if (!laps.isEmpty()) showLap(laps.get(0));
        debugWheelResource();
        updatePlayButtons();
        updateSpeedButton();
        Platform.runLater(this::updateSliderGeometry);
    }

    public Node getRoot() { return root; }
    public void showLap(Lap lap) { this.currentLap = lap; renderChart(); }

    // -------------------- LAYOUT & UTILS --------------------
    private void updateSliderGeometry() {
        double w = sliderPane.getWidth();
        if (w <= 0) return;
        double h = Math.max(20, sliderPane.getHeight() - 6);
        double y = (sliderPane.getHeight() - h) / 2.0;
        double btnW = 28, margin = 6, gap = 2;
        stepBackBtn.resizeRelocate(margin, y, btnW, h);
        stepFwdBtn.resizeRelocate(w - margin - btnW, y, btnW, h);
        double sliderX = margin + btnW + gap;
        double sliderW = Math.max(0, (w - margin - btnW - gap) - sliderX);
        xSlider.resizeRelocate(sliderX, y, sliderW, h);
    }
    private void updateCursorVisual() {
        if (circuitView.isVisible()) return;

        // OTTIMIZZAZIONE: Uso valori cachati invece di localToScene
        double xVal = cursorX.get();
        NumberAxis xa = (NumberAxis) chart.getXAxis();
        double x0 = xa.getLowerBound(), x1 = xa.getUpperBound();

        if (x1 <= x0 || cachedPlotW <= 0) return;

        double frac = (xVal - x0) / (x1 - x0);
        // Clamp tra 0 e 1 per non far uscire il cursore dal grafico
        frac = Math.max(0, Math.min(1, frac));

        double xPixel = cachedPlotX + frac * cachedPlotW;

        vline.setStartX(xPixel); vline.setEndX(xPixel);
        vline.setStartY(cachedPlotY); vline.setEndY(cachedPlotY + cachedPlotH);
    }

    private void updateWaypointVisual(){
        NumberAxis xa = (NumberAxis) chart.getXAxis();
        double x0 = xa.getLowerBound(), x1 = xa.getUpperBound();
        if (x1 <= x0 || cachedPlotW <= 0) return;

        for (Node n : wpGroup.getChildren()){
            Double xVal = (Double) n.getUserData();
            if (xVal == null) continue;

            // OTTIMIZZAZIONE: Uso valori cachati
            double frac = (xVal - x0) / (x1 - x0);
            double xPixel = cachedPlotX + Math.max(0, Math.min(1, frac)) * cachedPlotW;

            Line l = (Line) n;
            l.setStartX(xPixel); l.setEndX(xPixel);
            l.setStartY(cachedPlotY); l.setEndY(cachedPlotY + cachedPlotH);
        }
    }

    private void updateCircuitDataWithLayoutCheck() {
        if (currentLap == null) return;
        String venue = resolveTrackHint(currentLap);
        if (venue == null) return;
        List<TrackLayout> layouts = AcSectionsLoader.scanLayouts(venue);
        if (layouts.size() > 1) {
            Platform.runLater(() -> {
                TrackLayout choice = askUserForLayout(layouts);
                loadCircuitData(venue, (choice != null) ? choice : layouts.get(0));
            });
        } else {
            loadCircuitData(venue, null);
        }
    }

    private void loadCircuitData(String venue, TrackLayout layout) {
        if (currentLap == null) return;
        var samples = currentLap.samples.stream().map(Sample::values).toList();
        double[] xAxis = SeriesBundle.extract(currentLap, axis).x.stream().mapToDouble(Double::doubleValue).toArray();
        circuitCtl.setLapData(samples, xAxis, venue, layout);
        boolean hasDrs = circuitCtl.hasDrs();
        showDrsCheck.setVisible(hasDrs); showDrsCheck.setManaged(hasDrs);
        circuitView.setDrsVisible(hasDrs && showDrsCheck.isSelected());
        boolean hasIdeal = circuitCtl.hasIdealLine();
        showIdealLineCheck.setVisible(hasIdeal); showIdealLineCheck.setManaged(hasIdeal);
        circuitView.setIdealLineVisible(hasIdeal && showIdealLineCheck.isSelected());
        circuitCtl.updateCarAtX(cursorX.get());
        Platform.runLater(circuitView::requestLayout);
    }

    private TrackLayout askUserForLayout(List<TrackLayout> layouts) {
        ChoiceDialog<TrackLayout> dialog = new ChoiceDialog<>(layouts.get(0), layouts);
        dialog.setTitle("Selezione Variante");
        dialog.setHeaderText("Configurazioni multiple trovate.");
        dialog.setContentText("Scegli layout:");
        Optional<TrackLayout> result = dialog.showAndWait();
        return result.orElse(null);
    }

    private void promptForLayoutSelection() {
        if (currentLap == null) return;
        String venue = resolveTrackHint(currentLap);
        List<TrackLayout> layouts = AcSectionsLoader.scanLayouts(venue);
        if (layouts.size() > 1) {
            TrackLayout choice = askUserForLayout(layouts);
            if (choice != null) loadCircuitData(venue, choice);
        } else {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Nessuna variante disponibile.");
            alert.show();
        }
    }

    private double playbackSpeed() { return speedSteps[Math.max(0, Math.min(speedIdx, speedSteps.length - 1))]; }
    private void cycleSpeed() { speedIdx = (speedIdx + 1) % speedSteps.length; updateSpeedButton(); }
    private void updateSpeedButton() { double sp = playbackSpeed(); speedBtn.setText((Math.abs(sp-0.5)<1e-9)?"×0.5":"×"+(int)Math.round(sp)); }
    private void startPlayback() { if (!isPlaying) { isPlaying = true; player.start(); updatePlayButtons(); } }
    private void pausePlayback() { if (isPlaying) { isPlaying = false; player.stop(); updatePlayButtons(); } }
    private void updatePlayButtons() { playBtn.setDisable(isPlaying); pauseBtn.setDisable(!isPlaying); }

    private void advanceByTime(double deltaTLTarget) {
        if (sb == null || signals == null) return;
        double min = xSlider.getMin(), max = xSlider.getMax(), x0 = cursorX.get();
        if (!Double.isFinite(x0) || max <= min) return;
        Double tl0 = signals.lapTimeSec(x0);
        double xNext;
        if (axis != null && axis.useLapTime) xNext = x0 + deltaTLTarget;
        else if (tl0 != null && !tl0.isNaN()) {
            double tlTarget = tl0 + deltaTLTarget;
            xNext = invertLapTime(tlTarget, x0, min, max);
            if (Double.isNaN(xNext)) xNext = x0 + (max-min)*0.10*deltaTLTarget;
        } else xNext = x0 + (max-min)*0.10*deltaTLTarget;
        if (xNext >= max - 1e-9) { cursorX.set(max); pausePlayback(); } else cursorX.set(Math.max(min, Math.min(max, xNext)));
    }

    private double invertLapTime(double targetTL, double xStart, double min, double max) {
        Double tlMin=signals.lapTimeSec(min), tlMax=signals.lapTimeSec(max);
        if(tlMin==null||tlMax==null) return Double.NaN;
        if(targetTL<=tlMin) return min; if(targetTL>=tlMax) return max;
        double lo=min, hi=max;
        Double tlStart=signals.lapTimeSec(xStart);
        if(tlStart!=null && !tlStart.isNaN()){ if(targetTL>=tlStart) lo=xStart; else hi=xStart; }
        for(int i=0;i<50;i++){ double mid=0.5*(lo+hi); Double tl=signals.lapTimeSec(mid); if(tl==null)break; if(Math.abs(tl-targetTL)<1e-4)return mid; if(tl<targetTL)lo=mid;else hi=mid; }
        return 0.5*(lo+hi);
    }

    private void renderChart() {
        chart.getData().clear();
        if (currentLap == null) return;

        axis = AxisPicker.pick(currentLap);
        ((NumberAxis) chart.getXAxis()).setLabel(axis.label);
        ((NumberAxis) chart.getYAxis()).setLabel(yLabelFor(plotSelector.getValue()));

        // OTTIMIZZAZIONE 1: charts.renderChart ora gestisce già l'inserimento batch dei dati e i Bounds!
        charts.renderChart(chart, plotSelector.getValue(), currentLap, null, axis);

        // OTTIMIZZAZIONE 2: Disabilita simboli e animazioni per performance
        chart.setCreateSymbols(false);
        chart.setAnimated(false);

        sb = SeriesBundle.extract(currentLap, axis);
        signals = new Signals(currentLap, axis);

        // Logica veicolo e gauges (codice originale mantenuto)
        VehicleTraits traits = VehicleTraits.detect(this.data.getLaps());
        tyreWear.setVehicleTraits(traits);
        System.out.println("Veicolo rilevato: " + traits.category + " " + traits.powertrain);
        speedGauge.setMax(finiteMax(sb.speed, 300.0));
        rpmGauge.setMax(finiteMax(sb.rpm, 9000.0));

        buildPedalForces(currentLap, axis);

        // Logica slider (codice originale mantenuto)
        double xmin = min(sb.x), xmax = max(sb.x);
        if (Double.isFinite(xmin) && Double.isFinite(xmax) && xmax > xmin) {
            xSlider.setMin(xmin); xSlider.setMax(xmax);
            Double saved = cursorPosByLap.get(currentLap);
            cursorX.set((saved!=null && saved>=xmin && saved<=xmax)?saved:xmin);
        }

        // Logica circuito (codice originale mantenuto)
        boolean canBuild = hasTrackGeometry(currentLap);
        circuitToggle.setVisible(canBuild); circuitToggle.setManaged(canBuild);
        if (circuitView.isVisible() && !canBuild) {
            circuitToggle.setSelected(false);
            toggleCircuit(false);
        } else if (circuitView.isVisible() && canBuild) {
            buildCircuitGeometryForCurrentLap();
            circuitCtl.updateCarAtX(cursorX.get());
        }

        Platform.runLater(() -> {
            // --- MODIFICA QUI: Rimosso ChartInteractions.applyDataBoundsFromSeries(chart) ---
            // Era ridondante perché charts.renderChart lo fa già in modo ottimizzato.
            // --------------------------------------------------------------------------------

            cachePlotGeometry(); // Importante: ricalcola geometria pesante (cache) dopo il render

            updateCursorAndWidgets(cursorX.get());
            refreshWidgetsVisibility();
            updateSliderGeometry();
            refreshWaypointNodes();
            updateWaypointCombo();
            updateWaypointVisual();
        });
    }

    private void installShortcuts(Scene scene) {
        if (scene == null) return;
        var acc = scene.getAccelerators();
        acc.put(new KeyCodeCombination(KeyCode.SPACE), () -> { if (isPlaying) pausePlayback(); else startPlayback(); });
        acc.put(new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.SHIFT_DOWN), () -> stepBySamples(+1));
        acc.put(new KeyCodeCombination(KeyCode.LEFT,  KeyCombination.SHIFT_DOWN), () -> stepBySamples(-1));
        acc.put(new KeyCodeCombination(KeyCode.W), this::addWaypointAtCurrent);
        acc.put(new KeyCodeCombination(KeyCode.DELETE), this::removeSelectedOrLastWaypoint);
    }

    private void buildPedalForces(Lap lap, AxisChoice axis) { xPF.clear(); thrForce.clear(); brkForce.clear(); cluForce.clear(); if (lap == null || lap.samples == null) return; double d0=Double.NaN,lt0=Double.NaN,t0=Double.NaN; for(Sample s:lap.samples){ if(axis.useDist&&Double.isNaN(d0)&&!Double.isNaN(s.distance()))d0=s.distance(); Double lt=s.values().get(Channel.LAP_TIME); if(axis.useLapTime&&Double.isNaN(lt0)&&lt!=null)lt0=lt; Double ti=s.values().get(Channel.TIME); if(axis.useAbsTime&&Double.isNaN(t0)&&ti!=null)t0=ti; } for(Sample s:lap.samples){ double xVal; if(axis.useDist&&!Double.isNaN(s.distance()))xVal=s.distance()-(Double.isNaN(d0)?0:d0); else if(axis.useLapTime){ Double v=s.values().get(Channel.LAP_TIME); xVal=(v!=null)?(v-(Double.isNaN(lt0)?0:lt0)):xPF.size(); } else if(axis.useAbsTime){ Double v=s.values().get(Channel.TIME); xVal=(v!=null)?(v-(Double.isNaN(t0)?0:t0)):xPF.size(); } else xVal=xPF.size(); xPF.add(xVal); thrForce.add(s.values().getOrDefault(Channel.THROTTLE_FORCE,Double.NaN)); brkForce.add(s.values().getOrDefault(Channel.BRAKE_FORCE,Double.NaN)); cluForce.add(s.values().getOrDefault(Channel.CLUTCH_FORCE,Double.NaN)); } }
    private static String yLabelFor(ChartPane.PlotType t) { return switch(t){ case SPEED_DIST->"Speed [km/h]"; case THR_BRAKE_DIST->"%"; case STEERING_DIST->"Angle [deg]"; case RPM_TIME->"RPM"; case FFB_FORCE->"FFB"; case PEDAL_FORCE,SEAT_FORCE->"Force (N)"; default->"Y"; }; }

    private void updateCursorAndWidgets(double xVal) {
        if (sb == null || signals == null) return;
        readout.setX(formatX(xVal, axis)); readout.setTimeLap(signals.lapTimeSec(xVal));
        ChartPane.PlotType t = plotSelector.getValue();
        switch(t){
            case SPEED_DIST->{ readout.set1("Speed",String.format(Locale.ITALIAN,"%.1f km/h", interp(sb.x,sb.speed,xVal))); readout.set2("",""); readout.set3("",""); }
            case THR_BRAKE_DIST->{ readout.set1("Throttle",String.format(Locale.ITALIAN,"%.0f %%",signals.throttle01(xVal)*100)); readout.set2("Brake",String.format(Locale.ITALIAN,"%.0f %%",signals.brake01(xVal)*100)); readout.set3("Clutch",String.format(Locale.ITALIAN,"%.0f %%",signals.clutch01Pedal(xVal)*100)); }
            case STEERING_DIST->{ readout.set1("Steer",String.format(Locale.ITALIAN,"%.1f °",interp(sb.x,sb.steering,xVal))); readout.set2("",""); readout.set3("",""); }
            case RPM_TIME->{ readout.set1("RPM",String.format(Locale.ITALIAN,"%.0f",interp(sb.x,sb.rpm,xVal))); readout.set2("",""); readout.set3("",""); }
            case FFB_FORCE->{ readout.set1("FFB",String.format(Locale.ITALIAN,"%.0f %%",signals.ffb01(xVal)*100)); readout.set2("",""); readout.set3("",""); }
            case PEDAL_FORCE->{ readout.set1("Pedal force",String.format(Locale.ITALIAN,"%.0f N",interp(sb.x,sb.pedalForce,xVal))); readout.set2("",""); readout.set3("",""); }
            case SEAT_FORCE->{ readout.set1("Seat",String.format(Locale.ITALIAN,"%.0f N",interp(sb.x,sb.seatForce,xVal))); readout.set2("",""); readout.set3("",""); }
            default->{ readout.set1(""," "); readout.set2(""," "); readout.set3(""," "); }
        }

        speedGauge.update(interp(sb.x,sb.speed,xVal));
        rpmGauge.update(interp(sb.x,sb.rpm,xVal));
        wheel.setAngleDeg(interp(sb.x,sb.steering,xVal));
        pedals.update(signals.throttle01(xVal),signals.brake01(xVal),signals.clutch01Pedal(xVal), interpOrNaN(xPF,thrForce,xVal), interpOrNaN(xPF,brkForce,xVal), interpOrNaN(xPF,cluForce,xVal));
        ffbBar.update(signals.ffb01(xVal));

        // UPDATE UNIFICATO TYRE & BRAKES
        tyreWear.update(
                signals.value(Channel.TIRE_RUBBER_GRIP_FL,xVal), signals.value(Channel.TIRE_RUBBER_GRIP_FR,xVal), signals.value(Channel.TIRE_RUBBER_GRIP_RL,xVal), signals.value(Channel.TIRE_RUBBER_GRIP_RR,xVal),
                null, null, null, null, // Temp medie delegate a IMO
                signals.value(Channel.TIRE_PRESSURE_FL,xVal), signals.value(Channel.TIRE_PRESSURE_FR,xVal), signals.value(Channel.TIRE_PRESSURE_RL,xVal), signals.value(Channel.TIRE_PRESSURE_RR,xVal),
                signals.brakeTemp("FL", xVal), signals.brakeTemp("FR", xVal), signals.brakeTemp("RL", xVal), signals.brakeTemp("RR", xVal)
        );
        tyreWear.updateTempIMO(
                signals.value(Channel.TIRE_TEMP_INNER_FL,xVal),signals.value(Channel.TIRE_TEMP_MIDDLE_FL,xVal),signals.value(Channel.TIRE_TEMP_OUTER_FL,xVal),
                signals.value(Channel.TIRE_TEMP_INNER_FR,xVal),signals.value(Channel.TIRE_TEMP_MIDDLE_FR,xVal),signals.value(Channel.TIRE_TEMP_OUTER_FR,xVal),
                signals.value(Channel.TIRE_TEMP_INNER_RL,xVal),signals.value(Channel.TIRE_TEMP_MIDDLE_RL,xVal),signals.value(Channel.TIRE_TEMP_OUTER_RL,xVal),
                signals.value(Channel.TIRE_TEMP_INNER_RR,xVal),signals.value(Channel.TIRE_TEMP_MIDDLE_RR,xVal),signals.value(Channel.TIRE_TEMP_OUTER_RR,xVal)
        );

        seatTL.update(signals.seatForce01("LEFT",xVal),signals.seatForce01("RIGHT",xVal),signals.seatForce01("REAR",xVal));

        double pFL=travelPct(signals.value(Channel.SUSP_TRAVEL_FL,xVal),signals.value(Channel.MAX_SUS_TRAVEL_FL,xVal));
        double pFR=travelPct(signals.value(Channel.SUSP_TRAVEL_FR,xVal),signals.value(Channel.MAX_SUS_TRAVEL_FR,xVal));
        double pRL=travelPct(signals.value(Channel.SUSP_TRAVEL_RL,xVal),signals.value(Channel.MAX_SUS_TRAVEL_RL,xVal));
        double pRR=travelPct(signals.value(Channel.SUSP_TRAVEL_RR,xVal),signals.value(Channel.MAX_SUS_TRAVEL_RR,xVal));
        suspTL.update(pFL,pFR,pRL,pRR,signals.value(Channel.RIDE_HEIGHT_FL,xVal),signals.value(Channel.RIDE_HEIGHT_FR,xVal),signals.value(Channel.RIDE_HEIGHT_RL,xVal),signals.value(Channel.RIDE_HEIGHT_RR,xVal));

        coach.update(xVal,signals,sb);
        gForceTL.update(xVal, signals);

        updateCursorVisual();
    }

    private void refreshWidgetsVisibility(){
        if(signals==null)return;
        setVis(wheel.getRoot(),ui.showWheelTLProperty().get()||ui.showWheelProperty().get());
        setVis(speedGauge.getRoot(),ui.showSpeedTLProperty().get());
        setVis(rpmGauge.getRoot(),ui.showRpmTLProperty().get());
        setVis(wheelCard.getRoot(),ui.showWheelTLProperty().get()||ui.showWheelProperty().get());
        setVis(speedCard.getRoot(),ui.showSpeedTLProperty().get());
        setVis(rpmCard.getRoot(),ui.showRpmTLProperty().get());
        setVis(suspTL.getRoot(),ui.showSuspTLProperty().get());
        setVis(pedals.getRoot(),ui.showPedalsTLProperty().get()||ui.showPedalsProperty().get());
        setVis(ffbBar.getRoot(),ui.showFfbTLProperty().get()||ui.showFFBProperty().get());
        setVis(tyreWear.getRoot(),ui.showTyresTLProperty().get()||ui.showTyresProperty().get());
        setVis(seatTL.getRoot(),ui.showSeatTLProperty().get()||ui.showSeatProperty().get());
        setVis(coach.getRoot(),ui.showCoachTLProperty().get()||ui.showCoachProperty().get());
        setVis(gForceTL.getRoot(), ui.showGForceTLProperty().get());
    }

    private void attachVisibilityBindings(){
        Runnable apply=this::refreshWidgetsVisibility;
        ui.showWheelTLProperty().addListener((o,ov,nv)->apply.run());
        ui.showSpeedTLProperty().addListener((o,ov,nv)->apply.run());
        ui.showRpmTLProperty().addListener((o,ov,nv)->apply.run());
        ui.showPedalsTLProperty().addListener((o,ov,nv)->apply.run());
        ui.showFfbTLProperty().addListener((o,ov,nv)->apply.run());
        ui.showTyresTLProperty().addListener((o,ov,nv)->apply.run());
        ui.showBrakesTLProperty().addListener((o,ov,nv)->apply.run()); // Legacy prop but triggers refresh
        ui.showSeatTLProperty().addListener((o,ov,nv)->apply.run());
        ui.showCoachTLProperty().addListener((o,ov,nv)->apply.run());
        ui.showWheelProperty().addListener((o,ov,nv)->apply.run());
        ui.showPedalsProperty().addListener((o,ov,nv)->apply.run());
        ui.showFFBProperty().addListener((o,ov,nv)->apply.run());
        ui.showTyresProperty().addListener((o,ov,nv)->apply.run());
        ui.showGForceTLProperty().addListener((o,ov,nv)->apply.run());
        ui.showBrakesProperty().addListener((o,ov,nv)->apply.run());
        ui.showSeatProperty().addListener((o,ov,nv)->apply.run());
        ui.showCoachProperty().addListener((o,ov,nv)->apply.run());
        ui.showSuspTLProperty().addListener((o,ov,nv)->apply.run());
        refreshWidgetsVisibility();
    }

    private static void setVis(Node n, boolean v){ if(n==null)return; n.setVisible(v); n.setManaged(v); }
    private void addWaypointAtCurrent(){ if(currentLap==null)return; double x=cursorX.get(); if(!Double.isFinite(x))return; var list=waypointsByLap.computeIfAbsent(currentLap,k->new ArrayList<>()); if(!list.stream().anyMatch(v->Math.abs(v-x)<1e-6*Math.max(1,Math.abs(x)))){ list.add(x); list.sort(Double::compare); refreshWaypointNodes(); } if(circuitView.isVisible()) circuitCtl.setWaypointsByX(list); }
    private void removeSelectedOrLastWaypoint(){ if(currentLap==null)return; var list=waypointsByLap.get(currentLap); if(list==null||list.isEmpty())return; Double sel=wpCombo.getValue(); if(sel!=null){ list.removeIf(v->Math.abs(v-sel)<1e-9*Math.max(1.0,Math.abs(sel))); wpCombo.getSelectionModel().clearSelection(); }else{ list.remove(list.size()-1); } refreshWaypointNodes(); if(circuitView.isVisible()) circuitCtl.setWaypointsByX(list); }
    private void clearWaypoints(){ if(currentLap==null)return; var list=waypointsByLap.get(currentLap); if(list==null)return; list.clear(); refreshWaypointNodes(); }
    private void refreshWaypointNodes(){ wpGroup.getChildren().clear(); var list=waypointsByLap.getOrDefault(currentLap,List.of()); for(Double x:list){ Line l=new Line(); l.setStroke(Color.RED); l.setStrokeWidth(1.5); l.setOpacity(0.9); l.setUserData(x); wpGroup.getChildren().add(l); } updateWaypointCombo(); updateWaypointVisual(); }
    private void updateWaypointCombo(){ var list=waypointsByLap.getOrDefault(currentLap,List.of()); wpCombo.getItems().setAll(list); wpCombo.setDisable(list.isEmpty()); if(list.isEmpty())wpCombo.getSelectionModel().clearSelection(); }
    private static double interp(List<Double> xs, List<Double> ys, double xq){ int n=Math.min(xs.size(),ys.size()); if(n==0)return Double.NaN; if(xq<=xs.get(0))return ys.get(0); if(xq>=xs.get(n-1))return ys.get(n-1); int lo=0,hi=n-1; while(hi-lo>1){ int mid=(lo+hi)>>>1; if(xs.get(mid)<=xq)lo=mid; else hi=mid; } double x1=xs.get(lo),x2=xs.get(hi),y1=ys.get(lo),y2=ys.get(hi); if(x2==x1)return y1; return y1+(xq-x1)/(x2-x1)*(y2-y1); }
    private static Double interpOrNaN(List<Double> xs, List<Double> ys, double xq){ double v=interp(xs,ys,xq); return Double.isNaN(v)?Double.NaN:v; }
    private static double min(List<Double> a){ double m=Double.POSITIVE_INFINITY; for(Double v:a)if(v!=null&&!v.isNaN())m=Math.min(m,v); return m; }
    private static double max(List<Double> a){ double m=Double.NEGATIVE_INFINITY; for(Double v:a)if(v!=null&&!v.isNaN())m=Math.max(m,v); return m; }
    private static double finiteMax(List<Double> l, double def){ double m=Double.NEGATIVE_INFINITY; for(Double v:l)if(v!=null&&!v.isNaN()&&!v.isInfinite())m=Math.max(m,v); return(m==Double.NEGATIVE_INFINITY)?def:m; }
    private static String formatX(double x, AxisChoice a){ if(a==null)return String.format(Locale.ITALIAN,"%.3f",x); if(a.useDist)return String.format(Locale.ITALIAN,"%.1f m",x); if(a.useLapTime)return String.format(Locale.ITALIAN,"%.3f s (lap)",x); if(a.useAbsTime)return String.format(Locale.ITALIAN,"%.3f s (abs)",x); return String.format(Locale.ITALIAN,"%.3f",x); }
    private static void debugWheelResource(){ try{ String r="/assets/wheel.png"; System.out.println("=== DEBUG wheel ==="); System.out.println("1) "+TimeLineView.class.getResource(r)); }catch(Exception e){} }
    private static void lockCardSize(Node n){ if(n instanceof Region r){ r.setMinWidth(Region.USE_PREF_SIZE); r.setMaxWidth(Region.USE_PREF_SIZE); r.setMinHeight(Region.USE_PREF_SIZE); r.setMaxHeight(Region.USE_PREF_SIZE); } }
    private void stepBySamples(int d){ if(sb==null||sb.x==null||sb.x.isEmpty())return; double x=cursorX.get(); int i=indexAtOrBefore(sb.x,x); int j=Math.max(0,Math.min(sb.x.size()-1,i+d)); cursorX.set(sb.x.get(j)); }
    private static int indexAtOrBefore(List<Double> xs, double x){ int n=xs.size(); if(n==0)return 0; if(x<=xs.get(0))return 0; if(x>=xs.get(n-1))return n-1; int lo=0,hi=n-1; while(hi-lo>1){ int mid=(lo+hi)>>>1; if(xs.get(mid)<=x)lo=mid; else hi=mid; } return lo; }
    private static double travelPct(Double cur, Double mx){ if(cur==null||mx==null||cur.isNaN()||mx.isNaN()||mx==0)return 0; return Math.min(1,Math.abs(cur)/Math.abs(mx)); }
    private void toggleCircuit(boolean on){ circuitView.setVisible(on); circuitView.setManaged(on); chart.setVisible(!on); overlay.setVisible(!on); if(on){ if(!buildCircuitGeometryForCurrentLap()){ circuitToggle.setSelected(false); toggleCircuit(false); } else { circuitCtl.updateCarAtX(cursorX.get()); circuitCtl.setWaypointsByX(waypointsByLap.getOrDefault(currentLap,List.of())); } } }
    private boolean buildCircuitGeometryForCurrentLap(){ if(currentLap==null)return false; if(!hasGps(currentLap)&&!hasCarCoords(currentLap))return false; var samples=currentLap.samples.stream().map(Sample::values).toList(); double[] xAxis=SeriesBundle.extract(currentLap,axis).x.stream().mapToDouble(Double::doubleValue).toArray(); String trackHint=resolveTrackHint(currentLap); System.out.println("[Circuit] venue resolved = '"+trackHint+"'"); circuitCtl.setLapData(samples,xAxis,trackHint); Platform.runLater(circuitView::requestLayout); return true; }
    private static boolean hasTrackGeometry(Lap l){ if(l==null||l.samples==null)return false; boolean g=false,xy=false,xz=false; for(Sample s:l.samples){ Double la=s.values().get(Channel.GPS_LATITUDE),lo=s.values().get(Channel.GPS_LONGITUDE); if(la!=null&&lo!=null)g=true; Double x=s.values().get(Channel.CAR_COORD_X),y=s.values().get(Channel.CAR_COORD_Y),z=s.values().get(Channel.CAR_COORD_Z); if(x!=null&&y!=null)xy=true; if(x!=null&&z!=null)xz=true; if(g||xy||xz)return true; } return false; }
    private String resolveTrackHint(Lap lap){ if(lap==null)return null; try{var m=lap.getClass().getMethod("trackName");Object v=m.invoke(lap);if(v instanceof String s&&!s.isBlank())return s;}catch(Throwable t){} try{var m=lap.getClass().getMethod("metadata");Object meta=m.invoke(lap);if(meta instanceof Map<?,?> map){String s=firstNonBlank(getCI(map,"Venue"),getCI(map,"Track"),getCI(map,"Venue Name"));if(s!=null)return s;}}catch(Throwable t){} try{if(this.dataController!=null&&this.dataController.getCsvPath()!=null){String s=CsvVenueExtractor.fromFile(this.dataController.getCsvPath());if(s!=null)return s;}}catch(Throwable t){} try{if(this.dataController!=null&&this.dataController.getCsvPath()!=null)return String.valueOf(this.dataController.getCsvPath().getFileName());}catch(Throwable t){} return null; }
    private static String firstNonBlank(String... a){for(String s:a)if(s!=null&&!s.isBlank())return s;return null;}
    private static String getCI(Map<?,?> m,String k){for(var e:m.entrySet())if(String.valueOf(e.getKey()).equalsIgnoreCase(k))return String.valueOf(e.getValue());return null;}
    private static boolean hasGps(Lap l){if(l==null||l.samples==null)return false;for(Sample s:l.samples){Double la=s.values().get(Channel.GPS_LATITUDE);if(la!=null)return true;}return false;}
    private static boolean hasCarCoords(Lap l){if(l==null||l.samples==null)return false;for(Sample s:l.samples){Double x=s.values().get(Channel.CAR_COORD_X);if(x!=null)return true;}return false;}
    private interface DataControllerLike{List<Lap> getLaps();}
    // --- NUOVO METODO OTTIMIZZATO ---
    private void cachePlotGeometry() {
        Node plot = chart.lookup(".chart-plot-background");
        if (plot == null) return;

        // Calcoli pesanti di trasformazione coordinate spostati qui
        Bounds pbScene = plot.localToScene(plot.getBoundsInLocal());
        Point2D topLeft = overlay.sceneToLocal(pbScene.getMinX(), pbScene.getMinY());
        Point2D botRight = overlay.sceneToLocal(pbScene.getMaxX(), pbScene.getMaxY());

        this.cachedPlotX = topLeft.getX();
        this.cachedPlotY = topLeft.getY();
        this.cachedPlotW = botRight.getX() - topLeft.getX();
        this.cachedPlotH = botRight.getY() - topLeft.getY();
    }
}