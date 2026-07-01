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
                    "API 17 adds a `textAlignment` attribute to specify text alignment. However, " +
                    "if you are supporting older versions than API 17, you must also specify a " +
                    "gravity or layout_gravity attribute, since older platforms will ignore the " +
                    "`textAlignment` attribute.",
                    Category.RTL,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public boolean filterIncident(@NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (context.getMainProject().getMinSdkVersion().getApiLevel() >= 17) {
            return;
        }

        Element element = attribute.getOwnerElement();
        String gravity = element.getAttributeNS(ANDROID_URI, ATTR_GRAVITY);
        String layoutGravity = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY);

        if (gravity.isEmpty() && layoutGravity.isEmpty()) {
            context.report(ISSUE, context.getLocation(attribute),
                    "When using `textAlignment`, also specify `gravity` or `layout_gravity` for backward compatibility");
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.emptyList();
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                // No-op: RTL compatibility is checked via XML attributes
            }
        };
    }
}