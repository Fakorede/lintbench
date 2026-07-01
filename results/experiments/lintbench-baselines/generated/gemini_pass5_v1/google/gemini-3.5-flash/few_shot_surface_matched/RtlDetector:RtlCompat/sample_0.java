package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "API 17 adds a `textAlignment` attribute to specify text alignment. "
                    + "However, if you are supporting older versions than API 17, "
                    + "you must also specify a gravity or layout_gravity attribute, "
                    + "since older platforms will ignore the `textAlignment` attribute.",
            Category.RTL,
            6,
            Severity.ERROR,
            new Implementation(
                    RtlDetector.class,
                    java.util.EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)
            )
    );

    @Override
    public void filterIncident(@com.android.annotations.NonNull Incident incident) {
        super.filterIncident(incident);
    }

    @Override
    public void afterCheckRootProject(@com.android.annotations.NonNull Context context) {
        super.afterCheckRootProject(context);
    }

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Collections.singletonList("textAlignment");
    }

    @Override
    public void visitAttribute(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull org.w3c.dom.Attr attribute) {
        if ("textAlignment".equals(attribute.getLocalName())) {
            if (context.getProject().getMinSdkVersion().getFeatureLevel() < 17) {
                org.w3c.dom.Element element = attribute.getOwnerElement();
                String gravity = element.getAttributeNS("http://schemas.android.com/apk/res/android", "gravity");
                String layoutGravity = element.getAttributeNS("http://schemas.android.com/apk/res/android", "layout_gravity");
                if ((gravity == null || gravity.isEmpty()) && (layoutGravity == null || layoutGravity.isEmpty())) {
                    context.report(ISSUE, attribute, context.getLocation(attribute),
                            "To support older versions than API 17, you must also specify `gravity` or `layout_gravity` when using `textAlignment`");
                }
            }
        }
    }

    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        java.util.List<Class<? extends UElement>> types = new java.util.ArrayList<>();
        types.add(USimpleNameReferenceExpression.class);
        return types;
    }

    @Override
    public UElementHandler createUastHandler(@com.android.annotations.NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(@com.android.annotations.NonNull USimpleNameReferenceExpression node) {
                RtlDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    public void visitSimpleNameReferenceExpression(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull USimpleNameReferenceExpression node) {
        // No-op implementation for source code scanning; the primary logic resides in the XML check.
    }
}