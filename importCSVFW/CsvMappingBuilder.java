package org.simulator.importCSVFW;

import org.simulator.canale.Channel;

import java.util.HashMap;
import java.util.Map;

class CsvMappingBuilder {

    /** Costruisce il mapping indice-colonna → Channel (rispetta eventuale userMapping). */
    static Map<Integer, Channel> buildIndexMapping(String[] header, Map<String, Channel> userMapping) {
        Map<Integer, Channel> map = new HashMap<>();

        // dizionario alias
        for (int i = 0; i < header.length; i++) {
            String h = header[i] == null ? "" : header[i].trim();
            Channel ch = ChannelAliases.ALIAS_MAP.get(ChannelAliases.norm(h));
            if (ch != null) map.put(i, ch);
        }

        // override espliciti dall'utente (se forniti)
        if (userMapping != null) {
            for (int i = 0; i < header.length; i++) {
                Channel ch = userMapping.get(header[i]);
                if (ch != null) map.put(i, ch);
            }
        }
        return map;
    }
}
