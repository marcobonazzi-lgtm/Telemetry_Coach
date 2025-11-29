package org.simulator.ui.export;

public enum LapSelection {
    ALL,        // Tutti i giri (inclusi In/Out/Incompleti)
    ALL_VALID,  // Solo giri completi e validi (NUOVO)
    SELECTED,   // Selezione manuale multipla
    SINGLE,     // Singolo giro specifico
    BEST,       // Giro migliore
    WORST       // Giro peggiore
}