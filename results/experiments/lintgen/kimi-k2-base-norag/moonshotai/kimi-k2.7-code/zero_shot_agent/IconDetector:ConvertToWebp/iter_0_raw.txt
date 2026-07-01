package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.EnumSet;
import java.util.Locale;

public class IconDetector extends ResourceFolderDetector {
    private static final Implementation IMPLEMENTATION = new Implementation(
            IconDetector.class,
            Scope.RESOURCE_FILE_SCOPE
    );

    public static final Issue ICON_WEBP = Issue.create(
            "ConvertToWebp",
            "Convert to WebP",
            "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 "
                    + "it supports transparency and lossless conversion as well. Note that there is a "
                    + "quickfix in the IDE which lets you perform conversion.\n\n"
                    + "Previously, launcher icons were required to be in the PNG format but that "
                    + "restriction is no longer there, so lint now flags these.",
            Category.ICONS,
            5,
            Severity.WARNING,
            IMPLEMENTATION
    );

    @Override
    @NonNull
    public EnumSet<ResourceFolderType> getApplicableFolders() {
        return EnumSet.of(ResourceFolderType.DRAWABLE, ResourceFolderType.MIPMAP);
    }

    @Override
    public boolean appliesTo(@NonNull String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return (lower.endsWith(".png") && !lower.endsWith(".9.png"))
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".bmp");
    }

    @Override
    public void visitFile(@NonNull Context context, @NonNull File file) {
        if (context.getMainProject().getMinSdkVersion().getFeatureLevel() < 18) {
            return;
        }

        context.report(
                ICON_WEBP,
                Location.create(file),
                "This image can be converted to WebP for a smaller file size."
        );
    }
}