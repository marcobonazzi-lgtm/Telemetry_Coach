package org.simulator.setup.setup_advisor;

import org.junit.jupiter.api.Test;
import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SetupLapRecommenderTest {

    @Test
    void testPressureSuggestion() {
        // ARRANGE: Creiamo un Lap finto con pressione molto bassa (20 PSI)
        Lap dummyLap = createDummyLap(Channel.TIRE_PRESSURE_FL, 20.0);

        // ACT: Chiamiamo il Recommender usando lo stile NEUTRAL
        // Nota: SetupAdvisor.Recommendation è una classe statica interna
        List<SetupAdvisor.Recommendation> recs = SetupLapRecommender.forLap(dummyLap, SetupAdvisor.DriverStyle.NEUTRAL);

        // ASSERT: Verifichiamo che tra i consigli ci sia quello sulle pressioni basse
        boolean foundPressureWarning = recs.stream()
                .anyMatch(r -> r.area().equals("Pressioni") && r.message().contains("Pressioni globalmente basse"));

        assertTrue(foundPressureWarning, "Il sistema deve rilevare pressioni basse (20 PSI) e suggerire correzioni");
    }

    // Helper per creare un giro finto con valori costanti
    private Lap createDummyLap(Channel ch, double val) {
        EnumMap<Channel, Double> map = new EnumMap<>(Channel.class);
        // Popoliamo il canale di test
        map.put(ch, val);

        // Popoliamo altri canali essenziali per evitare NullPointer o NaN nelle medie
        map.put(Channel.SPEED, 100.0);       // Velocità media
        map.put(Channel.ENGINE_RPM, 5000.0);        // RPM medi
        map.put(Channel.THROTTLE, 100.0);    // Gas pieno (per attivare logiche trazione)
        map.put(Channel.STEER_ANGLE, 0.0);   // Dritto

        // Setup temperature gomme (valori sani per non triggerare altri warning)
        map.put(Channel.TIRE_TEMP_CORE_FL, 80.0); map.put(Channel.TIRE_TEMP_CORE_FR, 80.0);
        map.put(Channel.TIRE_TEMP_CORE_RL, 80.0); map.put(Channel.TIRE_TEMP_CORE_RR, 80.0);

        // Creiamo una lista di 10 campioni identici
        Sample s = new Sample(0.0, 0.0, map);
        List<Sample> samples = new ArrayList<>();
        for(int i=0; i<10; i++) samples.add(s);

        // Usiamo il costruttore corretto di Lap: (int index, List<Sample> samples)
        return new Lap(1, samples);
    }
}