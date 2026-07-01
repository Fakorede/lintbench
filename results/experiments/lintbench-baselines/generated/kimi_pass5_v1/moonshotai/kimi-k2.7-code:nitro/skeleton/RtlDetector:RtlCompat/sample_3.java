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
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_TEXT_ALIGNMENT = "textAlignment";
    private static final String ATTR_GRAVITY = "gravity";
    private static final String ATTR_LAYOUT_GRAVITY = "layout_gravity";

    private static final Implementation IMPLEMENTATION =
            new Implementation(RtlDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "RtlCompat",
                    "Right-to-left text compatibility issues",
                    "The android:textAlignment attribute was introduced in API level 17. When "
                            + "supporting older devices you must also specify android:gravity or "
                            + "android:layout_gravity, because earlier platforms ignore "
                            + "textAlignment.",
                    Category.RTL,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private int mMinSdk = -1;

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        mMinSdk = context.getMainProject().getMinSdk();
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        int minSdk = mMinSdk >= 0 ? mMinSdk : context.getMainProject().getMinSdk();
        return minSdk >= 17;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())
                || !ATTR_TEXT_ALIGNMENT.equals(attribute.getLocalName())) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (hasAttribute(element, ATTR_GRAVITY) || hasAttribute(element, ATTR_LAYOUT_GRAVITY)) {
            return;
        }

        context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "When using android:textAlignment on devices older than API 17 you must also "
                        + "specify android:gravity or android:layout_gravity");
    }

    private static boolean hasAttribute(Element element, String localName) {
        String value = element.getAttributeNS(ANDROID_URI, localName);
        return value != null && !value.isEmpty();
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.emptyList();
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // No Java-side checks are performed by this detector.
            }
        };
    }
}