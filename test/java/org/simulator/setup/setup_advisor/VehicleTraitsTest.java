package org.simulator.setup.setup_advisor;

import org.junit.jupiter.api.Test;
import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VehicleTraitsTest {

    @Test
    void testFormulaDetectionByRPM() {
        // ARRANGE: Un giro con RPM molto alti (13.000) tipici di una F1
        Lap formulaLap = createDummyLap(Channel.ENGINE_RPM, 13000.0);
        List<Lap> laps = Collections.singletonList(formulaLap);

        // ACT
        VehicleTraits traits = VehicleTraits.detect(laps);

        // ASSERT
        assertEquals(VehicleTraits.Category.FORMULA, traits.category,
                "Un motore a 13.000 RPM deve essere classificato come FORMULA");
    }

    @Test
    void testGtDetectionBySpeed() {
        // ARRANGE: Un giro veloce (280 km/h) ma RPM normali (8000)
        // Simuliamo un'auto GT3 veloce
        Lap gtLap = createDummyLap(Channel.SPEED, 280.0);
        // Sovrascriviamo RPM per essere sicuri che non sia Formula
        gtLap.samples.forEach(s -> s.values().put(Channel.ENGINE_RPM, 8000.0));

        List<Lap> laps = Collections.singletonList(gtLap);

        // ACT
        VehicleTraits traits = VehicleTraits.detect(laps);

        // ASSERT
        // La logica di detect usa maxSpeed >= 260 && RPM < 12500 -> GT
        assertEquals(VehicleTraits.Category.GT, traits.category,
                "Alta velocità (280km/h) con RPM < 12.5k deve essere GT");
    }

    private Lap createDummyLap(Channel ch, double val) {
        EnumMap<Channel, Double> map = new EnumMap<>(Channel.class);
        map.put(ch, val);
        // Valori di default per evitare crash
        if (ch != Channel.ENGINE_RPM) map.put(Channel.ENGINE_RPM, 5000.0);
        if (ch != Channel.SPEED) map.put(Channel.SPEED, 100.0);

        Sample s = new Sample(0.0, 0.0, map);
        List<Sample> samples = new ArrayList<>();
        samples.add(s);

        // Costruttore corretto: (index, samples)
        return new Lap(1, samples);
    }
}