package org.simulator.importCSVFW;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

/** Estrazione "venue/track name" da CSV (header oppure righe commento/meta). */
public final class CsvVenueExtractor {
    private CsvVenueExtractor(){}

    private static final Pattern META_KV = Pattern.compile(
            "^(?:#|;|//)?\\s*(venue|track|circuit|venue name|track name)\\s*[:=,;\\t]\\s*\"?\\s*([^\";,#]+?)\\s*\"?\\s*$",
            Pattern.CASE_INSENSITIVE);


    /** Scansiona le prime ~300 righe del file cercando "Venue: Mugello", "Track,Mugello", ecc. */
    public static String fromFile(Path csvPath) {
        if (csvPath == null) return null;
        try (BufferedReader br = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String line;
            int lines = 0;
            while ((line = br.readLine()) != null && lines++ < 300) {
                String s = line.strip();
                if (s.isEmpty()) continue;

                // 1) Pattern tipo "Venue: Mugello" / "Track,Mugello" / "Venue;Mugello"
                var m = META_KV.matcher(s);
                if (m.find()) {
                    String value = clean(m.group(2));
                    if (!value.isBlank()) return value;
                }

                // 2) Righe CSV tipo "Venue, Mugello" (senza commento davanti)
                //    Teniamolo molto permissivo, ma solo se il token chiave è la prima cella.
                int sep = firstSeparatorIndex(s);
                if (sep > 0) {
                    String k = norm(s.substring(0, sep));
                    if (k.equals("venue") || k.equals("track") || k.equals("circuit")
                            || k.equals("venue name") || k.equals("track name")) {
                        String value = clean(s.substring(sep + 1));
                        if (!value.isBlank()) return value;
                    }
                }

                // 3) interrompi se arriviamo all’header tabellare vero (tipico: contiene "time,speed,lap,...")
                //    così non sprechiamo tempo nel bulk dei dati.
                if (looksLikeDataHeader(s)) break;
            }
        } catch (Exception ignore) {}
        return null;
    }

    // ---------------- helpers ----------------
    private static String norm(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase(Locale.ROOT);
        t = t.replace('_', ' ').replace('-', ' ');
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }

    private static String clean(String s) {
        if (s == null) return "";
        String t = s.strip();
        if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
            t = t.substring(1, t.length() - 1);
        }
        // rimuovi eventuali tag tipo "layout", lasciamo il nome grezzo (ci pensa normalizeTrackId più avanti)
        return t.strip();
    }

    private static boolean looksLikeDataHeader(String s) {
        // euristica: se contiene diversi token tipici dei canali, fermiamoci.
        String ns = s.toLowerCase(Locale.ROOT);
        int hits = 0;
        if (ns.contains("time")) hits++;
        if (ns.contains("lap")) hits++;
        if (ns.contains("speed")) hits++;
        if (ns.contains("throttle")) hits++;
        if (ns.contains("brake")) hits++;
        if (ns.contains("rpm")) hits++;
        return hits >= 3;
    }

    private static int firstSeparatorIndex(String s) {
        int c = s.indexOf(','); if (c >= 0) return c;
        int sc = s.indexOf(';'); if (sc >= 0) return sc;
        int t = s.indexOf('\t'); if (t >= 0) return t;
        int p = s.indexOf('|'); if (p >= 0) return p;
        return -1;
    }
}
