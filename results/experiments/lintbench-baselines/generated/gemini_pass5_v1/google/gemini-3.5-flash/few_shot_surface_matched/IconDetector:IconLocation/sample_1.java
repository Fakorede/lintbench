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

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    IconDetector.class,
                    java.util.EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)
            );

    public static final Issue ISSUE =
            Issue.create(
                    "IconLocation",
                    "Image defined in density-independent drawable folder",
                    "The res/drawable folder is intended for density-independent graphics such as "
                            + "shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and consider "
                            + "providing higher and lower resolution versions in `drawable-ldpi`, `drawable-hdpi` "
                            + "and `drawable-xhdpi`. If the icon **really** is density independent (for example "
                            + "a solid color) you can place it in `drawable-nodpi`.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION
            );

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(Context context) {
        super.beforeCheckRootProject(context);
    }

    @Override
    public void afterCheckEachProject(Context context) {
        super.afterCheckEachProject(context);
    }

    @Override
    public void filterIncident(Context context, Incident incident) {
        super.filterIncident(context, incident);
    }

    @Override
    public boolean appliesTo(Context context, java.io.File file) {
        return super.appliesTo(context, file);
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("bitmap");
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        java.io.File file = context.file;
        java.io.File parent = file.getParentFile();
        if (parent != null && "drawable".equals(parent.getName())) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Bitmap defined in density-independent drawable folder"
            );
        }
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                // stub
            }

            @Override
            public void visitMethod(UMethod node) {
                // stub
            }

            @Override
            public void visitCallExpression(UCallExpression node) {
                // stub
            }

            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
                // stub
            }
        };
    }

    @Override
    public void visitMethod(JavaContext context, UMethod method) {
        // stub
    }

    @Override
    public void visitCallExpression(JavaContext context, UCallExpression node) {
        // stub
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        // stub
    }

    @Override
    public void visitSimpleNameReferenceExpression(JavaContext context, USimpleNameReferenceExpression node) {
        // stub
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
}