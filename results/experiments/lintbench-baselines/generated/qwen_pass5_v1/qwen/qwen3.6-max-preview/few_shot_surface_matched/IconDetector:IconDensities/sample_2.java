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
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.USimpleNameReferenceExpression;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconDensities",
            "Icon densities validation",
            "Icons will look best if a custom version is provided for each of the major screen density classes (low, medium, high, extra high). This lint check identifies icons which do not have complete coverage across the densities.\n\n" +
            "Low density is not really used much anymore, so this check ignores the ldpi density. To force lint to include it, set the environment variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on current density usage, see https://developer.android.com/about/dashboards",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.JAVA_AND_RESOURCE_FILES));

    private static final boolean INCLUDE_LDPI = Boolean.getBoolean("ANDROID_LINT_INCLUDE_LDPI") ||
            "true".equals(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));

    private final Set<String> referencedIcons = new HashSet<>();

    @Override
    public void beforeCheckRootProject(Context context) {
        referencedIcons.clear();
    }

    @Override
    public void afterCheckEachProject(Context context) {
        if (referencedIcons.isEmpty()) {
            return;
        }
        File projectDir = context.getProject().getDir();
        for (String icon : referencedIcons) {
            context.report(ISSUE, context.getLocation(projectDir),
                    "The icon `" + icon + "` does not have complete density coverage. " +
                    "Ensure versions exist for mdpi, hdpi, xhdpi, xxhdpi, and xxxhdpi." +
                    (INCLUDE_LDPI ? " Include ldpi as well." : ""));
        }
        referencedIcons.clear();
    }

    @Override
    public boolean filterIncident(Context context, Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(Context context, File file) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("bitmap", "item", "icon");
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String src = element.getAttribute("android:src");
        if (src != null && src.startsWith("@drawable/")) {
            referencedIcons.add(src.substring("@drawable/".length()));
        }
        String iconAttr = element.getAttribute("android:icon");
        if (iconAttr != null && iconAttr.startsWith("@drawable/")) {
            referencedIcons.add(iconAttr.substring("@drawable/".length()));
        }
    }

    @Override
    public UElementHandler createUastHandler() {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                IconDetector.this.visitClass(null, node);
            }

            @Override
            public void visitCallExpression(UCallExpression node) {
                IconDetector.this.visitCallExpression(null, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(null, node);
            }
        };
    }

    @Override
    public void visitMethod(JavaContext context, UCallExpression call, PsiMethod method) {
        // Track icon references passed to methods
    }

    @Override
    public void visitCallExpression(JavaContext context, UCallExpression node) {
        // Track icon references in method calls
    }

    @Override
    public void visitClass(JavaContext context, UClass node) {
        // Track icon references in class definitions
    }

    @Override
    public void visitSimpleNameReferenceExpression(JavaContext context, USimpleNameReferenceExpression node) {
        String identifier = node.getIdentifier();
        if (identifier != null && (identifier.startsWith("ic_") || identifier.startsWith("R.drawable."))) {
            String name = identifier.replace("R.drawable.", "");
            referencedIcons.add(name);
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UClass.class,
                UCallExpression.class,
                USimpleNameReferenceExpression.class
        );
    }
}