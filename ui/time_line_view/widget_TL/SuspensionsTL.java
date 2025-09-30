package org.simulator.ui.time_line_view.widget_TL;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.Locale;

import static java.lang.Math.max;
import static java.lang.Math.min;

public final class SuspensionsTL {

    // più compatto
    private static final double CARD_W = 300;
    private static final double CARD_H = 160;

    private final VBox root = new VBox(6);
    private final CheckBox cbTravel = new CheckBox("Corsa (%)");
    private final CheckBox cbRide   = new CheckBox("Ride height (mm)");

    private final Bar fl = new Bar("FL");
    private final Bar fr = new Bar("FR");
    private final Bar rl = new Bar("RL");
    private final Bar rr = new Bar("RR");

    public SuspensionsTL(){
        Label title = new Label("Sospensioni (istantaneo)");
        title.setStyle("-fx-text-fill:#cbd5e1; -fx-font-weight:700;");

        cbTravel.setSelected(true);
        cbRide.setSelected(true); // se preferisci default OFF, metti false
        cbTravel.setStyle("-fx-text-fill:#cbd5e1;");
        cbRide.setStyle("-fx-text-fill:#cbd5e1;");

        HBox toggles = new HBox(10, cbTravel, cbRide);
        toggles.setAlignment(Pos.CENTER_LEFT);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.add(fl.root, 0, 0);
        grid.add(fr.root, 1, 0);
        grid.add(rl.root, 0, 1);
        grid.add(rr.root, 1, 1);

        VBox card = new VBox(6, title, toggles, grid);
        card.setPadding(new Insets(10));
        card.setStyle("-fx-background-color:#0f172a; -fx-background-radius:10;");

        lock(card, CARD_W, CARD_H);
        lock(root, CARD_W, CARD_H);
        root.getChildren().setAll(card);

        cbTravel.setOnAction(e -> setShows());
        cbRide.setOnAction(e -> setShows());
        setShows();
    }

    public Node getRoot(){ return root; }

    /** p* in [0..1]; rh* in mm (può essere NaN). */
    public void update(double pFL, double pFR, double pRL, double pRR,
                       Double rhFL, Double rhFR, Double rhRL, Double rhRR)
    {
        fl.setPercent(s01(pFL));
        fr.setPercent(s01(pFR));
        rl.setPercent(s01(pRL));
        rr.setPercent(s01(pRR));

        fl.setRideHeight(rhFL);
        fr.setRideHeight(rhFR);
        rl.setRideHeight(rhRL);
        rr.setRideHeight(rhRR);
    }

    private void setShows(){
        boolean showTravel = cbTravel.isSelected();
        boolean showRide   = cbRide.isSelected();
        fl.showTravel(showTravel); fr.showTravel(showTravel); rl.showTravel(showTravel); rr.showTravel(showTravel);
        fl.showRide(showRide);     fr.showRide(showRide);     rl.showRide(showRide);     rr.showRide(showRide);
    }

    private static double s01(double v){ return (Double.isFinite(v)? max(0, min(1, v)) : 0); }
    private static void lock(Region r, double w, double h){
        r.setMinWidth(w);  r.setPrefWidth(w);  r.setMaxWidth(w);
        r.setMinHeight(h); r.setPrefHeight(h); r.setMaxHeight(h);
    }

    // ---------- barra compatta con frazione animata ----------
    private static final class Bar {
        final VBox root = new VBox(2);
        final Label title = new Label();
        final StackPane track = new StackPane();
        final Region fill = new Region();
        final Label lblTravel = new Label();
        final Label lblRide   = new Label();

        private final DoubleProperty frac = new SimpleDoubleProperty(0); // 0..1
        private Timeline anim;

        Bar(String name){
            title.setText(name);
            title.setStyle("-fx-text-fill:#cbd5e1; -fx-font-weight:600; -fx-font-size:11px;");

            lblTravel.setStyle("-fx-text-fill:#e5e7eb; -fx-font-size:11px;");
            lblRide.setStyle("-fx-text-fill:#94a3b8; -fx-font-size:11px;");

            // corsia sottile
            track.setMinHeight(8);
            track.setPrefHeight(8);
            track.setMaxHeight(8);
            track.setStyle("-fx-background-color:#334155; -fx-background-radius:5;");

            // fill arancione SOTTILE e non allargabile oltre la pref
            fill.setStyle("-fx-background-color:#f59e0b; -fx-background-radius:5;");
            fill.setMinWidth(0);
            fill.setPrefWidth(0);
            fill.setMaxWidth(Region.USE_PREF_SIZE); // <<< blocca lo StackPane dal farla a tutta larghezza

            // larghezza = track.width * frac (dinamico e sempre aggiornato)
            fill.prefWidthProperty().bind(track.widthProperty().multiply(frac));

            track.getChildren().add(fill);
            StackPane.setAlignment(fill, Pos.CENTER_LEFT);

            HBox values = new HBox(10, lblTravel, lblRide);
            values.setAlignment(Pos.CENTER_LEFT);

            root.getChildren().setAll(title, track, values);
            double colW = (CARD_W - 10 - 10) / 2.0;
            root.setPrefWidth(colW);
        }

        void setPercent(double p){
            if (anim != null) anim.stop();
            anim = new Timeline(
                    new KeyFrame(Duration.millis(140),
                            new KeyValue(frac, p, Interpolator.EASE_BOTH))
            );
            anim.play();
            lblTravel.setText((int)Math.round(p*100) + " %");
        }

        void setRideHeight(Double mm){
            lblRide.setText(!Double.isFinite(mm) ? "--" :
                    String.format(Locale.ROOT, "%.0f mm", mm));
        }

        void showTravel(boolean v){ lblTravel.setManaged(v); lblTravel.setVisible(v); }
        void showRide(boolean v){   lblRide.setManaged(v);   lblRide.setVisible(v);   }
    }
}
