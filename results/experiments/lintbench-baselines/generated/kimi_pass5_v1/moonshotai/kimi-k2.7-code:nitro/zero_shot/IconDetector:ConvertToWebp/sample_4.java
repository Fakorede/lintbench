package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

public class IconDetector extends ResourceXmlDetector implements BinaryResourceScanner {

    private static final String EXT_PNG = "png";
    private static final String EXT_JPG = "jpg";
    private static final String EXT_JPEG = "jpeg";

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
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE_SCOPE))
            .setEnabledByDefault(true);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(@NonNull Context context) {
        String fileName = context.file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == fileName.length() - 1) {
            return;
        }

        String extension = fileName.substring(dotIndex + 1).toLowerCase();
        if (extension.equals(EXT_PNG)
                || extension.equals(EXT_JPG)
                || extension.equals(EXT_JPEG)) {
            Location location = Location.create(context.file);
            String message = String.format("Convert `%1$s` to WebP format", fileName);
            context.report(CONVERT_TO_WEBP, location, message);
        }
    }
}