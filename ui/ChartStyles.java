package org.simulator.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import org.simulator.ui.settings.UiSettings;

import java.lang.ref.WeakReference;
import java.util.*;

public final class ChartStyles {
    private ChartStyles(){}

    private static final List<WeakReference<LineChart<Number,Number>>> REG = new ArrayList<>();
    private static boolean listenersAttached = false;

    // ====== API ======
    public static void register(LineChart<Number,Number> chart){
        ensureGlobalListeners();
        REG.add(new WeakReference<>(chart));
        reapply(chart);
    }

    public static void reapplyAll(){
        for (WeakReference<LineChart<Number,Number>> w : new ArrayList<>(REG)){
            LineChart<Number,Number> c = w.get();
            if (c != null) reapply(c);
        }
    }

    public static void reapply(LineChart<Number,Number> chart){
        UiSettings s = UiSettings.get();

        if (s.useDefaultColorsProperty().get()){
            clearSeriesStyles(chart);
            Platform.runLater(() -> clearLegendStyles(chart));
            return;
        }

        boolean overlayCompare = isOverlayCompare(chart); // confronto sovrapposto
        boolean comparedChart  = isCompared(chart);       // card “Sessione comparata”

        applyPalette(chart, s, comparedChart, overlayCompare);
        // Importante: NON applico stile ghost sui grafici di confronto
        if (!overlayCompare && !comparedChart) {
            applyGhostStylesForChart(chart, s);
        }
        Platform.runLater(() -> updateLegendFromSeries(chart, null));
    }

    // ====== internals ======
    private static void ensureGlobalListeners(){
        if (listenersAttached) return;
        listenersAttached = true;

        UiSettings s = UiSettings.get();
        Runnable R = ChartStyles::reapplyAll;

        s.useDefaultColorsProperty().addListener((a,b,c)-> R.run());

        // palette base
        s.colorSpeedProperty().addListener((a,b,c)-> R.run());
        s.colorThrottleProperty().addListener((a,b,c)-> R.run());
        s.colorBrakeProperty().addListener((a,b,c)-> R.run());
        s.colorClutchProperty().addListener((a,b,c)-> R.run());
        s.colorSteerProperty().addListener((a,b,c)-> R.run());
        s.colorRpmProperty().addListener((a,b,c)-> R.run());
        s.colorFfbProperty().addListener((a,b,c)-> R.run());
        s.colorSeatProperty().addListener((a,b,c)-> R.run());
        s.colorPedalForceProperty().addListener((a,b,c)-> R.run());

        // palette “comparata”
        s.cmpColorSpeedProperty().addListener((a,b,c)-> R.run());
        s.cmpColorThrottleProperty().addListener((a,b,c)-> R.run());
        s.cmpColorBrakeProperty().addListener((a,b,c)-> R.run());
        s.cmpColorClutchProperty().addListener((a,b,c)-> R.run());
        s.cmpColorSteerProperty().addListener((a,b,c)-> R.run());
        s.cmpColorRpmProperty().addListener((a,b,c)-> R.run());
        s.cmpColorFfbProperty().addListener((a,b,c)-> R.run());
        s.cmpColorSeatProperty().addListener((a,b,c)-> R.run());
        s.cmpColorPedalForceProperty().addListener((a,b,c)-> R.run());

        // ghost
        s.ghostColorAProperty().addListener((a,b,c)-> R.run());
        s.ghostColorBProperty().addListener((a,b,c)-> R.run());
        s.ghostColorCProperty().addListener((a,b,c)-> R.run());
        s.ghostOpacityProperty().addListener((a,b,c)-> R.run());
    }

    // mantenuto per retro-compatibilità con chiamate esistenti
    public static void attachGlobalListeners(){ ensureGlobalListeners(); }

    static void applyPalette(LineChart<Number,Number> chart, UiSettings s, boolean comparedChart, boolean overlayCompare){
        if (chart.getData()==null) return;

        for (XYChart.Series<Number,Number> series : chart.getData()){
            String name = (series.getName()!=null ? series.getName() : "").toLowerCase(Locale.ROOT);

            Color c;
            if (overlayCompare) {
                // Nel grafico “Confronto (sovrapposto)”:
                // - la serie “rif” (o “current/attuale”) usa palette base,
                // - la serie “cmp/comparata/ghost” usa palette COMPARATA (NON trattata da ghost).
                boolean isRef = name.contains("(rif)") || name.contains(" rif") || name.contains("current") || name.contains("attuale");
                boolean isCmp = name.contains("(cmp)") || name.contains("comparata") || name.contains(" compare") || name.contains("(ghost)");
                c = pickColorForMetric(name, s, /*useCompared*/ isCmp);
            } else if (comparedChart) {
                // Card “Sessione comparata” → sempre palette comparata
                c = pickColorForMetric(name, s, /*useCompared*/ true);
            } else {
                // Grafici normali → palette base
                c = pickColorForMetric(name, s, /*useCompared*/ false);
            }

            if (c == null) continue;

            Node line = series.getNode();
            if (line != null){
                line.setStyle("-fx-stroke: " + toHex(c) + "; -fx-stroke-width: 1.5;");
            }
            for (XYChart.Data<Number,Number> d : series.getData()){
                Node n = d.getNode();
                if (n != null) n.setStyle("-fx-background-color: " + toHex(c) + ", white;");
            }
        }
    }

    static void applyGhostStyle(Node seriesNode, Color color, double opacity){
        if (seriesNode!=null){
            seriesNode.setStyle(
                    "-fx-stroke: " + toHex(color) + ";" +
                            "-fx-opacity: " + opacity + ";" +
                            "-fx-stroke-width: 1.2;" +
                            "-fx-stroke-dash-array: 8 6;"
            );
        }
    }

    static void updateLegendFromSeries(LineChart<Number,Number> chart, List<Color> override){
        Node legend = chart.lookup(".chart-legend");
        if (legend == null) return;

        List<Node> symbols = legend.lookupAll(".chart-legend-item-symbol").stream().toList();
        if (symbols.isEmpty()) return;

        List<Color> colors = new ArrayList<>();
        if (override != null && !override.isEmpty()){
            colors.addAll(override);
        } else {
            for (XYChart.Series<Number,Number> s : chart.getData()){
                colors.add(extractStrokeColor(s.getNode()));
            }
        }

        int n = Math.min(symbols.size(), colors.size());
        for (int i=0;i<n;i++){
            Node sym = symbols.get(i);
            Color c = colors.get(i);
            if (c != null){
                sym.setStyle("-fx-background-color: " + toHex(c) + ", white; -fx-padding: 6;");
                if (sym instanceof Region r) r.setPrefSize(10,10);
            }
        }
    }

    private static void clearSeriesStyles(LineChart<Number,Number> chart){
        if (chart.getData()==null) return;
        for (XYChart.Series<Number,Number> s : chart.getData()){
            if (s.getNode()!=null) s.getNode().setStyle("");
            for (XYChart.Data<Number,Number> d : s.getData()){
                if (d.getNode()!=null) d.getNode().setStyle("");
            }
        }
    }

    private static void clearLegendStyles(LineChart<Number,Number> chart){
        Node legend = chart.lookup(".chart-legend");
        if (legend == null) return;
        for (Node sym : legend.lookupAll(".chart-legend-item-symbol")){
            sym.setStyle(""); // palette Modena di default
        }
    }

    // ---- colori per metrica (base o comparata) ----
    private static Color pickColorForMetric(String lowerName, UiSettings s, boolean useCompared){
        if (s.useDefaultColorsProperty().get()) return null;

        // normalizza chiavi (speed/throttle/brake/…)
        boolean isSpeed   = lowerName.contains("speed");
        boolean isThr     = lowerName.contains("thr") || lowerName.contains("throttle");
        boolean isBrake   = lowerName.contains("brake");
        boolean isClutch  = lowerName.contains("clutch");
        boolean isSteer   = lowerName.contains("steer");
        boolean isRpm     = lowerName.contains("rpm");
        boolean isFfb     = lowerName.contains("ffb") || lowerName.contains("feedback") || lowerName.contains("torque");
        boolean isSeat    = lowerName.contains("seat");
        boolean isForce   = lowerName.contains("pedal") || lowerName.contains("force");

        if (useCompared){
            if (isSpeed)  return s.cmpColorSpeedProperty().get();
            if (isThr)    return s.cmpColorThrottleProperty().get();
            if (isBrake)  return s.cmpColorBrakeProperty().get();
            if (isClutch) return s.cmpColorClutchProperty().get();
            if (isSteer)  return s.cmpColorSteerProperty().get();
            if (isRpm)    return s.cmpColorRpmProperty().get();
            if (isFfb)    return s.cmpColorFfbProperty().get();
            if (isSeat)   return s.cmpColorSeatProperty().get();
            if (isForce)  return s.cmpColorPedalForceProperty().get();
        } else {
            if (isSpeed)  return s.colorSpeedProperty().get();
            if (isThr)    return s.colorThrottleProperty().get();
            if (isBrake)  return s.colorBrakeProperty().get();
            if (isClutch) return s.colorClutchProperty().get();
            if (isSteer)  return s.colorSteerProperty().get();
            if (isRpm)    return s.colorRpmProperty().get();
            if (isFfb)    return s.colorFfbProperty().get();
            if (isSeat)   return s.colorSeatProperty().get();
            if (isForce)  return s.colorPedalForceProperty().get();
        }
        return null;
    }

    private static Color extractStrokeColor(Node node){
        try{
            String st = node==null ? null : node.getStyle();
            if (st==null || st.isBlank()) return null;
            int i = st.indexOf("-fx-stroke:");
            if (i<0) return null;
            String sub = st.substring(i + "-fx-stroke:".length()).trim();
            String col = sub.split("[; ]")[0].trim();
            return Color.web(col);
        }catch(Exception e){ return null; }
    }

    private static String toHex(Color c){
        if (c==null) return "";
        return String.format("#%02x%02x%02x",
                (int)Math.round(c.getRed()*255),
                (int)Math.round(c.getGreen()*255),
                (int)Math.round(c.getBlue()*255));
    }

    // Applica stile Ghost (GHOST 1/2/3) alle serie il cui nome contiene "ghost"
    private static void applyGhostStylesForChart(LineChart<Number,Number> chart, UiSettings s){
        if (chart.getData()==null) return;
        int ghostIdx = 0;
        for (XYChart.Series<Number,Number> series : chart.getData()){
            String name = (series.getName()!=null ? series.getName() : "").toLowerCase(Locale.ROOT);
            if (!name.contains("ghost")) continue;
            ghostIdx++;
            Color color = switch (ghostIdx) {
                case 1 -> s.ghostColorAProperty().get();
                case 2 -> s.ghostColorBProperty().get();
                case 3 -> s.ghostColorCProperty().get();
                default -> s.ghostColorAProperty().get();
            };
            double opacity = s.ghostOpacityProperty().get();
            applyGhostStyle(series.getNode(), color, opacity);
        }
    }

    // Heuristic/flag: grafico “Sessione comparata”
    private static boolean isCompared(LineChart<Number,Number> chart){
        if (chart == null) return false;
        if ("compared".equalsIgnoreCase(chart.getId())) return true;
        Object pal = chart.getProperties().get("palette");
        if (pal != null && "cmp".equalsIgnoreCase(String.valueOf(pal))) return true;
        if (chart.getStyleClass().contains("compared-chart")) return true;
        // fallback: se le serie hanno “[cmp]” o “comparata” nei nomi
        try{
            if (chart.getData()!=null){
                for (XYChart.Series<Number,Number> s : chart.getData()){
                    String n = (s.getName()==null? "": s.getName()).toLowerCase(Locale.ROOT);
                    if (n.contains("comparata") || n.contains("(cmp)") || n.contains("[cmp]")) return true;
                }
            }
        }catch(Exception ignore){}
        return false;
    }

    // Heuristic/flag: grafico “Confronto (sovrapposto)”
    private static boolean isOverlayCompare(LineChart<Number,Number> chart){
        if (chart == null) return false;
        if ("compare-overlay".equalsIgnoreCase(chart.getId())) return true;
        Object mode = chart.getProperties().get("compareMode");
        if (mode != null && "overlay".equalsIgnoreCase(String.valueOf(mode))) return true;
        if (chart.getStyleClass().contains("compare-overlay")) return true;

        // fallback: serie “(rif)” + “(cmp)” oppure “(rif)” + “(ghost)”
        try{
            boolean hasRif=false, hasCmp=false;
            if (chart.getData()!=null){
                for (XYChart.Series<Number,Number> s : chart.getData()){
                    String n = (s.getName()==null? "": s.getName()).toLowerCase(Locale.ROOT);
                    hasRif |= n.contains("(rif)") || n.contains(" rif") || n.contains("current") || n.contains("attuale");
                    hasCmp |= n.contains("(cmp)") || n.contains("comparata") || n.contains("(ghost)");
                }
            }
            return hasRif && hasCmp;
        }catch(Exception ignore){}
        return false;
    }
}
