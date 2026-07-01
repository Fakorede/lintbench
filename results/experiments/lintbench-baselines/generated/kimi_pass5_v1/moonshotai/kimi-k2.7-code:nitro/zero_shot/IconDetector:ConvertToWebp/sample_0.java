package com.android.tools.lint.checks;

import static com.android.SdkConstants.DOT_9PNG;
import static com.android.SdkConstants.DOT_JPEG;
import static com.android.SdkConstants.DOT_JPG;
import static com.android.SdkConstants.DOT_PNG;

import com.android.annotations.NonNull;
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

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue CONVERT_TO_WEBP = Issue.create(
            "ConvertToWebp",
            "Convert to WebP",
            "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 "
                    + "it supports transparency and lossless conversion as well. Note that there is a "
                    + "quickfix in the IDE which lets you perform conversion.\n"
                    + "\n"
                    + "Previously, launcher icons were required to be in the PNG format but that "
                    + "restriction is no longer there, so lint now flags these.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String name = file.getName().toLowerCase();
        if (name.endsWith(DOT_9PNG)) {
            return;
        }
        if (name.endsWith(DOT_PNG) || name.endsWith(DOT_JPG) || name.endsWith(DOT_JPEG)) {
            Location location = Location.create(file);
            context.report(CONVERT_TO_WEBP, location,
                    "This image can be converted to WebP for a smaller file size");
        }
    }
}