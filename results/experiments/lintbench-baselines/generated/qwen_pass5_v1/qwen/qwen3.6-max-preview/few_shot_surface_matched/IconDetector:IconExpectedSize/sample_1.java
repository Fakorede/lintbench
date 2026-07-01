package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import com.intellij.psi.*;
import org.jetbrains.uast.*;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExpectedSize",
            "Icon has incorrect size",
            "There are predefined sizes (for each density) for launcher icons. You should follow these conventions to make sure your icons fit in with the overall look of the platform.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void beforeCheckRootProject(Context context) {
    }

    @Override
    public void afterCheckEachProject(Context context) {
    }

    @Override
    public boolean filterIncident(Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(Context context, java.io.File file) {
        return true;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("ImageView");
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
    }

    @Override
    public UElementHandler createUastHandler() {
        return null;
    }

    @Override
    public void visitMethod(JavaContext context, UMethod node) {
    }

    @Override
    public void visitCallExpression(JavaContext context, UCallExpression node) {
    }

    @Override
    public void visitClass(JavaContext context, UClass node) {
    }

    @Override
    public void visitSimpleNameReferenceExpression(JavaContext context, USimpleNameReferenceExpression node) {
    }

    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Collections.emptyList();
    }
}