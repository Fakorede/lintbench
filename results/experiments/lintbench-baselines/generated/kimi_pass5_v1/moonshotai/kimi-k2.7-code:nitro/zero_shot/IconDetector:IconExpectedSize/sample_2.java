package com.android.tools.lint.checks;

import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    private static final String LAUNCHER_ICON_NAME = "ic_launcher.png";

    private static final Map<Density, Integer> EXPECTED_LAUNCHER_SIZE = new EnumMap<>(Density.class);
    static {
        EXPECTED_LAUNCHER_SIZE.put(Density.LDPI, 36);
        EXPECTED_LAUNCHER_SIZE.put(Density.MDPI, 48);
        EXPECTED_LAUNCHER_SIZE.put(Density.TVDPI, 64);
        EXPECTED_LAUNCHER_SIZE.put(Density.HDPI, 72);
        EXPECTED_LAUNCHER_SIZE.put(Density.XHDPI, 96);
        EXPECTED_LAUNCHER_SIZE.put(Density.XXHDPI, 144);
        EXPECTED_LAUNCHER_SIZE.put(Density.XXXHDPI, 192);
    }

    public static final Issue ICON_EXPECTED_SIZE = Issue.create(
            "IconExpectedSize",
            "Icon has incorrect size",
            "There are predefined sizes (for each density) for launcher icons. You should follow these conventions to make sure your icons fit in with the overall look of the platform.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MIPMAP || folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitResourceFolder(@NotNull Context context, @NotNull File folder) {
        String folderName = folder.getName();
        Density density = getDensity(folderName);
        if (density == null || !EXPECTED_LAUNCHER_SIZE.containsKey(density)) {
            return;
        }
        int expectedSize = EXPECTED_LAUNCHER_SIZE.get(density);

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!file.isFile() || !file.getName().equalsIgnoreCase(LAUNCHER_ICON_NAME)) {
                continue;
            }
            if (file.getName().endsWith(".9.png")) {
                continue;
            }

            int[] size = getImageSize(file);
            if (size == null) {
                continue;
            }
            int width = size[0];
            int height = size[1];

            if (width != expectedSize || height != expectedSize) {
                String message = String.format(
                        "The icon '%1$s' in the %2$s folder does not have the correct size: "
                                + "it should be %3$dx%3$d pixels for a launcher icon in this density, "
                                + "but it is %4$dx%5$d",
                        file.getName(), folderName, expectedSize, width, height);
                context.report(ICON_EXPECTED_SIZE, Location.create(file), message);
            }
        }
    }

    @Nullable
    private static Density getDensity(String folderName) {
        String[] parts = folderName.split("-");
        for (int i = 1; i < parts.length; i++) {
            Density density = Density.getEnum(parts[i]);
            if (density != null) {
                return density;
            }
        }
        return null;
    }

    @Nullable
    private static int[] getImageSize(File file) {
        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            byte[] signature = new byte[8];
            if (is.read(signature) != 8 || !isPngSignature(signature)) {
                return null;
            }

            while (true) {
                int length = readInt(is);
                byte[] type = new byte[4];
                if (is.read(type) != 4) {
                    return null;
                }

                if (type[0] == 'I' && type[1] == 'H' && type[2] == 'D' && type[3] == 'R') {
                    int width = readInt(is);
                    int height = readInt(is);
                    return new int[] { width, height };
                }

                if (!skipBytes(is, length + 4L)) {
                    return null;
                }
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean isPngSignature(byte[] sig) {
        return sig[0] == (byte) 0x89
                && sig[1] == 'P'
                && sig[2] == 'N'
                && sig[3] == 'G'
                && sig[4] == 0x0D
                && sig[5] == 0x0A
                && sig[6] == 0x1A
                && sig[7] == 0x0A;
    }

    private static int readInt(InputStream is) throws IOException {
        int b1 = is.read();
        int b2 = is.read();
        int b3 = is.read();
        int b4 = is.read();
        if ((b1 | b2 | b3 | b4) < 0) {
            throw new IOException("Unexpected end of stream");
        }
        return (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
    }

    private static boolean skipBytes(InputStream is, long count) throws IOException {
        long remaining = count;
        while (remaining > 0) {
            long skipped = is.skip(remaining);
            if (skipped <= 0) {
                return false;
            }
            remaining -= skipped;
        }
        return true;
    }
}