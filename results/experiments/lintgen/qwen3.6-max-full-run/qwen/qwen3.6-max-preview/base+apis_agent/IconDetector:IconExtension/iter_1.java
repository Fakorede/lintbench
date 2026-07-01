package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExtension",
            "Icon format does not match the file extension",
            "Ensures that icons have the correct file extension (e.g. a `.png` file is really in the PNG format and not for example a GIF file named `.png`).",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        File file = context.file;
        if (file == null) {
            return;
        }

        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot == -1) {
            return;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.US);

        switch (ext) {
            case "png":
            case "jpg":
            case "jpeg":
            case "gif":
            case "webp":
            case "bmp":
                break;
            default:
                return;
        }

        byte[] header = new byte[12];
        int read;
        try (FileInputStream fis = new FileInputStream(file)) {
            read = fis.read(header);
        } catch (IOException e) {
            return;
        }

        if (read < 4) {
            return;
        }

        boolean matches = false;
        switch (ext) {
            case "png":
                matches = header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47;
                break;
            case "jpg":
            case "jpeg":
                matches = header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF;
                break;
            case "gif":
                matches = header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x38;
                break;
            case "webp":
                matches = read >= 12 && header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46
                        && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50;
                break;
            case "bmp":
                matches = header[0] == 0x42 && header[1] == 0x4D;
                break;
        }

        if (!matches) {
            context.report(ISSUE, Location.create(file), "The icon format does not match the file extension");
        }
    }
}