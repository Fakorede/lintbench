package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceContext;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.EnumSet;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "ConvertToWebp",
            "Convert to WebP",
            "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 " +
            "it supports transparency and lossless conversion as well. Note that there is a " +
            "quickfix in the IDE which lets you perform conversion.\n\n" +
            "Previously, launcher icons were required to be in the PNG format but that " +
            "restriction is no longer there, so lint now flags these.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, EnumSet.of(Scope.BINARY_RESOURCE_FILE))
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType, @NonNull String fileName) {
        return (folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP)
                && (fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg"));
    }

    @Override
    public void visitBinaryResource(@NonNull BinaryResourceContext context) {
        File file = context.file;
        String message = "Consider converting this image to WebP format for better compression.";
        context.report(ISSUE, Location.create(file), message);
    }
}