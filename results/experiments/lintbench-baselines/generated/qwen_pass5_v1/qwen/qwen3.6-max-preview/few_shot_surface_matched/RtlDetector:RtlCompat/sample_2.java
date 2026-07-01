package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiElement;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UReferenceExpression;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "API 17 adds a `textAlignment` attribute to specify text alignment. However, "
                    + "if you are supporting older versions than API 17, you must also specify a "
                    + "gravity or layout_gravity attribute, since older platforms will ignore the "
                    + "`textAlignment` attribute.",
            Category.RTL,
            5,
            Severity.ERROR,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE));

    @Override
    public boolean filterIncident(@NonNull Incident incident) {
        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Lifecycle hook for post-project analysis cleanup or aggregation
    }

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("textAlignment");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull org.w3c.dom.Attr attribute) {
        org.w3c.dom.Element element = attribute.getOwnerElement();
        String gravity = element.getAttribute("gravity");
        String layoutGravity = element.getAttribute("layout_gravity");

        if (gravity.isEmpty() && layoutGravity.isEmpty()) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "Specifying `textAlignment` without `gravity` or `layout_gravity` is not "
                            + "compatible with API levels below 17");
        }
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler() {
        return new UElementHandler() {
            @Override
            public void visitReferenceExpression(@NonNull UReferenceExpression node) {
                // UAST traversal delegate for Java/Kotlin source files
            }
        };
    }

    @Override
    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull UReferenceExpression node, @Nullable PsiElement target) {
        // Hook for inspecting simple name references in Java/Kotlin code
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UReferenceExpression.class);
    }
}