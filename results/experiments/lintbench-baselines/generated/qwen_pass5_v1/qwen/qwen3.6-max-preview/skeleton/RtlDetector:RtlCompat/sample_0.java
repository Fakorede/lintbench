package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

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
                    "`textAlignment` attribute.",
                    Category.RTL,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No cross-file aggregation required for this detector
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("textAlignment");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        int minSdk = context.getProject().getMinSdk() != null
                ? context.getProject().getMinSdk().getApiLevel()
                : 1;
        if (minSdk >= 17) {
            return;
        }

        Element element = attribute.getOwnerElement();
        boolean hasGravity = element.hasAttributeNS(ANDROID_URI, "gravity");
        boolean hasLayoutGravity = element.hasAttributeNS(ANDROID_URI, "layout_gravity");

        if (!hasGravity && !hasLayoutGravity) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "Attribute `textAlignment` is only used in API level 17 and higher. " +
                    "To support older versions, also specify `android:gravity` or `android:layout_gravity`.");
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
                int minSdk = context.getProject().getMinSdk() != null
                        ? context.getProject().getMinSdk().getApiLevel()
                        : 1;
                if (minSdk >= 17) {
                    return;
                }

                String name = node.getIdentifier();
                if (name != null && name.startsWith("TEXT_ALIGNMENT")) {
                    context.report(ISSUE, node, context.getLocation(node),
                            "Using `TEXT_ALIGNMENT` constants requires API level 17. " +
                            "To support older versions, also set gravity.");
                }
            }
        };
    }
}