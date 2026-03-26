package org.simulator.widget;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility UI riutilizzabili per statistiche e note.
 */
public final class UiWidgets {

    private UiWidgets() {}

    private static final double EPS = 1e-9;

    private static boolean isMissingOrZero(Number n) {
        if (n == null) return true;
        double d = n.doubleValue();
        return Double.isNaN(d) || Double.isInfinite(d) || Math.abs(d) <= EPS;
    }

    private static boolean shouldHideEntry(Number n) {
        return isMissingOrZero(n);
    }

    private static final Comparator<Map.Entry<String, ? extends Number>> ENTRY_COMPARATOR = (a, b) -> {
        int ca = cornerRank(a.getKey());
        int cb = cornerRank(b.getKey());
        if (ca != cb) return Integer.compare(ca, cb);
        return a.getKey().compareToIgnoreCase(b.getKey());
    };

    public static Node buildStatsAccordion(Map<String, ? extends Number> stats) {
        List<String> groupOrder = List.of(
                "Giro", "Motore", "Ibrido (ERS/KERS/DRS)", "Fuel",
                "Sospensioni", "Assetto & Dinamica", "Gomme", "Freni",
                "Danni", "Meteo & Pista", "Coordinate & Velocità",
                "Telemetria", "Altro"
        );

        Map<String, List<Map.Entry<String, ? extends Number>>> groups = new LinkedHashMap<>();
        for (String g : groupOrder) groups.put(g, new ArrayList<>());

        for (var e : stats.entrySet()) {
            if (shouldHideEntry(e.getValue())) continue;
            String group = groupNameForKey(e.getKey());
            groups.getOrDefault(group, groups.get("Altro")).add(e);
        }

        VBox root = new VBox(5);
        root.setPadding(new Insets(2, 0, 0, 0));

        for (var g : groups.entrySet()) {
            List<Map.Entry<String, ? extends Number>> items = g.getValue();
            if (items.isEmpty()) continue;

            String key = g.getKey();
            Node content = switch (key) {
                case "Gomme" -> buildTyresSubAccordion(items);
                case "Assetto & Dinamica" -> buildSetupDynamicsSlim(items);
                case "Altro" -> buildOtherSubAccordion(items);
                default -> buildGroupGrid(items);
            };

            if (isEmptyNode(content)) continue;

            TitledPane tp = new TitledPane(key, content);
            tp.setCollapsible(true);
            tp.setExpanded("Giro".equals(key));
            tp.setAnimated(false);
            tp.getStyleClass().add("modern-titled-pane");
            root.getChildren().add(tp);
        }
        return root;
    }

    private static boolean isEmptyNode(Node n) {
        if (n instanceof VBox vb) return vb.getChildren().isEmpty();
        if (n instanceof GridPane gp) return gp.getChildren().isEmpty();
        return false;
    }

    private static Node buildTyresSubAccordion(List<Map.Entry<String, ? extends Number>> entries) {
        return buildSubGroups(entries, e -> {
            String k = e.getKey().toLowerCase(Locale.ROOT);
            if (k.contains("pressure")) return "Pressioni";
            if (k.contains("temp") || k.contains("temperature") || k.contains("°c") || k.contains("tair")) return "Temperature";
            if (k.contains("load")) return "Carichi";
            if (k.contains("grip") || k.contains("slip") || k.contains("dirt") || k.contains("wear")) return "Grip/Slip";
            if (k.contains("wheel angular") || k.contains("radius")) return "Ruote";
            return null;
        });
    }

    private static Node buildSetupDynamicsSlim(List<Map.Entry<String, ? extends Number>> entries) {
        List<Map.Entry<String, ? extends Number>> geometries = new ArrayList<>();
        List<Map.Entry<String, ? extends Number>> accels = new ArrayList<>();
        List<Map.Entry<String, ? extends Number>> others = new ArrayList<>();

        for (var e : entries) {
            String k = e.getKey().toLowerCase(Locale.ROOT);
            if (k.contains("camber") || k.contains("caster") || k.contains("toe in")) geometries.add(e);
            else if (k.contains("cg accel") || k.contains("accel ")) accels.add(e);
            else others.add(e);
        }

        VBox box = new VBox(6);
        if (!others.isEmpty()) box.getChildren().add(buildGroupGrid(others));

        addTitledIfNotEmpty(box, "Geometrie (Camber/Caster/Toe)", geometries);
        addTitledIfNotEmpty(box, "Accelerazioni CG", accels);

        return box;
    }

    private static Node buildOtherSubAccordion(List<Map.Entry<String, ? extends Number>> entries) {
        return buildSubGroups(entries, e -> {
            String k = e.getKey().toLowerCase(Locale.ROOT);
            if (k.startsWith("aid ") || k.contains("abs ") || k.contains("tc ") || k.startsWith("drs ")) return "Elettronica & Aiuti";
            if (k.startsWith("max ") || k.contains("limiter")) return "Limiti & Max";
            if (k.equals("flags") || k.contains("in pit") || k.contains("lap invalidated") || k.contains("off track")) return "Flags & Pit";
            if (k.contains("sample rate") || k.contains("sample clock")) return "Campionamento";
            return "Varie";
        });
    }

    private interface GroupSelector { String select(Map.Entry<String, ? extends Number> e); }

    private static Node buildSubGroups(List<Map.Entry<String, ? extends Number>> entries, GroupSelector selector) {
        Map<String, List<Map.Entry<String, ? extends Number>>> subs = new LinkedHashMap<>();

        for (var e : entries) {
            String subKey = selector.select(e);
            if (subKey != null) {
                subs.computeIfAbsent(subKey, ignored -> new ArrayList<>()).add(e);
            }
        }

        VBox box = new VBox(6);
        for (var se : subs.entrySet()) {
            addTitledIfNotEmpty(box, se.getKey(), se.getValue());
        }
        return box;
    }

    private static void addTitledIfNotEmpty(VBox root, String title, List<Map.Entry<String, ? extends Number>> items) {
        if (items == null || items.isEmpty()) return;
        TitledPane tp = new TitledPane(title, buildGroupGrid(items));
        tp.setCollapsible(true);
        tp.setExpanded(false);
        tp.setAnimated(false);
        tp.getStyleClass().add("modern-titled-pane");
        root.getChildren().add(tp);
    }

    // NUOVO: Aggiunta separatore e spaziature migliorate
    private static Node buildGroupGrid(List<Map.Entry<String, ? extends Number>> items) {
        List<Map.Entry<String, ? extends Number>> filtered = new ArrayList<>();
        for(var e : items) {
            if(!shouldHideEntry(e.getValue())) filtered.add(e);
        }

        if (filtered.isEmpty()) return new VBox(0);

        filtered.sort(ENTRY_COMPARATOR);

        GridPane gp = new GridPane();
        gp.setHgap(15);
        gp.setVgap(8);
        gp.setPadding(new Insets(5, 5, 5, 5));

        int r = 0;
        for (int i = 0; i < filtered.size(); i++) {
            var e = filtered.get(i);
            Label keyLbl = new Label(e.getKey() + ":");
            keyLbl.getStyleClass().add("stat-key");

            Label valLbl = new Label(formatValueForKey(e.getKey(), e.getValue()));
            valLbl.getStyleClass().add("stat-val");

            gp.add(keyLbl, 0, r);
            gp.add(valLbl, 1, r);
            r++;

            // Inserisce una riga di divisione se non è l'ultimo elemento
            if (i < filtered.size() - 1) {
                Region divider = new Region();
                divider.getStyleClass().add("stat-divider");
                gp.add(divider, 0, r, 2, 1);
                r++;
            }
        }
        return gp;
    }

    private static final Pattern P_DEG = Pattern.compile("\\bdeg\\b|angle|yaw|pitch|roll", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_PERCENT = Pattern.compile("%|bias|grip|pos(?!\\s*norm)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_SPEED = Pattern.compile("\\bspeed\\b|ground speed|drive\\s*train\\s*speed", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_PRESSURE = Pattern.compile("pressure", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_TEMP = Pattern.compile("\\btemp\\b|temperature|°c", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_TORQUE = Pattern.compile("torque|self align", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_ENERGY = Pattern.compile("energy|ers|kers", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_CLOCK = Pattern.compile("sample clock|sample rate|hr|mr|lr", Pattern.CASE_INSENSITIVE);

    private static String formatLapTime(double seconds) {
        if (Double.isNaN(seconds) || Double.isInfinite(seconds)) return "-";
        int totalMillis = (int) Math.round(seconds * 1000.0);
        int minutes = totalMillis / 60000;
        int secs = (totalMillis % 60000) / 1000;
        int millis = totalMillis % 1000;
        if (millis == 0) return String.format(Locale.getDefault(), "%d:%02d", minutes, secs);
        return String.format(Locale.getDefault(), "%d:%02d.%03d", minutes, secs, millis);
    }

    private static String formatValueForKey(String key, Number n) {
        if (n == null) return "-";
        double d = n.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) return "-";

        String kl = key.toLowerCase(Locale.ROOT);
        if (kl.contains("lap") && kl.contains("time")) return formatLapTime(d);
        if (kl.contains("rpm") || P_CLOCK.matcher(kl).find()) return String.format(Locale.getDefault(), "%.0f", d);
        if (P_DEG.matcher(kl).find()) return formatByMagnitude(d, 1);
        if (P_PERCENT.matcher(kl).find() || P_PRESSURE.matcher(kl).find() || P_TEMP.matcher(kl).find())
            return String.format(Locale.getDefault(), "%.1f", d);
        if (P_SPEED.matcher(kl).find() || P_TORQUE.matcher(kl).find() || P_ENERGY.matcher(kl).find())
            return formatByMagnitude(d, 0);

        double ad = Math.abs(d);
        if (ad >= 10000) return String.format(Locale.getDefault(), "%.0f", d);
        if (ad >= 100)   return String.format(Locale.getDefault(), "%.1f", d);
        return String.format(Locale.getDefault(), "%.2f", d);
    }

    private static String formatByMagnitude(double d, int bigDigits) {
        double ad = Math.abs(d);
        if (ad >= 100)   return String.format(Locale.getDefault(), bigDigits == 0 ? "%.0f" : "%.1f", d);
        if (ad >= 10)    return String.format(Locale.getDefault(), "%.1f", d);
        return String.format(Locale.getDefault(), "%.2f", d);
    }

    private static final Pattern P_CORNER = Pattern.compile("\\b([FR][LR])\\b", Pattern.CASE_INSENSITIVE);
    private static int cornerRank(String key) {
        Matcher m = P_CORNER.matcher(key.toUpperCase(Locale.ROOT));
        if (!m.find()) return 9;
        String g = m.group(1);
        return switch (g) {
            case "FL" -> 0;
            case "FR" -> 1;
            case "RL" -> 2;
            case "RR" -> 3;
            default -> 9;
        };
    }

    private static String groupNameForKey(String kRaw) {
        String k = kRaw.toLowerCase(Locale.ROOT);
        if (k.contains("lap time") || k.contains("best lap") || k.contains("last lap")
                || k.contains("last sector") || k.contains("session lap count") || k.equals("lap") || k.equals("position")) return "Giro";
        if (k.contains("rpm") || k.equals("gear") || k.contains("max rpm") || k.contains("max power") || k.contains("max torque")
                || k.contains("limiter") || k.contains("engine brake") || k.contains("turbo boost")
                || k.contains("water temp") || k.contains("oil temp")) return "Motore";
        if (k.startsWith("ers") || k.startsWith("kers") || k.startsWith("drs") || k.contains("battery")) return "Ibrido (ERS/KERS/DRS)";
        if (k.contains("fuel")) return "Fuel";
        if (k.contains("ride height") || k.contains("suspension travel") || k.contains("max sus travel") || k.contains("bumpstop")) return "Sospensioni";
        if (k.contains("camber") || k.contains("caster") || k.contains("toe in") || k.contains("cg accel") || k.contains("cg height") ||
                k.contains("pitch") || k.contains("roll") || k.contains("yaw") || k.contains("chassis velocity") || k.contains("steer") || k.contains("brake bias")) return "Assetto & Dinamica";
        if (k.contains("tire ") || k.contains("tyre ") || k.contains("wheel angular speed") || k.contains("radius")) return "Gomme";
        if (k.contains("brake temp") || (k.contains("brake") && k.contains("force"))) return "Freni";
        if (k.contains("damage")) return "Danni";
        if (k.contains("air temp") || k.contains("air density") || k.contains("road temp") || k.contains("wind ") || k.contains("surface grip") || k.contains("ballast")) return "Meteo & Pista";
        if (k.contains("car coord") || k.contains("car pos norm") || k.equals("speed") || k.contains("ground speed") || k.contains("drive train speed")
                || k.contains("straight speed") || k.contains("corner speed")) return "Coordinate & Velocità";
        if (k.contains("raw data sample rate") || k.contains("sample clock") || k.equals("flags") || k.equals("in pit") || k.contains("lap invalidated")
                || k.contains("num tires off track") || k.contains("aid ") || k.contains("penalties enabled") || k.equals("marker") || k.contains("beacon")) return "Telemetria";
        return "Altro";
    }
}
