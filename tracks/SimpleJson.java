package org.simulator.tracks;
import java.util.*;
final class SimpleJson {
    private SimpleJson(){}
    public static Map<String,Object> parse(String json){
        Tok t=new Tok(json); Object v=val(t);
        if(!(v instanceof Map)) throw new IllegalArgumentException("Root non è oggetto");
        return (Map<String,Object>) v;
    }
    static Object val(Tok t){
        t.ws(); char c=t.peek();
        if(c=='{') return obj(t);
        if(c=='[') return arr(t);
        if(c=='"') return str(t);
        if(c=='t'||c=='f') return bool(t);
        if(c=='n'){ t.read("null"); return null; }
        return num(t);
    }
    static Map<String,Object> obj(Tok t){
        Map<String,Object> m=new LinkedHashMap<>();
        t.read('{'); t.ws(); if(t.peek()=='}'){ t.read('}'); return m; }
        while(true){ String k=str(t); t.ws(); t.read(':');
            Object v=val(t); m.put(k,v); t.ws();
            if(t.peek()=='}'){ t.read('}'); break; } t.read(',');
        } return m;
    }
    static List<Object> arr(Tok t){
        List<Object> a=new ArrayList<>(); t.read('['); t.ws(); if(t.peek()==']'){ t.read(']'); return a; }
        while(true){ a.add(val(t)); t.ws(); if(t.peek()==']'){ t.read(']'); break; } t.read(','); }
        return a;
    }
    static String str(Tok t){
        t.read('"'); StringBuilder b=new StringBuilder();
        while(true){ char c=t.next(); if(c=='"') break;
            if(c=='\\'){ char e=t.next();
                switch(e){ case '"': b.append('"'); break; case '\\': b.append('\\'); break; case '/': b.append('/'); break;
                    case 'b': b.append('\b'); break; case 'f': b.append('\f'); break; case 'n': b.append('\n'); break;
                    case 'r': b.append('\r'); break; case 't': b.append('\t'); break;
                    case 'u': String h=""+t.next()+t.next()+t.next()+t.next(); b.append((char)Integer.parseInt(h,16)); break;
                    default: throw new RuntimeException("escape: \\"+e);
                } } else b.append(c); } return b.toString();
    }
    static Boolean bool(Tok t){ if(t.peek()=='t'){ t.read("true"); return true; } t.read("false"); return false; }
    static Number num(Tok t){ int s=t.pos; char c; do{ c=t.next(); } while("0123456789+-.eE".indexOf(c)>=0);
        t.pos--; String S=t.src.substring(s,t.pos);
        if(S.contains(".")||S.contains("e")||S.contains("E")) return Double.parseDouble(S);
        try{ return Integer.parseInt(S);}catch(Exception e){ return Long.parseLong(S);} }
    static final class Tok { final String src; int pos=0; Tok(String s){src=s;}
        void ws(){ while(pos<src.length() && Character.isWhitespace(src.charAt(pos))) pos++; }
        char peek(){ ws(); return src.charAt(pos); }
        char next(){ return src.charAt(pos++); }
        void read(char c){ ws(); if(src.charAt(pos++)!=c) throw new RuntimeException("atteso "+c); }
        void read(String s){ ws(); if(!src.startsWith(s,pos)) throw new RuntimeException("atteso "+s); pos+=s.length(); }
    }
}
