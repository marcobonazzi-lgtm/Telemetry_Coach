package org.simulator.ui.time_line_view.widget_TL;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.Locale;

public final class TyreWearTL {
    // dimensioni preferite ma NON bloccate (si adatta al contenitore)
    private static final double PREF_W = 420;

    private final VBox root = new VBox(8);
    private final CheckBox cbTemps = new CheckBox("°C (I/M/O)");
    private final CheckBox cbPress = new CheckBox("Pressione");
    private final CheckBox cbLoad  = new CheckBox("Carico");

    private final TyreBox fl = new TyreBox("FL");
    private final TyreBox fr = new TyreBox("FR");
    private final TyreBox rl = new TyreBox("RL");
    private final TyreBox rr = new TyreBox("RR");

    public TyreWearTL(){
        Label title = new Label("Consumo gomme (istantaneo)");
        title.setStyle("-fx-text-fill:#e5e7eb; -fx-font-weight:700;");

        HBox opts = new HBox(12, cbTemps, cbPress, cbLoad);
        opts.setAlignment(Pos.CENTER_LEFT);
        opts.setPadding(new Insets(2,0,4,0));
        String cbStyle = "-fx-text-fill:#cbd5e1;";
        cbTemps.setStyle(cbStyle); cbPress.setStyle(cbStyle); cbLoad.setStyle(cbStyle);
        cbTemps.setSelected(true); // default: mostro solo temperature

        GridPane grid = new GridPane();
        grid.setHgap(18); grid.setVgap(10);
        grid.add(fl.root, 0, 0); grid.add(fr.root, 1, 0);
        grid.add(rl.root, 0, 1); grid.add(rr.root, 1, 1);
        ColumnConstraints c1 = new ColumnConstraints();
        ColumnConstraints c2 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS); c2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().setAll(c1, c2);

        VBox box = new VBox(8, title, opts, grid);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color:#0f172a; -fx-background-radius:10;");

        root.getChildren().setAll(box);
        root.setFillWidth(true);
        root.setPrefWidth(PREF_W);
        VBox.setVgrow(box, Priority.NEVER);

        cbTemps.setOnAction(e -> refreshLabels());
        cbPress.setOnAction(e -> refreshLabels());
        cbLoad .setOnAction(e -> refreshLabels());
    }

    public Node getRoot(){ return root; }

    // ---------- Update API ----------
    /** Grip/vita gomma: 0..1 o 0..100 (alto = gomma fresca). */
    public void updateWear(Double flGrip, Double frGrip, Double rlGrip, Double rrGrip){
        fl.setWearPct(gripToLifePct(flGrip));
        fr.setWearPct(gripToLifePct(frGrip));
        rl.setWearPct(gripToLifePct(rlGrip));
        rr.setWearPct(gripToLifePct(rrGrip));
    }
    public void updateTempIMO(
            Double flI, Double flM, Double flO,
            Double frI, Double frM, Double frO,
            Double rlI, Double rlM, Double rlO,
            Double rrI, Double rrM, Double rrO){
        fl.setTemp(flI, flM, flO);
        fr.setTemp(frI, frM, frO);
        rl.setTemp(rlI, rlM, rlO);
        rr.setTemp(rrI, rrM, rrO);
        refreshLabels();
    }
    public void updatePress(Double flPsi, Double frPsi, Double rlPsi, Double rrPsi){
        fl.setPress(flPsi); fr.setPress(frPsi); rl.setPress(rlPsi); rr.setPress(rrPsi);
        refreshLabels();
    }
    public void updateLoad(Double flN, Double frN, Double rlN, Double rrN){
        fl.setLoad(flN); fr.setLoad(frN); rl.setLoad(rlN); rr.setLoad(rrN); // <-- fix refuso
        refreshLabels();
    }

    private void refreshLabels(){
        fl.refreshText(cbTemps.isSelected(), cbPress.isSelected(), cbLoad.isSelected());
        fr.refreshText(cbTemps.isSelected(), cbPress.isSelected(), cbLoad.isSelected());
        rl.refreshText(cbTemps.isSelected(), cbPress.isSelected(), cbLoad.isSelected());
        rr.refreshText(cbTemps.isSelected(), cbPress.isSelected(), cbLoad.isSelected());
    }

    // ---------- TyreBox ----------
    private static final class TyreBox {
        final VBox root = new VBox(4);
        final Region tyre = new Region();   // quadrato pieno
        final Label corner = new Label();   // FL/FR/RL/RR (sotto al quadrato)
        final Label line1 = new Label();    // Consumo (vita residua)
        final Label line2 = new Label();    // Temps/Press/Load

        Double tI, tM, tO, psi, load;

        TyreBox(String name){
            tyre.setMinSize(22, 22);
            tyre.setPrefSize(22, 22);
            tyre.setMaxSize(22, 22);
            tyre.setStyle("-fx-background-radius:4;");

            corner.setText(name);
            corner.setStyle("-fx-text-fill:#cbd5e1; -fx-font-weight:600;");

            styleSmall(line1);
            styleSmall(line2);
            line2.setWrapText(true);

            // ordine: quadrato → etichetta ruota → righe testo
            root.getChildren().addAll(tyre, corner, line1, line2);
            root.setAlignment(Pos.TOP_LEFT);
        }

        // --- setWearPct: gestione n/d e colore corretto (verde=100% vita, rosso=0) ---
        void setWearPct(double lifePctInput){
            if (Double.isNaN(lifePctInput)) {
                tyre.setStyle("-fx-background-color:#64748b; -fx-background-radius:4;"); // grigio = n/d
                line1.setText("Consumo n/d");
                return;
            }
            double life = clamp01(lifePctInput/100.0)*100.0; // 0..100
            double wearT = 1.0 - (life/100.0);               // 0=verde → 1=rosso
            Color c = colorForWear(wearT*100.0);
            tyre.setStyle("-fx-background-color:" + toHex(c) + "; -fx-background-radius:4;");
            line1.setText("Consumo " + asInt(life) + " %");  // mostra la "vita gomma" residua
            // se preferisci "Usura", usa: line1.setText("Usura " + asInt(100 - life) + " %");
        }

        void setTemp(Double i, Double m, Double o){ tI=i; tM=m; tO=o; }
        void setPress(Double p){ psi = p; }
        void setLoad(Double n){ load = n; }

        void refreshText(boolean showT, boolean showP, boolean showL){
            StringBuilder sb = new StringBuilder();
            if (showT && finite(tI) && finite(tM) && finite(tO))
                sb.append("I/M/O: ").append(asInt(tI)).append(" | ").append(asInt(tM)).append(" | ").append(asInt(tO)).append(" °C   ");
            if (showP && finite(psi))
                sb.append("P ").append(fmt(psi,1)).append(" psi   ");
            if (showL && finite(load))
                sb.append("L ").append(asInt(load)).append(" N");

            String txt = sb.toString().trim();
            line2.setManaged(!txt.isEmpty());
            line2.setVisible(!txt.isEmpty());
            line2.setText(txt);
        }
    }

    // ---------- helpers ----------
    private static void styleSmall(Label l){
        l.setStyle("-fx-text-fill:#e5e7eb; -fx-opacity:.9; -fx-font-size:11px;");
    }
    private static boolean finite(Double v){ return v!=null && !v.isNaN() && !v.isInfinite(); }
    private static String fmt(double v,int d){ return String.format(Locale.ROOT, "%."+d+"f", v); }
    private static int asInt(double v){ return (int)Math.round(v); }
    private static double clamp01(double v){ return Math.max(0, Math.min(1, v)); }

    /** Converte il canale di grip/vita gomma in percentuale di vita residua (0..100).
     *  Accetta input 0..1 o 0..100; valori 0 o mancanti => NaN (n/d). */
    private static double gripToLifePct(Double grip){
        if (!finite(grip)) return Double.NaN;
        double g = grip;
        if (g <= 1.0001) g *= 100.0;          // supporta 0..1
        if (g <= 0.0001) return Double.NaN;   // ACTI esporta 0 => trattalo come n/d
        return Math.max(0, Math.min(100, g)); // 0..100 (alto = gomma fresca)
    }

    /** Colore per la "usura" (0 = verde, 100 = rosso). */
    private static Color colorForWear(double pct){
        double t = clamp01(pct/100.0);
        Color green = Color.web("#10b981");
        Color yellow= Color.web("#f59e0b");
        Color red   = Color.web("#ef4444");
        if (t<=0.5) return green.interpolate(yellow, t/0.5);
        return yellow.interpolate(red, (t-0.5)/0.5);
    }
    private static String toHex(Color c){
        return String.format("#%02x%02x%02x",
                (int)Math.round(c.getRed()*255),
                (int)Math.round(c.getGreen()*255),
                (int)Math.round(c.getBlue()*255));
    }
}
