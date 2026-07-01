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

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    private static final String EXT_PNG = "png";
    private static final String EXT_JPG = "jpg";
    private static final String EXT_JPEG = "jpeg";
    private static final String EXT_GIF = "gif";
    private static final String NINE_PATCH = ".9.";

    public static final Issue CONVERT_TO_WEBP = Issue.create(
            "ConvertToWebp",
            "Convert to WebP",
            "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 "
                    + "it supports transparency and lossless conversion as well. Note that there is a "
                    + "quickfix in the IDE which lets you perform conversion.\n\n"
                    + "Previously, launcher icons were required to be in the PNG format but that "
                    + "restriction is no longer there, so lint now flags these.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE_SCOPE)
    );

    public Collection<String> applicableExtensions() {
        return Arrays.asList(EXT_PNG, EXT_JPG, EXT_JPEG, EXT_GIF);
    }

    public void checkBinaryResource(ResourceContext context, File file) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.DRAWABLE
                && folderType != ResourceFolderType.MIPMAP) {
            return;
        }

        String name = file.getName();
        if (name.contains(NINE_PATCH)) {
            return;
        }

        String message = "This image can be converted to WebP for a smaller file size.";
        context.report(CONVERT_TO_WEBP, Location.create(file), message);
    }
}