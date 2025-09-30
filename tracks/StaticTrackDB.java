package org.simulator.tracks;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/** Caricatore dei JSON statici delle piste (nessuna dipendenza dai grafici). */
public final class StaticTrackDB {
    private StaticTrackDB(){}

    public static TrackInfo get(String id){
        if (id==null || id.isBlank()) return null;
        String res = "/assets/tracks/" + id.toLowerCase(Locale.ROOT) + ".json";
        try (InputStream is = StaticTrackDB.class.getResourceAsStream(res)){
            if (is==null) return null;
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return parse(json);
        } catch (Exception e){ return null; }
    }

    @SuppressWarnings("unchecked")
    private static TrackInfo parse(String json){
        Map<String,Object> m = SimpleJson.parse(json);
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
}
