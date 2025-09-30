package org.simulator.ui;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.importCSVFW.CsvImporter;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DataController {

    private final CsvImporter importer = new CsvImporter();

    private List<Lap> laps = Collections.emptyList();
    private Path csvPath;
    private final Map<Integer, List<Double>> sectorMarksPerLap = new HashMap<>();

    public void load(Path path, Map<String, Channel> mapping) throws Exception {
        this.csvPath = path;
        this.laps = importer.importFile(path, mapping);
        sectorMarksPerLap.clear();

    }

    public List<Lap> getLaps() { return laps; }
    public Path getCsvPath() { return csvPath; }
    public Lap byIndex(int idx) {
        return laps.stream().filter(l -> l.index == idx).findFirst().orElse(null);
    }
    public List<Integer> lapIndices() {
        return laps.stream().map(l->l.index).collect(Collectors.toList());
    }
    public List<Double> sectorMarksForLap(int lapIdx) {
        return sectorMarksPerLap.getOrDefault(lapIdx, List.of());
    }
}
