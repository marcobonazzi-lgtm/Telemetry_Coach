package org.simulator.ui;

import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.VBox;
import org.simulator.canale.Lap;
import org.simulator.ui.asix_pack.AxisChoice;

public class ChartPane extends VBox {

    public enum PlotType {
        SPEED_DIST("Speed vs X"),
        THR_BRAKE_DIST("Throttle & Brake & Clutch vs X"),
        STEERING_DIST("Steering Angle vs X"),
        RPM_TIME("RPM vs X"),
        FFB_FORCE("Force Feedback vs X"),
        PEDAL_FORCE("Pedal Force vs X"),
        SEAT_FORCE("Seat Force vs X");
        final String label; PlotType(String l){ this.label = l; }
        @Override public String toString(){ return label; }
    }


    private final ComboBox<PlotType> selector = new ComboBox<>();
    private final LineChart<Number,Number> chart;
    private final String title;
    private final ChartManager chartManager;
    private Runnable onChange;
    private final boolean showSelector;

    /** Costruttore standard (con menù). */
    public ChartPane(String title, PlotType defaultType, ChartManager chartManager) {
        this(title, defaultType, chartManager, true);
    }

    /** Costruttore che consente di nascondere il menù a tendina. */
    public ChartPane(String title, PlotType defaultType, ChartManager chartManager, boolean showSelector) {
        this.title = title;
        this.chartManager = chartManager;
        this.showSelector = showSelector;

        setSpacing(6); setPadding(new Insets(6));
        this.chart = chartManager.buildChart(title, "X", "Y");

        if (showSelector) {
            selector.getItems().addAll(PlotType.values());
            if (defaultType != null) selector.getSelectionModel().select(defaultType);
            else selector.setPromptText("Seleziona tipo grafico…");
            selector.valueProperty().addListener((o, ov, nv) -> { if (onChange != null) onChange.run(); });
            getChildren().addAll(selector, chart);
        } else {
            getChildren().add(chart);
        }
    }


    public ComboBox<PlotType> selector(){ return selector; }

}
