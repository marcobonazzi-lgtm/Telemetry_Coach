package org.simulator.importCSVFW.converter;

import java.io.IOException;
import java.nio.file.*;

public final class CsvStandardizer {
    private CsvStandardizer() {}

    public static void convertToAssettoCorsa(Path in,
                                             Path out,
                                             ConverterProfile profile,
                                             boolean stripExtras) throws IOException {
        if (in == null || out == null) throw new IllegalArgumentException("Percorsi null.");
        ensureParentDir(out);

        switch (profile) {
            case LMU_MOTEC:
                new LmuToAssettoCorsaConverter(new LmuToAcHeaderMapping())
                        .convert(in, out, stripExtras);
                break;

                case ACC_MOTEC: // <— NUOVO
                    new AccToAssettoCorsaConverter(new AccToAcHeaderMapping())
                            .convert(in, out, stripExtras);
                    break;

            case ASSETTO_CORSA_PASS_THROUGH:
            default:
                Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void ensureParentDir(Path out) throws IOException {
        Path parent = out.toAbsolutePath().getParent();
        if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);
    }
}
