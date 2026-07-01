package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "ConvertToWebp",
            "Convert to WebP",
            "The WebP format is typically more compact than PNG and JPEG. " +
            "As of Android 4.2.1 it supports transparency and lossless conversion as well. " +
            "Note that there is a quickfix in the IDE which lets you perform conversion.\n\n" +
            "Previously, launcher icons were required to be in the PNG format but that " +
            "restriction is no longer there, so lint now flags these.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Nullable
    @Override
    public Collection<String> getApplicableFiles() {
        return Arrays.asList("png", "jpg", "jpeg");
    }

    @Override
    public void visitBinaryResource(@NonNull Context context) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP) {
            String name = context.file.getName();
            if (name.endsWith(".9.png")) {
                return;
            }
            context.report(ISSUE, Location.create(context.file),
                    "Consider converting to WebP format for better compression");
        }
    }
}