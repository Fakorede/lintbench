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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should "
                    + "probably also specify padding on the right side (and vice versa) for "
                    + "right-to-left layout symmetry.",
            Category.RTL,
            5,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.JAVA_AND_RESOURCE_FILES)
    );

    @Override
    public void filterIncident(Incident incident) {
        super.filterIncident(incident);
    }

    @Override
    public void afterCheckRootProject(Context context) {
        super.afterCheckRootProject(context);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList("paddingLeft", "paddingRight", "layout_marginLeft", "layout_marginRight");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String name = attribute.getLocalName();
        Element element = attribute.getOwnerElement();
        String namespace = attribute.getNamespaceURI();

        if ("paddingLeft".equals(name)) {
            if (!element.hasAttributeNS(namespace, "paddingRight")) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "Feedback: `paddingLeft` is defined but `paddingRight` is not");
            }
        } else if ("paddingRight".equals(name)) {
            if (!element.hasAttributeNS(namespace, "paddingLeft")) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "Feedback: `paddingRight` is defined but `paddingLeft` is not");
            }
        } else if ("layout_marginLeft".equals(name)) {
            if (!element.hasAttributeNS(namespace, "layout_marginRight")) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "Feedback: `layout_marginLeft` is defined but `layout_marginRight` is not");
            }
        } else if ("layout_marginRight".equals(name)) {
            if (!element.hasAttributeNS(namespace, "layout_marginLeft")) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "Feedback: `layout_marginRight` is defined but `layout_marginLeft` is not");
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
                RtlDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    public void visitSimpleNameReferenceExpression(JavaContext context, USimpleNameReferenceExpression node) {
        // Source analysis logic if required in the future
    }
}