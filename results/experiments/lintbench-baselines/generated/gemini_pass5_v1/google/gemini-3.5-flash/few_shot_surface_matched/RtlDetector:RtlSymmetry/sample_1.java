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
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            RtlDetector.class,
            EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE),
            Scope.RESOURCE_FILE_SCOPE
    );

    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should "
                    + "probably also specify padding on the right side (and vice versa) "
                    + "for right-to-left layout symmetry.",
            Category.RTL,
            6,
            Severity.WARNING,
            IMPLEMENTATION
    );

    @Override
    public void filterIncident(@NonNull Incident incident) {
        super.filterIncident(incident);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        super.afterCheckRootProject(context);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                "paddingLeft",
                "paddingRight",
                "layout_marginLeft",
                "layout_marginRight",
                "layout_marginStart",
                "layout_marginEnd",
                "paddingStart",
                "paddingEnd"
        );
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
        }

        Element element = attribute.getOwnerElement();
        String namespace = "http://schemas.android.com/apk/res/android";

        if ("paddingLeft".equals(name)) {
            if (!hasAttribute(element, "paddingRight", namespace)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "paddingLeft is defined but paddingRight is not");
            }
        } else if ("paddingRight".equals(name)) {
            if (!hasAttribute(element, "paddingLeft", namespace)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "paddingRight is defined but paddingLeft is not");
            }
        } else if ("layout_marginLeft".equals(name)) {
            if (!hasAttribute(element, "layout_marginRight", namespace)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "layout_marginLeft is defined but layout_marginRight is not");
            }
        } else if ("layout_marginRight".equals(name)) {
            if (!hasAttribute(element, "layout_marginLeft", namespace)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "layout_marginRight is defined but layout_marginLeft is not");
            }
        } else if ("paddingStart".equals(name)) {
            if (!hasAttribute(element, "paddingEnd", namespace)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "paddingStart is defined but paddingEnd is not");
            }
        } else if ("paddingEnd".equals(name)) {
            if (!hasAttribute(element, "paddingStart", namespace)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "paddingEnd is defined but paddingStart is not");
            }
        } else if ("layout_marginStart".equals(name)) {
            if (!hasAttribute(element, "layout_marginEnd", namespace)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "layout_marginStart is defined but layout_marginEnd is not");
            }
        } else if ("layout_marginEnd".equals(name)) {
            if (!hasAttribute(element, "layout_marginStart", namespace)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "layout_marginEnd is defined but layout_marginStart is not");
            }
        }
    }

    private boolean hasAttribute(Element element, String localName, String namespace) {
        return element.hasAttributeNS(namespace, localName) || element.hasAttribute(localName);
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Nullable
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
        // Checked via UAST if referencing layout constants or similar in future extensions
    }
}