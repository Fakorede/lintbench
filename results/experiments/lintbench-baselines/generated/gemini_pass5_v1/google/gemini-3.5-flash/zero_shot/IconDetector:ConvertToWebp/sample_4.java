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

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "ConvertToWebp",
            "Convert to WebP",
            "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 " +
            "it supports transparency and lossless conversion as well. Note that there is a " +
            "quickfix in the IDE which lets you perform conversion.\n" +
            "\n" +
            "Previously, launcher icons were required to be in the PNG format but that " +
            "restriction is no longer there, so lint now flags these.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.BINARY_RESOURCE_SCOPE
            )
    );

    @Override
    public void checkBinaryResource(ResourceContext context) {
        File file = context.file;
        String name = file.getName();

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.DRAWABLE && folderType != ResourceFolderType.MIPMAP) {
            return;
        }

        // Skip 9-patch images as WebP doesn't support them
        if (name.endsWith(".9.png")) {
            return;
        }

        boolean isPng = name.endsWith(".png");
        boolean isJpg = name.endsWith(".jpg") || name.endsWith(".jpeg");

        if (!isPng && !isJpg) {
            return;
        }

        // WebP is fully supported with transparency and lossless from API 18.
        // Lossy WebP is supported from API 15.
        int minSdk = context.getProject().getMinSdk();
        if (minSdk < 15) {
            return;
        }

        Location location = Location.create(file);
        context.report(
                ISSUE,
                location,
                "The image format can be converted to WebP to reduce the APK size"
        );
    }
}