package org.simulator.canale;

// Sample.java

import java.util.EnumMap;

public record Sample(double timestamp, double distance, EnumMap<Channel, Double> values) { }

