package org.simulator.setup;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Snapshot di setup con utility per diff/merge/import (esteso, retro-compatibile). */
public final class SetupSnapshot {
    // ====== CAMPI ESISTENTI (invariati) ======
    public Double frontARB, rearARB;
    public Double camberF, camberR, toeF, toeR;
    public Double springF, springR, bumpF, bumpR, reboundF, reboundR;
    public Double diffPower, diffCoast, diffPreload;
    public Double brakeBias, brakeDuctsF, brakeDuctsR;
    public Double wingF, wingR;
    public Double psiFL, psiFR, psiRL, psiRR;

    // ====== NUOVI CAMPI OPZIONALI (tutti nullable) ======
    /** Ride height statico (mm) per macroassi, utile per rake/kerb handling. */
    public Double rideHeightF, rideHeightR;
    /** Livelli aiuti o mappature legate alla trazione/frenata. */
    public Double tcLevel, absLevel, brakePressure, engineBrake;
    /** Geometria aggiuntiva (gradi) se disponibile. */
    public Double casterF, casterR;
    /** Aero fine (rake target o “gurney/beam wing” se il sim lo espone in step generici). */
    public Double rakeTarget;
    /** Powertrain/turbo map se gestita come valore discreto/percentuale. */
    public Double turboMap;

    /** Esporta in mappa (ordinata). */
    public Map<String,Object> asMap(){
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("frontARB",frontARB); m.put("rearARB",rearARB);
        m.put("camberF",camberF); m.put("camberR",camberR); m.put("toeF",toeF); m.put("toeR",toeR);
        m.put("springF",springF); m.put("springR",springR); m.put("bumpF",bumpF); m.put("bumpR",bumpR);
        m.put("reboundF",reboundF); m.put("reboundR",reboundR);
        m.put("diffPower",diffPower); m.put("diffCoast",diffCoast); m.put("diffPreload",diffPreload);
        m.put("brakeBias",brakeBias); m.put("brakeDuctsF",brakeDuctsF); m.put("brakeDuctsR",brakeDuctsR);
        m.put("wingF",wingF); m.put("wingR",wingR);
        m.put("psiFL",psiFL); m.put("psiFR",psiFR); m.put("psiRL",psiRL); m.put("psiRR",psiRR);
        // nuovi
        m.put("rideHeightF",rideHeightF); m.put("rideHeightR",rideHeightR);
        m.put("tcLevel",tcLevel); m.put("absLevel",absLevel); m.put("brakePressure",brakePressure); m.put("engineBrake",engineBrake);
        m.put("casterF",casterF); m.put("casterR",casterR);
        m.put("rakeTarget",rakeTarget); m.put("turboMap",turboMap);
        return m;
    }

    /** Mappa solo con i campi valorizzati (comodo per export/patch). */
    public Map<String,Object> nonNullMap(){
        Map<String,Object> all = asMap();
        Map<String,Object> nn = new LinkedHashMap<>();
        for (var e: all.entrySet()){
            if (e.getValue()!=null) nn.put(e.getKey(), e.getValue());
        }
        return nn;
    }

    /** Import da mappa (tollerante a tipi numerici). */
    public static SetupSnapshot fromMap(Map<String,?> m){
        SetupSnapshot s = new SetupSnapshot();
        if (m == null) return s;
        s.frontARB     = num(m.get("frontARB"));
        s.rearARB      = num(m.get("rearARB"));
        s.camberF      = num(m.get("camberF"));
        s.camberR      = num(m.get("camberR"));
        s.toeF         = num(m.get("toeF"));
        s.toeR         = num(m.get("toeR"));
        s.springF      = num(m.get("springF"));
        s.springR      = num(m.get("springR"));
        s.bumpF        = num(m.get("bumpF"));
        s.bumpR        = num(m.get("bumpR"));
        s.reboundF     = num(m.get("reboundF"));
        s.reboundR     = num(m.get("reboundR"));
        s.diffPower    = num(m.get("diffPower"));
        s.diffCoast    = num(m.get("diffCoast"));
        s.diffPreload  = num(m.get("diffPreload"));
        s.brakeBias    = num(m.get("brakeBias"));
        s.brakeDuctsF  = num(m.get("brakeDuctsF"));
        s.brakeDuctsR  = num(m.get("brakeDuctsR"));
        s.wingF        = num(m.get("wingF"));
        s.wingR        = num(m.get("wingR"));
        s.psiFL        = num(m.get("psiFL"));
        s.psiFR        = num(m.get("psiFR"));
        s.psiRL        = num(m.get("psiRL"));
        s.psiRR        = num(m.get("psiRR"));
        // nuovi
        s.rideHeightF  = num(m.get("rideHeightF"));
        s.rideHeightR  = num(m.get("rideHeightR"));
        s.tcLevel      = num(m.get("tcLevel"));
        s.absLevel     = num(m.get("absLevel"));
        s.brakePressure= num(m.get("brakePressure"));
        s.engineBrake  = num(m.get("engineBrake"));
        s.casterF      = num(m.get("casterF"));
        s.casterR      = num(m.get("casterR"));
        s.rakeTarget   = num(m.get("rakeTarget"));
        s.turboMap     = num(m.get("turboMap"));
        return s;
    }

    /** Diff contro un altro snapshot: mappa key -> delta (this-other). */
    public Map<String, Double> diff(SetupSnapshot other){
        Map<String, Double> d = new LinkedHashMap<>();
        for (var e : this.asMap().entrySet()){
            String k = e.getKey();
            Double a = num(e.getValue());
            Double b = other==null ? null : num(other.asMap().get(k));
            if (a!=null && b!=null){
                double delta = a - b;
                if (Math.abs(delta) > 1e-9) d.put(k, round3(delta));
            } else if (a!=null || b!=null){
                // uno è null, segnala valore “nuovo”
                d.put(k, a!=null ? round3(a) : null);
            }
        }
        return d;
    }

    /** Merge: i campi NON null di override sostituiscono i nostri. */
    public SetupSnapshot mergedWith(SetupSnapshot override){
        if (override == null) return this;
        SetupSnapshot r = new SetupSnapshot();
        Map<String,Object> a = this.asMap();
        Map<String,Object> b = override.asMap();
        for (String k : a.keySet()){
            Object v = (b.get(k) != null) ? b.get(k) : a.get(k);
            r.asMap().put(k, v);
        }
        // Copia nei campi concreti
        return fromMap(r.asMap());
    }

    /** Arrotonda tutti i valori (utile per UI esport). */
    public SetupSnapshot rounded(int decimals){
        double pow = Math.pow(10, Math.max(0, decimals));
        SetupSnapshot r = new SetupSnapshot();
        for (var e : this.asMap().entrySet()){
            Double v = num(e.getValue());
            if (v != null) r.asMap().put(e.getKey(), Math.round(v*pow)/pow);
        }
        return fromMap(r.asMap());
    }

    /** Applica una mappa di delta (this + delta) restituendo un nuovo snapshot. */
    public SetupSnapshot applyDelta(Map<String, Double> delta){
        if (delta == null || delta.isEmpty()) return this;
        Map<String,Object> base = new LinkedHashMap<>(this.asMap());
        for (var e : delta.entrySet()){
            String k = e.getKey();
            Double dv = e.getValue();
            if (!base.containsKey(k) || dv == null) continue;
            Double cur = num(base.get(k));
            base.put(k, (cur==null ? dv : cur + dv));
        }
        return fromMap(base);
    }

    /** Riempie i null con una baseline (senza toccare i valori già presenti). */
    public SetupSnapshot withDefaults(Map<String, Double> defaults){
        if (defaults == null || defaults.isEmpty()) return this;
        Map<String,Object> base = new LinkedHashMap<>(this.asMap());
        for (var e: defaults.entrySet()){
            if (base.get(e.getKey()) == null && e.getValue()!=null) {
                base.put(e.getKey(), e.getValue());
            }
        }
        return fromMap(base);
    }

    /** Valida/clampa i valori secondo i range passati (es. slider UI) e restituisce un nuovo snapshot. */
    public SetupSnapshot validated(Map<String, Range> ranges){
        if (ranges == null || ranges.isEmpty()) return this;
        Map<String,Object> base = new LinkedHashMap<>(this.asMap());
        for (var e: ranges.entrySet()){
            String k = e.getKey();
            Range r = e.getValue();
            if (!base.containsKey(k) || r == null) continue;
            Double v = num(base.get(k));
            if (v != null) base.put(k, r.clamp(v));
        }
        return fromMap(base);
    }

    /** Diff leggibile “per aree”, utile per changelog/commit del setup. */
    public String prettyDiff(SetupSnapshot other){
        Map<String, Double> d = this.diff(other);
        if (d.isEmpty()) return "(nessuna differenza)";
        StringBuilder sb = new StringBuilder();
        section(sb, "Gomme",
                fmt(d,"psiFL"), fmt(d,"psiFR"), fmt(d,"psiRL"), fmt(d,"psiRR"));
        section(sb, "Freni",
                fmt(d,"brakeBias"), fmt(d,"brakePressure"), fmt(d,"brakeDuctsF"), fmt(d,"brakeDuctsR"), fmt(d,"absLevel"));
        section(sb, "Barre",
                fmt(d,"frontARB"), fmt(d,"rearARB"));
        section(sb, "Sospensioni",
                fmt(d,"springF"), fmt(d,"springR"), fmt(d,"bumpF"), fmt(d,"bumpR"),
                fmt(d,"reboundF"), fmt(d,"reboundR"), fmt(d,"rideHeightF"), fmt(d,"rideHeightR"));
        section(sb, "Geometria",
                fmt(d,"camberF"), fmt(d,"camberR"), fmt(d,"toeF"), fmt(d,"toeR"),
                fmt(d,"casterF"), fmt(d,"casterR"), fmt(d,"rakeTarget"));
        section(sb, "Differenziale",
                fmt(d,"diffPower"), fmt(d,"diffCoast"), fmt(d,"diffPreload"), fmt(d,"engineBrake"), fmt(d,"tcLevel"));
        section(sb, "Aero/Powertrain",
                fmt(d,"wingF"), fmt(d,"wingR"), fmt(d,"turboMap"));
        String out = sb.toString().trim();
        return out.isEmpty() ? "(nessuna differenza)" : out;
    }

    @Override public String toString(){ return asMap().toString(); }

    // --- helpers & types ---
    private static Double num(Object o){
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); }
        catch (Exception ignored){ return null; }
    }
    private static double round3(double d){ return Math.round(d*1000.0)/1000.0; }

    /** Range chiuso [min,max] con clamp. */
    public static final class Range {
        public final double min, max;
        public Range(double min, double max){
            this.min = min; this.max = max;
        }
        public double clamp(double v){
            if (Double.isNaN(v)) return v;
            return Math.max(min, Math.min(max, v));
        }
        @Override public String toString(){ return "["+min+","+max+"]"; }
        @Override public boolean equals(Object o){
            if (this == o) return true;
            if (!(o instanceof Range r)) return false;
            return Double.compare(r.min, min)==0 && Double.compare(r.max, max)==0;
        }
        @Override public int hashCode(){ return Objects.hash(min,max); }
    }

    private static void section(StringBuilder sb, String title, String... items){
        StringBuilder tmp = new StringBuilder();
        for (String it : items) if (it != null && !it.isBlank()) tmp.append("  • ").append(it).append('\n');
        if (tmp.length()>0){
            sb.append(title).append(":\n").append(tmp);
        }
    }
    private static String fmt(Map<String,Double> d, String k){
        if (!d.containsKey(k)) return null;
        Double v = d.get(k);
        if (v == null) return k + " = (nuovo)";
        return k + (v>=0? " += " : " -= ") + Math.abs(round3(v));
    }
}
