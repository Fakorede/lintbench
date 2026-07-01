package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    private static final String ID = "IconExpectedSize";
    private static final String BRIEF_DESCRIPTION = "Icon has incorrect size";
    private static final String EXPLANATION =
            "There are predefined sizes (for each density) for launcher icons. You should follow " +
            "these conventions to make sure your icons fit in with the overall look of the platform.";

    public static final Issue ISSUE = Issue.create(
            ID,
            BRIEF_DESCRIPTION,
            EXPLANATION,
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE_SCOPE)
    );

    private static final int LAUNCHER_ICON_DP = 48;
    private static final int ADAPTIVE_LAYER_DP = 108;
    private static final int NOTIFICATION_ICON_DP = 24;
    private static final int ACTION_BAR_ICON_DP = 32;
    private static final int SMALL_ICON_DP = 16;

    private static final byte[] PNG_HEADER = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] RIFF_HEADER = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_HEADER = {0x57, 0x45, 0x42, 0x50};

    @Override
    public void checkBinaryResourceFile(@NonNull ResourceContext context) {
        File file = context.getFile();
        String fileName = file.getName();

        if (fileName.endsWith(".xml") || fileName.endsWith(".9.png")) {
            return;
        }

        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }

        String folderName = parent.getName();
        ResourceFolderType folderType = ResourceFolderType.getFolderType(folderName);
        if (folderType != ResourceFolderType.DRAWABLE && folderType != ResourceFolderType.MIPMAP) {
            return;
        }

        Density density = getDensity(folderName);
        if (density == null || density == Density.NODPI || density == Density.ANYDPI) {
            return;
        }

        double scale = getScaleFactor(density);
        int[] expectedDp = getExpectedDpSize(fileName, folderType);
        if (expectedDp == null) {
            return;
        }

        int expectedWidth = (int) Math.round(expectedDp[0] * scale);
        int expectedHeight = (int) Math.round(expectedDp[1] * scale);

        int[] actual = readImageDimensions(file);
        if (actual == null) {
            return;
        }

        int actualWidth = actual[0];
        int actualHeight = actual[1];

        if (actualWidth != expectedWidth || actualHeight != expectedHeight) {
            String message = String.format(
                    "Icon size %dx%d px does not match the expected size %dx%d px for %ddp at %s density",
                    actualWidth, actualHeight, expectedWidth, expectedHeight,
                    expectedDp[0], density.getResourceValue());
            context.report(ISSUE, Location.create(file), message);
        }
    }

    @Nullable
    private static int[] getExpectedDpSize(@NonNull String fileName,
            @SuppressWarnings("unused") @NonNull ResourceFolderType folderType) {
        String baseName = fileName;
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) {
            baseName = baseName.substring(0, dot);
        }

        if (baseName.equals("ic_launcher") || baseName.equals("ic_launcher_round")) {
            return new int[] {LAUNCHER_ICON_DP, LAUNCHER_ICON_DP};
        }
        if (baseName.equals("ic_launcher_foreground") || baseName.equals("ic_launcher_background")) {
            return new int[] {ADAPTIVE_LAYER_DP, ADAPTIVE_LAYER_DP};
        }
        if (baseName.startsWith("ic_stat_")) {
            return new int[] {NOTIFICATION_ICON_DP, NOTIFICATION_ICON_DP};
        }
        if (baseName.startsWith("ic_action_")) {
            return new int[] {ACTION_BAR_ICON_DP, ACTION_BAR_ICON_DP};
        }
        if (baseName.startsWith("ic_menu_") || baseName.startsWith("ic_dialog_")) {
            return new int[] {SMALL_ICON_DP, SMALL_ICON_DP};
        }
        return null;
    }

    @Nullable
    private static Density getDensity(@NonNull String folderName) {
        String[] parts = folderName.split("-");
        for (String part : parts) {
            Density density = Density.getEnum(part);
            if (density != null) {
                return density;
            }
        }
        return null;
    }

    private static double getScaleFactor(@NonNull Density density) {
        switch (density) {
            case LDPI: return 0.75;
            case MDPI: return 1.0;
            case TVDPI: return 1.33125;
            case HDPI: return 1.5;
            case XHDPI: return 2.0;
            case XXHDPI: return 3.0;
            case XXXHDPI: return 4.0;
            default: return 1.0;
        }
    }

    @Nullable
    private static int[] readImageDimensions(@NonNull File file) {
        try (InputStream stream = new FileInputStream(file)) {
            byte[] header = new byte[32];
            int read = stream.read(header);
            if (read < 24) {
                return null;
            }

            if (startsWith(header, PNG_HEADER)) {
                int width = readIntBigEndian(header, 16);
                int height = readIntBigEndian(header, 20);
                return new int[] {width, height};
            }

            if (header[0] == 'G' && header[1] == 'I' && header[2] == 'F') {
                int width = readIntLittleEndian(header, 6, 2);
                int height = readIntLittleEndian(header, 8, 2);
                return new int[] {width, height};
            }

            if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8) {
                return readJpegDimensions(stream, header);
            }

            if (startsWith(header, RIFF_HEADER) && startsWith(header, 8, WEBP_HEADER)) {
                return readWebpDimensions(header);
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    @Nullable
    private static int[] readJpegDimensions(@NonNull InputStream stream,
            @NonNull byte[] header) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        buffer.write(header);
        byte[] block = new byte[4096];
        int len;
        while ((len = stream.read(block)) != -1) {
            buffer.write(block, 0, len);
        }
        byte[] data = buffer.toByteArray();
        for (int i = 0; i < data.length - 9; i++) {
            if ((data[i] & 0xFF) == 0xFF) {
                int marker = data[i + 1] & 0xFF;
                if (marker == 0xC0 || marker == 0xC2) {
                    int height = readIntBigEndian(data, i + 5);
                    int width = readIntBigEndian(data, i + 7);
                    return new int[] {width, height};
                }
            }
        }
        return null;
    }

    @Nullable
    private static int[] readWebpDimensions(@NonNull byte[] header) {
        String chunk = new String(header, 12, 4);
        if ("VP8 ".equals(chunk)) {
            int width = ((header[23] & 0xFF) | ((header[24] & 0xFF) << 8)) & 0x3FFF;
            int height = ((header[25] & 0xFF) | ((header[26] & 0xFF) << 8)) & 0x3FFF;
            return new int[] {width, height};
        } else if ("VP8L".equals(chunk)) {
            int bits = readIntLittleEndian(header, 20, 4);
            int width = (bits & 0x3FFF) + 1;
            int height = ((bits >> 14) & 0x3FFF) + 1;
            return new int[] {width, height};
        } else if ("VP8X".equals(chunk)) {
            int width = (header[21] & 0xFF)
                    | ((header[22] & 0xFF) << 8)
                    | ((header[23] & 0xFF) << 16)
                    | 1;
            int height = (header[24] & 0xFF)
                    | ((header[25] & 0xFF) << 8)
                    | ((header[26] & 0xFF) << 16)
                    | 1;
            return new int[] {width, height};
        }
        return null;
    }

    private static int readIntBigEndian(@NonNull byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private static int readIntLittleEndian(@NonNull byte[] data, int offset, int length) {
        int value = 0;
        for (int i = 0; i < length; i++) {
            value |= (data[offset + i] & 0xFF) << (8 * i);
        }
        return value;
    }

    private static boolean startsWith(@NonNull byte[] data, @NonNull byte[] prefix) {
        return startsWith(data, 0, prefix);
    }

    private static boolean startsWith(@NonNull byte[] data, int offset, @NonNull byte[] prefix) {
        if (data.length < offset + prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}