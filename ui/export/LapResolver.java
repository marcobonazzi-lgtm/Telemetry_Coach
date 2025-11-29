package org.simulator.ui.export;

import org.simulator.canale.Lap;

import java.util.*;
import java.util.stream.Collectors;

final class LapResolver {
    private LapResolver(){}

    static List<Lap> resolveLaps(List<Lap> all, ExportOptions opts){
        if (all == null) return List.of();
        switch (opts.lapSelection()){
            case ALL:
                return all;
            case ALL_VALID:
                return all.stream().filter(l -> l.isComplete(all)).collect(Collectors.toList());
            case SELECTED:
                return filterByIndex(all, new HashSet<>(opts.selectedLaps()));
            case SINGLE:
                return filterByIndex(all, opts.singleLap() == null ? Set.of() : Set.of(opts.singleLap()));
            case BEST:
                return best(all).map(List::of).orElse(List.of());
            case WORST:
                return worst(all).map(List::of).orElse(List.of());
            default:
                return List.of();
        }
    }

    private static List<Lap> filterByIndex(List<Lap> all, Set<Integer> indices){
        if (indices == null || indices.isEmpty()) return List.of();
        return all.stream().filter(l -> indices.contains(l.index)).collect(Collectors.toList());
    }

    static Optional<Lap> best(List<Lap> laps){
        return laps.stream()
                .filter(l -> l != null && l.isComplete(laps))
                .min(Comparator.comparingDouble(Lap::lapTimeSafe));
    }

    static Optional<Lap> worst(List<Lap> laps){
        int firstIdx = laps.stream().filter(Objects::nonNull)
                .mapToInt(l -> l.index).min().orElse(Integer.MIN_VALUE);

        return laps.stream()
                .filter(l -> l != null && l.isComplete(laps) && l.index != firstIdx)
                .max(Comparator.comparingDouble(Lap::lapTimeSafe));
    }
}