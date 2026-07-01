package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;

public class RtlDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(RtlDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "RtlSymmetry",
                    "Padding and margin symmetry",
                    "If you specify padding or margin on the left side of a layout, you should "
                            + "probably also specify padding on the right side (and vice versa) for "
                            + "right-to-left layout symmetry.",
                    Category.RTL,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return java.util.Arrays.asList(
                "paddingLeft",
                "paddingRight",
                "layout_marginLeft",
                "layout_marginRight",
                "paddingStart",
                "paddingEnd",
                "layout_marginStart",
                "layout_marginEnd"
        );
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        org.w3c.dom.Element element = attribute.getOwnerElement();
        String namespace = attribute.getNamespaceURI();

        String counterpart = null;
        switch (name) {
            case "paddingLeft":
                counterpart = "paddingRight";
                break;
            case "paddingRight":
                counterpart = "paddingLeft";
                break;
            case "layout_marginLeft":
                counterpart = "layout_marginRight";
                break;
            case "layout_marginRight":
                counterpart = "layout_marginLeft";
                break;
            case "paddingStart":
                counterpart = "paddingEnd";
                break;
            case "paddingEnd":
                counterpart = "paddingStart";
                break;
            case "layout_marginStart":
                counterpart = "layout_marginEnd";
                break;
            case "layout_marginEnd":
                counterpart = "layout_marginStart";
                break;
        }

        if (counterpart != null && namespace != null) {
            if (!element.hasAttributeNS(namespace, counterpart)) {
                String message = String.format(
                        "To guarantee symmetrical layouts, when you define %1$s you should also define %2$s",
                        name, counterpart);
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        message
                );
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                // Not implemented for Java sources as symmetry checks primarily target XML layouts
            }
        };
    }
}