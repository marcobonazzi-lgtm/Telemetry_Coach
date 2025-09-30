package org.simulator.ui.analysis_view;

import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.VBox;
import org.simulator.ui.ChartManager;
import org.simulator.ui.ChartPane;

public class MiniChartBox extends VBox {
    public final ComboBox<ChartPane.PlotType> selector = new ComboBox<>();
    public final LineChart<Number, Number> chart;

    public MiniChartBox(String title, ChartPane.PlotType def){
        setSpacing(6);
        setPadding(new Insets(6));

        selector.getItems().addAll(
                ChartPane.PlotType.SPEED_DIST,
                ChartPane.PlotType.THR_BRAKE_DIST,
                ChartPane.PlotType.STEERING_DIST,
                ChartPane.PlotType.RPM_TIME,
                ChartPane.PlotType.FFB_FORCE,
                ChartPane.PlotType.PEDAL_FORCE,
                ChartPane.PlotType.SEAT_FORCE
        );
        selector.getSelectionModel().select(def);

        ChartManager cm = new ChartManager();
        chart = cm.buildChart(title, "X", "Y");

        getChildren().addAll(selector, chart);
    }

    public ChartPane.PlotType type(){ return selector.getValue(); }
}
