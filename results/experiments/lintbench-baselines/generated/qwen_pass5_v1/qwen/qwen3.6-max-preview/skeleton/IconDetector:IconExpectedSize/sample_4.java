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
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
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

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "IconExpectedSize",
                    "Icon has incorrect size",
                    "There are predefined sizes (for each density) for launcher icons. You should follow these conventions to make sure your icons fit in with the overall look of the platform.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No global initialization required
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No cleanup required
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("bitmap", "vector", "adaptive-icon", "icon");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.MIPMAP && folderType != ResourceFolderType.DRAWABLE) {
            return;
        }

        String folderName = context.getFile().getParentFile().getName();
        String densityName = "mdpi";
        if (folderName.contains("-ldpi")) {
            densityName = "ldpi";
        } else if (folderName.contains("-hdpi")) {
            densityName = "hdpi";
        } else if (folderName.contains("-xhdpi")) {
            densityName = "xhdpi";
        } else if (folderName.contains("-xxhdpi")) {
            densityName = "xxhdpi";
        } else if (folderName.contains("-xxxhdpi")) {
            densityName = "xxxhdpi";
        }

        int expectedSize;
        switch (densityName) {
            case "ldpi": expectedSize = 36; break;
            case "hdpi": expectedSize = 72; break;
            case "xhdpi": expectedSize = 96; break;
            case "xxhdpi": expectedSize = 144; break;
            case "xxxhdpi": expectedSize = 192; break;
            default: expectedSize = 48; break;
        }

        String width = element.getAttribute("android:width");
        String height = element.getAttribute("android:height");

        if (width.isEmpty() && height.isEmpty() && folderType == ResourceFolderType.MIPMAP) {
            String message = String.format(
                    "Launcher icon has incorrect size. Expected %1$dx%1$dpx for %2$s density.",
                    expectedSize, densityName);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Java class scanning not required for icon size validation
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.emptyList();
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // No-op: UAST scanning disabled for this detector
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // No-op: UAST scanning disabled for this detector
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                // No-op: UAST scanning disabled for this detector
            }
        };
    }
}