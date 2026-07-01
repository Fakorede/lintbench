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
                    new Implementation(IconDetector.class, Scope.JAVA_AND_RESOURCE_FILES));

    @Override
    public void beforeCheckRootProject(@com.android.annotations.NonNull Context context) {
        super.beforeCheckRootProject(context);
    }

    @Override
    public void afterCheckEachProject(@com.android.annotations.NonNull Context context) {
        super.afterCheckEachProject(context);
    }

    @Override
    public void filterIncident(
            @com.android.annotations.NonNull Incident incident,
            @com.android.annotations.NonNull Context context,
            @com.android.annotations.NonNull LintMap map) {
        super.filterIncident(incident, context, map);
    }

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE ||
               folderType == com.android.resources.ResourceFolderType.MIPMAP;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("vector");
    }

    @Override
    public void visitElement(
            @com.android.annotations.NonNull XmlContext context,
            @com.android.annotations.NonNull org.w3c.dom.Element element) {
        // Implementation for checking icon sizes in XML drawables
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
            public void visitSimpleNameReferenceExpression(
                    @com.android.annotations.NonNull USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    @Override
    public void visitMethod(
            @com.android.annotations.NonNull JavaContext context,
            @com.android.annotations.NonNull UMethod method) {
        // Implementation for scanning method declarations
    }

    @Override
    public void visitCallExpression(
            @com.android.annotations.NonNull JavaContext context,
            @com.android.annotations.NonNull UCallExpression node) {
        // Implementation for scanning method calls or constructor invocations
    }

    @Override
    public void visitClass(
            @com.android.annotations.NonNull JavaContext context,
            @com.android.annotations.NonNull UClass declaration) {
        // Implementation for scanning class references or definitions
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @com.android.annotations.NonNull JavaContext context,
            @com.android.annotations.NonNull USimpleNameReferenceExpression node) {
        // Implementation for scanning resource references or variables
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