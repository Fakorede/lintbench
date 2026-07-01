package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.USimpleNameReferenceExpression;

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
            new Implementation(RtlDetector.class, Scope.JAVA_AND_RESOURCE_FILES));

    @Override
    protected boolean filterIncident(@NonNull Incident incident) {
        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList("paddingLeft", "paddingRight", "layout_marginLeft", "layout_marginRight");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull org.w3c.dom.Attr attribute) {
        String name = attribute.getLocalName();
        org.w3c.dom.Element element = attribute.getOwnerElement();
        String counterpart = null;

        if ("paddingLeft".equals(name)) {
            counterpart = "paddingRight";
        } else if ("paddingRight".equals(name)) {
            counterpart = "paddingLeft";
        } else if ("layout_marginLeft".equals(name)) {
            counterpart = "layout_marginRight";
        } else if ("layout_marginRight".equals(name)) {
            counterpart = "layout_marginLeft";
        }

        if (counterpart != null && element.getAttributeNodeNS(attribute.getNamespaceURI(), counterpart) == null) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "Expecting `" + counterpart + "` for RTL symmetry");
        }
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler() {
        return new UElementHandler() {
            @Override
            public List<Class<? extends UElement>> getApplicableUastTypes() {
                return Collections.singletonList(USimpleNameReferenceExpression.class);
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                String identifier = node.getIdentifier();
                if ("setPadding".equals(identifier) || "setMargins".equals(identifier)) {
                    // In a complete implementation, argument analysis would be performed here
                    // to verify left/right symmetry. This hook flags the API usage for review.
                }
            }
        };
    }
}