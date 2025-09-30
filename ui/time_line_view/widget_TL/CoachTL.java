package org.simulator.ui.time_line_view.widget_TL;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.simulator.canale.Channel;
import org.simulator.ui.SeriesBundle;
import org.simulator.ui.time_line_view.Signals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static java.lang.Math.*;

/**
 * Coach istantaneo completo (adattivo al tipo veicolo).
 *
 * NOTE DI IMPLEMENTAZIONE
 * - Mantiene la stessa logica funzionale del file fornito (tips Guida/Setup/Meteo, profilo veicolo,
 *   target dinamici gomme/freni, UI con filtri).
 * - Per alleggerire la classe principale, sono stati introdotti NESTED HELPERS statici:
 *      • VehicleProfileDetector  → rilevazione Category/Drivetrain/Powertrain
 *      • Targets                 → range target gomme/freni per categoria
 *      • TipBuilder              → regole che generano i suggerimenti
 *      • UIx / Numx              → util di UI e numeriche
 * - Nessuna dipendenza esterna aggiunta, stesso package e API pubbliche invarianti.
 */
public final class CoachTL {

    // —— fallback generali (restano come default di sicurezza) ——
    private static final double COAST_THR    = 0.05;  // 0..1
    private static final double BRK_ON       = 0.20;
    private static final double TYRE_OK_MIN_FALLBACK  = 80;    // °C
    private static final double TYRE_OK_MAX_FALLBACK  = 100;   // °C
    private static final double BRAKE_OK_MIN_FALLBACK = 150;   // °C
    private static final double BRAKE_OK_MAX_FALLBACK = 300;   // °C
    private static final double WIND_STRONG  = 12.0;  // m/s circa

    private static final double CARD_W = 560;
    private static final double CARD_H = 200;

    // UI
    private final VBox  root  = new VBox(6);
    private final Label title = new Label("Coach (istantaneo)");
    private final VBox  list  = new VBox(8);
    private final ScrollPane scroll = new ScrollPane(list);
    private final CheckBox cbGuida = new CheckBox("Guida");
    private final CheckBox cbSetup = new CheckBox("Setup");
    private final CheckBox cbMeteo = new CheckBox("Meteo");
    private final CheckBox cbStats = new CheckBox("Statistiche");

    // stato per refresh con i filtri
    private double lastX = Double.NaN;
    private Signals lastS = null;
    private SeriesBundle lastSB = null;

    // ===== Profilo veicolo (come nel codice originale) =====
    private enum Category { FORMULA, PROTOTYPE, GT, ROAD, OTHER }
    private enum Drivetrain { RWD, FWD, AWD, UNKNOWN }
    private enum Powertrain { NA, TURBO, HYBRID, UNKNOWN }
    private record VehicleProfile(Category cat, Drivetrain dt, Powertrain pt) {}
    private static record Range(double min, double max) {}

    public CoachTL(){
        title.setStyle("-fx-text-fill:#e5e7eb; -fx-font-weight:bold;");

        // Toolbar filtri
        cbGuida.setSelected(true);
        cbSetup.setSelected(true);
        cbMeteo.setSelected(true);
        cbStats.setSelected(true);
        String cbStyle = "-fx-text-fill:#cbd5e1;";
        cbGuida.setStyle(cbStyle); cbSetup.setStyle(cbStyle);
        cbMeteo.setStyle(cbStyle); cbStats.setStyle(cbStyle);

        HBox filters = new HBox(10, cbGuida, cbSetup, cbMeteo, cbStats);
        filters.setPadding(new Insets(0,0,2,0));

        // ScrollPane trasparente
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPannable(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        list.setStyle("-fx-background-color: transparent;");

        VBox box = new VBox(6, title, filters, scroll);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color:#0f172a; -fx-background-radius:10;");

        UIx.lockFixedSize(box, CARD_W, CARD_H);
        scroll.setPrefViewportHeight(CARD_H - 72); // spazio per i filtri

        root.getChildren().setAll(box);
        UIx.lockFixedSize(root, CARD_W, CARD_H);

        Platform.runLater(() -> {
            Node vp = scroll.lookup(".viewport");
            if (vp instanceof Region r) r.setStyle("-fx-background-color: transparent;");
        });

        // refresh on toggle
        cbGuida.setOnAction(e -> refresh());
        cbSetup.setOnAction(e -> refresh());
        cbMeteo.setOnAction(e -> refresh());
        cbStats.setOnAction(e -> refresh());
    }

    public Node getRoot(){ return root; }

    // ========================================================================
    // UPDATE
    // ========================================================================
    public void update(double xVal, Signals s, SeriesBundle sb){
        if (s == null || sb == null) return;
        this.lastX = xVal; this.lastS = s; this.lastSB = sb;
        rebuild(xVal, s, sb);
    }

    private void refresh(){
        if (lastS != null && lastSB != null && Double.isFinite(lastX)){
            rebuild(lastX, lastS, lastSB);
        }
    }

    private void rebuild(double xVal, Signals s, SeriesBundle sb){
        List<String> tipsGuida = new ArrayList<>();
        List<String> tipsSetup = new ArrayList<>();
        List<String> tipsMeteo = new ArrayList<>();

        // —— Profilo veicolo & target dinamici ——
        VehicleProfile vp = VehicleProfileDetector.detect(s, sb);
        Range tyreTarget  = Targets.tyreTargetFor(vp.cat());
        Range brakeTarget = Targets.brakeTargetFor(vp.cat());

        // —— Letture core (istantanee) ——
        double thr01  = s.throttle01(xVal);
        double brk01  = s.brake01(xVal);
        double clu01p = s.clutch01Pedal(xVal);
        double ffb01  = s.ffb01(xVal);

        double steer  = rd(s, sb, Channel.STEER_ANGLE,  xVal);
        double speed  = rd(s, sb, Channel.SPEED,        xVal);
        double rpm    = rd(s, sb, Channel.ENGINE_RPM,   xVal);
        double rpmMax = Numx.maxFinite(sb.rpm);

        // gomme core/freni
        Double tFL = s.tyreTemp("FL", xVal), tFR = s.tyreTemp("FR", xVal),
                tRL = s.tyreTemp("RL", xVal), tRR = s.tyreTemp("RR", xVal);
        Double btFL = s.brakeTemp("FL", xVal);
        Double btFR = s.brakeTemp("FR", xVal);
        Double btRL = s.brakeTemp("RL", xVal);
        Double btRR = s.brakeTemp("RR", xVal);

        // seat
        double seatL = s.seatForce01("LEFT", xVal);
        double seatR = s.seatForce01("RIGHT", xVal);
        double seatRear = s.seatForce01("REAR", xVal);

        // derivate locali
        double dx   = localDx(sb);
        double thrL = s.throttle01(xVal - dx), thrR = s.throttle01(xVal + dx);
        double brkL = s.brake01  (xVal - dx), brkR = s.brake01  (xVal + dx);
        double stL  = interpAround(sb.x, sb.steering, xVal, -0.05);
        double stR  = interpAround(sb.x, sb.steering, xVal, +0.05);

        // —— Guida: overlap / coasting / sequencing ——
        TipBuilder.coastingAndOverlap(tipsGuida, thr01, brk01, speed);
        TipBuilder.ffbAndSteer(tipsGuida, ffb01, steer, stL, stR, speed);
        TipBuilder.throttleSmoothness(tipsGuida, thrL, thrR, thr01, steer);
        TipBuilder.rpmAndGearing(tipsGuida, rpm, rpmMax, speed, thr01, steer, ffb01);
        TipBuilder.clutchUse(tipsGuida, clu01p, thr01);

        // Sistemi durante la guida
        TipBuilder.driverAids(tipsGuida, s, xVal, brk01, thr01);
        TipBuilder.drsErsBoost(tipsGuida, s, xVal, steer, speed, thr01);

        // Setup: engine brake, bias, gomme/freni/press/load, sospensioni, danni
        TipBuilder.setupEngineBrakeBias(tipsSetup, s, xVal, brk01, steer);
        TipBuilder.tyreAndPressure(tipsSetup, tFL, tFR, tRL, tRR, tyreTarget, s, xVal);
        TipBuilder.thermalAsymmetry(tipsSetup, tFL, tFR, tRL, tRR);
        TipBuilder.slipBalance(tipsGuida, s, xVal, vp.dt());
        TipBuilder.brakeTemps(tipsSetup, btFL, btFR, btRL, btRR, brakeTarget);
        TipBuilder.lowSpeedLock(tipsGuida, brk01, speed);
        TipBuilder.suspKerb(tipsSetup, s, xVal);
        TipBuilder.seatImbalance(tipsGuida, seatL, seatR, seatRear, steer);

        // meteo / grip
        TipBuilder.weatherGrip(tipsMeteo, s, xVal);

        // danni (setup)
        TipBuilder.damage(tipsSetup, s, xVal);

        TipBuilder.positiveFeedback(tipsGuida, thr01, brk01, thrL, thrR, stL, stR, ffb01);


        // ===== Render UI =====================================================
        List<Node> nodes = new ArrayList<>();
        if (cbGuida.isSelected() && !tipsGuida.isEmpty()){
            nodes.add(UIx.sectionHeader("Guida"));
            tipsGuida.forEach(t -> nodes.add(UIx.bullet(t, CARD_W)));
        }
        if (cbSetup.isSelected() && !tipsSetup.isEmpty()){
            nodes.add(UIx.sectionHeader("Setup"));
            tipsSetup.forEach(t -> nodes.add(UIx.bullet(t, CARD_W)));
        }
        if (cbMeteo.isSelected() && !tipsMeteo.isEmpty()){
            nodes.add(UIx.sectionHeader("Meteo"));
            tipsMeteo.forEach(t -> nodes.add(UIx.bullet(t, CARD_W)));
        }
        if (nodes.isEmpty()) nodes.add(UIx.bullet("Tutto regolare in questo istante.", CARD_W));

        if (cbStats.isSelected()){
            nodes.add(UIx.sectionHeader("Statistiche (istantanee)"));
            // profilo veicolo + target attivi
            nodes.add(UIx.stat("Vehicle: " + vp.cat() + " / " + vp.dt() + " / " + vp.pt(), CARD_W));
            nodes.add(UIx.stat(String.format(Locale.ROOT, "Tyre target: %.0f–%.0f °C | Brake target: %.0f–%.0f °C",
                    tyreTarget.min, tyreTarget.max, brakeTarget.min, brakeTarget.max), CARD_W));
            nodes.addAll(statLines(xVal, s, sb));
        }

        list.getChildren().setAll(nodes);
    }

    // ========================================================================
    // Statistiche (come originale, con util estratte)
    // ========================================================================
    private List<Node> statLines(double x, Signals s, SeriesBundle sb){
        List<Node> out = new ArrayList<>();
        // base
        double speed  = rd(s, sb, Channel.SPEED, x);
        double rpm    = rd(s, sb, Channel.ENGINE_RPM, x);
        double steer  = rd(s, sb, Channel.STEER_ANGLE, x);

        out.add(UIx.stat("Speed: " + (Double.isFinite(speed) ? (int) round(speed) + " km/h" : "n/d"), CARD_W));
        out.add(UIx.stat("RPM: " + (Double.isFinite(rpm) ? (int) round(rpm) : "n/d"), CARD_W));
        Double gear = s.value(Channel.GEAR, x);
        out.add(UIx.stat("Gear: " + (Numx.isFinite(gear) ? (gear%1==0 ? Integer.toString((int) Math.round(gear)) : Numx.fmt(gear,0)) : "n/d"), CARD_W));

        out.add(UIx.stat("Throttle: " + Numx.pct(s.throttle01(x)), CARD_W));
        out.add(UIx.stat("Brake: "    + Numx.pct(s.brake01(x)), CARD_W));
        out.add(UIx.stat("Clutch: "   + Numx.pct(s.clutch01Pedal(x)), CARD_W));
        out.add(UIx.stat("Steer: "    + (Double.isFinite(steer) ? Numx.fmt(steer,0) + " °" : "n/d"), CARD_W));
        out.add(UIx.stat("FFB: "      + Numx.pct(s.ffb01(x)), CARD_W));

        // g-forces / chassis
        addStat(out, "g Long",  s.value(Channel.CG_ACCEL_LONGITUDINAL, x), v -> Numx.fmt(v,2) + " g");
        addStat(out, "g Lat",   s.value(Channel.CG_ACCEL_LATERAL, x),      v -> Numx.fmt(v,2) + " g");
        addStat(out, "Yaw rate",s.value(Channel.CHASSIS_YAW_RATE, x),      v -> Numx.fmt(v,2) + " rad/s");

        // sistemi
        addStat(out, "ABS active", s.value(Channel.ABS_ACTIVE, x), v -> Numx.onoff(v));
        addStat(out, "TC active",  s.value(Channel.TC_ACTIVE, x),  v -> Numx.onoff(v));
        addStat(out, "DRS",        s.value(Channel.DRS_ACTIVE, x), v -> Numx.onoff(v) + (Numx.isOn(s.value(Channel.DRS_AVAILABLE, x)) ? " (avail)" : ""));
        addStat(out, "Brake bias", s.value(Channel.BRAKE_BIAS, x), v -> Numx.fmt(v,1) + " %");

        addStat(out, "ERS power",    s.value(Channel.ERS_POWER_LEVEL, x),    v -> Numx.fmt(v,0));
        addStat(out, "ERS recovery", s.value(Channel.ERS_RECOVERY_LEVEL, x), v -> Numx.fmt(v,0));
        addStat(out, "KERS charge",  s.value(Channel.KERS_CHARGE, x),        v -> Numx.fmt(v,0));
        addStat(out, "Boost",        s.value(Channel.TURBO_BOOST, x),        v -> Numx.fmt(v,2));

        // gomme
        addStat(out, "Tyre core FL", s.tyreTemp("FL", x), v -> Numx.fmt(v,0) + " °C");
        addStat(out, "Tyre core FR", s.tyreTemp("FR", x), v -> Numx.fmt(v,0) + " °C");
        addStat(out, "Tyre core RL", s.tyreTemp("RL", x), v -> Numx.fmt(v,0) + " °C");
        addStat(out, "Tyre core RR", s.tyreTemp("RR", x), v -> Numx.fmt(v,0) + " °C");

        addStat(out, "Press FL", s.value(Channel.TIRE_PRESSURE_FL, x), v -> Numx.fmt(v,1) + " psi");
        addStat(out, "Press FR", s.value(Channel.TIRE_PRESSURE_FR, x), v -> Numx.fmt(v,1) + " psi");
        addStat(out, "Press RL", s.value(Channel.TIRE_PRESSURE_RL, x), v -> Numx.fmt(v,1) + " psi");
        addStat(out, "Press RR", s.value(Channel.TIRE_PRESSURE_RR, x), v -> Numx.fmt(v,1) + " psi");

        addStat(out, "Load FL", s.value(Channel.TIRE_LOAD_FL, x), v -> Numx.fmt(v,0) + " N");
        addStat(out, "Load FR", s.value(Channel.TIRE_LOAD_FR, x), v -> Numx.fmt(v,0) + " N");
        addStat(out, "Load RL", s.value(Channel.TIRE_LOAD_RL, x), v -> Numx.fmt(v,0) + " N");
        addStat(out, "Load RR", s.value(Channel.TIRE_LOAD_RR, x), v -> Numx.fmt(v,0) + " N");

        // freni
        addStat(out, "Brake FL", s.brakeTemp("FL", x), v -> Numx.fmt(v,0) + " °C");
        addStat(out, "Brake FR", s.brakeTemp("FR", x), v -> Numx.fmt(v,0) + " °C");
        addStat(out, "Brake RL", s.brakeTemp("RL", x), v -> Numx.fmt(v,0) + " °C");
        addStat(out, "Brake RR", s.brakeTemp("RR", x), v -> Numx.fmt(v,0) + " °C");

        // meteo / pista
        addStat(out, "Air temp",  s.value(Channel.AIR_TEMP, x),    v -> Numx.fmt(v,1) + " °C");
        addStat(out, "Road temp", s.value(Channel.ROAD_TEMP, x),   v -> Numx.fmt(v,1) + " °C");
        addStat(out, "Wind",      s.value(Channel.WIND_SPEED, x),  v -> Numx.fmt(v,1));
        addStat(out, "Wind dir",  s.value(Channel.WIND_DIRECTION, x), v -> Numx.fmt(v,0) + " °");
        addStat(out, "Surface grip", s.value(Channel.SURFACE_GRIP, x), v -> Numx.fmt(v,2));

        return out;
    }

    private static void addStat(List<Node> out, String name, Double v, java.util.function.Function<Double,String> fmtFun){
        if (Numx.isFinite(v)) out.add(UIx.stat(name + ": " + fmtFun.apply(v), CARD_W));
    }

    // ========================================================================
    // UI + Numerics + Read helpers
    // ========================================================================
    private static class UIx {
        static Label sectionHeader(String text){
            Label h = new Label(text);
            h.setStyle("-fx-text-fill:#cbd5e1; -fx-font-weight:700; -fx-opacity:.95;");
            return h;
        }
        static Node bullet(String s, double cardW){
            Label lab = new Label(s);
            lab.setStyle("-fx-text-fill:#e5e7eb; -fx-opacity:.95;");
            lab.setWrapText(true);
            lab.setPrefWidth(cardW - 20);
            return lab;
        }
        static Node stat(String s, double cardW){
            Label lab = new Label(s);
            lab.setStyle("-fx-text-fill:#e5e7eb; -fx-opacity:.85;");
            lab.setWrapText(true);
            lab.setPrefWidth(cardW - 20);
            return lab;
        }
        static void lockFixedSize(Region r, double w, double h){
            r.setMinWidth(w);  r.setPrefWidth(w);  r.setMaxWidth(w);
            r.setMinHeight(h); r.setPrefHeight(h); r.setMaxHeight(h);
        }
    }

    private static class Numx {
        static boolean isFinite(Double v){ return v != null && !v.isNaN() && !v.isInfinite(); }
        static boolean isOn(Double v){ return isFinite(v) && v >= 0.5; }
        static String fmt(double v, int d){
            double p = Math.pow(10, d);
            return String.format(Locale.ROOT, "%." + d + "f", Math.round(v*p)/p);
        }
        static String pct(double x){
            double v = Math.max(0, Math.min(1, x)) * 100.0;
            return (int) Math.round(v) + " %";
        }
        static double maxFinite(List<Double> vals){
            if (vals == null || vals.isEmpty()) return Double.NaN;
            double m = Double.NEGATIVE_INFINITY;
            for (Double v: vals) if (v != null && !v.isNaN() && !v.isInfinite()) m = Math.max(m, v);
            return m;
        }
        static Double meanFinite(Double a, Double b){
            if (!isFinite(a) || !isFinite(b)) return null;
            return (a + b)/2.0;
        }
        static Double meanAbs(Double a, Double b){
            int n=0; double s=0;
            if (isFinite(a)) { s += Math.abs(a); n++; }
            if (isFinite(b)) { s += Math.abs(b); n++; }
            return n==0 ? null : (s/n);
        }
        static String onoff(Double v) { return (isFinite(v) && v >= 0.5) ? "ON" : "OFF"; }
    }

    private static double rd(Signals s, SeriesBundle sb, Channel ch, double x){
        Double v = s.value(ch, x);
        if (Numx.isFinite(v)) return v;
        // fallback a sb se mancano questi canali
        switch (ch){
            case SPEED:       return interp(sb.x, sb.speed, x);
            case ENGINE_RPM:  return interp(sb.x, sb.rpm, x);
            case STEER_ANGLE: return interp(sb.x, sb.steering, x);
            default:          return Double.NaN;
        }
    }
    private static double localDx(SeriesBundle sb){
        if (sb == null || sb.x == null || sb.x.size() < 2) return 0.02;
        double x0 = sb.x.get(0), x1 = sb.x.get(sb.x.size()-1);
        if (!Double.isFinite(x0) || !Double.isFinite(x1)) return 0.02;
        double span = Math.abs(x1 - x0);
        return Math.max(1e-3, span / 500.0);
    }
    private static double interp(List<Double> xs, List<Double> ys, double xq){
        int n = Math.min(xs.size(), ys.size());
        if (n==0) return Double.NaN;
        if (xq <= xs.get(0)) return ys.get(0);
        if (xq >= xs.get(n-1)) return ys.get(n-1);
        int lo=0, hi=n-1;
        while (hi - lo > 1){
            int mid = (lo+hi)/2;
            double xm = xs.get(mid);
            if (xm <= xq) lo = mid; else hi = mid;
        }
        double x1 = xs.get(lo), x2 = xs.get(hi);
        double y1 = ys.get(lo), y2 = ys.get(hi);
        if (x2==x1) return y1;
        double t = (xq - x1)/(x2 - x1);
        return y1 + t*(y2 - y1);
    }
    private static double interpAround(List<Double> xs, List<Double> ys, double x, double fracSpan){
        if (xs == null || xs.size() < 2) return Double.NaN;
        double span = Math.abs(xs.get(xs.size()-1) - xs.get(0));
        return interp(xs, ys, x + fracSpan * span);
    }

    // ========================================================================
    // Vehicle profile + targets (estratti in helper)
    // ========================================================================
    private static final class VehicleProfileDetector {
        static VehicleProfile detect(Signals s, SeriesBundle sb){
            if (sb==null || sb.x==null || sb.x.isEmpty())
                return new VehicleProfile(Category.OTHER, Drivetrain.UNKNOWN, Powertrain.UNKNOWN);

            // Campionamento leggero lungo l'intero span
            int S = Math.min(120, Math.max(40, sb.x.size()/10));
            List<Double> xs = sb.x;
            List<Double> rpmL = clean(sb.rpm);
            List<Double> spdL = clean(sb.speed);

            boolean drsSeen=false, ersSeen=false, ersActive=false, turboSeen=false, turboActive=false;
            double maxSpeed=0;
            List<Double> rpmSamples = new ArrayList<>();

            for (int i=0;i<S;i++){
                double t = (double)i/(S-1);
                double x = xs.get(0) + t*(xs.get(xs.size()-1)-xs.get(0));

                // sistemi
                Double da = s.value(Channel.DRS_AVAILABLE, x);
                if (Numx.isFinite(da) && da>0.5) drsSeen=true;

                Double erc = s.value(Channel.ERS_IS_CHARGING, x);
                Double kde = s.value(Channel.KERS_DEPLOYED_ENERGY, x);
                if (Numx.isFinite(erc) || Numx.isFinite(kde)) ersSeen=true;
                if ((Numx.isFinite(erc) && erc>0.5) || (Numx.isFinite(kde) && kde>0.0)) ersActive=true;

                Double tb = s.value(Channel.TURBO_BOOST, x);
                Double mtb= s.value(Channel.MAX_TURBO_BOOST, x);
                if (Numx.isFinite(tb) || Numx.isFinite(mtb)) turboSeen=true;
                if (Numx.isFinite(tb) && tb>0.15) turboActive=true;

                // rpm & speed
                double r = interp(xs, rpmL, x);
                if (Double.isFinite(r)) rpmSamples.add(r);
                double v = interp(xs, spdL, x);
                if (Double.isFinite(v)) maxSpeed = Math.max(maxSpeed, v);
            }

            double rpmQ98 = percentile(rpmSamples, 0.98);

            // Powertrain
            Powertrain pt = Powertrain.UNKNOWN;
            if (ersActive) pt = Powertrain.HYBRID;
            else if (turboActive) pt = Powertrain.TURBO;
            else pt = Powertrain.NA;

            // Category
            Category cat;
            if (drsSeen || (Double.isFinite(rpmQ98) && rpmQ98 >= 14000)) cat = Category.FORMULA;
            else if (pt == Powertrain.HYBRID) cat = Category.PROTOTYPE;
            else {
                boolean absCap=false, tcCap=false;
                for (int i=0;i<S;i++){
                    double t = (double)i/(S-1);
                    double x = xs.get(0) + t*(xs.get(xs.size()-1)-xs.get(0));
                    Double abse = s.value(Channel.ABS_ENABLED, x);
                    Double absa = s.value(Channel.ABS_ACTIVE, x);
                    Double tce  = s.value(Channel.TC_ENABLED,  x);
                    Double tca  = s.value(Channel.TC_ACTIVE,   x);
                    if (Numx.isFinite(abse) || Numx.isFinite(absa)) absCap=true;
                    if (Numx.isFinite(tce)  || Numx.isFinite(tca))  tcCap=true;
                }
                if (absCap || tcCap) cat = (maxSpeed >= 260) ? Category.GT : Category.ROAD;
                else cat = Category.GT;
            }

            // Drivetrain (da slip in rettilineo accelerato)
            int base=0, fHigh=0, rHigh=0;
            for (int i=0;i<S;i++){
                double t = (double)i/(S-1);
                double x = xs.get(0) + t*(xs.get(xs.size()-1)-xs.get(0));
                Double thr = s.value(Channel.THROTTLE, x);
                Double st  = s.value(Channel.STEER_ANGLE, x);
                if (!Numx.isFinite(thr) || !Numx.isFinite(st)) continue;
                if (thr <= 70 || Math.abs(st) >= 5) continue;
                Double fl = s.value(Channel.TIRE_SLIP_RATIO_FL, x);
                Double fr = s.value(Channel.TIRE_SLIP_RATIO_FR, x);
                Double rl = s.value(Channel.TIRE_SLIP_RATIO_RL, x);
                Double rr = s.value(Channel.TIRE_SLIP_RATIO_RR, x);
                boolean fH = (Numx.isFinite(fl) && Math.abs(fl)>0.12) || (Numx.isFinite(fr) && Math.abs(fr)>0.12);
                boolean rH = (Numx.isFinite(rl) && Math.abs(rl)>0.12) || (Numx.isFinite(rr) && Math.abs(rr)>0.12);
                if (fH) fHigh++; if (rH) rHigh++; base++;
            }
            Drivetrain dt = Drivetrain.UNKNOWN;
            if (base >= 10){
                if (rHigh > fHigh * 1.3) dt = Drivetrain.RWD;
                else if (fHigh > rHigh * 1.3) dt = Drivetrain.FWD;
                else dt = Drivetrain.AWD;
            }

            return new VehicleProfile(cat, dt, pt);
        }

        private static List<Double> clean(List<Double> v){
            if (v==null) return Collections.emptyList();
            List<Double> out = new ArrayList<>(v.size());
            for (Double d : v) if (d!=null && !d.isNaN() && !d.isInfinite()) out.add(d);
            return out;
        }
        private static double percentile(List<Double> a, double p){
            if (a==null || a.isEmpty()) return Double.NaN;
            List<Double> v = new ArrayList<>(a);
            Collections.sort(v);
            p = Math.max(0, Math.min(1, p));
            double idx = p * (v.size()-1);
            int lo = (int)Math.floor(idx);
            int hi = (int)Math.ceil(idx);
            if (lo==hi) return v.get(lo);
            double t = idx - lo;
            return v.get(lo)*(1.0-t) + v.get(hi)*t;
        }
    }

    private static final class Targets {
        static Range tyreTargetFor(Category c){
            return switch (c){
                case FORMULA   -> new Range(90, 110);
                case PROTOTYPE -> new Range(85, 105);
                case GT        -> new Range(80, 100);
                case ROAD      -> new Range(75, 95);
                default        -> new Range(TYRE_OK_MIN_FALLBACK, TYRE_OK_MAX_FALLBACK);
            };
        }
        static Range brakeTargetFor(Category c){
            // target ampi: Formula (carbon) molto alti; GT/ROAD più bassi
            return switch (c){
                case FORMULA   -> new Range(300, 900);
                case PROTOTYPE -> new Range(220, 800);
                case GT        -> new Range(150, 600);
                case ROAD      -> new Range(120, 450);
                default        -> new Range(BRAKE_OK_MIN_FALLBACK, BRAKE_OK_MAX_FALLBACK);
            };
        }
    }

    private static final class TipBuilder {

        // —— Guida base ——
        static void coastingAndOverlap(List<String> tipsGuida, double thr01, double brk01, double speed){
            if (thr01 > 0.15 && brk01 > 0.15) tipsGuida.add("Overlap gas/freno: rilascia il freno prima di accelerare.");
            if (thr01 > 0.30 && brk01 > 0.30) tipsGuida.add("Overlap marcato: sequenzia freno → rilascio → gas (allunghi la frenata).");
            if (thr01 < COAST_THR && brk01 < COAST_THR && (Double.isNaN(speed) || speed > 40))
                tipsGuida.add("Coasting: anticipa leggermente il gas o ritarda la frenata per più scorrevolezza.");
        }

        static void ffbAndSteer(List<String> tipsGuida, double ffb01, double steer, double stL, double stR, double speed){
            if (ffb01 > 0.98)      tipsGuida.add("FFB in saturazione: riduci il gain o alleggerisci l’ingresso.");
            else if (ffb01 > 0.92) tipsGuida.add("FFB molto alto: rischio clipping, lavora su linea e carico anteriore.");
            else if (ffb01 > 0.80) tipsGuida.add("FFB alto: attenzione al carico davanti.");
            if (abs(steer) > 45 && (Double.isNaN(speed) || speed > 70))
                tipsGuida.add("Sterzo molto aperto: probabile sottosterzo, rilascia freno prima o pulisci la traiettoria.");
            if (Double.isFinite(stL) && Double.isFinite(stR) && abs(stR - stL) > 25)
                tipsGuida.add("Sterzo nervoso: movimenti rapidi sul volante, cerca input più puliti.");
            if (abs(steer) > 20 && (Double.isNaN(speed) || speed > 60) && ffb01 < 0.15)
                tipsGuida.add("FFB basso in percorrenza con sterzo aperto: poco carico sull’anteriore.");
        }

        static void throttleSmoothness(List<String> tipsGuida, double thrL, double thrR, double thr01, double steer){
            if (abs(thrR - thrL) > 0.25 && max(thrL, thrR) > 0.35)
                tipsGuida.add("Gas irregolare: apri con più progressività.");
            if ((thrR - thrL) > 0.45 && thrR > 0.75)
                tipsGuida.add("Apertura gas molto brusca: usa un ramp-up più dolce per evitare pattinamento.");
            if (thr01 > 0.85 && abs(steer) > 18)
                tipsGuida.add("Gas quasi pieno a volante ancora girato: rischio sottosterzo/spinta, raddrizza prima.");
        }

        static void rpmAndGearing(List<String> tipsGuida, double rpm, double rpmMax, double speed, double thr01, double steer, double ffb01){
            if (Double.isFinite(rpm) && Double.isFinite(rpmMax) && rpmMax > 0 && rpm >= 0.95 * rpmMax)
                tipsGuida.add("Vicino al limitatore: valuta cambio marcia un filo prima.");
            if (Double.isFinite(rpm) && Double.isFinite(rpmMax) && rpmMax > 0 && rpm < 0.25 * rpmMax && (Double.isNaN(speed) || speed > 60))
                tipsGuida.add("Giri molto bassi a velocità sostenuta: scala una marcia per più coppia in uscita.");
            if (thr01 > 0.8 && Double.isFinite(rpm) && Double.isFinite(rpmMax) && rpm > 0.7 * rpmMax && ffb01 < 0.25 && abs(steer) > 20)
                tipsGuida.add("Molti giri con poco carico davanti: possibile pattinamento/sottosterzo in trazione. Rilascia un filo e raddrizza.");
        }

        static void clutchUse(List<String> tipsGuida, double clu01p, double thr01){
            if (clu01p > 0.10) {
                if (thr01 > 0.40) tipsGuida.add("Frizione parzialmente premuta con gas: possibile slittamento. Chiudi frizione prima di accelerare deciso.");
                else tipsGuida.add("Frizione premuta: verifica input o configurazione.");
            }
        }

        // Sistemi / ERS / DRS / Boost
        static void driverAids(List<String> tipsGuida, Signals s, double xVal, double brk01, double thr01){
            if (Numx.isOn(s.value(Channel.ABS_ACTIVE, xVal)) && brk01 > BRK_ON)
                tipsGuida.add("ABS attivo: entra più graduale e modula meglio il freno.");
            if (Numx.isOn(s.value(Channel.TC_ACTIVE, xVal)) && thr01 > 0.4)
                tipsGuida.add("TC attivo: dosa il gas o rivedi il livello TC.");
        }
        static void drsErsBoost(List<String> tipsGuida, Signals s, double xVal, double steer, double speed, double thr01){
            Double drsAvail = s.value(Channel.DRS_AVAILABLE, xVal);
            Double drsActive= s.value(Channel.DRS_ACTIVE, xVal);
            if (Numx.isOn(drsAvail) && !Numx.isOn(drsActive) && abs(steer) < 3 && (Double.isNaN(speed) || speed > 140))
                tipsGuida.add("DRS disponibile: aprilo sul dritto (sterzo quasi dritto).");

            if (Numx.isOn(s.value(Channel.ERS_IS_CHARGING, xVal)) && thr01 > 0.6)
                tipsGuida.add("ERS in carica con gas alto: valuta settaggio recupero se limita la spinta.");
            Double kersCharge = s.value(Channel.KERS_CHARGE, xVal);
            if (Numx.isFinite(kersCharge) && kersCharge < 0.1 && thr01 > 0.6)
                tipsGuida.add("KERS quasi scarico: pianifica deploy più conservativo.");

            Double boost = s.value(Channel.TURBO_BOOST, xVal);
            if (Numx.isFinite(boost) && thr01 > 0.8 && boost < 0.5)
                tipsGuida.add("Boost basso con gas alto: verifica mappa turbo o anticipo apertura.");
        }

        // Setup
        static void setupEngineBrakeBias(List<String> tipsSetup, Signals s, double xVal, double brk01, double steer){
            Double engineBrake = s.value(Channel.ENGINE_BRAKE_SETTING, xVal);
            if (Numx.isFinite(engineBrake) && engineBrake >= 7)
                tipsSetup.add("Engine brake elevato: valuta -1/-2 step per ridurre instabilità in rilascio.");

            Double bias = s.value(Channel.BRAKE_BIAS, xVal);
            if (Numx.isFinite(bias) && brk01 > 0.5 && abs(steer) > 10)
                tipsSetup.add("Freno in curva con bias " + Numx.fmt(bias,1) + "%: se instabile, sposta leggermente in avanti.");
        }

        static void tyreAndPressure(List<String> tipsSetup, Double tFL, Double tFR, Double tRL, Double tRR, Range tyreTarget, Signals s, double xVal){
            addTyreTempTips(tipsSetup, tFL, "FL", tyreTarget);
            addTyreTempTips(tipsSetup, tFR, "FR", tyreTarget);
            addTyreTempTips(tipsSetup, tRL, "RL", tyreTarget);
            addTyreTempTips(tipsSetup, tRR, "RR", tyreTarget);

            addPressTips(tipsSetup, s.value(Channel.TIRE_PRESSURE_FL, xVal), "FL");
            addPressTips(tipsSetup, s.value(Channel.TIRE_PRESSURE_FR, xVal), "FR");
            addPressTips(tipsSetup, s.value(Channel.TIRE_PRESSURE_RL, xVal), "RL");
            addPressTips(tipsSetup, s.value(Channel.TIRE_PRESSURE_RR, xVal), "RR");
        }

        static void thermalAsymmetry(List<String> tipsSetup, Double tFL, Double tFR, Double tRL, Double tRR){
            if (finite(tFL, tFR) && abs(tFL - tFR) > 8)
                tipsSetup.add("Asimmetria termica avantreno (" + Math.round(abs(tFL - tFR)) + "°C): rivedi linea/camber/pressioni.");
            if (finite(tRL, tRR) && abs(tRL - tRR) > 8)
                tipsSetup.add("Asimmetria termica retrotreno (" + Math.round(abs(tRL - tRR)) + "°C): attenzione a trazione in appoggio.");
            Double avgF = Numx.meanFinite(tFL, tFR), avgR = Numx.meanFinite(tRL, tRR);
            if (avgF != null && avgR != null) {
                double dFR = avgF - avgR;
                if (dFR > 8)  tipsSetup.add("Anteriore più caldo (+" + Math.round(dFR) + "°C): tendenza al sottosterzo a regime.");
                if (dFR < -8) tipsSetup.add("Posteriore più caldo (" + Math.round(-dFR) + "°C): tendenza al sovrasterzo.");
            }
        }

        static void slipBalance(List<String> tipsGuida, Signals s, double xVal, Drivetrain dt){
            Double srFL = s.value(Channel.TIRE_SLIP_RATIO_FL, xVal);
            Double srFR = s.value(Channel.TIRE_SLIP_RATIO_FR, xVal);
            Double srRL = s.value(Channel.TIRE_SLIP_RATIO_RL, xVal);
            Double srRR = s.value(Channel.TIRE_SLIP_RATIO_RR, xVal);
            Double saFL = s.value(Channel.TIRE_SLIP_ANGLE_FL, xVal);
            Double saFR = s.value(Channel.TIRE_SLIP_ANGLE_FR, xVal);
            Double saRL = s.value(Channel.TIRE_SLIP_ANGLE_RL, xVal);
            Double saRR = s.value(Channel.TIRE_SLIP_ANGLE_RR, xVal);

            Double srF = Numx.meanAbs(srFL, srFR), srR = Numx.meanAbs(srRL, srRR);
            Double saF = Numx.meanAbs(saFL, saFR), saR = Numx.meanAbs(saRL, saRR);

            if ((Numx.isFinite(srF) && Numx.isFinite(srR) && srF > srR * 1.2) ||
                    (Numx.isFinite(saF) && Numx.isFinite(saR) && saF > saR * 1.2))
                tipsGuida.add("Indizi di sottosterzo: entra più dolce e lavora sulla rotazione a metà curva.");

            if ((Numx.isFinite(srF) && Numx.isFinite(srR) && srR > srF * 1.2) ||
                    (Numx.isFinite(saF) && Numx.isFinite(saR) && saR > saF * 1.2)){
                String extra = switch (dt){
                    case FWD -> " (tipico FWD se forzi il gas).";
                    case RWD -> " (attenzione alla trazione).";
                    default  -> ".";
                };
                tipsGuida.add("Indizi di sovrasterzo in trazione: dosa il gas e raddrizza prima" + extra);
            }
        }

        static void brakeTemps(List<String> tipsSetup, Double btFL, Double btFR, Double btRL, Double btRR, Range target){
            addBrakeTempTips(tipsSetup, btFL, "FL", target);
            addBrakeTempTips(tipsSetup, btFR, "FR", target);
            addBrakeTempTips(tipsSetup, btRL, "RL", target);
            addBrakeTempTips(tipsSetup, btRR, "RR", target);
        }

        static void lowSpeedLock(List<String> tipsGuida, double brk01, double speed){
            if (brk01 > 0.90 && (Double.isNaN(speed) || speed < 40))
                tipsGuida.add("Frenata quasi al 100% a bassa velocità: rischio bloccaggio. Allenta leggermente sotto i 40 km/h.");
        }

        static void suspKerb(List<String> tipsSetup, Signals s, double xVal){
            if (isSpike(s.value(Channel.SUSP_TRAVEL_FL, xVal), s.value(Channel.MAX_SUS_TRAVEL_FL, xVal)) ||
                    isSpike(s.value(Channel.SUSP_TRAVEL_FR, xVal), s.value(Channel.MAX_SUS_TRAVEL_FR, xVal)) ||
                    isSpike(s.value(Channel.SUSP_TRAVEL_RL, xVal), s.value(Channel.MAX_SUS_TRAVEL_RL, xVal)) ||
                    isSpike(s.value(Channel.SUSP_TRAVEL_RR, xVal), s.value(Channel.MAX_SUS_TRAVEL_RR, xVal)))
                tipsSetup.add("Colpo/kerb significativo: evita cordoli alti o indurisci leggermente il rebound.");
        }

        static void seatImbalance(List<String> tipsGuida, double seatL, double seatR, double seatRear, double steer){
            double seatMax01 = max(max(seatL, seatR), seatRear);
            if (seatMax01 > 0.85) tipsGuida.add("Cordolo aggressivo: usa meno cordolo nelle curve veloci per stabilità.");
            if (abs(seatL - seatR) > 0.40 && abs(steer) > 10) {
                if (seatL > seatR) tipsGuida.add("Carico laterale a sinistra elevato: dosa gas e tieni il volante più fermo in uscita.");
                else               tipsGuida.add("Carico laterale a destra elevato: evita micro-correzioni col gas, raddrizza progressivamente.");
            }
        }

        static void weatherGrip(List<String> tipsMeteo, Signals s, double xVal){
            Double wind = s.value(Channel.WIND_SPEED, xVal);
            if (Numx.isFinite(wind) && wind > WIND_STRONG) tipsMeteo.add("Vento forte: attenzione a stabilità nei curvoni.");
            Double grip2 = s.value(Channel.SURFACE_GRIP, xVal);
            if (Numx.isFinite(grip2) && grip2 < 1.0) tipsMeteo.add("Grip pista ridotto (" + Numx.fmt(grip2,2) + "): adatta i riferimenti di frenata/ingresso.");
        }

        static void damage(List<String> tipsSetup, Signals s, double xVal){
            addIfFinite(tipsSetup, s.value(Channel.CAR_DAMAGE_FRONT, xVal), v -> {
                if (v > 0) tipsSetup.add("Danno frontale "+ Numx.fmt(v,0) +"%: attenzione a carico/raffreddamento.");
            });
        }

        // === local helpers (TipBuilder scope) ===
        private static void addIfFinite(List<String> tips, Double v, java.util.function.Consumer<Double> f){
            if (Numx.isFinite(v)) f.accept(v);
        }
        private static void addTyreTempTips(List<String> tips, Double t, String pos, Range target){
            double lo = (target!=null? target.min : TYRE_OK_MIN_FALLBACK);
            double hi = (target!=null? target.max : TYRE_OK_MAX_FALLBACK);
            if (!Numx.isFinite(t)) return;
            if (t < lo) tips.add("Gomme " + pos + " fredde (" + Math.round(t) + "°C): scalda o alza leggermente la pressione.");
            else if (t > hi) tips.add("Gomme " + pos + " calde (" + Math.round(t) + "°C): alleggerisci il carico o scendi di 0.2–0.4 psi.");
        }
        private static void addPressTips(List<String> tips, Double psi, String pos){
            if (!Numx.isFinite(psi)) return;
            if (psi < 26) tips.add("Pressione " + pos + " bassa (" + Numx.fmt(psi,1) + " psi): +0.2/+0.4 psi.");
            else if (psi > 28) tips.add("Pressione " + pos + " alta (" + Numx.fmt(psi,1) + " psi): -0.2/-0.4 psi.");
        }
        private static void addBrakeTempTips(List<String> tips, Double bt, String pos, Range target){
            double lo = (target!=null? target.min : BRAKE_OK_MIN_FALLBACK);
            double hi = (target!=null? target.max : BRAKE_OK_MAX_FALLBACK);
            if (!Numx.isFinite(bt)) return;
            if (bt < lo) tips.add("Freni " + pos + " freddi (" + Math.round(bt) + "°C): attenzione alla prima staccata.");
            else if (bt > hi) tips.add("Freni " + pos + " molto caldi (" + Math.round(bt) + "°C): apri ducts o gestisci raffreddamento.");
        }
        private static boolean isSpike(Double cur, Double mx){
            return Numx.isFinite(cur) && Numx.isFinite(mx) && Math.abs(cur) > Math.abs(mx) * 0.90;
        }
        private static boolean finite(Double a, Double b) {
            return Numx.isFinite(a) && Numx.isFinite(b);
        }
        static void positiveFeedback(List<String> tipsGuida,
                                     double thr01, double brk01,
                                     double thrL, double thrR,
                                     double stL, double stR,
                                     double ffb01){
            // coerenza con la logica originale: nessun overlap gas/freno,
            // gas progressivo, sterzo stabile, FFB in finestra utile
            boolean noOverlap = !(thr01 > 0.10 && brk01 > 0.10);
            boolean progressiveGas = (Double.isFinite(thrL) && Double.isFinite(thrR)) && ((thrR - thrL) < 0.35);
            boolean stableSteer = (Double.isFinite(stL) && Double.isFinite(stR)) && (Math.abs(stR - stL) < 18);
            boolean ffbOk = (ffb01 >= 0.25 && ffb01 <= 0.90);

            if (noOverlap && progressiveGas && stableSteer && ffbOk) {
                tipsGuida.add("Buona sequenza: rilascio → rotazione → trazione. Continua così!");
            }
        }

    }

    private static final class TargetsProxy { /* placeholder per eventuali estensioni future */ }

    // Adattatori a metodi liberi esistenti (per non cambiare firme sopra)
    private static VehicleProfile detectVehicleProfile(Signals s, SeriesBundle sb){
        return VehicleProfileDetector.detect(s, sb);
    }
    private static Range tyreTargetFor(Category c){ return Targets.tyreTargetFor(c); }
    private static Range brakeTargetFor(Category c){ return Targets.brakeTargetFor(c); }
}
