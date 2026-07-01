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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;

public class RtlDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

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
        return Arrays.asList(
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
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }
        String localName = attribute.getLocalName();
        String partner = null;
        switch (localName) {
            case "paddingLeft":
                partner = "paddingRight";
                break;
            case "paddingRight":
                partner = "paddingLeft";
                break;
            case "layout_marginLeft":
                partner = "layout_marginRight";
                break;
            case "layout_marginRight":
                partner = "layout_marginLeft";
                break;
            case "paddingStart":
                partner = "paddingEnd";
                break;
            case "paddingEnd":
                partner = "paddingStart";
                break;
            case "layout_marginStart":
                partner = "layout_marginEnd";
                break;
            case "layout_marginEnd":
                partner = "layout_marginStart";
                break;
        }

        if (partner != null) {
            org.w3c.dom.Element element = attribute.getOwnerElement();
            if (!element.hasAttributeNS(ANDROID_URI, partner)) {
                String message = String.format(
                        "To ensure symmetrical properties, `%s` should be accompanied by `%s`",
                        localName, partner);
                Incident incident = new Incident(ISSUE, attribute, context.getLocation(attribute), message);
                context.report(incident);
            }
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
                // No-op
            }
        };
    }
}