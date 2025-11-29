package org.simulator.ui.export;

import org.simulator.canale.Channel;
import org.simulator.ui.DataController;

import java.util.Map;

/** Ritorna le unità lette dal CSV: Channel -> Unit string (es. "km/h", "°C"). */
public interface UnitsProvider {
    Map<Channel, String> units(DataController data);
}
