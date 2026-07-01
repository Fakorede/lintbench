package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExtension",
            "Icon format does not match the file extension",
            "Ensures that icons have the correct file extension (e.g. a `.png` file is really in the PNG format and not for example a GIF file named `.png`).",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        File file = context.file;
        String name = file.getName().toLowerCase(Locale.US);

        boolean isPng = name.endsWith(".png");
        boolean isJpg = name.endsWith(".jpg") || name.endsWith(".jpeg");
        boolean isGif = name.endsWith(".gif");
        boolean isWebp = name.endsWith(".webp");

        if (!isPng && !isJpg && !isGif && !isWebp) {
            return;
        }

        byte[] header = new byte[12];
        int bytesRead;
        try (InputStream is = new FileInputStream(file)) {
            bytesRead = is.read(header);
        } catch (IOException e) {
            return;
        }

        if (bytesRead < 3) {
            return;
        }

        String actualFormat = null;
        if (bytesRead >= 8 &&
                header[0] == (byte) 0x89 &&
                header[1] == (byte) 0x50 &&
                header[2] == (byte) 0x4E &&
                header[3] == (byte) 0x47 &&
                header[4] == (byte) 0x0D &&
                header[5] == (byte) 0x0A &&
                header[6] == (byte) 0x1A &&
                header[7] == (byte) 0x0A) {
            actualFormat = "PNG";
        } else if (bytesRead >= 3 &&
                header[0] == (byte) 0x47 &&
                header[1] == (byte) 0x49 &&
                header[2] == (byte) 0x46) {
            actualFormat = "GIF";
        } else if (bytesRead >= 3 &&
                header[0] == (byte) 0xFF &&
                header[1] == (byte) 0xD8 &&
                header[2] == (byte) 0xFF) {
            actualFormat = "JPEG";
        } else if (bytesRead >= 12 &&
                header[0] == (byte) 'R' &&
                header[1] == (byte) 'I' &&
                header[2] == (byte) 'F' &&
                header[3] == (byte) 'F' &&
                header[8] == (byte) 'W' &&
                header[9] == (byte) 'E' &&
                header[10] == (byte) 'B' &&
                header[11] == (byte) 'P') {
            actualFormat = "WEBP";
        }

        String expectedFormat = null;
        if (isPng) {
            expectedFormat = "PNG";
        } else if (isJpg) {
            expectedFormat = "JPEG";
        } else if (isGif) {
            expectedFormat = "GIF";
        } else if (isWebp) {
            expectedFormat = "WEBP";
        }

        if (expectedFormat != null && !expectedFormat.equals(actualFormat)) {
            String message;
            if (actualFormat != null) {
                message = String.format(
                        "File extension does not match actual format (expected %s, but was %s)",
                        expectedFormat, actualFormat
                );
            } else {
                message = String.format(
                        "File extension does not match actual format (expected %s)",
                        expectedFormat
                );
            }
            context.report(ISSUE, Location.create(file), message);
        }
    }
}