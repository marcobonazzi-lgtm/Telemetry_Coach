package org.simulator.ui.time_line_view.widget_TL;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import org.simulator.canale.Channel;
import org.simulator.ui.time_line_view.Signals;

import java.util.Locale;

public final class GForceTL {

    private static final double CARD_W = 240;
    private static final double CARD_H = 260;
    private static final double PLOT_SIZE = 150; // Dimensione grafico
    private static final double MAX_G_LATLONG = 3.0;
    private static final double MAX_G_VERT = 2.5;

    private final VBox root = new VBox(0);

    // Elementi UI Grafici
    private final Pane plotArea = new Pane();
    private final Circle puck = new Circle(5, Color.web("#22d3ee"));
    private final Rectangle vertFill = new Rectangle();

    // Elementi UI Testo (Label)
    private final Label lblLong = new Label("Lon:+0.0");
    private final Label lblLat  = new Label("Lat:+0.0");
    private final Label lblVert = new Label("Ver:+0.0");

    public GForceTL(){
        Label title = new Label("G-Force");
        title.setStyle("-fx-text-fill:#ffffff; -fx-font-weight:800; -fx-font-size:14px;");

        // --- 1. COSTRUZIONE PLOT (Lat/Long) ---
        StackPane plotContainer = new StackPane();
        plotContainer.setMinSize(PLOT_SIZE, PLOT_SIZE);
        plotContainer.setMaxSize(PLOT_SIZE, PLOT_SIZE);

        Circle bg = new Circle(PLOT_SIZE/2, Color.web("#1e293b"));
        bg.setStroke(Color.web("#334155"));

        // Cerchi concentrici guida (1G, 2G)
        double r1 = (PLOT_SIZE/2) * (1.0/MAX_G_LATLONG);
        double r2 = (PLOT_SIZE/2) * (2.0/MAX_G_LATLONG);
        Circle c1 = new Circle(r1); c1.setFill(null); c1.setStroke(Color.web("#334155")); c1.getStrokeDashArray().addAll(3d,3d);
        Circle c2 = new Circle(r2); c2.setFill(null); c2.setStroke(Color.web("#334155")); c2.getStrokeDashArray().addAll(3d,3d);

        Line xAxis = new Line(0, PLOT_SIZE/2, PLOT_SIZE, PLOT_SIZE/2); xAxis.setStroke(Color.web("#475569"));
        Line yAxis = new Line(PLOT_SIZE/2, 0, PLOT_SIZE/2, PLOT_SIZE); yAxis.setStroke(Color.web("#475569"));

        plotArea.getChildren().addAll(xAxis, yAxis, puck);
        puck.setCenterX(PLOT_SIZE/2); puck.setCenterY(PLOT_SIZE/2);

        plotContainer.getChildren().addAll(bg, c1, c2, plotArea);

        // --- 2. BARRA VERTICALE (Accanto) ---
        VBox vertBox = new VBox(2);
        vertBox.setAlignment(Pos.CENTER);
        StackPane vTrack = new StackPane();
        vTrack.setPrefSize(12, PLOT_SIZE);
        vTrack.setStyle("-fx-background-color:#1e293b; -fx-border-color:#334155; -fx-background-radius:4; -fx-border-radius:4;");

        vertFill.setWidth(8); vertFill.setHeight(2); vertFill.setFill(Color.web("#facc15"));

        // Linea zero per riferimento
        Line vZero = new Line(0, 0, 10, 0); vZero.setStroke(Color.GRAY); vZero.setOpacity(0.5);

        vTrack.getChildren().addAll(vZero, vertFill);
        Label vLbl = new Label("V"); vLbl.setStyle("-fx-text-fill:#94a3b8; -fx-font-size:9px; -fx-font-weight:bold;");
        vertBox.getChildren().addAll(vLbl, vTrack);

        HBox graphics = new HBox(10, plotContainer, vertBox);
        graphics.setAlignment(Pos.CENTER);

        // --- 3. DATA ROW (Affiancati come richiesto) ---
        HBox dataRow = new HBox(12);
        dataRow.setAlignment(Pos.CENTER);
        dataRow.setPadding(new Insets(8,0,0,0));

        // Stile compatto e colorato
        styleLbl(lblLong, "#22d3ee"); // Ciano
        styleLbl(lblLat,  "#22d3ee");
        styleLbl(lblVert, "#facc15"); // Giallo

        dataRow.getChildren().addAll(lblLong, lblLat, lblVert);

        // --- ASSEMBLY ---
        VBox card = new VBox(8, title, graphics, dataRow);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color:#0f172a; -fx-background-radius:12; -fx-border-color: #1e293b; -fx-border-width: 1; -fx-border-radius: 12;");
        card.setAlignment(Pos.TOP_CENTER);

        UIx.lockFixedSize(card, CARD_W, CARD_H);
        root.getChildren().add(card);
    }

    private void styleLbl(Label l, String col) {
        l.setStyle("-fx-text-fill:"+col+"; -fx-font-family:'Monospaced'; -fx-font-size:11px; -fx-font-weight:bold;");
    }

    public Node getRoot(){ return root; }

    public void update(double xVal, Signals s){
        // --- LOGICA DATI PRESA DALLA TUA CLASSE FUNZIONANTE ---

        // Lat
        Double gLat = s.value(Channel.ACC_LAT, xVal);
        if (!isF(gLat)) gLat = s.value(Channel.CG_ACCEL_LATERAL, xVal);

        // Long
        Double gLong = s.value(Channel.ACC_LONG, xVal);
        if (!isF(gLong)) gLong = s.value(Channel.CG_ACCEL_LONGITUDINAL, xVal);

        // Vert
        Double gVert = s.value(Channel.ACC_VERT, xVal);
        if (!isF(gVert)) gVert = s.value(Channel.CG_ACCEL_VERTICAL, xVal);

        // Check finite
        double lat = isF(gLat) ? gLat : 0.0;
        double lon = isF(gLong) ? gLong : 0.0;
        double vert = isF(gVert) ? gVert : 0.0;

        // --- UPDATE TESTI (Formattazione compatta per stare in riga) ---
        lblLong.setText(String.format(Locale.ROOT, "Lon:%+.1f", lon));
        lblLat.setText(String.format(Locale.ROOT, "Lat:%+.1f", lat));
        lblVert.setText(String.format(Locale.ROOT, "Ver:%+.1f", vert));

        // --- UPDATE PLOT (Logica standard) ---
        double scale = (PLOT_SIZE/2) / MAX_G_LATLONG;

        // Nota: Y cresce verso il basso. Frenata (Lon negativo) -> Pallino giù o su?
        // Solitamente nei grafici G: Frenata (G-) va in alto, Accel (G+) in basso.
        // Ma se seguiamo coordinate cartesiane pure:
        double px = (PLOT_SIZE/2) + (lat * scale);
        double py = (PLOT_SIZE/2) - (lon * scale);

        // Clamp
        puck.setCenterX(Math.max(0, Math.min(PLOT_SIZE, px)));
        puck.setCenterY(Math.max(0, Math.min(PLOT_SIZE, py)));

        // Colore rosso se frenata forte
        if (lon < -1.5) puck.setFill(Color.web("#ef4444"));
        else puck.setFill(Color.web("#22d3ee"));

        // --- UPDATE BARRA VERTICALE ---
        double vScale = (PLOT_SIZE/2) / MAX_G_VERT;
        double vH = Math.min(PLOT_SIZE/2, Math.abs(vert) * vScale);

        vertFill.setHeight(vH);
        // Se positivo (compressione) va su (o giù a seconda convenzione), qui usiamo:
        // Positivo -> Giallo (Su), Negativo -> Viola (Giù/Lift)
        if (vert >= 0) {
            vertFill.setTranslateY(-vH/2);
            vertFill.setFill(Color.web("#facc15"));
        } else {
            vertFill.setTranslateY(vH/2);
            vertFill.setFill(Color.web("#a855f7"));
        }
    }

    // Helper per controllare se il numero è valido
    private static boolean isF(Double d){ return d != null && !d.isNaN() && !d.isInfinite(); }

    private static class UIx {
        static void lockFixedSize(Region r, double w, double h) { r.setMinSize(w,h); r.setMaxSize(w,h); }
    }
}