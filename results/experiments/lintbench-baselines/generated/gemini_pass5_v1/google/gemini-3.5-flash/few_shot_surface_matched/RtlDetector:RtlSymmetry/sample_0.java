package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
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
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "RtlSymmetry",
                    "Padding and margin symmetry",
                    "If you specify padding or margin on the left side of a layout, you should "
                            + "probably also specify padding on the right side (and vice versa) "
                            + "for right-to-left layout symmetry.",
                    Category.RTL,
                    6,
                    Severity.WARNING,
                    new Implementation(RtlDetector.class, Scope.JAVA_AND_RESOURCE_FILES));

    public RtlDetector() {}

    @Override
    public void filterIncident(
            @com.android.annotations.NonNull Incident incident,
            @com.android.annotations.NonNull Context context) {
        super.filterIncident(incident, context);
    }

    @Override
    public void afterCheckRootProject(@com.android.annotations.NonNull Context context) {
        super.afterCheckRootProject(context);
    }

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Arrays.asList(
                "paddingLeft",
                "paddingRight",
                "layout_marginLeft",
                "layout_marginRight"
        );
    }

    @Override
    public void visitAttribute(
            @com.android.annotations.NonNull XmlContext context,
            @com.android.annotations.NonNull org.w3c.dom.Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
        }

        org.w3c.dom.Element element = attribute.getOwnerElement();
        if ("paddingLeft".equals(name)) {
            if (!hasAttribute(element, "paddingRight")) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "To guarantee symmetrical margins, you should also define `paddingRight` when defining `paddingLeft`.");
            }
        } else if ("paddingRight".equals(name)) {
            if (!hasAttribute(element, "paddingLeft")) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "To guarantee symmetrical margins, you should also define `paddingLeft` when defining `paddingRight`.");
            }
        } else if ("layout_marginLeft".equals(name)) {
            if (!hasAttribute(element, "layout_marginRight")) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "To guarantee symmetrical margins, you should also define `layout_marginRight` when defining `layout_marginLeft`.");
            }
        } else if ("layout_marginRight".equals(name)) {
            if (!hasAttribute(element, "layout_marginLeft")) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "To guarantee symmetrical margins, you should also define `layout_marginLeft` when defining `layout_marginRight`.");
            }
        }
    }

    private boolean hasAttribute(org.w3c.dom.Element element, String name) {
        return element.hasAttributeNS("http://schemas.android.com/apk/res/android", name)
                || element.hasAttribute(name);
    }

    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@com.android.annotations.NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(
                    @com.android.annotations.NonNull USimpleNameReferenceExpression node) {
                RtlDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    public void visitSimpleNameReferenceExpression(
            @com.android.annotations.NonNull JavaContext context,
            @com.android.annotations.NonNull USimpleNameReferenceExpression node) {
        // No-op placeholder for Java analysis symmetry
    }
}