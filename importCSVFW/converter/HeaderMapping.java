package org.simulator.importCSVFW.converter;

import java.util.Map;

/**
 * Interfaccia che definisce la mappatura da un set di intestazioni (LMU)
 * a un altro (Assetto Corsa).
 */
public interface HeaderMapping {
    /**
     * @return Mappa colonnaLMU -> colonnaAC
     */
    Map<String, String> map();
}
