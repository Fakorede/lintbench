package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import org.jetbrains.annotations.NotNull;
import java.io.File;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "ConvertToWebp",
            "Convert to WebP",
            "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 " +
            "it supports transparency and lossless conversion as well. Note that there is a " +
            "quickfix in the IDE which lets you perform conversion.\n\n" +
            "Previously, launcher icons were required to be in the PNG format but that " +
            "restriction is no longer there, so lint now flags these.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.ALL_RESOURCE_FILES_SCOPE)
    );

    @Override
    public void checkBinaryResource(@NotNull ResourceContext context) {
        File file = context.file;
        if (file == null) {
            return;
        }
        String name = file.getName();

        if (name.endsWith(".9.png")) {
            return;
        }

        boolean isPng = name.endsWith(".png");
        boolean isJpg = name.endsWith(".jpg") || name.endsWith(".jpeg");

        if (!isPng && !isJpg) {
            return;
        }

        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }

        ResourceFolderType folderType = ResourceFolderType.getFolderType(parent.getName());
        if (folderType != ResourceFolderType.DRAWABLE && folderType != ResourceFolderType.MIPMAP) {
            return;
        }

        String message = String.format("Consider converting `%s` to WebP format for better compression", name);
        context.report(ISSUE, Location.create(file), message);
    }
}