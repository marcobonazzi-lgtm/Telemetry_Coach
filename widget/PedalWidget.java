package org.simulator.widget;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;

import java.util.List;

public final class PedalWidget {
    private static final double REF_BRAKE_FORCE_N = 800.0;
    private static final double REF_THR_FORCE_N   = 300.0; // opzionale: se avrai la forza gas
    private static final double REF_CL_FORCE_N    = 300.0; // opzionale: se avrai la forza frizione

    private PedalWidget(){}

    public static Node build(Lap lap){
        if (lap == null) return placeholder("Nessun giro");
        Metrics m = metricsForLap(lap);
        return render(m, "Giro");
    }
    public static Node buildFromLaps(List<Lap> laps){
        if (laps == null || laps.isEmpty()) return placeholder("Nessuna sessione");
        Metrics acc = new Metrics();
        int n=0;
        for (Lap l : laps){ if (l!=null){ acc.add(metricsForLap(l)); n++; } }
        if (n>0) acc.div(n);
        return render(acc, "Media sessione");
    }

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

            // fallback legacy: solo freno PEDAL_FORCE
            if ((bf==null || bf.isNaN()) && s.values().get(Channel.PEDAL_FORCE)!=null){
                bf = s.values().get(Channel.PEDAL_FORCE);
            }

            // ---- POSIZIONI (percentuali) ----
            double thPct = toPercent(t);           // 0..100 o NaN
            double brPct = toPercent(b);
            double clPct = toPercent(c);
            if (!Double.isNaN(thPct)) { sumThr += thPct;       cThr++; }
            if (!Double.isNaN(brPct)) { sumBrk += brPct;       cBrk++; }
            if (!Double.isNaN(clPct)) { sumCl  += (100.0 - clPct); cCl++; } // <-- INVERTI qui

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

    /** Converte un valore pedale in percentuale 0..100, accetta 0..1, 0..100, 0..10000. */
    private static double toPercent(Double v){
        if (v == null || v.isNaN() || v.isInfinite()) return Double.NaN;
        double x = v;
        if (x <= 1.0001)                 x *= 100.0;   // 0..1 -> %
        else if (x > 100.001 && x <= 10000.0) x /= 100.0;   // 0..10000 -> %
        // altrimenti è già 0..100
        if (x < 0) x = 0; else if (x > 100) x = 100;
        return x;
    }


    private static Node render(Metrics m, String subtitle){
        VBox clutch = pedalBox("CL", m.clPctAvg, m.clForceAvg, m.clForceMax, REF_CL_FORCE_N);
        VBox brake  = pedalBox("BR", m.brkPctAvg, m.brkForceAvg, m.brkForceMax, REF_BRAKE_FORCE_N);
        VBox thr    = pedalBox("TH", m.thrPctAvg, m.thrForceAvg, m.thrForceMax, REF_THR_FORCE_N);

        HBox row = new HBox(16, clutch, brake, thr);
        row.setAlignment(Pos.CENTER);
        row.setPadding(new Insets(8));
        row.setFillHeight(true);

        Label title = new Label("Movimento pedali  •  " + subtitle);
        title.setStyle("-fx-font-weight: bold; -fx-opacity: 0.8;");

        VBox card = new VBox(6, title, row);
        card.setPadding(new Insets(8));
        card.setBackground(new Background(new BackgroundFill(Color.web("#f6f6f8"), new CornerRadii(8), Insets.EMPTY)));
        card.setBorder(new Border(new BorderStroke(Color.web("#d6d6de"),
                BorderStrokeStyle.SOLID, new CornerRadii(8), new BorderWidths(1))));
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private static VBox pedalBox(String label, double posAvgPct, double forceAvgN, double forceMaxN, double refN){
        double posNorm = clamp01((Double.isNaN(posAvgPct)?0.0:posAvgPct)/100.0);
        double colorNorm = (!Double.isNaN(forceAvgN) && forceAvgN>0 && refN>0) ? clamp01(forceAvgN/refN) : posNorm;
        Color c = heatColor(colorNorm);

        Rectangle body = new Rectangle(44, 100);
        body.setArcWidth(10); body.setArcHeight(10);
        body.setFill(c.deriveColor(0,1,1,0.9));
        body.setStroke(Color.web("#444"));
        body.setStrokeWidth(1);

        Label head = new Label(label);
        head.setStyle("-fx-font-weight: bold; -fx-text-fill: #222;");

        Label posLbl  = new Label("pos. media: " + (Double.isNaN(posAvgPct) ? "—" : String.format("%.0f%%", clamp01(posAvgPct/100.0)*100.0)));
        posLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #333;");

        String favg = (forceAvgN > 0) ? String.format("%.0f N", forceAvgN) : "—";
        String fmax = (forceMaxN > 0) ? String.format("%.0f N", forceMaxN) : "—";
        Label fAvgLbl = new Label("forza media: " + favg);
        Label fMaxLbl = new Label("forza max: "   + fmax);
        fAvgLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");
        fMaxLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");

        VBox v = new VBox(4, head, body, posLbl, fAvgLbl, fMaxLbl);
        v.setAlignment(Pos.CENTER);
        v.setPadding(new Insets(2,4,2,4));
        return v;
    }

    private static Node placeholder(String s){
        Label l = new Label(s);
        l.setStyle("-fx-opacity: .8;");
        VBox v = new VBox(l); v.setAlignment(Pos.CENTER); v.setPadding(new Insets(8));
        v.setBackground(new Background(new BackgroundFill(Color.web("#f6f6f8"), new CornerRadii(8), Insets.EMPTY)));
        v.setBorder(new Border(new BorderStroke(Color.web("#d6d6de"),
                BorderStrokeStyle.SOLID, new CornerRadii(8), new BorderWidths(1))));
        return v;
    }

    private static double clamp01(double x){ if (Double.isNaN(x)) return 0.0; return x<0?0:(x>1?1:x); }
    private static Color heatColor(double x){
        x = clamp01(x);
        if (x < 0.5){ double t = x/0.5; return blend(Color.web("#35b36b"), Color.web("#d5b60a"), t); }
        double t = (x-0.5)/0.5; return blend(Color.web("#d5b60a"), Color.web("#d34b4b"), t);
    }
    private static Color blend(Color a, Color b, double t){
        return new Color(a.getRed()*(1-t)+b.getRed()*t, a.getGreen()*(1-t)+b.getGreen()*t, a.getBlue()*(1-t)+b.getBlue()*t, 1.0);
    }
}
