package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExtension",
            "Icon format does not match the file extension",
            "Ensures that icons have the correct file extension (e.g. a `.png` file is really in the PNG format and not for example a GIF file named `.png`).",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot == -1) {
            return;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.US);

        if (!ext.equals("png") && !ext.equals("jpg") && !ext.equals("jpeg") && !ext.equals("gif") && !ext.equals("webp")) {
            return;
        }

        byte[] header = new byte[12];
        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            int read = is.read(header);
            if (read < 4) {
                return;
            }
        } catch (IOException e) {
            return;
        }

        String actualFormat = null;
        if (header[0] == (byte) 0x89 && header[1] == (byte) 0x50 && header[2] == (byte) 0x4E && header[3] == (byte) 0x47) {
            actualFormat = "png";
        } else if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
            actualFormat = "jpg";
        } else if (header[0] == (byte) 'G' && header[1] == (byte) 'I' && header[2] == (byte) 'F') {
            actualFormat = "gif";
        } else if (header[0] == (byte) 'R' && header[1] == (byte) 'I' && header[2] == (byte) 'F' && header[3] == (byte) 'F' &&
                header[8] == (byte) 'W' && header[9] == (byte) 'E' && header[10] == (byte) 'B' && header[11] == (byte) 'P') {
            actualFormat = "webp";
        }

        if (actualFormat != null) {
            boolean match;
            if (actualFormat.equals("jpg")) {
                match = ext.equals("jpg") || ext.equals("jpeg");
            } else {
                match = actualFormat.equals(ext);
            }

            if (!match) {
                String message = String.format("Misleading file extension; named `.%s` but the file format is `%s`", ext, actualFormat);
                context.report(ISSUE, Location.create(file), message);
            }
        }
    }
}