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
                    "RtlCompat",
                    "Right-to-left text compatibility issues",
                    "API 17 adds a `textAlignment` attribute to specify text alignment. However, " +
                    "if you are supporting older versions than API 17, you must also specify a " +
                    "gravity or layout_gravity attribute, since older platforms will ignore the " +
                    "textAlignment attribute.",
                    Category.RTL,
                    6,
                    Severity.ERROR,
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
        return java.util.Collections.singletonList("textAlignment");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String localName = attribute.getLocalName();
        if (localName == null) {
            localName = attribute.getName();
            int colon = localName.indexOf(':');
            if (colon != -1) {
                localName = localName.substring(colon + 1);
            }
        }
        if (!"textAlignment".equals(localName)) {
            return;
        }

        if (context.getProject().getMinSdk() >= 17) {
            return;
        }

        org.w3c.dom.Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        String namespace = "http://schemas.android.com/apk/res/android";
        if (element.hasAttributeNS(namespace, "gravity") || element.hasAttributeNS(namespace, "layout_gravity")) {
            return;
        }

        if (element.hasAttribute("android:gravity") || element.hasAttribute("android:layout_gravity")) {
            return;
        }

        Incident incident = new Incident(ISSUE, attribute, context.getLocation(attribute),
                "To support older versions than API 17, you must also specify `gravity` or `layout_gravity` when using `textAlignment`",
                null);
        context.report(incident);
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
                // No-op
            }
        };
    }
}