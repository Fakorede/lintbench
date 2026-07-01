package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    IconDetector.class,
                    java.util.EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE));

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
                    IMPLEMENTATION);

    @Override
    public void beforeCheckRootProject(Context context) {
        super.beforeCheckRootProject(context);
    }

    @Override
    public void afterCheckEachProject(Context context) {
        super.afterCheckEachProject(context);
    }

    @Override
    public void filterIncident(Context context, Incident incident, LintMap map) {
    }

    @Override
    public boolean appliesTo(Project project, java.io.File file) {
        return true;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("item");
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String value = element.getAttributeNS("http://schemas.android.com/apk/res/android", "drawable");
        if (value != null && value.endsWith(".png")) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Convert launcher/drawable icon to WebP"
            );
        }
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod node) {
                IconDetector.this.visitMethod(context, node);
            }

            @Override
            public void visitCallExpression(UCallExpression node) {
                IconDetector.this.visitCallExpression(context, node);
            }

            @Override
            public void visitClass(UClass node) {
                IconDetector.this.visitClass(context, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    public void visitMethod(JavaContext context, UMethod method) {
    }

    public void visitCallExpression(JavaContext context, UCallExpression node) {
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
    }

    public void visitSimpleNameReferenceExpression(JavaContext context, USimpleNameReferenceExpression node) {
        String name = node.getIdentifier();
        if (name.startsWith("ic_launcher") || name.endsWith("_png")) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Convert launcher icon to WebP"
            );
        }
    }

    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Arrays.asList(
                UMethod.class,
                UCallExpression.class,
                UClass.class,
                USimpleNameReferenceExpression.class
        );
    }
}