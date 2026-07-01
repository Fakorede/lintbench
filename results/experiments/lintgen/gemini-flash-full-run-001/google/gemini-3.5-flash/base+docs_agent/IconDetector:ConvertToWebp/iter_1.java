package com.android.tools.lint.checks;

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

    public static final Issue ISSUE = Issue.create(
        "ConvertToWebp",
        "Convert to WebP",
        "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1  " +
        "it supports transparency and lossless conversion as well. Note that there is a  " +
        "quickfix in the IDE which lets you perform conversion.n n Previously, launcher " +
        "icons were required to be in the PNG format but that  restriction is no longer " +
        "there, so lint now flags these.",
        Category.ICONS,
        5,
        Severity.WARNING,
        new Implementation(
            IconDetector.class,
            Scope.BINARY_RESOURCE_FILE_SCOPE
        )
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        
        if (name.endsWith(".9.png")) {
            return;
        }

        int minSdk = context.getProject().getMinSdk();

        if (name.endsWith(".png")) {
            if (minSdk >= 18) {
                context.report(
                    ISSUE,
                    Location.create(file),
                    "One or more images could be converted to WebP to save space"
                );
            }
        } else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            if (minSdk >= 14) {
                context.report(
                    ISSUE,
                    Location.create(file),
                    "One or more images could be converted to WebP to save space"
                );
            }
        }
    }
}