package org.simulator.widget;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;

import java.util.List;
import java.util.Locale;

public final class PedalWidget {

    // Stili Base
    private static final String BG_DARK = "-fx-background-color:#0f172a;";
    private static final String CARD_BASE = "-fx-background-color:#1e293b; -fx-background-radius:8;";

    // Colori Pedali
    private static final String COL_CLUTCH = "#3b82f6"; // Blu
    private static final String COL_BRAKE  = "#ef4444"; // Rosso
    private static final String COL_THROTTLE = "#22c55e"; // Verde

    private PedalWidget(){}

    public static TitledPane build(Lap lap){
        Metrics m = (lap == null) ? new Metrics() : metricsForLap(lap);
        return render(m, "Pedali (Giro)");
    }

    public static TitledPane buildFromLaps(List<Lap> laps){
        Metrics acc = new Metrics();
        int n=0;
        if (laps != null) {
            for (Lap l : laps){
                if (l!=null){ acc.add(metricsForLap(l)); n++; }
            }
        }
        if (n>0) acc.div(n);
        return render(acc, "Pedali (Media Sessione)");
    }

    // --- RENDERING UI ---

    private static TitledPane render(Metrics m, String title){
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setPadding(new Insets(10));
        grid.setStyle(BG_DARK);

        // 3 Colonne uguali (33%)
        ColumnConstraints colC = new ColumnConstraints();
        colC.setPercentWidth(33.33);
        grid.getColumnConstraints().addAll(colC, colC, colC);

        // 1 Riga che riempie l'altezza
        RowConstraints rowC = new RowConstraints();
        rowC.setPercentHeight(100);
        grid.getRowConstraints().add(rowC);

        // Creazione Cards (Clutch -> Brake -> Throttle)
        grid.add(createPedalCard("CLUTCH", COL_CLUTCH, m.clPctAvg, m.clForceAvg, m.clForceMax), 0, 0);
        grid.add(createPedalCard("BRAKE",  COL_BRAKE,  m.brkPctAvg, m.brkForceAvg, m.brkForceMax), 1, 0);
        grid.add(createPedalCard("THROTTLE", COL_THROTTLE, m.thrPctAvg, m.thrForceAvg, m.thrForceMax), 2, 0);

        TitledPane tp = new TitledPane(title, grid);
        tp.setCollapsible(false);
        tp.setAnimated(false);
        tp.setStyle("-fx-base: #0f172a; -fx-box-border: transparent; -fx-text-fill: white; -fx-font-weight:bold;");
        tp.setMaxHeight(Double.MAX_VALUE);

        return tp;
    }

    private static VBox createPedalCard(String name, String colorHex, double pctAvg, double forceAvg, double forceMax) {
        VBox card = new VBox(6);
        card.setAlignment(Pos.BOTTOM_CENTER); // Allinea tutto in basso per far crescere la barra
        card.setPadding(new Insets(10, 6, 10, 6));
        card.setStyle(CARD_BASE + "-fx-border-color:" + colorHex + "; -fx-border-width:0 0 4 0;"); // Bordo colorato sotto

        // Espansione
        card.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        GridPane.setFillHeight(card, true);

        // 1. Header Titolo
        Label lblTitle = new Label(name);
        lblTitle.setStyle("-fx-text-fill:#94a3b8; -fx-font-weight:bold; -fx-font-size:10px;");

        // 2. Visualizzazione Barra (StackPane con sfondo + fill)
        StackPane barTrack = new StackPane();
        barTrack.setPrefWidth(24);
        barTrack.setMaxWidth(24);
        barTrack.setStyle("-fx-background-color:#334155; -fx-background-radius:4;"); // Track grigia
        VBox.setVgrow(barTrack, Priority.ALWAYS); // La barra occupa tutto lo spazio verticale disponibile

        // Calcolo altezza percentuale (0.0 - 1.0)
        double ratio = clamp01((Double.isNaN(pctAvg) ? 0.0 : pctAvg) / 100.0);

        Rectangle barFill = new Rectangle();
        barFill.setWidth(24);
        barFill.setArcWidth(4); barFill.setArcHeight(4);
        barFill.setFill(Color.web(colorHex));

        // Binding altezza barra: ratio * altezza track
        barFill.heightProperty().bind(barTrack.heightProperty().multiply(ratio));

        // Allinea la barra in basso dentro la track
        StackPane.setAlignment(barFill, Pos.BOTTOM_CENTER);
        barTrack.getChildren().add(barFill);

        // 3. Valore Percentuale (Grande)
        Label lblPct = new Label();
        if (Double.isNaN(pctAvg)) lblPct.setText("--");
        else lblPct.setText(String.format(Locale.ROOT, "%.0f%%", pctAvg));
        lblPct.setStyle("-fx-text-fill:#f1f5f9; -fx-font-family:'Monospaced'; -fx-font-weight:bold; -fx-font-size:16px;");

        // 4. Dati Forza (Piccoli)
        VBox forceBox = new VBox(0);
        forceBox.setAlignment(Pos.CENTER);

        // Mostra forza solo se disponibile (> 1N)
        if (!Double.isNaN(forceAvg) && forceAvg > 1.0) {
            Label lAvg = new Label(String.format(Locale.ROOT, "Avg: %.0f N", forceAvg));
            Label lMax = new Label(String.format(Locale.ROOT, "Max: %.0f N", forceMax));
            lAvg.setStyle("-fx-text-fill:#94a3b8; -fx-font-size:9px;");
            lMax.setStyle("-fx-text-fill:#64748b; -fx-font-size:9px;");
            forceBox.getChildren().addAll(lAvg, lMax);
        } else {
            // Placeholder se non c'è forza
            Label lNa = new Label("No Force Data");
            lNa.setStyle("-fx-text-fill:#475569; -fx-font-size:8px;");
            forceBox.getChildren().add(lNa);
        }

        // Ordine inserimento: Titolo in alto, Barra al centro (grow), Dati in basso
        // Usiamo un VBox interno per Titolo e Barra per gestire meglio lo spacing
        card.getChildren().clear();
        card.getChildren().addAll(lblTitle, barTrack, lblPct, forceBox);

        return card;
    }
    // --- LOGICA CALCOLO ---

    private static final class Metrics {
        double thrPctAvg, brkPctAvg, clPctAvg;
        double thrForceAvg, thrForceMax;
        double brkForceAvg, brkForceMax;
        double clForceAvg,  clForceMax;
        int nThr,nBrk,nCl, nTF,nBF,nCF;

        void add(Metrics o){
            thrPctAvg += o.thrPctAvg; if (o.nThr>0) nThr++;
            brkPctAvg += o.brkPctAvg; if (o.nBrk>0) nBrk++;
            clPctAvg  += o.clPctAvg;  if (o.nCl >0) nCl++;

            thrForceAvg += o.thrForceAvg; if (o.nTF>0) nTF++;
            brkForceAvg += o.brkForceAvg; if (o.nBF>0) nBF++;
            clForceAvg  += o.clForceAvg;  if (o.nCF>0) nCF++;

            thrForceMax = Math.max(thrForceMax, o.thrForceMax);
            brkForceMax = Math.max(brkForceMax, o.brkForceMax);
            clForceMax  = Math.max(clForceMax,  o.clForceMax);
        }
        void div(double k){
            if (nThr>0) thrPctAvg/=nThr;
            if (nBrk>0) brkPctAvg/=nBrk;
            if (nCl >0) clPctAvg/=nCl;
            if (nTF>0) thrForceAvg/=nTF;
            if (nBF>0) brkForceAvg/=nBF;
            if (nCF>0) clForceAvg/=nCF;
        }
    }

    private static Metrics metricsForLap(Lap lap){
        Metrics m = new Metrics();
        if (lap == null || lap.samples == null || lap.samples.isEmpty()) return m;

        double sumThr=0,sumBrk=0,sumCl=0;
        int cThr=0,cBrk=0,cCl=0;
        double sumTF=0,sumBF=0,sumCF=0; int cTF=0,cBF=0,cCF=0;
        double maxTF=Double.NaN,maxBF=Double.NaN,maxCF=Double.NaN;

        for (Sample s : lap.samples){
            Double t  = s.values().get(Channel.THROTTLE);
            Double b  = s.values().get(Channel.BRAKE);
            Double c  = s.values().get(Channel.CLUTCH);
            Double tf = s.values().get(Channel.THROTTLE_FORCE);
            Double bf = s.values().get(Channel.BRAKE_FORCE);
            Double cf = s.values().get(Channel.CLUTCH_FORCE);

            // fallback legacy
            if ((bf==null || bf.isNaN()) && s.values().get(Channel.PEDAL_FORCE)!=null){
                bf = s.values().get(Channel.PEDAL_FORCE);
            }

            // ---- POSIZIONI (percentuali) ----
            double thPct = toPercent(t);
            double brPct = toPercent(b);
            double clPct = toPercent(c);

            if (!Double.isNaN(thPct)) { sumThr += thPct; cThr++; }
            if (!Double.isNaN(brPct)) { sumBrk += brPct; cBrk++; }
            if (!Double.isNaN(clPct)) {
                // Mantengo logica inversione originale se necessario per il sim specifico
                sumCl += (100.0 - clPct);
                cCl++;
            }

            // ---- FORZE (N) ----
            if (tf!=null && !tf.isNaN()){ sumTF+=tf; cTF++; maxTF = Double.isNaN(maxTF)? tf: Math.max(maxTF, tf); }
            if (bf!=null && !bf.isNaN()){ sumBF+=bf; cBF++; maxBF = Double.isNaN(maxBF)? bf: Math.max(maxBF, bf); }
            if (cf!=null && !cf.isNaN()){ sumCF+=cf; cCF++; maxCF = Double.isNaN(maxCF)? cf: Math.max(maxCF, cf); }
        }

        if (cThr>0){ m.thrPctAvg = sumThr/cThr; m.nThr=cThr; }
        if (cBrk>0){ m.brkPctAvg = sumBrk/cBrk; m.nBrk=cBrk; }
        if (cCl >0){ m.clPctAvg  = sumCl /cCl;  m.nCl =cCl;  }

        if (cTF>0){ m.thrForceAvg = sumTF/cTF; m.thrForceMax = Double.isNaN(maxTF)?0:maxTF; m.nTF=cTF; }
        if (cBF>0){ m.brkForceAvg = sumBF/cBF; m.brkForceMax = Double.isNaN(maxBF)?0:maxBF; m.nBF=cBF; }
        if (cCF>0){ m.clForceAvg  = sumCF/cCF; m.clForceMax  = Double.isNaN(maxCF)?0:maxCF;  m.nCF=cCF; }

        return m;
    }

    private static double toPercent(Double v){
        if (v == null || v.isNaN() || v.isInfinite()) return Double.NaN;
        double x = v;
        if (x <= 1.0001) x *= 100.0;
        else if (x > 100.001 && x <= 10000.0) x /= 100.0;
        if (x < 0) x = 0; else if (x > 100) x = 100;
        return x;
    }

    private static double clamp01(double x){
        if (Double.isNaN(x)) return 0.0;
        return Math.max(0.0, Math.min(1.0, x));
    }
}