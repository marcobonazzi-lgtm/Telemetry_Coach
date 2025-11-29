package org.simulator.ui.time_line_view.widget_TL;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

import java.util.Locale;

public final class RpmGaugeTL {

    private static final double SIZE = 220;
    private static final double RADIUS = 80;
    private static final double START_ANGLE = -135;
    private static final double MAX_ANGLE = 270;

    private final VBox root = new VBox(0);
    private final Label valLbl = new Label("0");
    private final Label unitLbl = new Label("RPM");

    private final Polygon needle = new Polygon();
    private final Rotate needleRotate = new Rotate(START_ANGLE);

    private Timeline anim;
    private double maxRpm = 9000.0;

    public RpmGaugeTL(){
        // MODIFICA 1: Titolo aggiornato
        Label title = new Label("Engine RPM");
        title.setStyle("-fx-text-fill:#ffffff; -fx-font-weight:800; -fx-font-size:14px;");

        StackPane gauge = new StackPane();
        gauge.setMinSize(SIZE, SIZE);
        gauge.setMaxSize(SIZE, SIZE);

        // 1. Sfondo
        Circle bg = new Circle(RADIUS, Color.web("#1e293b"));
        bg.setStroke(Color.web("#334155"));
        bg.setStrokeWidth(2);

        // MODIFICA 2: Rimosso l'oggetto "Arc redZone" che disegnava il segmento curvo

        // 2. Tacche
        Pane ticksPane = new Pane();
        ticksPane.setPrefSize(SIZE, SIZE);
        for (int i = 0; i <= 10; i++) {
            double angle = START_ANGLE + (i * (MAX_ANGLE / 10.0));
            // Manteniamo le tacche rosse alla fine, ma senza l'arco sotto
            Rectangle tick = new Rectangle(2, 8, (i>=8 ? Color.web("#ef4444") : Color.web("#64748b")));
            tick.setX(SIZE/2 - 1);
            tick.setY(SIZE/2 - RADIUS + 4);
            tick.getTransforms().add(new Rotate(angle, SIZE/2, SIZE/2));
            ticksPane.getChildren().add(tick);
        }

        // 3. Lancetta (Posizionata con il FIX del Pane)
        needle.getPoints().addAll(-3.0, 10.0, 3.0, 10.0, 0.0, -(RADIUS - 15));
        needle.setFill(Color.web("#f59e0b")); // Arancio

        Pane needlePane = new Pane();
        needlePane.setPrefSize(SIZE, SIZE);
        needlePane.setMouseTransparent(true);

        // Posiziona il perno (0,0 della lancetta) al centro esatto del Pane
        needle.setLayoutX(SIZE/2);
        needle.setLayoutY(SIZE/2);

        needleRotate.setPivotX(0);
        needleRotate.setPivotY(0);
        needle.getTransforms().add(needleRotate);
        needlePane.getChildren().add(needle);

        // 4. Cap (Perno centrale)
        Circle cap = new Circle(5, Color.web("#e2e8f0"));

        // 5. Testo
        VBox textCen = new VBox(0, valLbl, unitLbl);
        textCen.setAlignment(Pos.CENTER);
        textCen.setTranslateY(35);
        valLbl.setStyle("-fx-text-fill:#f1f5f9; -fx-font-family:'Monospaced'; -fx-font-weight:bold; -fx-font-size:28px;");
        unitLbl.setStyle("-fx-text-fill:#94a3b8; -fx-font-size:11px; -fx-font-weight:bold;");

        // Rimosso "redZone" dalla lista dei figli
        gauge.getChildren().addAll(bg, ticksPane, textCen, needlePane, cap);

        VBox card = new VBox(4, title, gauge);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color:#0f172a; -fx-background-radius:12; -fx-border-color: #1e293b; -fx-border-width: 1; -fx-border-radius: 12;");

        UIx.lockFixedSize(card, 220, 260);
        root.getChildren().add(card);
    }

    public void setMax(double max){ if (max > 1000) this.maxRpm = max; }

    public void update(Double rpm){
        double v = (rpm == null || Double.isNaN(rpm)) ? 0.0 : Math.max(0, rpm);
        valLbl.setText(String.format(Locale.ROOT, "%.0f", v));

        double pct = Math.min(1.0, Math.max(0, v / maxRpm));
        double targetAngle = START_ANGLE + (pct * MAX_ANGLE);

        // Cambio colore se vicino al limitatore
        if (pct > 0.92) {
            needle.setFill(Color.web("#ef4444")); // Rosso
            valLbl.setTextFill(Color.web("#ef4444"));
        } else {
            needle.setFill(Color.web("#f59e0b")); // Arancio
            valLbl.setTextFill(Color.web("#f1f5f9"));
        }

        if (anim != null) anim.stop();
        anim = new Timeline(new KeyFrame(Duration.millis(80),
                new KeyValue(needleRotate.angleProperty(), targetAngle, Interpolator.EASE_OUT)));
        anim.play();
    }

    public Node getRoot(){ return root; }

    private static class UIx {
        static void lockFixedSize(javafx.scene.layout.Region r, double w, double h) {
            r.setMinSize(w,h); r.setMaxSize(w,h);
        }
    }
}