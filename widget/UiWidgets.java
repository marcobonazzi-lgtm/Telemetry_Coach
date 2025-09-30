package org.simulator.widget;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility UI riutilizzabili per statistiche e note (con sottosezioni e nomi in italiano). */
public final class UiWidgets {

    private UiWidgets() {}

    // -------------------- PARAMETRI PRUNING --------------------
    /** Tolleranza per considerare "zero" un valore numerico. */
    private static final double EPS = 1e-9;

    /** Ritorna true se il valore è assente o non significativo (null/NaN/≈0). */
    private static boolean isMissingOrZero(Number n) {
        if (n == null) return true;
        double d = n.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) return true;
        return Math.abs(d) <= EPS;
    }

    /** Regola generale: nascondi l'entry se il valore è assente/non significativo. */
    private static boolean shouldHideEntry(String key, Number n) {
        // In futuro si possono introdurre eccezioni per alcune chiavi (es. contatori a 0 che vuoi comunque mostrare).
        return isMissingOrZero(n);
    }

    // -------------------- STATISTICHE: griglia semplice --------------------

    /** Griglia chiave→valore ordinata; evita notazione scientifica, RPM come intero. */
    public static Node buildStatsGrid(Map<String, ? extends Number> stats) {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(4);

        // Pruning per-entry
        List<Map.Entry<String, ? extends Number>> entries = new ArrayList<>();
        for (var e : stats.entrySet()) {
            if (!shouldHideEntry(e.getKey(), e.getValue())) {
                entries.add(e);
            }
        }
        // Se non rimane nulla, torna un contenitore vuoto
        if (entries.isEmpty()) return new VBox(0);

        entries.sort((a, b) -> {
            int ca = cornerRank(a.getKey());
            int cb = cornerRank(b.getKey());
            if (ca != cb) return Integer.compare(ca, cb);
            return a.getKey().compareToIgnoreCase(b.getKey());
        });

        int r = 0;
        for (var e : entries) {
            String key = e.getKey();
            Number val = e.getValue();
            Label k = new Label(key + ":");
            Label v = new Label(formatValueForKey(key, val));
            k.getStyleClass().add("stat-key");
            v.getStyleClass().add("stat-val");
            grid.add(k, 0, r);
            grid.add(v, 1, r);
            r++;
        }
        return grid;
    }

    // -------------------- STATISTICHE: accordion con sottosezioni --------------------

    /**
     * Pannelli collassabili per gruppi principali (in italiano) con sottosezioni dove serve.
     * Apre di default solo "Giro".
     */
    public static Node buildStatsAccordion(Map<String, ? extends Number> stats) {
        // Ordine gruppi principali
        List<String> groupOrder = List.of(
                "Giro",
                "Motore",
                "Ibrido (ERS/KERS/DRS)",
                "Fuel",
                "Sospensioni",
                "Assetto & Dinamica",
                "Gomme",
                "Freni",
                "Danni",
                "Meteo & Pista",
                "Coordinate & Velocità",
                "Telemetria",
                "Altro"
        );

        LinkedHashMap<String, List<Map.Entry<String, ? extends Number>>> groups = new LinkedHashMap<>();
        for (String g : groupOrder) groups.put(g, new ArrayList<>());

        // Smista chiavi → gruppo principale (PRIMA del pruning)
        for (var e : stats.entrySet()) {
            String group = groupNameForKey(e.getKey());
            groups.getOrDefault(group, groups.get("Altro")).add(Map.entry(e.getKey(), e.getValue()));
        }

        // PRUNING: rimuovi entries non significative da tutti i gruppi
        for (var g : groups.entrySet()) {
            g.getValue().removeIf(e -> shouldHideEntry(e.getKey(), e.getValue()));
        }

        VBox root = new VBox(8);
        root.setPadding(new Insets(2, 0, 0, 0));

        for (var g : groups.entrySet()) {
            if (g.getValue().isEmpty()) continue; // nascondi gruppi vuoti

            Node content;
            if ("Gomme".equals(g.getKey())) {
                content = buildTyresSubAccordion(g.getValue());
                if (isEmptyNode(content)) continue; // tutte le sotto-sezioni vuote
            } else if ("Assetto & Dinamica".equals(g.getKey())) {
                content = buildSetupDynamicsSlim(g.getValue()); // solo Geometrie & Accelerazioni CG
                if (isEmptyNode(content)) continue;
            } else if ("Altro".equals(g.getKey())) {
                content = buildOtherSubAccordion(g.getValue()); // suddivisione per accorciarlo
                if (isEmptyNode(content)) continue;
            } else {
                content = buildGroupGrid(g.getValue());
                if (isEmptyNode(content)) continue;
            }

            TitledPane tp = new TitledPane(g.getKey(), content);
            tp.setCollapsible(true);
            tp.setExpanded("Giro".equals(g.getKey())); // apri solo "Giro" di default
            root.getChildren().add(tp);
        }
        return root;
    }

    // Ritorna true se il Node è una VBox/GridPane vuota (usata dopo pruning profondo)
    private static boolean isEmptyNode(Node n) {
        if (n instanceof VBox vb) return vb.getChildren().isEmpty();
        if (n instanceof GridPane gp) return gp.getChildren().isEmpty();
        return false;
    }

    // ---- Sottosezione: Gomme
    private static Node buildTyresSubAccordion(List<Map.Entry<String, ? extends Number>> entries) {
        Map<String, List<Map.Entry<String, ? extends Number>>> sub = new LinkedHashMap<>();
        sub.put("Pressioni", new ArrayList<>());
        sub.put("Temperature", new ArrayList<>());
        sub.put("Carichi", new ArrayList<>());
        sub.put("Grip/Slip", new ArrayList<>());
        sub.put("Ruote", new ArrayList<>());

        for (var e : entries) {
            if (shouldHideEntry(e.getKey(), e.getValue())) continue;
            String k = e.getKey().toLowerCase(Locale.ROOT);
            if (k.contains("pressure")) sub.get("Pressioni").add(e);
            else if (k.contains("temp") || k.contains("temperature") || k.contains("°c")) sub.get("Temperature").add(e);
            else if (k.contains("load")) sub.get("Carichi").add(e);
            else if (k.contains("grip") || k.contains("slip") || k.contains("dirt")) sub.get("Grip/Slip").add(e);
            else if (k.contains("wheel angular speed") || k.contains("radius")) sub.get("Ruote").add(e);
            else sub.get("Ruote").add(e); // fallback gomme/ruote
        }

        VBox box = new VBox(6);
        for (var se : sub.entrySet()) {
            if (se.getValue().isEmpty()) continue;
            TitledPane tp = new TitledPane(se.getKey(), buildGroupGrid(se.getValue()));
            tp.setCollapsible(true);
            tp.setExpanded(false);
            box.getChildren().add(tp);
        }
        return box;
    }

    // ---- Sottosezione semplificata: Assetto & Dinamica (senza "Chassis & Sterzo")
    private static Node buildSetupDynamicsSlim(List<Map.Entry<String, ? extends Number>> entries) {
        Map<String, List<Map.Entry<String, ? extends Number>>> sub = new LinkedHashMap<>();
        sub.put("Geometrie (Camber/Caster/Toe)", new ArrayList<>());
        sub.put("Accelerazioni CG", new ArrayList<>());
        List<Map.Entry<String, ? extends Number>> altri = new ArrayList<>();

        for (var e : entries) {
            if (shouldHideEntry(e.getKey(), e.getValue())) continue;
            String k = e.getKey().toLowerCase(Locale.ROOT);
            if (k.contains("camber") || k.contains("caster") || k.contains("toe in")) {
                sub.get("Geometrie (Camber/Caster/Toe)").add(e);
            } else if (k.contains("cg accel") || k.contains("accel ")) {
                sub.get("Accelerazioni CG").add(e);
            } else {
                altri.add(e); // queste voci restano direttamente nel gruppo
            }
        }

        VBox box = new VBox(6);
        if (!altri.isEmpty()) box.getChildren().add(buildGroupGrid(altri));
        for (var se : sub.entrySet()) {
            if (se.getValue().isEmpty()) continue;
            TitledPane tp = new TitledPane(se.getKey(), buildGroupGrid(se.getValue()));
            tp.setCollapsible(true);
            tp.setExpanded(false);
            box.getChildren().add(tp);
        }
        return box;
    }

    // ---- Sottosezioni per "Altro"
    private static Node buildOtherSubAccordion(List<Map.Entry<String, ? extends Number>> entries) {
        Map<String, List<Map.Entry<String, ? extends Number>>> sub = new LinkedHashMap<>();
        sub.put("Elettronica & Aiuti", new ArrayList<>()); // ABS/TC/AID/DRS
        sub.put("Limiti & Max", new ArrayList<>());        // Max RPM/Power/Torque/Turbo/Fuel, limiter
        sub.put("Flags & Pit", new ArrayList<>());         // flags, in pit, lap invalidated, off-track
        sub.put("Campionamento", new ArrayList<>());       // sample rate, HR/MR/LR clock
        sub.put("Varie", new ArrayList<>());

        for (var e : entries) {
            if (shouldHideEntry(e.getKey(), e.getValue())) continue;
            String k = e.getKey().toLowerCase(Locale.ROOT);

            if (k.startsWith("aid ") || k.contains("abs ") || k.contains("tc ") || k.startsWith("drs ")) {
                sub.get("Elettronica & Aiuti").add(e);
            } else if (k.startsWith("max ") || k.contains("limiter")) {
                sub.get("Limiti & Max").add(e);
            } else if (k.equals("flags") || k.contains("in pit") || k.contains("lap invalidated") || k.contains("off track")) {
                sub.get("Flags & Pit").add(e);
            } else if (k.contains("sample rate") || k.contains("sample clock")) {
                sub.get("Campionamento").add(e);
            } else {
                sub.get("Varie").add(e);
            }
        }

        VBox box = new VBox(6);
        for (var se : sub.entrySet()) {
            if (se.getValue().isEmpty()) continue;
            TitledPane tp = new TitledPane(se.getKey(), buildGroupGrid(se.getValue()));
            tp.setCollapsible(true);
            tp.setExpanded(false);
            box.getChildren().add(tp);
        }
        return box;
    }

    // ---- Griglia ordinata per un gruppo (o sottogruppo)
    private static Node buildGroupGrid(List<Map.Entry<String, ? extends Number>> items) {
        // Pruning per-entry (safety net)
        items.removeIf(e -> shouldHideEntry(e.getKey(), e.getValue()));
        if (items.isEmpty()) return new GridPane();

        items.sort((a, b) -> {
            int ca = cornerRank(a.getKey());
            int cb = cornerRank(b.getKey());
            if (ca != cb) return Integer.compare(ca, cb);
            return a.getKey().compareToIgnoreCase(b.getKey());
        });

        GridPane gp = new GridPane();
        gp.setHgap(8); gp.setVgap(4);
        int r = 0;
        for (var e : items) {
            gp.add(new Label(e.getKey() + ":"), 0, r);
            gp.add(new Label(formatValueForKey(e.getKey(), e.getValue())), 1, r);
            r++;
        }
        return gp;
    }

    // -------------------- FORMATTAZIONE --------------------

    private static final Pattern P_DEG = Pattern.compile("\\bdeg\\b|angle|yaw|pitch|roll", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_PERCENT = Pattern.compile("%|bias|grip|pos(?!\\s*norm)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_SPEED = Pattern.compile("\\bspeed\\b|ground speed|drive\\s*train\\s*speed", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_PRESSURE = Pattern.compile("pressure", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_TEMP = Pattern.compile("\\btemp\\b|temperature|°c", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_TORQUE = Pattern.compile("torque|self align", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_ENERGY = Pattern.compile("energy|ers|kers", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_CLOCK = Pattern.compile("sample clock|sample rate|hr|mr|lr", Pattern.CASE_INSENSITIVE);

    // mm:ss.SSS da secondi
    private static String formatLapTime(double seconds) {
        if (Double.isNaN(seconds) || Double.isInfinite(seconds)) return "-";
        int totalMillis = (int) Math.round(seconds * 1000.0);
        int minutes = totalMillis / 60000;
        int secs = (totalMillis % 60000) / 1000;
        int millis = totalMillis % 1000;
        if (millis == 0) return String.format(Locale.getDefault(), "%d:%02d", minutes, secs);
        return String.format(Locale.getDefault(), "%d:%02d.%03d", minutes, secs, millis);
    }

    /** Formattazione senza notazione scientifica + casi speciali (lap, rpm, gradi, %, pressioni, temperature, ecc.). */
    private static String formatValueForKey(String key, Number n) {
        if (n == null) return "-";
        double d = n.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) return "-";

        String kl = key.toLowerCase(Locale.ROOT);
        if (kl.contains("lap") && kl.contains("time")) return formatLapTime(d);
        if (kl.contains("rpm")) return String.format(Locale.getDefault(), "%.0f", d);
        if (P_CLOCK.matcher(kl).find()) return String.format(Locale.getDefault(), "%.0f", d);
        if (P_DEG.matcher(kl).find()) return formatByMagnitude(d, 1, 1, 2);
        if (P_PERCENT.matcher(kl).find()) return String.format(Locale.getDefault(), "%.1f", d);
        if (P_SPEED.matcher(kl).find()) return formatByMagnitude(d, 0, 1, 2);
        if (P_PRESSURE.matcher(kl).find()) return String.format(Locale.getDefault(), "%.1f", d);
        if (P_TEMP.matcher(kl).find()) return String.format(Locale.getDefault(), "%.1f", d);
        if (P_TORQUE.matcher(kl).find()) return formatByMagnitude(d, 0, 1, 2);
        if (P_ENERGY.matcher(kl).find()) return formatByMagnitude(d, 0, 1, 2);

        double ad = Math.abs(d);
        if (ad >= 10000) return String.format(Locale.getDefault(), "%.0f", d);
        if (ad >= 1000)  return String.format(Locale.getDefault(), "%.0f", d);
        if (ad >= 100)   return String.format(Locale.getDefault(), "%.1f", d);
        if (ad >= 10)    return String.format(Locale.getDefault(), "%.1f", d);
        return String.format(Locale.getDefault(), "%.2f", d);
    }

    private static String formatByMagnitude(double d, int bigDigits, int midDigits, int smallDigits) {
        double ad = Math.abs(d);
        if (ad >= 100)   return String.format(Locale.getDefault(), "%." + bigDigits + "f", d);
        if (ad >= 10)    return String.format(Locale.getDefault(), "%." + midDigits + "f", d);
        return String.format(Locale.getDefault(), "%." + smallDigits + "f", d);
    }

    // Corner ordering: FL(0), FR(1), RL(2), RR(3), altri(9)
    private static final Pattern P_CORNER = Pattern.compile("\\b([FR][LR])\\b", Pattern.CASE_INSENSITIVE);
    private static int cornerRank(String key) {
        Matcher m = P_CORNER.matcher(key.toUpperCase(Locale.ROOT));
        if (!m.find()) return 9;
        return switch (m.group(1)) {
            case "FL" -> 0;
            case "FR" -> 1;
            case "RL" -> 2;
            case "RR" -> 3;
            default -> 9;
        };
    }

    // Mappa regole → gruppo principale (in italiano)
    private static String groupNameForKey(String kRaw) {
        String k = kRaw.toLowerCase(Locale.ROOT);

        if (k.contains("lap time") || k.contains("best lap") || k.contains("last lap")
                || k.contains("last sector") || k.contains("session lap count") || k.equals("lap") || k.equals("position"))
            return "Giro";

        if (k.contains("rpm") || k.equals("gear") || k.contains("max rpm")
                || k.contains("max power") || k.contains("max torque")
                || k.contains("limiter") || k.contains("engine brake")
                || k.contains("turbo boost"))
            return "Motore";

        if (k.startsWith("ers") || k.startsWith("kers") || k.startsWith("drs"))
            return "Ibrido (ERS/KERS/DRS)";

        if (k.contains("fuel"))
            return "Fuel";

        if (k.contains("ride height") || k.contains("suspension travel") || k.contains("max sus travel"))
            return "Sospensioni";

        if (k.contains("camber") || k.contains("caster") || k.contains("toe in") ||
                k.contains("cg accel") || k.contains("cg height") ||
                k.contains("pitch") || k.contains("roll") || k.contains("yaw") ||
                k.contains("chassis velocity") || k.contains("steer") || k.contains("brake bias"))
            return "Assetto & Dinamica";

        if (k.contains("tire ") || k.contains("tyre ") ||
                k.contains("wheel angular speed") || k.contains("radius"))
            return "Gomme";

        if (k.contains("brake temp") || (k.contains("brake") && k.contains("force")))
            return "Freni";

        if (k.contains("damage"))
            return "Danni";

        if (k.contains("air temp") || k.contains("air density") || k.contains("road temp") ||
                k.contains("wind ") || k.contains("surface grip") || k.contains("ballast"))
            return "Meteo & Pista";

        if (k.contains("car coord") || k.contains("car pos norm") ||
                k.equals("speed") || k.contains("ground speed") || k.contains("drive train speed"))
            return "Coordinate & Velocità";

        if (k.contains("raw data sample rate") || k.contains("sample clock") ||
                k.equals("flags") || k.equals("in pit") || k.contains("lap invalidated") ||
                k.contains("num tires off track") || k.contains("aid ") || k.contains("penalties enabled"))
            return "Telemetria";

        return "Altro";
    }

    // -------------------- NOTE DEL COACH --------------------

    /** Lista scrollabile con pallini colorati (🔴/🟠/🟢 all’inizio della stringa). */
    private static final class PriorityWrappingCell extends ListCell<String> {
        private final HBox root = new HBox(8);
        private final Circle dot = new Circle(5);
        private final Label label = new Label();

        PriorityWrappingCell() {
            label.setWrapText(true);
            HBox.setHgrow(label, Priority.ALWAYS);
            root.getChildren().addAll(dot, label);
            root.setPadding(new Insets(2, 2, 2, 2));
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) { setGraphic(null); return; }

            String text = item;
            Color c = Color.web("#2ecc71"); // default green

            if (item.startsWith("🔴 ")) { c = Color.web("#e74c3c"); text = item.substring(2).trim(); }
            else if (item.startsWith("🟠 ")) { c = Color.web("#f39c12"); text = item.substring(2).trim(); }
            else if (item.startsWith("🟢 ")) { c = Color.web("#2ecc71"); text = item.substring(2).trim(); }

            dot.setFill(c);
            dot.setStroke(c.darker());
            label.setText(text);

            setGraphic(root);
        }
    }

    // Manteniamo compatibilità con eventuali usi esistenti:
    private static boolean hideIfAllZero(List<Map.Entry<String, ? extends Number>> entries) {
        if (entries == null || entries.isEmpty()) return true;
        for (var e : entries) {
            if (!shouldHideEntry(e.getKey(), e.getValue())) return false;
        }
        return true;
    }
    private static boolean isExactlyZero(Number n) {
        // mantenuta per compatibilità, ora delega a isMissingOrZero
        return isMissingOrZero(n);
    }
}
