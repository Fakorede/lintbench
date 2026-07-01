package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
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
                    "IconExpectedSize",
                    "Icon has incorrect size",
                    "There are predefined sizes (for each density) for launcher icons. You "
                            + "should follow these conventions to make sure your icons fit in with the "
                            + "overall look of the platform.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            java.util.EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE)));

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(com.android.annotations.NonNull Context context) {
        super.beforeCheckRootProject(context);
    }

    @Override
    public void afterCheckEachProject(com.android.annotations.NonNull Context context) {
        super.afterCheckEachProject(context);
    }

    @Override
    public void filterIncident(com.android.annotations.NonNull Incident incident) {
        super.filterIncident(incident);
    }

    @Override
    public boolean appliesTo(com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE
                || folderType == com.android.resources.ResourceFolderType.MIPMAP;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Arrays.asList("adaptive-icon", "vector", "bitmap");
    }

    @Override
    public void visitElement(
            com.android.annotations.NonNull XmlContext context,
            com.android.annotations.NonNull org.w3c.dom.Element element) {
        String tagName = element.getTagName();
        if ("vector".equals(tagName)) {
            String width = element.getAttributeNS("http://schemas.android.com/apk/res/android", "width");
            if (width != null && width.endsWith("dp")) {
                try {
                    int w = Integer.parseInt(width.replaceAll("[^0-9]", ""));
                    if (w > 512) {
                        context.report(
                                ISSUE,
                                element,
                                context.getLocation(element),
                                "Icon width is larger than standard launcher sizes");
                    }
                } catch (NumberFormatException e) {
                    // Ignore parsing errors
                }
            }
        }
    }

    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        java.util.List<Class<? extends UElement>> types = new java.util.ArrayList<>();
        types.add(UClass.class);
        types.add(UMethod.class);
        types.add(UCallExpression.class);
        types.add(USimpleNameReferenceExpression.class);
        return types;
    }

    @Override
    public UElementHandler createUastHandler(com.android.annotations.NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(com.android.annotations.NonNull UClass node) {
                IconDetector.this.visitClass(node);
            }

            @Override
            public void visitMethod(com.android.annotations.NonNull UMethod node) {
                IconDetector.this.visitMethod(node);
            }

            @Override
            public void visitCallExpression(com.android.annotations.NonNull UCallExpression node) {
                IconDetector.this.visitCallExpression(node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    com.android.annotations.NonNull USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(node);
            }
        };
    }

    @Override
    public void visitClass(
            com.android.annotations.NonNull JavaContext context,
            com.android.annotations.NonNull UClass declaration) {
        // SourceCodeScanner class visitor implementation
    }

    @Override
    public void visitMethodCall(
            com.android.annotations.NonNull JavaContext context,
            com.android.annotations.NonNull UCallExpression node,
            com.android.annotations.NonNull PsiMethod method) {
        // SourceCodeScanner method call implementation
    }

    public void visitClass(com.android.annotations.NonNull UClass node) {
        // UElementHandler delegate helper
    }

    public void visitMethod(com.android.annotations.NonNull UMethod node) {
        // UElementHandler delegate helper
    }

    public void visitCallExpression(com.android.annotations.NonNull UCallExpression node) {
        // UElementHandler delegate helper
    }

    public void visitSimpleNameReferenceExpression(
            com.android.annotations.NonNull USimpleNameReferenceExpression node) {
        // UElementHandler delegate helper
    }
}