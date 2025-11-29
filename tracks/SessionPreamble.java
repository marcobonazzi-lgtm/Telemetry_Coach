package org.simulator.tracks;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Path;

public final class SessionPreamble {
    public final String venue;
    public final String vehicle;
    private SessionPreamble(String venue, String vehicle){ this.venue=venue; this.vehicle=vehicle; }

    public static SessionPreamble parse(Path csv){
        if (csv==null) return new SessionPreamble(null,null);
        String venue=null, vehicle=null;
        try (BufferedReader br = new BufferedReader(new FileReader(csv.toFile()))){
            String line; int safety=0;
            while ((line=br.readLine())!=null && safety++<40){
                String l = line.toLowerCase(java.util.Locale.ROOT);
                if (l.startsWith("\"venue\""))   venue  = val(line);
                if (l.startsWith("\"vehicle\"")) vehicle= val(line);
                if (l.startsWith("\"time\"")) break;
            }
        } catch (Exception ignore){}
        return new SessionPreamble(venue, vehicle);
    }
    private static String val(String line){
        String[] p = line.split(",");
        if (p.length>=2) return p[1].replace("\"","").trim();
        return null;
    }
}
