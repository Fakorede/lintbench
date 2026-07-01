package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.io.File;
import java.util.EnumSet;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
        "ConvertToWebp",
        "Convert to WebP",
        "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 " +
        "it supports transparency and lossless conversion as well. Note that there is a " +
        "quickfix in the IDE which lets you perform conversion. " +
        "Previously, launcher icons were required to be in the PNG format but that " +
        "restriction is no longer there, so lint now flags these.",
        Category.ICONS,
        5,
        Severity.WARNING,
        new Implementation(
            IconDetector.class,
            EnumSet.of(Scope.BINARY_RESOURCE_FILE)
        )
    );

    @Override
    public void checkBinaryResource(ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        String lower = name.toLowerCase(java.util.Locale.US);
        if ((lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
                && !lower.endsWith(".9.png")) {
            ResourceFolderType folderType = context.getResourceFolderType();
            if (folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP) {
                context.report(
                    ISSUE,
                    context.getLocation(file),
                    "Convert this image to WebP"
                );
            }
        }
    }
}