package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResource;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "ConvertToWebp",
            "Convert to WebP",
            "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 "
                    + "it supports transparency and lossless conversion as well. Note that there is a "
                    + "quickfix in the IDE which lets you perform conversion.\n\nPreviously, launcher "
                    + "icons were required to be in the PNG format but that restriction is no longer "
                    + "there, so lint now flags these.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.BINARY_RESOURCE_FILE)
            )
    );

    @Override
    @NonNull
    public List<Issue> getIssues() {
        return Collections.singletonList(ISSUE);
    }

    @Override
    public void checkBinaryResource(
            @NonNull ResourceContext context,
            @NonNull BinaryResourceScanner.Type type,
            @NonNull BinaryResource binaryResource) {
        if (type != BinaryResourceScanner.Type.PNG
                && type != BinaryResourceScanner.Type.JPEG) {
            return;
        }

        ResourceFolderType folderType =
                ResourceFolderType.getFolderType(context.getResourceFolder());
        if (folderType != ResourceFolderType.DRAWABLE
                && folderType != ResourceFolderType.MIPMAP) {
            return;
        }

        String message = "Convert " + binaryResource.getName() + " to WebP";
        context.report(ISSUE, Location.create(context.getFile()), message);
    }

    @Override
    @NonNull
    public Speed getBinaryFileScanningSpeed() {
        return Speed.FAST;
    }
}