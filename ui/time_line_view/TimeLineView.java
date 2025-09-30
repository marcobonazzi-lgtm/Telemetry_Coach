package org.simulator.ui.time_line_view;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;
import org.simulator.ui.ChartInteractions;
import org.simulator.ui.ChartManager;
import org.simulator.ui.ChartPane;
import org.simulator.ui.SeriesBundle;
import org.simulator.ui.asix_pack.AxisChoice;
import org.simulator.ui.asix_pack.AxisPicker;
import org.simulator.ui.settings.UiSettings;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.simulator.ui.time_line_view.widget_TL.*;


import java.util.*;
import java.util.Locale;
import java.util.Optional;

public final class TimeLineView {

    // -------------------- campi principali --------------------
    private final DataControllerLike data;
    private final ChartManager charts = new ChartManager();
    private final BorderPane root = new BorderPane();
    private final ComboBox<ChartPane.PlotType> plotSelector = new ComboBox<>();
    private final LineChart<Number, Number> chart;
    private final StackPane chartStack = new StackPane();
    private final Pane overlay = new Pane();
    private final Line vline = new Line();
    private final UiSettings ui = UiSettings.get();

    // Slider (allineato al plot)
    private final Slider xSlider = new Slider();
    private final Pane sliderPane = new Pane(); // contiene SOLO lo slider
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

    // Widget timeline-driven
    private final WheelTL      wheel      = new WheelTL("/assets/wheel.png");
    private final SpeedGaugeTL speedGauge = new SpeedGaugeTL();
    private final RpmGaugeTL rpmGauge   = new RpmGaugeTL();
    private final PedalBarsTL pedals     = new PedalBarsTL();
    private final FFBBarTL ffbBar     = new FFBBarTL();
    private final TyreWearTL tyreWear = new TyreWearTL();
    private final BrakeTempsTL brakesTL   = new BrakeTempsTL();
    private final SeatActuatorsTL seatTL  = new SeatActuatorsTL();
    private final CoachTL coach      = new CoachTL();
    private final SuspensionsTL suspTL     = new SuspensionsTL();
    // --- Overlay numerico (wrapper) --------------------------
    private WidgetValueOverlay wheelCard; // angolo sterzo
    private WidgetValueOverlay speedCard; // km/h
    private WidgetValueOverlay rpmCard;   // RPM

    // dati correnti
    private Lap currentLap;
    private AxisChoice axis;
    private SeriesBundle sb;
    private Signals signals;

    // Forze pedali (stesso asse X del grafico)
    private final List<Double> xPF = new ArrayList<>();
    private final List<Double> thrForce = new ArrayList<>();
    private final List<Double> brkForce = new ArrayList<>();
    private final List<Double> cluForce = new ArrayList<>();

    private static final double WIDGETS_H = 260;

    // Memoria posizione e waypoint per giro
    private final Map<Lap, Double>       cursorPosByLap  = new HashMap<>();
    private final Map<Lap, List<Double>> waypointsByLap  = new HashMap<>();

    // -------------------- ctor --------------------
    public TimeLineView(org.simulator.ui.DataController dataController) {
        this.data = dataController::getLaps;

        // TOP
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
        plotSelector.valueProperty().addListener((o, ov, nv) -> renderChart());

        var laps = Optional.ofNullable(this.data.getLaps()).orElseGet(List::of);
        Label comboInfo = new Label("(il giro si sceglie dalla barra in alto)");
        comboInfo.setStyle("-fx-opacity:.7; -fx-font-size:11px;");

        HBox top = new HBox(12, new Label("Grafico:"), plotSelector, comboInfo);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(8));

        // CHART
        chart = charts.buildChart("Timeline", "X", "Y");
        overlay.setMouseTransparent(true);
        vline.setStrokeWidth(1.3);
        vline.getStyleClass().add("timeline-vline");
        overlay.getChildren().addAll(wpGroup, vline);
        chartStack.getChildren().addAll(chart, overlay);
        StackPane.setAlignment(overlay, Pos.CENTER);
        ChartInteractions.applyDataBoundsFromSeries(chart);

        // SLIDER
        xSlider.setBlockIncrement(0.02);
        xSlider.valueProperty().bindBidirectional(cursorX);
        cursorX.addListener((o, ov, nv) -> {
            double x = nv.doubleValue();
            updateCursorAndWidgets(x);
            if (currentLap != null && Double.isFinite(x)) cursorPosByLap.put(currentLap, x);
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

        // PLAYBACK + WAYPOINT
        playBtn.setOnAction(e -> startPlayback());
        pauseBtn.setOnAction(e -> pausePlayback());
        speedBtn.setOnAction(e -> cycleSpeed());

        addWpBtn.setTooltip(new Tooltip("Aggiungi waypoint alla posizione corrente (W)"));
        addWpBtn.setOnAction(e -> addWaypointAtCurrent());
        delLastWpBtn.setTooltip(new Tooltip("Rimuovi waypoint selezionato o ultimo (Canc)"));
        delLastWpBtn.setOnAction(e -> removeSelectedOrLastWaypoint());
        clearWpBtn.setTooltip(new Tooltip("Rimuovi tutti i waypoint"));
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
        keyHint.setStyle("-fx-opacity:.7; -fx-font-size:11px;"); // <- in piccolo

        HBox controls = new HBox(8,
                playBtn, pauseBtn, speedBtn,
                posLabel, keyHint,              // <- hint tra parentesi
                addWpBtn, delLastWpBtn, clearWpBtn,
                new Label("Waypoints:"), wpCombo
        );
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(6, 10, 4, 10));

        // WIDGETS ROW
        wheelCard = new WidgetValueOverlay(wheel.getRoot(), Pos.BOTTOM_CENTER, new Insets(0, 0, 8, 8));
        speedCard = new WidgetValueOverlay(speedGauge.getRoot(), Pos.BOTTOM_RIGHT, new Insets(0, 8, 8, 0));
        rpmCard   = new WidgetValueOverlay(rpmGauge.getRoot(),   Pos.BOTTOM_RIGHT, new Insets(0, 8, 8, 0));

        HBox widgetsRow = new HBox(16,
                wheelCard.getRoot(),
                speedCard.getRoot(),
                rpmCard.getRoot(),
                pedals.getRoot(),
                ffbBar.getRoot(),
                tyreWear.getRoot(),
                brakesTL.getRoot(),
                suspTL.getRoot(),
                seatTL.getRoot(),
                coach.getRoot()
        );
        widgetsRow.setAlignment(Pos.CENTER_LEFT);

        lockCardSize(wheel.getRoot());
        lockCardSize(speedGauge.getRoot());
        lockCardSize(rpmGauge.getRoot());
        lockCardSize(pedals.getRoot());
        lockCardSize(ffbBar.getRoot());
        lockCardSize(tyreWear.getRoot());
        lockCardSize(brakesTL.getRoot());
        lockCardSize(seatTL.getRoot());
        lockCardSize(coach.getRoot());
        lockCardSize(wheelCard.getRoot());
        lockCardSize(speedCard.getRoot());
        lockCardSize(rpmCard.getRoot());

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

        // LAYOUT
        root.setTop(top);
        BorderPane center = new BorderPane(chartStack);
        center.setRight(readout.getRoot());
        VBox bottom = new VBox(controls, sliderPane, widgetsScroll);
        root.setCenter(center);
        root.setBottom(bottom);

        // LISTENERS
        chart.widthProperty().addListener((a, b, c) -> { updateCursorVisual(); updateSliderGeometry(); updateWaypointVisual(); });
        chart.heightProperty().addListener((a, b, c) -> { updateCursorVisual(); updateSliderGeometry(); updateWaypointVisual(); });
        NumberAxis xa = (NumberAxis) chart.getXAxis();
        NumberAxis ya = (NumberAxis) chart.getYAxis();
        xa.lowerBoundProperty().addListener((a, b, c) -> { updateCursorVisual(); updateSliderGeometry(); updateWaypointVisual(); });
        xa.upperBoundProperty().addListener((a, b, c) -> { updateCursorVisual(); updateSliderGeometry(); updateWaypointVisual(); });
        ya.lowerBoundProperty().addListener((a, b, c) -> { updateCursorVisual(); updateSliderGeometry(); updateWaypointVisual(); });
        ya.upperBoundProperty().addListener((a, b, c) -> { updateCursorVisual(); updateSliderGeometry(); updateWaypointVisual(); });
        root.widthProperty().addListener((a, b, c) -> Platform.runLater(() -> { updateSliderGeometry(); updateWaypointVisual(); }));
        root.heightProperty().addListener((a, b, c) -> Platform.runLater(() -> { updateSliderGeometry(); updateWaypointVisual(); }));

        // bindings impostazioni
        wheel.setImageFrom(ui.wheelImagePathProperty().get());
        ui.wheelImagePathProperty().addListener((o, ov, nv) -> wheel.setImageFrom(nv));

        attachVisibilityBindings();

        // 👉 COPIA stile numeri dal widget Pedali (Throttle/Brake/Clutch)
        Platform.runLater(() ->
                WidgetNumbersBinder.bindToPedalsFont(pedals.getRoot(), wheelCard, speedCard, rpmCard)
        );
        // Installa scorciatoie quando la Scene è disponibile
        root.sceneProperty().addListener((o, oldSc, newSc) -> {
            if (newSc != null) installShortcuts(newSc);
        });
// Se la Scene è già presente (raro ma possibile)
        if (root.getScene() != null) installShortcuts(root.getScene());


        // stato iniziale
        if (!laps.isEmpty()) showLap(laps.get(0));
        debugWheelResource();
        updatePlayButtons();
        updateSpeedButton();
        Platform.runLater(this::updateSliderGeometry);
    }

    public Node getRoot() { return root; }

    public void showLap(Lap lap) {
        this.currentLap = lap;
        renderChart();
    }

    // -------------------- playback helpers --------------------
    private double playbackSpeed() { return speedSteps[Math.max(0, Math.min(speedIdx, speedSteps.length - 1))]; }
    private void cycleSpeed() { speedIdx = (speedIdx + 1) % speedSteps.length; updateSpeedButton(); }
    private void updateSpeedButton() {
        double sp = playbackSpeed();
        String label = (Math.abs(sp - 0.5) < 1e-9) ? "×0.5" : "×" + (int) Math.round(sp);
        speedBtn.setText(label);
    }
    private void startPlayback() { if (!isPlaying) { isPlaying = true; player.start(); updatePlayButtons(); } }
    private void pausePlayback() { if (isPlaying) { isPlaying = false; player.stop(); updatePlayButtons(); } }
    private void updatePlayButtons() { playBtn.setDisable(isPlaying); pauseBtn.setDisable(!isPlaying); }

    private void advanceByTime(double deltaTLTarget) {
        if (sb == null || signals == null) return;

        double min = xSlider.getMin();
        double max = xSlider.getMax();
        double x0 = cursorX.get();
        if (!Double.isFinite(x0) || max <= min) return;

        Double tl0 = signals.lapTimeSec(x0);
        boolean tlOk = (tl0 != null && !tl0.isNaN() && !tl0.isInfinite());

        double xNext;
        if (axis != null && axis.useLapTime) {
            xNext = x0 + deltaTLTarget;
        } else if (tlOk) {
            double tlTarget = tl0 + deltaTLTarget;
            xNext = invertLapTime(tlTarget, x0, min, max);
            if (Double.isNaN(xNext)) {
                double span = Math.max(1e-9, max - min);
                double dxLin = span * 0.10 * (deltaTLTarget / 1.0);
                xNext = x0 + dxLin;
            }
        } else {
            double span = Math.max(1e-9, max - min);
            double dxLin = span * 0.10 * (deltaTLTarget / 1.0);
            xNext = x0 + dxLin;
        }

        if (xNext >= max - 1e-9) {
            cursorX.set(max);
            pausePlayback();
        } else {
            cursorX.set(Math.max(min, Math.min(max, xNext)));
        }
    }

    private double invertLapTime(double targetTL, double xStart, double min, double max) {
        Double tlMin = signals.lapTimeSec(min);
        Double tlMax = signals.lapTimeSec(max);
        if (tlMin == null || tlMin.isNaN() || tlMax == null || tlMax.isNaN()) return Double.NaN;
        if (targetTL <= tlMin) return min;
        if (targetTL >= tlMax) return max;

        double lo = min, hi = max;
        Double tlStart = signals.lapTimeSec(xStart);
        if (tlStart != null && !tlStart.isNaN()) { if (targetTL >= tlStart) lo = xStart; else hi = xStart; }
        for (int it = 0; it < 50; it++) {
            double mid = 0.5 * (lo + hi);
            Double tl = signals.lapTimeSec(mid);
            if (tl == null || tl.isNaN()) break;
            if (Math.abs(tl - targetTL) < 1e-4) return mid;
            if (tl < targetTL) lo = mid; else hi = mid;
        }
        return 0.5 * (lo + hi);
    }

    // -------------------- render grafico --------------------
    private void renderChart() {
        chart.getData().clear();
        if (currentLap == null) return;

        axis = AxisPicker.pick(currentLap);
        ((NumberAxis) chart.getXAxis()).setLabel(axis.label);
        ((NumberAxis) chart.getYAxis()).setLabel(yLabelFor(plotSelector.getValue()));

        charts.renderChart(chart, plotSelector.getValue(), currentLap, null, axis);

        sb = SeriesBundle.extract(currentLap, axis);
        signals = new Signals(currentLap, axis);

        // aggiorna limiti gauge da dati
        double maxSp = finiteMax(sb.speed, 300.0);
        double maxR = finiteMax(sb.rpm, 9000.0);
        speedGauge.setMax(maxSp);
        rpmGauge.setMax(maxR);

        // liste forze pedali
        buildPedalForces(currentLap, axis);

        // slider range
        double xmin = min(sb.x), xmax = max(sb.x);
        if (Double.isFinite(xmin) && Double.isFinite(xmax) && xmax > xmin) {
            xSlider.setMin(xmin);
            xSlider.setMax(xmax);
            Double saved = cursorPosByLap.get(currentLap);
            double start = (saved != null && saved >= xmin && saved <= xmax) ? saved : xmin;
            cursorX.set(start);
        }

        Platform.runLater(() -> {
            ChartInteractions.applyDataBoundsFromSeries(chart);
            updateCursorAndWidgets(cursorX.get());
            refreshWidgetsVisibility();
            updateSliderGeometry();
            refreshWaypointNodes();
            updateWaypointCombo();
            updateWaypointVisual();
        });
    }
    // --- Scorciatoie da tastiera (Play/Pause e step frame) ---
// --- Scorciatoie da tastiera (Space = Play/Pause, Shift+Freccia = step) ---
    private void installShortcuts(Scene scene) {
        if (scene == null) return;
        var acc = scene.getAccelerators();

        // Space -> toggle Play/Pause
        acc.put(new KeyCodeCombination(KeyCode.SPACE), () -> {
            if (isPlaying) pausePlayback(); else startPlayback();
        });

        // Shift+Freccia -> step frame (evita conflitti con focus/controlli)
        acc.put(new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.SHIFT_DOWN), () -> stepBySamples(+1));
        acc.put(new KeyCodeCombination(KeyCode.LEFT,  KeyCombination.SHIFT_DOWN), () -> stepBySamples(-1));
        // W -> aggiungi waypoint alla posizione corrente
        acc.put(new KeyCodeCombination(KeyCode.W), this::addWaypointAtCurrent);

// Canc -> elimina selezionato (se c’è) o ultimo
        acc.put(new KeyCodeCombination(KeyCode.DELETE), this::removeSelectedOrLastWaypoint);

    }



    private void buildPedalForces(Lap lap, AxisChoice axis) {
        xPF.clear(); thrForce.clear(); brkForce.clear(); cluForce.clear();
        if (lap == null || lap.samples == null || lap.samples.isEmpty()) return;

        double d0 = Double.NaN, lt0 = Double.NaN, t0 = Double.NaN;
        for (Sample s : lap.samples) {
            if (axis.useDist && Double.isNaN(d0) && !Double.isNaN(s.distance())) d0 = s.distance();
            Double lt = s.values().get(Channel.LAP_TIME);
            if (axis.useLapTime && Double.isNaN(lt0) && lt != null && !lt.isNaN()) lt0 = lt;
            Double ti = s.values().get(Channel.TIME);
            if (axis.useAbsTime && Double.isNaN(t0) && ti != null && !ti.isNaN()) t0 = ti;
        }
        for (Sample s : lap.samples) {
            double xVal;
            if (axis.useDist && !Double.isNaN(s.distance())) xVal = s.distance() - (Double.isNaN(d0) ? 0 : d0);
            else if (axis.useLapTime) {
                Double v = s.values().get(Channel.LAP_TIME);
                xVal = (v != null && !v.isNaN()) ? (v - (Double.isNaN(lt0) ? 0 : lt0)) : xPF.size();
            } else if (axis.useAbsTime) {
                Double v = s.values().get(Channel.TIME);
                xVal = (v != null && !v.isNaN()) ? (v - (Double.isNaN(t0) ? 0 : t0)) : xPF.size();
            } else xVal = xPF.size();

            xPF.add(xVal);
            thrForce.add(s.values().getOrDefault(Channel.THROTTLE_FORCE, Double.NaN));
            brkForce.add(s.values().getOrDefault(Channel.BRAKE_FORCE, Double.NaN));
            cluForce.add(s.values().getOrDefault(Channel.CLUTCH_FORCE, Double.NaN));
        }
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

    // -------------------- aggiornamento cursore + widget --------------------
    private void updateCursorAndWidgets(double xVal) {
        if (sb == null || signals == null) return;

        // Readout
        readout.setX(formatX(xVal, axis));
        readout.setTimeLap(signals.lapTimeSec(xVal));

        ChartPane.PlotType t = plotSelector.getValue();
        switch (t) {
            case SPEED_DIST -> {
                double s = interp(sb.x, sb.speed, xVal);
                readout.set1("Speed", String.format(Locale.ITALIAN, "%.1f km/h", s));
                readout.set2("", ""); readout.set3("", "");
            }
            case THR_BRAKE_DIST -> {
                double th = signals.throttle01(xVal);
                double br = signals.brake01(xVal);
                double cl = signals.clutch01Pedal(xVal);
                readout.set1("Throttle", String.format(Locale.ITALIAN, "%.0f %%", th * 100.0));
                readout.set2("Brake", String.format(Locale.ITALIAN, "%.0f %%", br * 100.0));
                readout.set3("Clutch", String.format(Locale.ITALIAN, "%.0f %%", cl * 100.0));
            }
            case STEERING_DIST -> {
                double st = interp(sb.x, sb.steering, xVal);
                readout.set1("Steer", String.format(Locale.ITALIAN, "%.1f °", st));
                readout.set2("", ""); readout.set3("", "");
            }
            case RPM_TIME -> {
                double rpm = interp(sb.x, sb.rpm, xVal);
                readout.set1("RPM", String.format(Locale.ITALIAN, "%.0f", rpm));
                readout.set2("", ""); readout.set3("", "");
            }
            case FFB_FORCE -> {
                double f = signals.ffb01(xVal);
                readout.set1("FFB", String.format(Locale.ITALIAN, "%.0f %%", f * 100.0));
                readout.set2("", ""); readout.set3("", "");
            }
            case PEDAL_FORCE -> {
                double pf = interp(sb.x, sb.pedalForce, xVal);
                readout.set1("Pedal force", String.format(Locale.ITALIAN, "%.0f N", pf));
                readout.set2("", ""); readout.set3("", "");
            }
            case SEAT_FORCE -> {
                double sf = interp(sb.x, sb.seatForce, xVal);
                readout.set1("Seat", String.format(Locale.ITALIAN, "%.0f N", sf));
                readout.set2("", ""); readout.set3("", "");
            }
            default -> { readout.set1("",""); readout.set2("",""); readout.set3("",""); }
        }

        // Widget dinamici
        double speedNow = interp(sb.x, sb.speed, xVal);
        double rpmNow   = interp(sb.x, sb.rpm,   xVal);
        double steerNow = interp(sb.x, sb.steering, xVal);

        speedGauge.update(speedNow);
        rpmGauge.update(rpmNow);
        wheel.setAngleDeg(steerNow);

        // Pedali
        Double thrN = interpOrNaN(xPF, thrForce, xVal);
        Double brkN = interpOrNaN(xPF, brkForce, xVal);
        Double cluN = interpOrNaN(xPF, cluForce, xVal);
        pedals.update(
                signals.throttle01(xVal), signals.brake01(xVal), signals.clutch01Pedal(xVal),
                thrN, brkN, cluN
        );

        ffbBar.update(signals.ffb01(xVal));
// Consumo (preferisci RubberGrip se disponibile: consumo = 1 - grip)
// === Consumo gomme (colore pieno in base al consumo = 100 - rubber grip) ===
        tyreWear.updateWear(
                signals.value(Channel.TIRE_RUBBER_GRIP_FL, xVal),
                signals.value(Channel.TIRE_RUBBER_GRIP_FR, xVal),
                signals.value(Channel.TIRE_RUBBER_GRIP_RL, xVal),
                signals.value(Channel.TIRE_RUBBER_GRIP_RR, xVal)
        );

// Temperature I/M/O (numeri sotto le gomme)
        tyreWear.updateTempIMO(
                signals.value(Channel.TIRE_TEMP_INNER_FL, xVal),  signals.value(Channel.TIRE_TEMP_MIDDLE_FL, xVal),  signals.value(Channel.TIRE_TEMP_OUTER_FL, xVal),
                signals.value(Channel.TIRE_TEMP_INNER_FR, xVal),  signals.value(Channel.TIRE_TEMP_MIDDLE_FR, xVal),  signals.value(Channel.TIRE_TEMP_OUTER_FR, xVal),
                signals.value(Channel.TIRE_TEMP_INNER_RL, xVal),  signals.value(Channel.TIRE_TEMP_MIDDLE_RL, xVal),  signals.value(Channel.TIRE_TEMP_OUTER_RL, xVal),
                signals.value(Channel.TIRE_TEMP_INNER_RR, xVal),  signals.value(Channel.TIRE_TEMP_MIDDLE_RR, xVal),  signals.value(Channel.TIRE_TEMP_OUTER_RR, xVal)
        );

// Pressioni (psi) e Carichi (N)
        tyreWear.updatePress(
                signals.value(Channel.TIRE_PRESSURE_FL, xVal),
                signals.value(Channel.TIRE_PRESSURE_FR, xVal),
                signals.value(Channel.TIRE_PRESSURE_RL, xVal),
                signals.value(Channel.TIRE_PRESSURE_RR, xVal)
        );
        tyreWear.updateLoad(
                signals.value(Channel.TIRE_LOAD_FL, xVal),
                signals.value(Channel.TIRE_LOAD_FR, xVal),
                signals.value(Channel.TIRE_LOAD_RL, xVal),
                signals.value(Channel.TIRE_LOAD_RR, xVal)
        );


        brakesTL.update(
                signals.brakeTemp("FL", xVal),
                signals.brakeTemp("FR", xVal),
                signals.brakeTemp("RL", xVal),
                signals.brakeTemp("RR", xVal)
        );
        seatTL.update(
                signals.seatForce01("LEFT", xVal),
                signals.seatForce01("RIGHT", xVal),
                signals.seatForce01("REAR", xVal)
        );
        double pFL = travelPct(signals.value(org.simulator.canale.Channel.SUSP_TRAVEL_FL, xVal),
                signals.value(org.simulator.canale.Channel.MAX_SUS_TRAVEL_FL, xVal));
        double pFR = travelPct(signals.value(org.simulator.canale.Channel.SUSP_TRAVEL_FR, xVal),
                signals.value(org.simulator.canale.Channel.MAX_SUS_TRAVEL_FR, xVal));
        double pRL = travelPct(signals.value(org.simulator.canale.Channel.SUSP_TRAVEL_RL, xVal),
                signals.value(org.simulator.canale.Channel.MAX_SUS_TRAVEL_RL, xVal));
        double pRR = travelPct(signals.value(org.simulator.canale.Channel.SUSP_TRAVEL_RR, xVal),
                signals.value(org.simulator.canale.Channel.MAX_SUS_TRAVEL_RR, xVal));

        Double rhFL = signals.value(org.simulator.canale.Channel.RIDE_HEIGHT_FL, xVal);
        Double rhFR = signals.value(org.simulator.canale.Channel.RIDE_HEIGHT_FR, xVal);
        Double rhRL = signals.value(org.simulator.canale.Channel.RIDE_HEIGHT_RL, xVal);
        Double rhRR = signals.value(org.simulator.canale.Channel.RIDE_HEIGHT_RR, xVal);

        suspTL.update(pFL, pFR, pRL, pRR, rhFL, rhFR, rhRL, rhRR);
        coach.update(xVal, signals, sb);
        updateCursorVisual();
    }

    private void refreshWidgetsVisibility(){
        if (signals == null) return;

        setVis(wheel.getRoot(),     ui.showWheelTLProperty().get()  || ui.showWheelProperty().get());
        setVis(speedGauge.getRoot(),ui.showSpeedTLProperty().get());
        setVis(rpmGauge.getRoot(),  ui.showRpmTLProperty().get());
        setVis(wheelCard.getRoot(), ui.showWheelTLProperty().get()  || ui.showWheelProperty().get());
        setVis(speedCard.getRoot(), ui.showSpeedTLProperty().get());
        setVis(rpmCard.getRoot(),   ui.showRpmTLProperty().get());
        setVis(suspTL.getRoot(),  ui.showSuspTLProperty().get());  // <<< nuovo

        setVis(pedals.getRoot(),    ui.showPedalsTLProperty().get() || ui.showPedalsProperty().get());
        setVis(ffbBar.getRoot(),    ui.showFfbTLProperty().get()    || ui.showFFBProperty().get());
        setVis(tyreWear.getRoot(),   ui.showTyresTLProperty().get()  || ui.showTyresProperty().get());
        setVis(seatTL.getRoot(),    ui.showSeatTLProperty().get()   || ui.showSeatProperty().get());
        setVis(coach.getRoot(),     ui.showCoachTLProperty().get()  || ui.showCoachProperty().get());

        boolean hasBrakeTemps = signals.hasAnyBrakeTemp();
        boolean wantBrakes = ui.showBrakesTLProperty().get() || ui.showBrakesProperty().get();
        setVis(brakesTL.getRoot(), wantBrakes && hasBrakeTemps);
    }

    private void attachVisibilityBindings() {
        Runnable apply = this::refreshWidgetsVisibility;
        ui.showWheelTLProperty().addListener((o, ov, nv) -> apply.run());
        ui.showSpeedTLProperty().addListener((o, ov, nv) -> apply.run());
        ui.showRpmTLProperty().addListener((o, ov, nv) -> apply.run());
        ui.showPedalsTLProperty().addListener((o, ov, nv) -> apply.run());
        ui.showFfbTLProperty().addListener((o, ov, nv) -> apply.run());
        ui.showTyresTLProperty().addListener((o, ov, nv) -> apply.run());
        ui.showBrakesTLProperty().addListener((o, ov, nv) -> apply.run());
        ui.showSeatTLProperty().addListener((o, ov, nv) -> apply.run());
        ui.showCoachTLProperty().addListener((o, ov, nv) -> apply.run());
        // legacy
        ui.showWheelProperty().addListener((o, ov, nv) -> apply.run());
        ui.showPedalsProperty().addListener((o, ov, nv) -> apply.run());
        ui.showFFBProperty().addListener((o, ov, nv) -> apply.run());
        ui.showTyresProperty().addListener((o, ov, nv) -> apply.run());
        ui.showBrakesProperty().addListener((o, ov, nv) -> apply.run());
        ui.showSeatProperty().addListener((o, ov, nv) -> apply.run());
        ui.showCoachProperty().addListener((o, ov, nv) -> apply.run());
        ui.showSuspTLProperty().addListener((o, ov, nv) -> apply.run());  // <<< nuovo

        refreshWidgetsVisibility();
    }

    private static void setVis(Node n, boolean v) { if (n == null) return; n.setVisible(v); n.setManaged(v); }

    private void updateCursorVisual() {
        Node plot = chart.lookup(".chart-plot-background");
        if (plot == null) return;
        double xVal = cursorX.get();
        NumberAxis xa = (NumberAxis) chart.getXAxis();
        Bounds pbScene = plot.localToScene(plot.getBoundsInLocal());
        Point2D topLeft = overlay.sceneToLocal(pbScene.getMinX(), pbScene.getMinY());
        Point2D botRight = overlay.sceneToLocal(pbScene.getMaxX(), pbScene.getMaxY());
        double plotW = botRight.getX() - topLeft.getX();
        double plotH = botRight.getY() - topLeft.getY();
        double x0 = xa.getLowerBound(), x1 = xa.getUpperBound();
        if (x1 <= x0) return;
        double frac = (xVal - x0) / (x1 - x0);
        frac = Math.max(0, Math.min(1, frac));
        double xPixel = topLeft.getX() + frac * plotW;
        vline.setStartX(xPixel); vline.setEndX(xPixel);
        vline.setStartY(topLeft.getY()); vline.setEndY(topLeft.getY() + plotH);
    }

    private void updateSliderGeometry() {
        Node plot = chart.lookup(".chart-plot-background");
        if (plot == null || chart.getScene() == null) return;
        Bounds pbScene = plot.localToScene(plot.getBoundsInLocal());
        Bounds spScene = sliderPane.localToScene(sliderPane.getBoundsInLocal());
        if (pbScene == null || spScene == null) return;
        double startX = pbScene.getMinX() - spScene.getMinX();
        double endX   = pbScene.getMaxX() - spScene.getMinX();
        double width  = Math.max(0, endX - startX);
        double h = Math.max(20, sliderPane.getHeight() - 6);
        double y = (sliderPane.getHeight() - h) / 2.0;
        xSlider.resizeRelocate(startX, y, width, h);
        double m = 6; double bw = 28, bh = h;
        stepBackBtn.resizeRelocate(Math.max(0, startX - bw - m), y, bw, bh);
        stepFwdBtn.resizeRelocate(endX + m, y, bw, bh);
    }

    // === Waypoint =============================================
    private void addWaypointAtCurrent(){
        if (currentLap == null) return;
        double x = cursorX.get();
        if (!Double.isFinite(x)) return;
        var list = waypointsByLap.computeIfAbsent(currentLap, k -> new ArrayList<>());
        boolean exists = list.stream().anyMatch(v -> Math.abs(v - x) < 1e-6 * Math.max(1.0, Math.abs(x)));
        if (!exists){ list.add(x); list.sort(Double::compare); refreshWaypointNodes(); }
    }
    private void removeLastWaypoint(){ if (currentLap == null) return;
        var list = waypointsByLap.get(currentLap); if (list == null || list.isEmpty()) return;
        list.remove(list.size()-1); refreshWaypointNodes(); }
    private void clearWaypoints(){ if (currentLap == null) return;
        var list = waypointsByLap.get(currentLap); if (list == null) return; list.clear(); refreshWaypointNodes(); }

    private void refreshWaypointNodes(){
        wpGroup.getChildren().clear();
        var list = waypointsByLap.getOrDefault(currentLap, List.of());
        for (Double x : list){
            Line l = new Line();
            l.setStroke(Color.RED); l.setStrokeWidth(1.5); l.setOpacity(0.9);
            l.setUserData(x);
            wpGroup.getChildren().add(l);
        }
        updateWaypointCombo();
        updateWaypointVisual();
    }

    private void updateWaypointVisual(){
        Node plot = chart.lookup(".chart-plot-background");
        if (plot == null) return;
        Bounds pbScene = plot.localToScene(plot.getBoundsInLocal());
        Point2D topLeft  = overlay.sceneToLocal(pbScene.getMinX(), pbScene.getMinY());
        Point2D botRight = overlay.sceneToLocal(pbScene.getMaxX(), pbScene.getMaxY());
        double plotW = botRight.getX() - topLeft.getX();
        double plotH = botRight.getY() - topLeft.getY();
        NumberAxis xa = (NumberAxis) chart.getXAxis();
        double x0 = xa.getLowerBound(), x1 = xa.getUpperBound();
        if (x1 <= x0) return;
        for (Node n : wpGroup.getChildren()){
            Double xVal = (Double) n.getUserData();
            if (xVal == null) continue;
            double frac = (xVal - x0) / (x1 - x0);
            double xPixel = topLeft.getX() + Math.max(0, Math.min(1, frac)) * plotW;
            Line l = (Line) n;
            l.setStartX(xPixel); l.setEndX(xPixel);
            l.setStartY(topLeft.getY()); l.setEndY(topLeft.getY() + plotH);
        }
    }
    private void updateWaypointCombo() {
        var list = waypointsByLap.getOrDefault(currentLap, List.of());
        wpCombo.getItems().setAll(list);
        wpCombo.setDisable(list.isEmpty());
        if (list.isEmpty()) wpCombo.getSelectionModel().clearSelection();
    }

    // -------------------- numerica --------------------
    private static double interp(List<Double> xs, List<Double> ys, double xq) {
        int n = Math.min(xs.size(), ys.size());
        if (n == 0) return Double.NaN;
        if (xq <= xs.get(0)) return ys.get(0);
        if (xq >= xs.get(n - 1)) return ys.get(n - 1);
        int lo = 0, hi = n - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) / 2;
            double xm = xs.get(mid);
            if (xm <= xq) lo = mid; else hi = mid;
        }
        double x1 = xs.get(lo), x2 = xs.get(hi);
        double y1 = ys.get(lo), y2 = ys.get(hi);
        if (x2 == x1) return y1;
        double t = (xq - x1) / (x2 - x1);
        return y1 + t * (y2 - y1);
    }
    private static Double interpOrNaN(List<Double> xs, List<Double> ys, double xq) {
        double v = interp(xs, ys, xq);
        return Double.isNaN(v) ? Double.NaN : v;
    }
    private static double min(List<Double> a) { double m = Double.POSITIVE_INFINITY; for (Double v : a) if (v != null && !v.isNaN()) m = Math.min(m, v); return m; }
    private static double max(List<Double> a) { double m = Double.NEGATIVE_INFINITY; for (Double v : a) if (v != null && !v.isNaN()) m = Math.max(m, v); return m; }
    private static double finiteMax(List<Double> list, double def) {
        double m = Double.NEGATIVE_INFINITY;
        for (Double v : list) if (v != null && !v.isNaN() && !v.isInfinite()) m = Math.max(m, v);
        return (m == Double.NEGATIVE_INFINITY) ? def : m;
    }
    private static String formatX(double x, AxisChoice axis) {
        if (axis == null) return String.format(java.util.Locale.ITALIAN, "%.3f", x);
        if (axis.useDist)     return String.format(java.util.Locale.ITALIAN, "%.1f m", x);
        if (axis.useLapTime)  return String.format(java.util.Locale.ITALIAN, "%.3f s (lap)", x);
        if (axis.useAbsTime)  return String.format(java.util.Locale.ITALIAN, "%.3f s (abs)", x);
        return String.format(java.util.Locale.ITALIAN, "%.3f", x);
    }

    private static void debugWheelResource() {
        try {
            String res = "/assets/wheel.png";
            System.out.println("=== DEBUG wheel resource ===");
            java.net.URL u1 = TimeLineView.class.getResource(res);
            System.out.println("1) TimeLineView.getResource(\"" + res + "\") -> " + u1);
            String noSlash = res.startsWith("/") ? res.substring(1) : res;
            java.net.URL u2 = TimeLineView.class.getResource(noSlash);
            System.out.println("2) TimeLineView.getResource(noSlash) -> " + u2);
            java.net.URL u3 = Thread.currentThread().getContextClassLoader().getResource(noSlash);
            System.out.println("3) ContextClassLoader.getResource(noSlash) -> " + u3);
            System.out.println("==============================");
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void lockCardSize(Node n) {
        if (n instanceof Region r) {
            r.setMinWidth(Region.USE_PREF_SIZE);
            r.setMaxWidth(Region.USE_PREF_SIZE);
            r.setMinHeight(Region.USE_PREF_SIZE);
            r.setMaxHeight(Region.USE_PREF_SIZE);
        }
    }

    private void stepBySamples(int delta) {
        if (sb == null || sb.x == null || sb.x.isEmpty()) return;
        double xCur = cursorX.get();
        int i = indexAtOrBefore(sb.x, xCur);
        int j = Math.max(0, Math.min(sb.x.size() - 1, i + delta));
        cursorX.set(sb.x.get(j));
    }

    private static int indexAtOrBefore(List<Double> xs, double x) {
        int n = xs.size();
        if (n == 0) return 0;
        if (x <= xs.get(0)) return 0;
        if (x >= xs.get(n - 1)) return n - 1;
        int lo = 0, hi = n - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            double xm = xs.get(mid);
            if (xm <= x) lo = mid; else hi = mid;
        }
        return lo;
    }
    // Elimina il waypoint selezionato nella combo, altrimenti l'ultimo
    private void removeSelectedOrLastWaypoint() {
        if (currentLap == null) return;
        var list = waypointsByLap.get(currentLap);
        if (list == null || list.isEmpty()) return;

        Double sel = wpCombo.getValue();
        if (sel != null) {
            // rimuovi quello selezionato (con tolleranza numerica)
            list.removeIf(v -> Math.abs(v - sel) < 1e-9 * Math.max(1.0, Math.abs(sel)));
            wpCombo.getSelectionModel().clearSelection();
        } else {
            // fallback: rimuovi l'ultimo
            list.remove(list.size() - 1);
        }
        refreshWaypointNodes(); // aggiorna linee + combo
    }
    private static double travelPct(Double cur, Double mx){
        if (cur == null || mx == null || cur.isNaN() || mx.isNaN() || mx == 0.0) return 0.0;
        return Math.min(1.0, Math.abs(cur) / Math.abs(mx));
    }


    private interface DataControllerLike { java.util.List<Lap> getLaps(); }
}
