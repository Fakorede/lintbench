package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should "
                    + "probably also specify padding on the right side (and vice versa) for "
                    + "right-to-left layout symmetry.",
            Category.create("RTL", 100),
            5,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList("paddingLeft", "paddingRight", "layout_marginLeft", "layout_marginRight");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        String counterpart = null;
        if ("paddingLeft".equals(name) || "layout_marginLeft".equals(name)) {
            counterpart = name.replace("Left", "Right");
        } else if ("paddingRight".equals(name) || "layout_marginRight".equals(name)) {
            counterpart = name.replace("Right", "Left");
        } else {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (!element.hasAttributeNS(ANDROID_URI, counterpart)) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "Specify `" + counterpart + "` for RTL symmetry");
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                RtlDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        String id = node.getIdentifier();
        if ("paddingLeft".equals(id) || "layout_marginLeft".equals(id) ||
            "paddingRight".equals(id) || "layout_marginRight".equals(id)) {
            context.report(ISSUE, node, context.getLocation(node),
                    "Ensure corresponding right/left padding or margin is set for RTL symmetry");
        }
    }

    @Override
    public boolean filterIncident(@NonNull Incident incident) {
        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No-op
    }
}