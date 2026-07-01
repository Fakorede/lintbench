package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

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
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            java.util.EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE)));

    @Override
    public void beforeCheckRootProject(@com.android.annotations.NonNull Context context) {
        super.beforeCheckRootProject(context);
    }

    @Override
    public void afterCheckEachProject(@com.android.annotations.NonNull Context context) {
        super.afterCheckEachProject(context);
        java.io.File resDir = new java.io.File(context.getProject().getDir(), "src/main/res");
        if (resDir.exists()) {
            checkFolder(context, resDir);
        }
    }

    private void checkFolder(@com.android.annotations.NonNull Context context, java.io.File dir) {
        java.io.File[] files = dir.listFiles();
        if (files == null) return;
        for (java.io.File file : files) {
            if (file.isDirectory()) {
                checkFolder(context, file);
            } else {
                String name = file.getName().toLowerCase();
                if ((name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"))
                        && !name.endsWith(".9.png")) {
                    Location location = Location.create(file);
                    context.report(
                            ISSUE,
                            location,
                            "The image format can be converted to WebP for better compression"
                    );
                }
            }
        }
    }

    @Override
    public void filterIncident(@com.android.annotations.NonNull Incident incident) {
        super.filterIncident(incident);
    }

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull Context context, @com.android.annotations.NonNull java.io.File file) {
        return super.appliesTo(context, file);
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Arrays.asList("bitmap", "item");
    }

    @Override
    public void visitElement(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull org.w3c.dom.Element element) {
        String src = element.getAttributeNS("http://schemas.android.com/apk/res/android", "src");
        if (src != null && (src.endsWith(".png") || src.endsWith(".jpg") || src.endsWith(".jpeg")) && !src.endsWith(".9.png")) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "The image format can be converted to WebP for better compression"
            );
        }
    }

    @Override
    public UElementHandler createUastHandler(@com.android.annotations.NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@com.android.annotations.NonNull UMethod node) {
                IconDetector.this.visitMethod(context, node);
            }

            @Override
            public void visitCallExpression(@com.android.annotations.NonNull UCallExpression node) {
                IconDetector.this.visitCallExpression(context, node);
            }

            @Override
            public void visitClass(@com.android.annotations.NonNull UClass node) {
                IconDetector.this.visitClass(context, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(@com.android.annotations.NonNull USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    public void visitMethod(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UMethod method) {
        // Method check not directly required for WebP but implemented to satisfy specification
    }

    public void visitCallExpression(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UCallExpression node) {
        // Call expression check not directly required for WebP but implemented to satisfy specification
    }

    @Override
    public void visitClass(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UClass declaration) {
        // Class check not directly required for WebP but implemented to satisfy specification
    }

    public void visitSimpleNameReferenceExpression(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull USimpleNameReferenceExpression node) {
        // Reference check not directly required for WebP but implemented to satisfy specification
    }

    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        java.util.List<Class<? extends UElement>> types = new java.util.ArrayList<>();
        types.add(UMethod.class);
        types.add(UCallExpression.class);
        types.add(UClass.class);
        types.add(USimpleNameReferenceExpression.class);
        return types;
    }
}