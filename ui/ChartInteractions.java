package org.simulator.ui;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.control.ToolBar;
import javafx.stage.Stage;

public final class ChartInteractions {
    private ChartInteractions(){}

    private static final String KEY_BOUNDS   = "tc.dataBounds";
    private static final String KEY_ATTACHED = "tc.interactionsAttached";
    private static final double MIN_X_RANGE = 1e-9;
    private static final double MIN_Y_RANGE = 1e-9;
    private static final double ZOOM_IN_FACTOR  = 0.85;  // 15% in
    private static final double ZOOM_OUT_FACTOR = 1.15;  // 15% out
    private static final double PAD_RATIO = 0.0;         // *** niente padding oltre i dati ***

    // API
    public static void applyDataBoundsFromSeries(LineChart<Number,Number> chart){
        attachOnce(chart);
        computeAndStoreDataBounds(chart);
        clampViewToData(chart, true);
    }
    public static void setDataBounds(LineChart<Number,Number> chart,
                                     double xMin, double xMax, double yMin, double yMax){
        attachOnce(chart);
        BoundsXY b = new BoundsXY();
        b.xMin = Math.min(xMin, xMax);
        b.xMax = Math.max(xMin, xMax);
        b.yMin = Math.min(yMin, yMax);
        b.yMax = Math.max(yMin, yMax);
        if (!b.valid()) return;
        chart.getProperties().put(KEY_BOUNDS, b);
        clampViewToData(chart, true);
    }

    // attach interazioni
    private static void attachOnce(LineChart<Number,Number> chart){
        Object flag = chart.getProperties().get(KEY_ATTACHED);
        if (flag instanceof Boolean && (Boolean)flag) return;

        chart.getData().addListener((ListChangeListener<XYChart.Series<Number,Number>>) c ->
                Platform.runLater(() -> { computeAndStoreDataBounds(chart); clampViewToData(chart, true); })
        );
        chart.sceneProperty().addListener((o,ov,nv) ->
                Platform.runLater(() -> { computeAndStoreDataBounds(chart); clampViewToData(chart, true); })
        );

        // Zoom rotellina (Ctrl = solo X, Shift = solo Y)
        // Zoom a scatti con la rotellina: SOLO avanti/indietro (niente X/Y separati, niente focus sul mouse)
        chart.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.getDeltaY() == 0) return;
            // rotella su = zoom in, giù = zoom out
            stepZoom(chart, e.getDeltaY() > 0);
            e.consume();
        });


        // Pan drag (Shift = solo Y; Ctrl = X+Y)
// Pan drag
// Nuovo comportamento:
//  - trascinamento con tasto sinistro = pan su X **e** Y
//  - Ctrl trascina = solo X
//  - Shift trascina = solo Y
//  - Ctrl+Shift = X+Y (come default)
        final double[] last = new double[2];

        chart.setOnMousePressed(ev -> {
            if (ev.getButton() == MouseButton.PRIMARY) {
                last[0] = ev.getX();
                last[1] = ev.getY();
            }
        });

        chart.setOnMouseDragged(ev -> {
            if (ev.getButton() != MouseButton.PRIMARY) return;

            double dx = ev.getX() - last[0];
            double dy = ev.getY() - last[1];
            last[0] = ev.getX();
            last[1] = ev.getY();

            boolean ctrl  = ev.isControlDown();
            boolean shift = ev.isShiftDown();

            boolean panX, panY;
            if (!ctrl && !shift) {        // default: muovi ovunque
                panX = true;  panY = true;
            } else if (ctrl && !shift) {  // solo X
                panX = true;  panY = false;
            } else if (!ctrl && shift) {  // solo Y
                panX = false; panY = true;
            } else {                      // ctrl+shift: X+Y
                panX = true;  panY = true;
            }

            pan(chart, dx, dy, panX, panY);
        });


        // Doppio click = reset
        chart.setOnMouseClicked(ev -> { if (ev.getClickCount()==2) clampViewToData(chart, true); });

        // Menu contestuale: zoom +/- , reset, fullscreen
        MenuItem zoomIn  = new MenuItem("Zoom avanti (10–15%)");
        MenuItem zoomOut = new MenuItem("Zoom indietro (10–15%)");
        MenuItem reset   = new MenuItem("Reset");
        MenuItem full    = new MenuItem("Schermo intero…");
        zoomIn.setOnAction(a -> stepZoom(chart, true));
        zoomOut.setOnAction(a -> stepZoom(chart, false));
        reset.setOnAction(a -> clampViewToData(chart, true));
        full.setOnAction(a -> openFullscreen(chart, chart.getTitle()));
        ContextMenu cm = new ContextMenu(zoomIn, zoomOut, new SeparatorMenuItem(), reset, full);
        chart.setOnContextMenuRequested(ev -> cm.show(chart, ev.getScreenX(), ev.getScreenY()));

        chart.getProperties().put(KEY_ATTACHED, Boolean.TRUE);
    }

    private static final class BoundsXY {
        double xMin=Double.POSITIVE_INFINITY, xMax=Double.NEGATIVE_INFINITY;
        double yMin=Double.POSITIVE_INFINITY, yMax=Double.NEGATIVE_INFINITY;
        boolean valid(){ return xMax>xMin && yMax>yMin &&
                !Double.isNaN(xMin) && !Double.isInfinite(xMin); }
        void add(double x,double y){
            if (Double.isNaN(x) || Double.isNaN(y) || Double.isInfinite(x) || Double.isInfinite(y)) return;
            if (x<xMin) xMin=x; if (x>xMax) xMax=x;
            if (y<yMin) yMin=y; if (y>yMax) yMax=y;
        }
        void pad(double r){
            if (r<=0) return;
            double xr = Math.max(MIN_X_RANGE, xMax-xMin);
            double yr = Math.max(MIN_Y_RANGE, yMax-yMin);
            xMin -= xr*r; xMax += xr*r;
            yMin -= yr*r; yMax += yr*r;
        }
    }

    private static void computeAndStoreDataBounds(LineChart<Number,Number> chart){
        BoundsXY b = new BoundsXY();
        for (XYChart.Series<Number,Number> s: chart.getData()){
            for (XYChart.Data<Number,Number> d: s.getData()){
                if (d.getXValue()==null || d.getYValue()==null) continue;
                b.add(d.getXValue().doubleValue(), d.getYValue().doubleValue());
            }
        }
        if (b.valid()){
            b.pad(PAD_RATIO); // ← 0.0: nessun “bordo extra”
            chart.getProperties().put(KEY_BOUNDS, b);
        }
    }
    private static BoundsXY getBounds(LineChart<Number,Number> chart){
        Object o = chart.getProperties().get(KEY_BOUNDS);
        return (o instanceof BoundsXY) ? (BoundsXY)o : null;
    }

    private static void clampViewToData(LineChart<Number,Number> chart, boolean reset){
        BoundsXY b = getBounds(chart); if (b==null || !b.valid()) return;
        NumberAxis x = (NumberAxis) chart.getXAxis();
        NumberAxis y = (NumberAxis) chart.getYAxis();

        x.setAutoRanging(false); y.setAutoRanging(false);

        if (reset) {
            x.setLowerBound(b.xMin); x.setUpperBound(b.xMax);
            y.setLowerBound(b.yMin); y.setUpperBound(b.yMax);
            return;
        }
        double loX = Math.max(b.xMin, x.getLowerBound());
        double hiX = Math.min(b.xMax, x.getUpperBound());
        if (hiX-loX < MIN_X_RANGE){
            double cx = (b.xMin+b.xMax)/2.0;
            loX = cx - MIN_X_RANGE/2; hiX = cx + MIN_X_RANGE/2;
        }
        x.setLowerBound(loX); x.setUpperBound(hiX);

        double loY = Math.max(b.yMin, y.getLowerBound());
        double hiY = Math.min(b.yMax, y.getUpperBound());
        if (hiY-loY < MIN_Y_RANGE){
            double cy = (b.yMin+b.yMax)/2.0;
            loY = cy - MIN_Y_RANGE/2; hiY = cy + MIN_Y_RANGE/2;
        }
        y.setLowerBound(loY); y.setUpperBound(hiY);
    }


    private static void stepZoom(LineChart<Number,Number> chart, boolean in){
        BoundsXY b = getBounds(chart); if (b==null) return;
        NumberAxis x = (NumberAxis) chart.getXAxis();
        NumberAxis y = (NumberAxis) chart.getYAxis();
        double fx = in ? ZOOM_IN_FACTOR : ZOOM_OUT_FACTOR;

        double cx = (x.getLowerBound()+x.getUpperBound())/2.0;
        double cy = (y.getLowerBound()+y.getUpperBound())/2.0;

        double hx = (x.getUpperBound()-x.getLowerBound())*fx/2.0;
        double hy = (y.getUpperBound()-y.getLowerBound())*fx/2.0;

        double lx = Math.max(b.xMin, cx - hx);
        double hx2= Math.min(b.xMax, cx + hx);
        double ly = Math.max(b.yMin, cy - hy);
        double hy2= Math.min(b.yMax, cy + hy);

        if (hx2-lx >= MIN_X_RANGE){ x.setLowerBound(lx); x.setUpperBound(hx2); }
        if (hy2-ly >= MIN_Y_RANGE){ y.setLowerBound(ly); y.setUpperBound(hy2); }
    }

    private static void pan(LineChart<Number,Number> chart, double dx, double dy, boolean panX, boolean panY){
        BoundsXY b = getBounds(chart); if (b==null) return;
        NumberAxis x = (NumberAxis) chart.getXAxis();
        NumberAxis y = (NumberAxis) chart.getYAxis();

        Node plot = chart.lookup(".chart-plot-background");
        double w = (plot!=null) ? plot.getBoundsInLocal().getWidth() : chart.getWidth();
        double h = (plot!=null) ? plot.getBoundsInLocal().getHeight(): chart.getHeight();
        w = Math.max(1, w); h = Math.max(1, h);

        if (panX) {
            double range = x.getUpperBound()-x.getLowerBound();
            double shift = -dx / w * range;
            double lo = x.getLowerBound()+shift, hi = x.getUpperBound()+shift;
            if (lo < b.xMin) { hi += (b.xMin-lo); lo = b.xMin; }
            if (hi > b.xMax) { lo -= (hi-b.xMax); hi = b.xMax; }
            x.setLowerBound(lo); x.setUpperBound(hi);
        }
        if (panY) {
            double range = y.getUpperBound()-y.getLowerBound();
            double shift = dy / h * range;
            double lo = y.getLowerBound()+shift, hi = y.getUpperBound()+shift;
            if (lo < b.yMin) { hi += (b.yMin-lo); lo = b.yMin; }
            if (hi > b.yMax) { lo -= (hi-b.yMax); hi = b.yMax; }
            y.setLowerBound(lo); y.setUpperBound(hi);
        }
    }

    private static void openFullscreen(LineChart<Number,Number> src, String title){
        NumberAxis fx = new NumberAxis(); fx.setLabel(((NumberAxis)src.getXAxis()).getLabel());
        NumberAxis fy = new NumberAxis(); fy.setLabel(((NumberAxis)src.getYAxis()).getLabel());
        LineChart<Number,Number> copy = new LineChart<>(fx, fy);
        copy.setCreateSymbols(false);
        copy.setAnimated(false);
        copy.setLegendVisible(src.isLegendVisible());
        copy.setTitle(title);

        for (XYChart.Series<Number,Number> s : src.getData()){
            XYChart.Series<Number,Number> ns = new XYChart.Series<>();
            ns.setName(s.getName());
            for (XYChart.Data<Number,Number> d: s.getData()){
                if (d.getXValue()==null || d.getYValue()==null) continue;
                ns.getData().add(new XYChart.Data<>(d.getXValue(), d.getYValue()));
            }
            copy.getData().add(ns);
        }

        attachOnce(copy);
        Platform.runLater(() -> { computeAndStoreDataBounds(copy); clampViewToData(copy, true); });

        Button close = new Button("Chiudi (Esc)");
        ToolBar tb = new ToolBar(new Label(title), new Separator(), close);
        BorderPane root = new BorderPane(copy);
        root.setTop(tb);

        Stage st = new Stage();
        st.setTitle(title);
        st.setScene(new Scene(root, 1280, 800));
        st.setFullScreenExitHint("");
        st.setFullScreen(true);
        close.setOnAction(e -> st.close());
        st.getScene().setOnKeyPressed(k -> { if (k.getCode()==javafx.scene.input.KeyCode.ESCAPE) st.close(); });
        st.show();
    }
}
