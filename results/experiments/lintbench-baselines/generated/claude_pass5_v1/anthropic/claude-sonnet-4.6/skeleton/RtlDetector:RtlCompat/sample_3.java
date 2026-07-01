package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_GRAVITY;
import static com.android.SdkConstants.ATTR_LAYOUT_GRAVITY;
import static com.android.SdkConstants.ATTR_TEXT_ALIGNMENT;

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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends LayoutDetector {

    private static final String KEY_MIN_SDK = "minSdk";

    private static final Implementation IMPLEMENTATION =
            new Implementation(RtlDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "RtlCompat",
                    "Right-to-left text compatibility issues",
                    "API 17 adds a `textAlignment` attribute to specify text alignment. However, "
                            + "if you are supporting older versions than API 17, you must **also** specify a "
                            + "gravity or layout_gravity attribute, since older platforms will ignore the "
                            + "`textAlignment` attribute.",
                    Category.RTL,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Only report if minSdk < 17
        if (context.getMainProject().getMinSdk() < 17) {
            return true;
        }
        return false;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Nothing special needed after checking root project
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Check if textAlignment is used without a gravity/layout_gravity fallback
        Element element = attribute.getOwnerElement();

        boolean hasGravity = element.hasAttributeNS(ANDROID_URI, ATTR_GRAVITY);
        boolean hasLayoutGravity = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY);

        if (!hasGravity && !hasLayoutGravity) {
            String message =
                    "To support older versions than API 17 (current min is %1$d) you "
                            + "should **also** specify `gravity` or `layout_gravity=\"%2$s\"`";

            String textAlignmentValue = attribute.getValue();
            String gravityEquivalent = getGravityForTextAlignment(textAlignmentValue);

            String formattedMessage =
                    String.format(message, context.getMainProject().getMinSdk(), gravityEquivalent);

            Incident incident =
                    new Incident(ISSUE, attribute, context.getLocation(attribute), formattedMessage);
            context.report(incident, map -> map.put(KEY_MIN_SDK, context.getMainProject().getMinSdk()));
        }
    }

    private String getGravityForTextAlignment(String textAlignment) {
        if (textAlignment == null) {
            return "center";
        }
        switch (textAlignment) {
            case "center":
                return "center";
            case "textStart":
            case "viewStart":
                return "start";
            case "textEnd":
            case "viewEnd":
                return "end";
            case "gravity":
                return "center";
            default:
                return textAlignment;
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
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // Check for references to textAlignment in Java/Kotlin code
                String name = node.getIdentifier();
                if ("textAlignment".equals(name) || "TEXT_ALIGNMENT_GRAVITY".equals(name)
                        || "TEXT_ALIGNMENT_CENTER".equals(name)
                        || "TEXT_ALIGNMENT_TEXT_START".equals(name)
                        || "TEXT_ALIGNMENT_TEXT_END".equals(name)
                        || "TEXT_ALIGNMENT_VIEW_START".equals(name)
                        || "TEXT_ALIGNMENT_VIEW_END".equals(name)) {
                    // Only flag if minSdk < 17
                    if (context.getMainProject().getMinSdk() < 17) {
                        String message =
                                "`textAlignment` requires API level 17 (current min is "
                                        + context.getMainProject().getMinSdk()
                                        + "), and there is no fallback for older versions";
                        Incident incident =
                                new Incident(
                                        ISSUE,
                                        node,
                                        context.getLocation(node),
                                        message);
                        context.report(incident, map -> map.put(KEY_MIN_SDK, context.getMainProject().getMinSdk()));
                    }
                }
            }
        };
    }
}