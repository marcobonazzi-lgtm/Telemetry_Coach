package org.simulator.ui.time_line_view.widget_TL;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.simulator.canale.Channel;
import org.simulator.setup.setup_advisor.VehicleTraits;
import org.simulator.ui.SeriesBundle;
import org.simulator.ui.time_line_view.Signals;

import java.util.*;

/**
 * Coach "Sticky" Definitivo + Dinamica Avanzata.
 * - UI: Header Telemetria Grande + Feed Scrollabile.
 * - Features: DRS, Overlap, Coasting, Controsterzo, Sovrasterzo, Testacoda.
 */
public final class CoachTL {

    private static final double CARD_W = 280;
    private static final double CARD_H = 260;

    // Logica Sticky
    private static final long MIN_DISPLAY_TIME_MS = 4000;
    private static final int MAX_BUFFER_SIZE = 15;

    // --- UI Components ---
    private final VBox root = new VBox(0);

    // Header Telemetria
    private final Label lblGear = new Label("-");
    private final Label lblRpm  = new Label("0");
    private final Label lblKmh  = new Label("0");

    // Feed
    private final VBox feedContainer = new VBox(6);
    private final ScrollPane scrollPane;

    // --- State & Data ---
    private DrivingPhase currentPhase = DrivingPhase.STRAIGHT;
    private VehicleTraits cachedTraits = null;
    private long lastUpdate = 0;

    // Coda Messaggi
    private final LinkedList<ActiveMessage> messageFeed = new LinkedList<>();
    private List<TrackSection> trackSections = new ArrayList<>();

    public CoachTL() {
        Label title = new Label("Race Coach");
        title.setStyle("-fx-text-fill:#ffffff; -fx-font-weight:800; -fx-font-size:14px;");

        // 1. Header Telemetria (Big Data)
        HBox telemBox = new HBox(15);
        telemBox.setAlignment(Pos.CENTER);
        telemBox.setPadding(new Insets(10, 0, 10, 0));
        telemBox.setStyle("-fx-background-color:#1e293b; -fx-background-radius:6; -fx-border-color:#334155; -fx-border-radius:6;");

        VBox gBox = UIx.createBigVal("GEAR", lblGear, "#facc15", 28);
        VBox rBox = UIx.createBigVal("RPM",  lblRpm,  "#f1f5f9", 18);
        VBox sBox = UIx.createBigVal("KMH",  lblKmh,  "#22d3ee", 22);

        telemBox.getChildren().addAll(gBox, UIx.createVertSep(), rBox, UIx.createVertSep(), sBox);

        // 2. Area Consigli (Scrollabile)
        feedContainer.setPadding(new Insets(0, 4, 0, 0));

        scrollPane = new ScrollPane(feedContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background:transparent; -fx-background-color:transparent;");
        scrollPane.setPadding(Insets.EMPTY);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // 3. Main Layout
        VBox card = new VBox(10, title, telemBox, scrollPane);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color:#0f172a; -fx-background-radius:12; -fx-border-color: #1e293b; -fx-border-width: 1; -fx-border-radius: 12;");
        UIx.lockFixedSize(card, CARD_W, CARD_H);

        root.getChildren().add(card);
    }

    public Node getRoot() { return root; }

    public void setSections(List<TrackSection> sections) {
        this.trackSections = (sections != null) ? sections : new ArrayList<>();
    }

    public void update(double xVal, Signals s, SeriesBundle sb) {
        if (s == null) return;
        long now = System.currentTimeMillis();

        // A. Update UI Telemetria
        updateTelemetryUI(s, xVal);

        // B. Rilevamento Contesto
        if (cachedTraits == null || (now - lastUpdate > 5000)) {
            // Default o rilevamento istantaneo se non abbiamo lo storico completo
            cachedTraits = new VehicleTraits(VehicleTraits.Category.GT, VehicleTraits.Drivetrain.RWD, VehicleTraits.Powertrain.NA);
        }
        currentPhase = detectPhase(s, xVal, sb);
        String currentSection = findSectionName(xVal);

        // C. Analisi Completa
        List<Tip> frameTips = new ArrayList<>();

        analyzeDRS(frameTips, s, xVal);
        analyzeGearAndSpeed(frameTips, s, xVal, sb, currentPhase);
        analyzeDrivingLines(frameTips, s, xVal, sb, currentPhase, cachedTraits);
        analyzeCriticalSetup(frameTips, s, xVal, cachedTraits);
        analyzeDynamics(frameTips, s, xVal, cachedTraits); // <--- NUOVA ANALISI DINAMICA

        // D. Gestione Feed Sticky
        updateFeedLogic(frameTips, currentSection, now);

        // E. Render
        renderFeedUI();

        lastUpdate = now;
    }

    // ================= ANALISI DINAMICA (NUOVA) =================

    private void analyzeDynamics(List<Tip> tips, Signals s, double x, VehicleTraits traits) {
        // 1. Controsterzo
        // Se Angolo Sterzo e G Laterali hanno segno opposto, stai controsterzando.
        Double steer = s.value(Channel.STEER_ANGLE, x);
        Double latG  = s.value(Channel.ACC_LAT, x); // Assumiamo convenzione standard
        Double spd   = s.value(Channel.SPEED, x);

        if (Numx.isFinite(steer) && Numx.isFinite(latG) && Numx.isFinite(spd) && spd > 30) {
            // Logica: Se sto curvando forte (LatG > 0.5) e lo sterzo è girato dall'altra parte (> 10 gradi)
            boolean counterSteering = (steer * latG < 0) && (Math.abs(steer) > 10) && (Math.abs(latG) > 0.5);

            if (counterSteering) {
                tips.add(new Tip(Severity.MEDIUM, "GUIDA", "🔄 Controsterzo! Gestisci il ritorno."));
            }

            // 2. Testacoda (Spin)
            // Se i G laterali sono estremi (> 2.5G è spesso slide incontrollato nelle sim)
            if (Math.abs(latG) > 2.5) {
                tips.add(new Tip(Severity.HIGH, "PERICOLO", "⛔ TESTACODA?! Freni a fondo!"));
            }
        }

        // 3. Sovrasterzo di Potenza (Preventivo)
        // Se sei RWD, in uscita, dai tanto gas e hai ancora tanto sterzo
        if (traits.drivetrain == VehicleTraits.Drivetrain.RWD && currentPhase == DrivingPhase.EXIT) {
            double thr = s.throttle01(x);
            if (thr > 0.9 && Math.abs(orZero(steer)) > 20) {
                tips.add(new Tip(Severity.LOW, "TRAZIONE", "⚠️ Occhio al posteriore: parzializza."));
            }
        }
    }

    // ================= ANALISI CLASSICA (RIPRISTINATA) =================

    private void analyzeDRS(List<Tip> tips, Signals s, double x) {
        Double avail = s.value(Channel.DRS_AVAILABLE, x);
        Double active = s.value(Channel.DRS_ACTIVE, x);
        Double spd = s.value(Channel.SPEED, x);
        boolean isZone = (avail != null && avail > 0.5);
        boolean isOn   = (active != null && active > 0.5);
        if (isZone && !isOn && (spd != null && spd > 80)) {
            tips.add(new Tip(Severity.HIGH, "DRS", "🚀 ATTIVA DRS ORA!"));
        }
    }

    private void analyzeGearAndSpeed(List<Tip> tips, Signals s, double x, SeriesBundle sb, DrivingPhase phase) {
        double rpm = rd(s, sb, Channel.ENGINE_RPM, x);
        double maxRpm = orZero(s.value(Channel.MAX_RPM, x));
        if (maxRpm < 1000) maxRpm = 8000;
        double gear = orZero(s.value(Channel.GEAR, x));

        if (phase == DrivingPhase.BRAKING) {
            if (rpm < maxRpm * 0.40 && gear > 1)
                tips.add(new Tip(Severity.MEDIUM, "CAMBIO", "⬇️ Scala marcia: giri troppo bassi."));
            if (rpm > maxRpm * 0.92)
                tips.add(new Tip(Severity.HIGH, "MOTORE", "⚠️ Fuorigiri: ritarda la scalata!"));
        }
        else if (phase == DrivingPhase.EXIT || phase == DrivingPhase.STRAIGHT) {
            if (rpm > maxRpm * 0.96)
                tips.add(new Tip(Severity.HIGH, "CAMBIO", "⬆️ Upshift ora! Limitatore vicino."));
        }
    }

    private void analyzeDrivingLines(List<Tip> tips, Signals s, double x, SeriesBundle sb, DrivingPhase phase, VehicleTraits traits) {
        double thr = s.throttle01(x);
        double brk = s.brake01(x);
        double speed = rd(s, sb, Channel.SPEED, x);
        double steer = Math.abs(rd(s, sb, Channel.STEER_ANGLE, x));

        switch (phase) {
            case BRAKING -> {
                if (thr > 0.1 && brk > 0.1 && speed > 50)
                    tips.add(new Tip(Severity.HIGH, "PEDALI", "❌ Overlap Gas/Freno: rilascia il gas!"));
                if (orZero(s.value(Channel.ABS_ACTIVE, x)) > 0.5 && brk > 0.95)
                    tips.add(new Tip(Severity.MEDIUM, "FRENATA", "⚠️ ABS Attivo: riduci pressione."));
            }
            case APEX -> {
                if (thr < 0.05 && brk < 0.05 && speed > 40 && traits.category != VehicleTraits.Category.FORMULA) {
                    tips.add(new Tip(Severity.MEDIUM, "APEX", "🐢 Coasting eccessivo: gas prima o frena dopo."));
                }
            }
            case EXIT -> {
                if (orZero(s.value(Channel.TC_ACTIVE, x)) > 0.5)
                    tips.add(new Tip(Severity.MEDIUM, "TRAZIONE", "⚠️ TC Attivo: parzializza il gas."));
                if (thr > 0.9 && steer > 40)
                    tips.add(new Tip(Severity.HIGH, "USCITA", "🚫 Troppo sterzo col gas: raddrizza!"));
            }
        }
    }

    private void analyzeCriticalSetup(List<Tip> tips, Signals s, double x, VehicleTraits traits) {
        double tFL = orZero(s.tyreTemp("FL", x));
        double maxT = traits.targets.tempCoreMax();
        if (tFL > maxT + 15)
            tips.add(new Tip(Severity.HIGH, "GOMME", "🔥 FL Surriscaldata (" + (int)tFL + "°C)."));
    }

    // ================= FEED MANAGEMENT =================

    private void updateFeedLogic(List<Tip> newTips, String sectionName, long now) {
        for (Tip tip : newTips) {
            Optional<ActiveMessage> existing = messageFeed.stream()
                    .filter(m -> m.tip.key().equals(tip.key()))
                    .findFirst();

            if (existing.isPresent()) {
                existing.get().lastTriggerTime = now;
            } else {
                messageFeed.addFirst(new ActiveMessage(tip, now, sectionName));
            }
        }
        messageFeed.removeIf(m -> (now - m.firstShowTime > MIN_DISPLAY_TIME_MS) && (now - m.lastTriggerTime > 500));
        while (messageFeed.size() > MAX_BUFFER_SIZE) {
            messageFeed.removeLast();
        }
    }

    private void renderFeedUI() {
        Platform.runLater(() -> {
            feedContainer.getChildren().clear();
            if (messageFeed.isEmpty()) {
                Label ok = new Label("Guida pulita. Concentrati sui riferimenti.");
                ok.setStyle("-fx-text-fill:#475569; -fx-font-style:italic; -fx-font-size:11px; -fx-padding:10;");
                ok.setMaxWidth(Double.MAX_VALUE); ok.setAlignment(Pos.CENTER);
                feedContainer.getChildren().add(ok);
            } else {
                for (ActiveMessage m : messageFeed) {
                    feedContainer.getChildren().add(UIx.createTipCard(m));
                }
            }
        });
    }

    private void updateTelemetryUI(Signals s, double x) {
        Double gear = s.value(Channel.GEAR, x);
        Double rpm  = s.value(Channel.ENGINE_RPM, x);
        Double spd  = s.value(Channel.SPEED, x);

        Platform.runLater(() -> {
            if (gear == null) lblGear.setText("-");
            else if (gear == 0) lblGear.setText("N");
            else if (gear < 0)  lblGear.setText("R");
            else lblGear.setText(String.format("%.0f", gear));

            lblRpm.setText(rpm == null ? "0" : String.format("%.0f", rpm));
            lblKmh.setText(spd == null ? "0" : String.format("%.0f", spd));
        });
    }

    // ================= HELPERS =================

    public record TrackSection(double start, double end, String name) {}

    private String findSectionName(double x) {
        if (trackSections == null) return "";
        for (TrackSection ts : trackSections) {
            if (x >= ts.start && x <= ts.end) return ts.name;
        }
        return "";
    }

    private enum DrivingPhase { STRAIGHT, BRAKING, ENTRY, APEX, EXIT }
    private enum Severity { LOW, MEDIUM, HIGH }
    private record Tip(Severity sev, String cat, String msg) { String key() { return cat + "|" + msg; } }

    private static class ActiveMessage {
        final Tip tip;
        final long firstShowTime;
        long lastTriggerTime;
        final String section;
        ActiveMessage(Tip t, long now, String sect) {
            this.tip = t; this.firstShowTime = now; this.lastTriggerTime = now; this.section = sect;
        }
    }

    private static class Numx {
        static boolean isFinite(Double v) { return v != null && !v.isNaN() && !v.isInfinite(); }
        static boolean isOn(Double v) { return isFinite(v) && v >= 0.5; }
    }

    private double rd(Signals s, SeriesBundle sb, Channel ch, double x) {
        Double v = s.value(ch, x);
        if (Numx.isFinite(v)) return v;
        if (sb != null) {
            if (ch == Channel.SPEED) return interp(sb.x, sb.speed, x);
            if (ch == Channel.ENGINE_RPM) return interp(sb.x, sb.rpm, x);
            if (ch == Channel.STEER_ANGLE) return interp(sb.x, sb.steering, x);
        }
        return 0.0;
    }
    private double interp(List<Double> xs, List<Double> ys, double xq) {
        if(xs==null||ys==null||xs.isEmpty())return 0;
        int idx=Collections.binarySearch(xs,xq);
        if(idx>=0)return ys.get(idx);
        int i=-idx-1;
        if(i==0)return ys.get(0); if(i>=xs.size())return ys.get(ys.size()-1);
        double x1=xs.get(i-1), x2=xs.get(i), y1=ys.get(i-1), y2=ys.get(i);
        return y1+(xq-x1)/(x2-x1)*(y2-y1);
    }
    private double orZero(Double d){ return (d==null||Double.isNaN(d))?0.0:d; }

    private DrivingPhase detectPhase(Signals s, double x, SeriesBundle sb) {
        double thr = s.throttle01(x);
        double brk = s.brake01(x);
        double steer = Math.abs(rd(s, sb, Channel.STEER_ANGLE, x));
        double latG = Math.abs(orZero(s.value(Channel.ACC_LAT, x)));

        if (brk > 0.05 && thr < 0.1) return DrivingPhase.BRAKING;
        if (latG > 0.5 || steer > 10) {
            if (brk > 0.05) return DrivingPhase.ENTRY;
            if (thr > 0.5) return DrivingPhase.EXIT;
            return DrivingPhase.APEX;
        }
        return DrivingPhase.STRAIGHT;
    }

    // ================= UI FACTORY =================
    private static class UIx {
        static void lockFixedSize(Region r, double w, double h) { r.setMinSize(w,h); r.setMaxSize(w,h); }

        static VBox createBigVal(String title, Label valLbl, String colHex, int fontSize) {
            Label t = new Label(title);
            t.setStyle("-fx-text-fill:#64748b; -fx-font-size:9px; -fx-font-weight:bold;");
            valLbl.setStyle("-fx-text-fill:"+colHex+"; -fx-font-family:'Monospaced'; -fx-font-weight:bold; -fx-font-size:"+fontSize+"px;");
            VBox vb = new VBox(0, t, valLbl);
            vb.setAlignment(Pos.CENTER);
            vb.setMinWidth(55);
            return vb;
        }

        static Node createVertSep() {
            Rectangle r = new Rectangle(1, 30, Color.web("#334155"));
            return r;
        }

        static Node createTipCard(ActiveMessage m) {
            String hex = switch (m.tip.sev) {
                case HIGH -> "#ef4444";
                case MEDIUM -> "#f59e0b";
                case LOW -> "#3b82f6";
            };

            Text h = new Text(m.tip.cat + (m.section.isEmpty() ? "" : " @ " + m.section) + "\n");
            h.setStyle("-fx-font-weight:bold; -fx-font-size:10px; -fx-fill:"+hex+";");
            Text b = new Text(m.tip.msg);
            b.setStyle("-fx-fill:#e2e8f0; -fx-font-size:11px;");

            TextFlow tf = new TextFlow(h, b);
            HBox card = new HBox(0, tf);
            card.setPadding(new Insets(6, 8, 6, 8));
            card.setStyle("-fx-background-color:#1e293b; -fx-background-radius:4; -fx-border-color: "+hex+"; -fx-border-width: 0 0 0 3;");
            return card;
        }
    }
}