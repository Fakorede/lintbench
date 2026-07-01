package com.android.tools.lint.checks;

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
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.visitor.UastVisitor;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Implementation IMPLEMENTATION = new Implementation(
            IconDetector.class,
            java.util.EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)
    );

    public static final Issue ISSUE = Issue.create(
            "IconExtension",
            "Icon format does not match the file extension",
            "Ensures that icons have the correct file extension (e.g. a `.png` file is really in the PNG format and not for example a GIF file named `.png`).",
            Category.ICONS,
            5,
            Severity.WARNING,
            IMPLEMENTATION
    );

    @Override
    public void beforeCheckRootProject(Context context) {
        super.beforeCheckRootProject(context);
    }

    @Override
    public void afterCheckEachProject(Context context) {
        super.afterCheckEachProject(context);
    }

    @Override
    public boolean filterIncident(Context context, Incident incident, java.util.Map<String, Object> map) {
        return super.filterIncident(context, incident, map);
    }

    @Override
    public boolean appliesTo(Context context, java.io.File file) {
        return super.appliesTo(context, file);
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
    }

    @Override
    public UastVisitor createUastHandler(JavaContext context) {
        return null;
    }

    @Override
    public void visitMethod(JavaContext context, UCallExpression node, PsiMethod method) {
    }

    @Override
    public void visitCallExpression(JavaContext context, UCallExpression node) {
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
    }

    @Override
    public void visitSimpleNameReferenceExpression(JavaContext context, USimpleNameReferenceExpression node) {
    }

    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        return null;
    }
}