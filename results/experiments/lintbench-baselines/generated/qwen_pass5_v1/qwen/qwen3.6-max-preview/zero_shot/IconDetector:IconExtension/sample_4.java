package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFile;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExtension",
            "Icon format does not match the file extension",
            "Ensures that icons have the correct file extension (e.g. a `.png` file is really in the PNG format and not for example a GIF file named `.png`).",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Nullable
    @Override
    public List<ResourceFolderType> getApplicableFolderTypes() {
        return Collections.singletonList(ResourceFolderType.DRAWABLE);
    }

    @Override
    public void visitBinaryResource(@NotNull ResourceContext context, @NotNull ResourceFolderType folderType, @NotNull ResourceFile file, @NotNull byte[] contents) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot == -1 || dot == name.length() - 1) {
            return;
        }

        String ext = name.substring(dot + 1).toLowerCase(Locale.US);
        if (!isImageExtension(ext)) {
            return;
        }

        if (contents.length < 4) {
            return;
        }

        // Skip XML/text based drawables (vectors, selectors, etc.)
        if (contents[0] == '<' || contents[0] == '?' || contents[0] == '#' || contents[0] == '/') {
            return;
        }

        String actualFormat = detectFormat(contents);
        if (actualFormat == null) {
            return;
        }

        boolean matches = actualFormat.equals(ext) ||
                (actualFormat.equals("jpeg") && (ext.equals("jpg") || ext.equals("jpeg")));

        if (!matches) {
            String message = String.format(
                    "The icon file `%s` appears to be a `%s` but has the extension `%s`",
                    name, actualFormat, ext);
            Location location = Location.create(file);
            context.report(ISSUE, location, message);
        }
    }

    private static boolean isImageExtension(@NotNull String ext) {
        return ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg") ||
               ext.equals("gif") || ext.equals("webp") || ext.equals("bmp");
    }

    @Nullable
    private static String detectFormat(@NotNull byte[] contents) {
        if (contents.length >= 4 &&
            contents[0] == (byte) 0x89 && contents[1] == 'P' &&
            contents[2] == 'N' && contents[3] == 'G') {
            return "png";
        }
        if (contents.length >= 3 &&
            contents[0] == (byte) 0xFF && contents[1] == (byte) 0xD8 &&
            contents[2] == (byte) 0xFF) {
            return "jpeg";
        }
        if (contents.length >= 4 &&
            contents[0] == 'G' && contents[1] == 'I' &&
            contents[2] == 'F' && contents[3] == '8') {
            return "gif";
        }
        if (contents.length >= 12 &&
            contents[0] == 'R' && contents[1] == 'I' &&
            contents[2] == 'F' && contents[3] == 'F' &&
            contents[8] == 'W' && contents[9] == 'E' &&
            contents[10] == 'B' && contents[11] == 'P') {
            return "webp";
        }
        if (contents.length >= 2 &&
            contents[0] == 'B' && contents[1] == 'M') {
            return "bmp";
        }
        return null;
    }
}