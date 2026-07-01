package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
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
                            + "probably also specify padding on the right side (and vice versa) for "
                            + "right-to-left layout symmetry.",
                    Category.RTL,
                    5,
                    Severity.WARNING,
                    new Implementation(RtlDetector.class, Scope.JAVA_AND_RESOURCE_FILES));

    public RtlDetector() {}

    @Override
    public void filterIncident(Incident incident) {
        super.filterIncident(incident);
    }

    @Override
    public void afterCheckRootProject(Context context) {
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
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            return;
        }
        org.w3c.dom.Element element = attribute.getOwnerElement();
        if (name.equals("paddingLeft")) {
            if (!element.hasAttributeNS("http://schemas.android.com/apk/res/android", "paddingRight")) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "Feedback: paddingLeft defined without paddingRight");
            }
        } else if (name.equals("paddingRight")) {
            if (!element.hasAttributeNS("http://schemas.android.com/apk/res/android", "paddingLeft")) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "Feedback: paddingRight defined without paddingLeft");
            }
        } else if (name.equals("layout_marginLeft")) {
            if (!element.hasAttributeNS("http://schemas.android.com/apk/res/android", "layout_marginRight")) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "Feedback: layout_marginLeft defined without layout_marginRight");
            }
        } else if (name.equals("layout_marginRight")) {
            if (!element.hasAttributeNS("http://schemas.android.com/apk/res/android", "layout_marginLeft")) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "Feedback: layout_marginRight defined without layout_marginLeft");
            }
        }
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
                RtlDetector.this.visitSimpleNameReferenceExpression(node);
            }
        };
    }

    public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
        // No-op implementation for symmetry checks via UAST reference scanning
    }

    @Override
    public java.util.List<java.lang.Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Collections.singletonList(USimpleNameReferenceExpression.class);
    }
}