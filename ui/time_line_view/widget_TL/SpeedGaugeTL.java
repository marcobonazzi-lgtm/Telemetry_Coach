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
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

import java.util.Locale;

public final class SpeedGaugeTL {

    private static final double SIZE = 220;
    private static final double RADIUS = 80;
    private static final double START_ANGLE = -135;
    private static final double MAX_ANGLE = 270;

    private final VBox root = new VBox(0);
    private final Label valLbl = new Label("0");
    private final Label unitLbl = new Label("km/h");

    private final Polygon needle = new Polygon();
    private final Rotate needleRotate = new Rotate(START_ANGLE);

    private Timeline anim;
    private double maxVal = 300.0;

    public SpeedGaugeTL(){
        Label title = new Label("Speed");
        title.setStyle("-fx-text-fill:#ffffff; -fx-font-weight:800; -fx-font-size:14px;");

        StackPane gauge = new StackPane();
        gauge.setMinSize(SIZE, SIZE);
        gauge.setMaxSize(SIZE, SIZE);

        // 1. Sfondo
        Circle bg = new Circle(RADIUS, Color.web("#1e293b"));
        bg.setStroke(Color.web("#334155"));
        bg.setStrokeWidth(2);

        // 2. Tacche
        Pane ticksPane = new Pane();
        ticksPane.setPrefSize(SIZE, SIZE);
        for (int i = 0; i <= 10; i++) {
            double angle = START_ANGLE + (i * (MAX_ANGLE / 10.0));
            Rectangle tick = new Rectangle(2, 8, Color.web("#64748b"));
            tick.setX(SIZE/2 - 1);
            tick.setY(SIZE/2 - RADIUS + 4);
            tick.getTransforms().add(new Rotate(angle, SIZE/2, SIZE/2));
            ticksPane.getChildren().add(tick);
        }

        // 3. Lancetta (FIX: Inserita in un Pane dedicato per coordinate assolute)
        needle.getPoints().addAll(
                -3.0,  10.0,
                3.0,  10.0,
                0.0, - (RADIUS - 15)
        );
        needle.setFill(Color.web("#3b82f6")); // Azzurro

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

        // 4. Cap Perno
        Circle cap = new Circle(5, Color.web("#e2e8f0"));
        cap.setEffect(new javafx.scene.effect.DropShadow(5, Color.BLACK));

        // 5. Testo
        VBox textCen = new VBox(0, valLbl, unitLbl);
        textCen.setAlignment(Pos.CENTER);
        textCen.setTranslateY(35);
        valLbl.setStyle("-fx-text-fill:#f1f5f9; -fx-font-family:'Monospaced'; -fx-font-weight:bold; -fx-font-size:32px;");
        unitLbl.setStyle("-fx-text-fill:#94a3b8; -fx-font-size:12px;");

        // Ordine di aggiunta allo StackPane
        gauge.getChildren().addAll(bg, ticksPane, textCen, needlePane, cap);

        VBox card = new VBox(4, title, gauge);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color:#0f172a; -fx-background-radius:12; -fx-border-color: #1e293b; -fx-border-width: 1; -fx-border-radius: 12;");

        UIx.lockFixedSize(card, 220, 260);
        root.getChildren().add(card);
    }

    public void setMax(double m){ this.maxVal = m; }

    public void update(Double v){
        if (v == null || Double.isNaN(v)) v = 0.0;
        valLbl.setText(String.format(Locale.ROOT, "%.0f", v));

        double pct = Math.min(1.0, Math.max(0, v / maxVal));
        double targetAngle = START_ANGLE + (pct * MAX_ANGLE);

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