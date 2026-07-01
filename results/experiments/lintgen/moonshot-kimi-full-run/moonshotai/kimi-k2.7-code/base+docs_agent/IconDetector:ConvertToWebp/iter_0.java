package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

public class IconDetector extends Detector implements BinaryResourceScanner {

    private static final String EXT_PNG = "png";
    private static final String EXT_JPG = "jpg";
    private static final String EXT_JPEG = "jpeg";
    private static final String EXT_GIF = "gif";

    public static final Issue CONVERT_TO_WEBP = Issue.create(
            "ConvertToWebp",
            "Convert to WebP",
            "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 "
                    + "it supports transparency and lossless conversion as well. Launcher icons "
                    + "are no longer required to be in PNG format, so they can be converted too.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableExtensions() {
        return Arrays.asList(EXT_PNG, EXT_JPG, EXT_JPEG, EXT_GIF);
    }

    @Override
    public void checkBinaryResource(ResourceContext context, File file) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.DRAWABLE
                && folderType != ResourceFolderType.MIPMAP) {
            return;
        }

        String name = file.getName();
        if (name.contains(".9.")) {
            // Skip nine-patch PNGs; they are not directly convertible.
            return;
        }

        String message = "This image can be converted to WebP for a smaller file size.";
        Location location = Location.create(file);
        context.report(CONVERT_TO_WEBP, location, message);
    }
}