package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ConvertToWebp",
                    "Convert to WebP",
                    "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 "
                            + "it supports transparency and lossless conversion as well. Note that there is a "
                            + "quickfix in the IDE which lets you perform conversion.\n\n"
                            + "Previously, launcher icons were required to be in the PNG format but that "
                            + "restriction is no longer there, so lint now flags these.",
                    Category.ICONS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No-op for this detector
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No-op for this detector
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Allow all incidents through by default
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        // We check image files directly, not XML elements in this simplified version
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No XML element visiting needed for image file conversion
    }

    /**
     * Checks whether the given file is a PNG or JPEG that could be converted to WebP.
     */
    private void checkFile(@NonNull Context context, @NonNull File file) {
        String name = file.getName();
        String lowerName = name.toLowerCase();

        if (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            // Skip nine-patch files as they cannot be converted
            if (lowerName.endsWith(".9.png")) {
                return;
            }

            context.report(
                    new Incident(
                            ISSUE,
                            context.getLocation(file),
                            "Convert `" + name + "` to WebP format to reduce file size"));
        }
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        String name = file.getName();
        String lowerName = name.toLowerCase();

        if (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            // Skip nine-patch files
            if (lowerName.endsWith(".9.png")) {
                return;
            }

            // Check if this is in a drawable or mipmap folder
            File parentDir = file.getParentFile();
            if (parentDir != null) {
                String parentName = parentDir.getName();
                if (parentName.startsWith("drawable") || parentName.startsWith("mipmap")) {
                    context.report(
                            new Incident(
                                    ISSUE,
                                    context.getLocation(file),
                                    "Convert `"
                                            + name
                                            + "` to WebP format to reduce file size"));
                }
            }
        }
    }
}