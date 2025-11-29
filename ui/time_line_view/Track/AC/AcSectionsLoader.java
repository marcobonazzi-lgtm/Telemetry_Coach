package org.simulator.ui.time_line_view.Track.AC;

import javafx.scene.image.Image;
import org.simulator.ui.time_line_view.Track.TrackLayout;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.text.Normalizer;
import java.util.regex.Pattern;

public final class AcSectionsLoader {
    private AcSectionsLoader(){}

    public static AcMapData loadMap(String venue) {
        Path folder = findTrackFolderByVenue(venue);
        if (folder == null) return null;

        Path iniPath = folder.resolve("map.png");
        Path cfgPath = folder.resolve("data/map.ini");
        if (!Files.isRegularFile(cfgPath)) cfgPath = folder.resolve("map.ini");

        if (!Files.isRegularFile(iniPath) || !Files.isRegularFile(cfgPath)) return null;

        try {
            Map<String, String> ini = loadIniMap(cfgPath);
            double width = parseDouble(ini.get("WIDTH"));
            double height = parseDouble(ini.get("HEIGHT"));
            double xOff = parseDouble(ini.get("X_OFFSET"));
            double zOff = parseDouble(ini.get("Z_OFFSET"));
            double margin = parseDouble(ini.get("MARGIN"));

            if (Double.isNaN(width) || Double.isNaN(height)) return null;

            Image img = new Image(Files.newInputStream(iniPath));
            return new AcMapData(img, xOff, zOff, width, height, margin);
        } catch (Exception e) {
            return null;
        }
    }

    public static AcMapData loadMapFromPath(Path folder) {
        Path iniPath = folder.resolve("map.png");
        Path cfgPath = folder.resolve("data/map.ini");
        if (!Files.isRegularFile(cfgPath)) cfgPath = folder.resolve("map.ini");
        if (!Files.isRegularFile(iniPath) || !Files.isRegularFile(cfgPath)) return null;
        try {
            Map<String, String> ini = loadIniMap(cfgPath);
            double w = parseDouble(ini.get("WIDTH")); double h = parseDouble(ini.get("HEIGHT"));
            double x = parseDouble(ini.get("X_OFFSET")); double z = parseDouble(ini.get("Z_OFFSET"));
            double m = parseDouble(ini.get("MARGIN"));
            if (Double.isNaN(w) || Double.isNaN(h)) return null;
            return new AcMapData(new Image(Files.newInputStream(iniPath)), x, z, w, h, m);
        } catch (Exception e) { return null; }
    }

    public static java.util.List<AcSection> tryLoadFromVenue(String venue){
        Path folder = findTrackFolderByVenue(venue);
        if (folder == null) return java.util.List.of();

        Path ini = folder.resolve("data/sections.ini");
        if (!Files.isRegularFile(ini)) ini = folder.resolve("sections.ini");

        if (!Files.isRegularFile(ini)) return java.util.List.of();
        try { return load(ini); } catch (IOException e) { return java.util.List.of(); }
    }

    public static Path findTrackFolderByVenue(String venue){
        String root = System.getProperty("ac.tracks.dir", "E:/SteamLibrary/steamapps/common/assettocorsa/content/tracks");
        Path base = Paths.get(root);
        if (!Files.isDirectory(base)) return null;

        String want = normalizeVenue(venue);
        // Se normalize ritorna vuoto o troppo corto, è inutile cercare
        if (want.length() < 3) return null;

        List<Path> allTracks;
        try {
            allTracks = Files.list(base).filter(Files::isDirectory).collect(Collectors.toList());
        } catch (IOException e) { return null; }

        // 1. Match Esatto (es. "ks_vallelunga")
        for (Path p : allTracks) {
            String dirName = p.getFileName().toString().toLowerCase();
            if (dirName.equals(want)) return p;
        }

        // 2. Match "ks_" + nome (es. cerco "vallelunga", trovo "ks_vallelunga")
        for (Path p : allTracks) {
            String dirName = p.getFileName().toString().toLowerCase();
            if (dirName.equals("ks_" + want)) return p;
        }

        // 3. Match Contiene (es. "vallelunga" in "ks_vallelunga") - SOLO se il nome cercato è pulito
        for (Path p : allTracks) {
            String dirName = p.getFileName().toString().toLowerCase();
            if (dirName.contains(want)) return p;

            // Check JSON name
            Path ui = p.resolve("css/ui_track.json");
            if (Files.isRegularFile(ui)) {
                try {
                    String name = extractJsonName(Files.readString(ui));
                    if (normalizeVenue(name).contains(want)) return p;
                } catch(Exception ignored){}
            }
        }

        return null;
    }

    // --- SCAN LAYOUTS (Per Varianti) ---
    public static List<TrackLayout> scanLayouts(String venue) {
        List<TrackLayout> layouts = new ArrayList<>();
        Path rootFolder = findTrackFolderByVenue(venue);
        if (rootFolder == null || !Files.isDirectory(rootFolder)) return layouts;

        // Layout Root (Main)
        if (hasTrackData(rootFolder)) {
            String name = "Main Layout";
            Path ui = rootFolder.resolve("css/ui_track.json");
            if (Files.exists(ui)) { try { name = extractJsonName(Files.readString(ui)); } catch(Exception e){} }
            layouts.add(new TrackLayout(name, rootFolder));
        }

        // Layouts Subfolders (es. classic, club)
        try {
            Files.list(rootFolder).filter(Files::isDirectory).forEach(sub -> {
                if (hasTrackData(sub)) {
                    String name = sub.getFileName().toString();
                    Path ui = sub.resolve("css/ui_track.json");
                    if (Files.exists(ui)) { try { name = extractJsonName(Files.readString(ui)); } catch(Exception e){} }
                    layouts.add(new TrackLayout(name, sub));
                }
            });
        } catch (IOException e) {}
        return layouts;
    }

    private static boolean hasTrackData(Path folder) {
        boolean hasAi = Files.exists(folder.resolve("ai/fast_lane.ai")) || Files.exists(folder.resolve("data/ai/fast_lane.ai"));
        boolean hasMap = Files.exists(folder.resolve("map.png")) || Files.exists(folder.resolve("data/map.ini"));
        return hasAi || hasMap || Files.isDirectory(folder.resolve("data"));
    }

    public static java.util.List<AcSection> load(Path sectionsIni) throws IOException {
        List<AcSection> list = new ArrayList<>();
        List<String> lines = Files.readAllLines(sectionsIni);
        double in = Double.NaN, out = Double.NaN; String name = null;
        for (String L : lines){
            String s = L.trim();
            if (s.isEmpty() || s.startsWith(";") || s.startsWith("#")) continue;
            if (s.startsWith("[")) {
                if (name!=null && !Double.isNaN(in) && !Double.isNaN(out)){
                    list.add(new AcSection(name, clamp01(in), clamp01(out)));
                }
                name=null; in=out=Double.NaN; continue;
            }
            String[] kv = s.split("=", 2); if (kv.length != 2) continue;
            String k = kv[0].trim().toUpperCase(Locale.ROOT); String v = kv[1].trim();
            switch (k){
                case "IN" -> in = parseDouble(v);
                case "OUT" -> out = parseDouble(v);
                case "TEXT" -> name = v;
            }
        }
        if (name!=null && !Double.isNaN(in) && !Double.isNaN(out)) list.add(new AcSection(name, clamp01(in), clamp01(out)));
        return list;
    }

    private static Map<String, String> loadIniMap(Path p) throws IOException {
        Map<String, String> m = new HashMap<>();
        List<String> lines = Files.readAllLines(p);
        for (String l : lines) {
            l = l.trim();
            if (l.isEmpty() || l.startsWith(";") || l.startsWith("[")) continue;
            String[] kv = l.split("=", 2);
            if (kv.length == 2) m.put(kv[0].trim().toUpperCase(), kv[1].trim());
        }
        return m;
    }

    // --- HELPER NORMALIZE VENUE (POTENZIATO) ---
    public static String normalizeVenue(String s){
        if (s == null) return "";
        String t = s.toLowerCase(Locale.ROOT);

        // 1. Taglia tutto ciò che segue i separatori comuni nei file telemetrici
        if (t.contains("_&_")) t = t.substring(0, t.indexOf("_&_"));
        if (t.contains("&")) t = t.substring(0, t.indexOf("&"));
        // 2. Rimuovi estensioni .csv
        if (t.endsWith(".csv")) t = t.substring(0, t.lastIndexOf(".csv"));

        // 3. Pulizia caratteri
        t = deAccent(t);
        t = t.replaceAll("[^a-z0-9_ ]"," ").trim(); // Mantieni underscore per ks_vallelunga

        // 4. Se rimane solo "ks_vallelunga", è perfetto.
        return t;
    }

    private static String deAccent(String str) {
        String nfdNormalizedString = Normalizer.normalize(str, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfdNormalizedString).replaceAll("");
    }

    private static boolean matchSlug(String s, String want){ return s.contains(want) || want.contains(s); }
    private static String extractJsonName(String json){
        if (json == null) return "";
        var m = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find()? m.group(1) : "";
    }
    private static double parseDouble(String s){ try { return Double.parseDouble(s); } catch (Exception e) { return Double.NaN; } }
    private static double clamp01(double v){ return Math.max(0.0, Math.min(1.0, v)); }
    // Aggiungi questo metodo statico
    public static List<AcDrsZone> loadDrsZones(Path trackFolder) {
        if (trackFolder == null) return List.of();

        // Il file può essere nella root o in /data
        Path ini = trackFolder.resolve("data/drs_zones.ini");
        if (!Files.isRegularFile(ini)) ini = trackFolder.resolve("drs_zones.ini");

        if (!Files.isRegularFile(ini)) return List.of();

        List<AcDrsZone> zones = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(ini);
            double start = Double.NaN, end = Double.NaN;

            for (String L : lines) {
                String s = L.trim();
                if (s.isEmpty() || s.startsWith(";") || s.startsWith("#")) continue;

                if (s.startsWith("[")) {
                    // Salva la zona precedente se valida
                    if (!Double.isNaN(start) && !Double.isNaN(end)) {
                        zones.add(new AcDrsZone(clamp01(start), clamp01(end)));
                    }
                    start = Double.NaN; end = Double.NaN;
                    continue;
                }

                String[] kv = s.split("=", 2);
                if (kv.length != 2) continue;
                String k = kv[0].trim().toUpperCase(Locale.ROOT);
                String v = kv[1].trim();

                // AC usa "START" ed "END" nel drs_zones.ini
                if (k.equals("START")) start = parseDouble(v);
                else if (k.equals("END")) end = parseDouble(v);
            }
            // Aggiungi l'ultima se c'è
            if (!Double.isNaN(start) && !Double.isNaN(end)) {
                zones.add(new AcDrsZone(clamp01(start), clamp01(end)));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return zones;
    }
}