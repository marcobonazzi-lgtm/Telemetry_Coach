package org.simulator.ui.export;

import java.util.List;

public record ExportOptions(
        // Laps da includere
        LapSelection lapSelection,
        List<Integer> selectedLaps,
        Integer singleLap,

        // Sezioni
        boolean includeCircuit,
        boolean includeCircuitNotes,
        CoachingScope coachingScope,
        boolean includeSetup,
        SetupScope setupScope,

        // Tabelle
        boolean includePrimaryTable,
        boolean includeSecondaryTable,

        // Opzioni Grafici (NEW)
        boolean includeDeltaChart,

        // Variante circuito
        String trackVariantId
) {}