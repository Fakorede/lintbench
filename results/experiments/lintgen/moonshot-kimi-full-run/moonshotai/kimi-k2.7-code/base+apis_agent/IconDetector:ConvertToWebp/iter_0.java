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

import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Locale;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "ConvertToWebp",
            "Convert to WebP",
            "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 "
                    + "it supports transparency and lossless conversion as well. Note that there is a "
                    + "quickfix in the IDE which lets you perform conversion. Previously, launcher icons "
                    + "were required to be in the PNG format but that restriction is no longer there, "
                    + "so lint now flags these.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, EnumSet.of(Scope.BINARY_RESOURCE_FILE_SCOPE))
    );

    @Override
    public boolean appliesToFolder(@NotNull String folderName) {
        ResourceFolderType type = ResourceFolderType.getFolderType(folderName);
        return type == ResourceFolderType.DRAWABLE || type == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(
            @NotNull ResourceContext context,
            @NotNull ResourceFolderType type,
            @NotNull String folderName,
            @NotNull String fileName) {
        if (type != ResourceFolderType.DRAWABLE && type != ResourceFolderType.MIPMAP) {
            return;
        }

        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")) {
            context.report(
                    ISSUE,
                    Location.create(context.getFile()),
                    "This image could be converted to WebP for a smaller file size.");
        }
    }
}