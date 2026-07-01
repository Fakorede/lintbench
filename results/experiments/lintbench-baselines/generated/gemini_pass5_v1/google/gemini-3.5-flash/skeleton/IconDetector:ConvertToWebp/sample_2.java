package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.*;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, java.util.EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "ConvertToWebp",
                    "Convert to WebP",
                    "The WebP format is typically more compact than PNG and JPEG. As of Android "
                            + "4.2.1  it supports transparency and lossless conversion as well. Note that there is a "
                            + "quickfix in the IDE which lets you perform conversion.\n\n"
                            + "Previously, launcher icons were required to be in the PNG format but that "
                            + "restriction is no longer there, so lint now flags these.",
                    Category.ICONS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckRootProject(@org.jetbrains.annotations.NotNull Context context) {
        // No-op
    }

    @Override
    public void afterCheckEachProject(@org.jetbrains.annotations.NotNull Context context) {
        if (context.getProject().getMinSdkVersion().getFeatureLevel() < 15) {
            return;
        }
        java.util.List<java.io.File> resFolders = context.getProject().getResourceFolders();
        for (java.io.File resFolder : resFolders) {
            java.io.File[] subfolders = resFolder.listFiles();
            if (subfolders != null) {
                for (java.io.File subfolder : subfolders) {
                    String name = subfolder.getName();
                    if (name.startsWith("drawable") || name.startsWith("mipmap")) {
                        java.io.File[] files = subfolder.listFiles();
                        if (files != null) {
                            for (java.io.File file : files) {
                                String fileName = file.getName();
                                if (isConvertibleToWebp(fileName)) {
                                    reportWebp(context, file);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isConvertibleToWebp(String name) {
        String lower = name.toLowerCase(java.util.Locale.US);
        return (lower.endsWith(".png") && !lower.endsWith(".9.png"))
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg");
    }

    private void reportWebp(Context context, java.io.File file) {
        context.report(
                new Incident(
                        ISSUE,
                        Location.create(file),
                        "The image format can be converted to WebP, which is typically more compact"
                )
        );
    }

    @Override
    public boolean filterIncident(
            @org.jetbrains.annotations.NotNull Context context, @org.jetbrains.annotations.NotNull Incident incident, @org.jetbrains.annotations.NotNull LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@org.jetbrains.annotations.NotNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE || folderType == com.android.resources.ResourceFolderType.MIPMAP;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.emptyList();
    }

    @Override
    public void visitElement(@org.jetbrains.annotations.NotNull XmlContext context, @org.jetbrains.annotations.NotNull org.w3c.dom.Element element) {
        // No-op
    }

    @Override
    public void visitClass(@org.jetbrains.annotations.NotNull JavaContext context, @org.jetbrains.annotations.NotNull UClass declaration) {
        // No-op
    }

    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Collections.emptyList();
    }

    @Override
    public UElementHandler createUastHandler(@org.jetbrains.annotations.NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@org.jetbrains.annotations.NotNull UMethod node) {
                // No-op
            }

            @Override
            public void visitCallExpression(@org.jetbrains.annotations.NotNull UCallExpression node) {
                // No-op
            }

            @Override
            public void visitSimpleNameReferenceExpression(@org.jetbrains.annotations.NotNull USimpleNameReferenceExpression node) {
                // No-op
            }
        };
    }
}