package org.simulator.tracks;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/** Caricatore dei JSON statici delle piste (nessuna dipendenza dai grafici). */
public final class StaticTrackDB {
    private StaticTrackDB(){}

    /** Caricamento classico: invariato. */
    public static TrackInfo get(String id){
        Map<String,Object> base = loadRootMap(id);
        if (base == null) return null;
        return parse(base);
    }


    /** Ritorna la lista degli id variante disponibili (ordine di definizione). */
    public static List<String> getVariantIds(String id){
        Map<String,Object> root = loadRootMap(id);
        if (root == null) return List.of();
        Object v = root.get("variants");
        if (!(v instanceof Map)) return List.of();
        @SuppressWarnings("unchecked")
        Map<String,Object> vm = (Map<String,Object>) v;
        return new ArrayList<>(vm.keySet());
    }

    /** Ritorna l’id della variante di default, se presente. */
    public static String getDefaultVariantId(String id){
        Map<String,Object> root = loadRootMap(id);
        if (root == null) return null;
        Object dv = root.get("default_variant");
        return (dv instanceof String && !((String)dv).isBlank()) ? (String) dv : null;
    }

    /**
     * Carica una variante come TrackInfo facendo overlay dei campi della variante
     * sopra al root. Le chiavi assenti nella variante ereditano dal root.
     */
    public static TrackInfo getVariant(String id, String variantId){
        Map<String,Object> root = loadRootMap(id);
        if (root == null) return null;
        Object vs = root.get("variants");
        if (!(vs instanceof Map)) return null;
        @SuppressWarnings("unchecked")
        Map<String,Object> variants = (Map<String,Object>) vs;
        Object overlay = variants.get(variantId);
        if (!(overlay instanceof Map)) return null;

        // copia profonda del root e merge overlay
        @SuppressWarnings("unchecked")
        Map<String,Object> merged = deepCopy(root);
        // non propagare il nodo "variants" dentro la variante istanziata
        merged.remove("variants");
        merged.remove("default_variant");
        @SuppressWarnings("unchecked")
        Map<String,Object> ov = (Map<String,Object>) overlay;
        deepMerge(merged, ov);
        return parse(merged);
    }

    /* ===================== IMPLEMENTAZIONE ===================== */

    @SuppressWarnings("unchecked")
    private static Map<String,Object> loadRootMap(String id){
        if (id==null || id.isBlank()) return null;
        String res = "/assets/tracks/" + id.toLowerCase(Locale.ROOT) + ".json";
        try (InputStream is = StaticTrackDB.class.getResourceAsStream(res)){
            if (is==null) return null;
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Object root = SimpleJson.parse(json);
            if (!(root instanceof Map)) return null;
            return (Map<String,Object>) root;
        } catch (Exception e){ return null; }
    }

    @SuppressWarnings("unchecked")
    private static TrackInfo parse(Map<String,Object> m){
        String id = (String) m.getOrDefault("id","");
        String name = (String) m.getOrDefault("displayName", id);
        double lenKm = toD(m.get("length_km"), 0.0);
        List<Double> splits = ((List<Object>) m.getOrDefault("sector_splits", List.of(1.0)))
                .stream().map(o->toD(o,1.0)).collect(Collectors.toList());
        String img = (String) m.getOrDefault("image", null);
        boolean imgHasNumbers = Boolean.TRUE.equals(m.get("image_has_numbers"));
        double start = toD(m.get("start_finish_norm"), 0.0);
        Double pitIn = m.containsKey("pit_entry_norm") ? toD(m.get("pit_entry_norm"), 0.0) : null;
        Double pitOut = m.containsKey("pit_exit_norm") ? toD(m.get("pit_exit_norm"), 0.0) : null;
        String desc = (String) m.getOrDefault("description", "");

        List<TrackInfo.Turn> turns = new ArrayList<>();
        List<Object> arr = (List<Object>) m.getOrDefault("turns", List.of());
        for (Object o : arr){
            Map<String,Object> t = (Map<String,Object>) o;
            int num = (int) Math.round(toD(t.get("n"),0));
            String tname = (String) t.getOrDefault("name", "T"+num);
            double pos = toD(t.get("pos_norm"), (num-1)/10.0);
            Map<String,Object> posImg = (Map<String,Object>) t.getOrDefault("img", Map.of());
            double ix = toD(posImg.get("x"), 0.5), iy = toD(posImg.get("y"), 0.5);

            Map<String,TrackInfo.Advice> advice = new HashMap<>();
            Map<String,Object> adv = (Map<String,Object>) t.getOrDefault("advice", Map.of());
            for (String k : adv.keySet()){
                Map<String,Object> a = (Map<String,Object>) adv.get(k);
                Integer g = a.containsKey("gear") ? ((Number)a.get("gear")).intValue() : null;
                Double v = a.containsKey("v_min_ideal_kmh") ? ((Number)a.get("v_min_ideal_kmh")).doubleValue() : null;
                Double r = a.containsKey("v_range_kmh") ? ((Number)a.get("v_range_kmh")).doubleValue() : null;
                String note = (String) a.getOrDefault("note","");
                advice.put(k.toUpperCase(Locale.ROOT), new TrackInfo.Advice(g, v, r, note));
            }
            turns.add(new TrackInfo.Turn(num, tname, pos, ix, iy, advice));
        }

        return new TrackInfo(id, name, lenKm, splits, img, imgHasNumbers, start, pitIn, pitOut, desc, turns);
    }

    private static double toD(Object o, double def){ return o==null? def : ((Number)o).doubleValue(); }

    /* ---------------- Deep copy / merge utility ---------------- */

    @SuppressWarnings("unchecked")
    private static Map<String,Object> deepCopy(Map<String,Object> src){
        Map<String,Object> out = new LinkedHashMap<>();
        for (Map.Entry<String,Object> e : src.entrySet()){
            Object v = e.getValue();
            if (v instanceof Map){
                out.put(e.getKey(), deepCopy((Map<String,Object>) v));
            } else if (v instanceof List){
                out.put(e.getKey(), new ArrayList<>((List<Object>) v));
            } else {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void deepMerge(Map<String,Object> base, Map<String,Object> overlay){
        for (Map.Entry<String,Object> e : overlay.entrySet()){
            String k = e.getKey();
            Object v = e.getValue();
            if (v instanceof Map && base.get(k) instanceof Map){
                deepMerge((Map<String,Object>) base.get(k), (Map<String,Object>) v);
            } else {
                base.put(k, v);
            }
        }
    }
}
