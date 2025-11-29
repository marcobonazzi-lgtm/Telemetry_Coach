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
        // Pulisce i riferimenti morti e riapplica
        REG.removeIf(w -> w.get() == null);
        for (WeakReference<LineChart<Number,Number>> w : new ArrayList<>(REG)){
            LineChart<Number,Number> c = w.get();
            if (c != null) reapply(c);
        }
    }

    public static void reapply(LineChart<Number,Number> chart){
        UiSettings s = UiSettings.get();

        // Sempre applicare i colori dal Settings (che ora sono sincronizzati col Tema)
        boolean overlayCompare = isOverlayCompare(chart);
        boolean comparedChart  = isCompared(chart);

        applyPalette(chart, s, comparedChart, overlayCompare);

        // Applica stile ghost tratteggiato solo se NON è un grafico di confronto puro
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
        s.currentThemeProperty().addListener((a,b,c)-> R.run());

        // Ascolta tutti i cambiamenti di colore
        List<javafx.beans.property.Property<?>> props = Arrays.asList(
                s.colorSpeedProperty(), s.colorThrottleProperty(), s.colorBrakeProperty(), s.colorClutchProperty(),
                s.colorSteerProperty(), s.colorRpmProperty(), s.colorFfbProperty(), s.colorSeatProperty(), s.colorPedalForceProperty(),
                s.cmpColorSpeedProperty(), s.cmpColorThrottleProperty(), s.cmpColorBrakeProperty(), s.cmpColorClutchProperty(),
                s.cmpColorSteerProperty(), s.cmpColorRpmProperty(), s.cmpColorFfbProperty(), s.cmpColorSeatProperty(), s.cmpColorPedalForceProperty(),
                s.ghostColorAProperty(), s.ghostColorBProperty(), s.ghostColorCProperty(), s.ghostOpacityProperty()
        );
        props.forEach(p -> p.addListener((a,b,c) -> R.run()));
    }

    static void applyPalette(LineChart<Number,Number> chart, UiSettings s, boolean comparedChart, boolean overlayCompare){
        if (chart.getData()==null) return;

        for (XYChart.Series<Number,Number> series : chart.getData()){
            String name = (series.getName()!=null ? series.getName() : "").toLowerCase(Locale.ROOT);

            Color c;
            if (overlayCompare) {
                boolean isCmp = name.contains("(cmp)") || name.contains("comparata") || name.contains(" compare") || name.contains("(ghost)") || name.contains("sessione comparata");
                c = pickColorForMetric(name, s, isCmp);
            } else if (comparedChart) {
                c = pickColorForMetric(name, s, true);
            } else {
                c = pickColorForMetric(name, s, false);
            }

            if (c == null) continue;

            Node line = series.getNode();
            if (line != null){
                // FIX: Rimosso "px" per evitare errore parser.
                // Spessore aumentato a 3.5 per visibilità.
                line.setStyle("-fx-stroke: " + toHex(c) + "; -fx-stroke-width: 3.5 ; -fx-stroke-line-cap: round;");
            }
        }
    }

    static void applyGhostStyle(Node seriesNode, Color color, double opacity){
        if (seriesNode!=null){
            // FIX: Rimosso "px".
            // Ghost leggermente più sottile (2.5) della principale ma ben visibile.
            seriesNode.setStyle(
                    "-fx-stroke: " + toHex(color) + ";" +
                            "-fx-opacity: " + opacity + ";" +
                            "-fx-stroke-width: 2.5;" +
                            "-fx-stroke-dash-array: 10 6;"
            );
        }
    }

    static void updateLegendFromSeries(LineChart<Number,Number> chart, List<Color> override){
        Node legend = chart.lookup(".chart-legend");
        if (legend == null) return;
        legend.setStyle("-fx-background-color: transparent; -fx-padding: 10;");

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
                sym.setStyle("-fx-background-color: " + toHex(c) + ", white; -fx-background-radius: 2;");
                if (sym instanceof Region r) r.setPrefSize(16,16); // Icona leggenda più grande
            }
        }
    }

    private static Color pickColorForMetric(String lowerName, UiSettings s, boolean useCompared){
        boolean isSpeed   = lowerName.contains("speed") || lowerName.contains("velocità");
        boolean isThr     = lowerName.contains("thr") || lowerName.contains("throttle") || lowerName.contains("acceleratore");
        boolean isBrake   = lowerName.contains("brake") || lowerName.contains("freno");
        boolean isClutch  = lowerName.contains("clutch") || lowerName.contains("frizione");
        boolean isSteer   = lowerName.contains("steer") || lowerName.contains("sterzo") || lowerName.contains("angle");
        boolean isRpm     = lowerName.contains("rpm") || lowerName.contains("giri");
        boolean isFfb     = lowerName.contains("ffb") || lowerName.contains("feedback") || lowerName.contains("torque");
        boolean isSeat    = lowerName.contains("seat") || lowerName.contains("sedile");
        boolean isForce   = lowerName.contains("pedal") || lowerName.contains("force") || lowerName.contains("forza");

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
        if (c==null) return "#999999";
        return String.format("#%02x%02x%02x",
                (int)Math.round(c.getRed()*255),
                (int)Math.round(c.getGreen()*255),
                (int)Math.round(c.getBlue()*255));
    }

    private static void applyGhostStylesForChart(LineChart<Number,Number> chart, UiSettings s){
        if (chart.getData()==null) return;
        int ghostIdx = 0;
        for (XYChart.Series<Number,Number> series : chart.getData()){
            String name = (series.getName()!=null ? series.getName() : "").toLowerCase(Locale.ROOT);
            if (!name.contains("ghost") && !name.contains("comparata")) continue;
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

    private static boolean isCompared(LineChart<Number,Number> chart){
        if (chart == null) return false;
        if ("compared".equalsIgnoreCase(chart.getId())) return true;
        Object pal = chart.getProperties().get("palette");
        if (pal != null && "cmp".equalsIgnoreCase(String.valueOf(pal))) return true;
        if (chart.getStyleClass().contains("compared-chart")) return true;
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

    private static boolean isOverlayCompare(LineChart<Number,Number> chart){
        if (chart == null) return false;
        if ("compare-overlay".equalsIgnoreCase(chart.getId())) return true;
        Object mode = chart.getProperties().get("compareMode");
        if (mode != null && "overlay".equalsIgnoreCase(String.valueOf(mode))) return true;
        if (chart.getStyleClass().contains("compare-overlay")) return true;
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