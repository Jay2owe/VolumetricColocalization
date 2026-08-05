package volcoloc;

import sc.fiji.volcoloc.core.OverlapResult;

import ij.ImagePlus;
import ij.process.ShortProcessor;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class BoundingBoxParallelismTest {

    @Test
    public void parallelRowsExactlyMatchSerialRowsAndOrder() {
        ImagePlus source = plane("source", 1, 1, 0, 2, 2, 0, 3, 3, 4, 4);
        ImagePlus target = plane("target", 5, 5, 0, 6, 0, 0, 7, 7, 8, 0);
        VolColocParameters parameters = VolColocParameters.builder(source, target)
                .bidirectional(true)
                .boundingBoxThresholdsPercent(Arrays.asList(20.0, 20.0))
                .includeBoundingBoxOverlap(true)
                .includeBoundingBoxCpc(true)
                .includeBoundingBoxVolumeFill(true)
                .build();

        String previous = System.getProperty("volcoloc.parallelism");
        try {
            System.setProperty("volcoloc.parallelism", "1");
            List<OverlapResult.BoundingBoxDirectionResult> serial =
                    VolColoc.run(parameters).getBoundingBoxDirectionResults();
            System.setProperty("volcoloc.parallelism", "4");
            List<OverlapResult.BoundingBoxDirectionResult> parallel =
                    VolColoc.run(parameters).getBoundingBoxDirectionResults();
            assertEquals(serial.size(), parallel.size());
            for (int direction = 0; direction < serial.size(); direction++) {
                List<OverlapResult.BoundingBoxObjectResult> expected =
                        serial.get(direction).getObjects();
                List<OverlapResult.BoundingBoxObjectResult> actual =
                        parallel.get(direction).getObjects();
                assertEquals(expected.size(), actual.size());
                for (int row = 0; row < expected.size(); row++) {
                    assertSameRow(expected.get(row), actual.get(row));
                }
            }
        } finally {
            restore("volcoloc.parallelism", previous);
        }
    }

    private static void assertSameRow(
            OverlapResult.BoundingBoxObjectResult expected,
            OverlapResult.BoundingBoxObjectResult actual) {
        assertEquals(expected.getSourceLabel(), actual.getSourceLabel());
        assertEquals(expected.getBoxVolume(), actual.getBoxVolume());
        assertEquals(expected.getBoundingBoxOverlapPercent(),
                actual.getBoundingBoxOverlapPercent(), 0.0);
        assertEquals(expected.getBoundingBoxOverlapPartnerLabel(),
                actual.getBoundingBoxOverlapPartnerLabel());
        assertEquals(expected.getBoundingBoxCpcPartnerLabel(),
                actual.getBoundingBoxCpcPartnerLabel());
        assertEquals(expected.getBoundingBoxCpcContainsCount(),
                actual.getBoundingBoxCpcContainsCount());
        assertEquals(expected.getBoundingBoxVolumeBestPercent(),
                actual.getBoundingBoxVolumeBestPercent(), 0.0);
        assertEquals(expected.getBoundingBoxVolumeTotalPercent(),
                actual.getBoundingBoxVolumeTotalPercent(), 0.0);
        assertEquals(expected.getBoundingBoxVolumePartnerLabel(),
                actual.getBoundingBoxVolumePartnerLabel());
    }

    private static ImagePlus plane(String title, int... labels) {
        ShortProcessor processor = new ShortProcessor(labels.length, 1);
        for (int x = 0; x < labels.length; x++) processor.set(x, 0, labels[x]);
        return new ImagePlus(title, processor);
    }

    private static void restore(String key, String previous) {
        if (previous == null) System.clearProperty(key);
        else System.setProperty(key, previous);
    }
}
