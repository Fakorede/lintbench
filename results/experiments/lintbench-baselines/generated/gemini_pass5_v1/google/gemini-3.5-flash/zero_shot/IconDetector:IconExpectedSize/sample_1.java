package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExpectedSize",
            "Icon has incorrect size",
            "There are predefined sizes (for each density) for launcher icons. You "
                    + "should follow these conventions to make sure your icons fit in with the "
                    + "overall look of the platform.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.BINARY_RESOURCE_FILE_SCOPE
            )
    );

    private static class Dimension {
        final int width;
        final int height;

        Dimension(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    @Override
    public void checkBinaryResource(@NotNull ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        if (!(name.startsWith("ic_launcher") && (name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".jpg") || name.endsWith(".jpeg")))) {
            return;
        }

        String folderName = file.getParentFile().getName();
        int expectedSize = getExpectedSize(folderName);
        if (expectedSize == -1) {
            return;
        }

        Dimension dim = getImageDimension(file);
        if (dim != null) {
            if (dim.width != expectedSize || dim.height != expectedSize) {
                String message = String.format(
                        "Expected size for %s is %dx%d pixels, but was %dx%d",
                        folderName, expectedSize, expectedSize, dim.width, dim.height
                );
                context.report(ISSUE, Location.create(file), message);
            }
        }
    }

    private static int getExpectedSize(String folderName) {
        if (folderName.contains("-xxxhdpi")) {
            return 192;
        }
        if (folderName.contains("-xxhdpi")) {
            return 144;
        }
        if (folderName.contains("-xhdpi")) {
            return 96;
        }
        if (folderName.contains("-hdpi")) {
            return 72;
        }
        if (folderName.contains("-mdpi")) {
            return 48;
        }
        if (folderName.contains("-ldpi")) {
            return 36;
        }
        return -1;
    }

    private static Dimension getImageDimension(File file) {
        String name = file.getName().toLowerCase(Locale.US);
        Dimension dim = null;
        if (name.endsWith(".png")) {
            dim = getPngDimension(file);
        } else if (name.endsWith(".webp")) {
            dim = getWebpDimension(file);
        }
        if (dim == null) {
            try {
                BufferedImage image = ImageIO.read(file);
                if (image != null) {
                    dim = new Dimension(image.getWidth(), image.getHeight());
                }
            } catch (IOException e) {
                // Ignore
            }
        }
        return dim;
    }

    private static Dimension getPngDimension(File file) {
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            if (in.readLong() != 0x89504E470D0A1A0AL) {
                return null;
            }
            in.readInt(); // chunk length
            if (in.readInt() != 0x49484452) { // "IHDR"
                return null;
            }
            int width = in.readInt();
            int height = in.readInt();
            return new Dimension(width, height);
        } catch (Exception e) {
            return null;
        }
    }

    private static Dimension getWebpDimension(File file) {
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            byte[] header = new byte[30];
            if (in.read(header) < 30) {
                return null;
            }
            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F' ||
                    header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') {
                return null;
            }
            String format = new String(header, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if ("VP8 ".equals(format)) {
                int width = ((header[27] & 0xFF) << 8 | (header[26] & 0xFF)) & 0x3FFF;
                int height = ((header[29] & 0xFF) << 8 | (header[28] & 0xFF)) & 0x3FFF;
                return new Dimension(width, height);
            } else if ("VP8L".equals(format)) {
                int b0 = header[21] & 0xFF;
                int b1 = header[22] & 0xFF;
                int b2 = header[23] & 0xFF;
                int b3 = header[24] & 0xFF;
                int width = 1 + (((b1 & 0x3F) << 8) | b0);
                int height = 1 + (((b3 & 0xF) << 10) | ((b2 & 0xFF) << 2) | ((b1 & 0xC0) >> 6));
                return new Dimension(width, height);
            } else if ("VP8X".equals(format)) {
                int width = 1 + ((header[24] & 0xFF) | ((header[25] & 0xFF) << 8) | ((header[26] & 0xFF) << 16));
                int height = 1 + ((header[27] & 0xFF) | ((header[28] & 0xFF) << 8) | ((header[29] & 0xFF) << 16));
                return new Dimension(width, height);
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
}