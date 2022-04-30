import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ClassForTest {

    @Test
    public void a() throws IOException {
        Path path = Files.find(Path.of("C://Program Files"), 10,
                        (p, att) -> p.endsWith("bin/ffmpeg.exe")).findAny().orElse(null);

        if (path != null) {
            System.out.println("Found ffmpeg at " + path.toAbsolutePath());
            Runtime.getRuntime().exec(String.format("%s -i output.wav output.mp3", path.toAbsolutePath()));
        }
        else
            System.out.println("no ffmpeg");
    }

    private static float bytesToMegaBytes(long bytes) {
        return ((float) bytes) / (1024 * 1024);
    }
}
