package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
import static com.android.SdkConstants.ATTR_PADDING_LEFT;
import static com.android.SdkConstants.ATTR_PADDING_RIGHT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
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

    private static final String KEY_HAS_RTL = "hasRtl";

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

    /** Whether we've seen any RTL attributes (paddingStart, marginStart, etc.) in the project */
    private boolean mUsesRtl;

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Only report if the project actually uses RTL attributes
        if (map.getBoolean(KEY_HAS_RTL, false)) {
            return true;
        }
        // If we haven't seen RTL usage, suppress the warning
        return false;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Nothing special needed here; filtering is done in filterIncident
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                ATTR_PADDING_LEFT,
                ATTR_PADDING_RIGHT,
                ATTR_LAYOUT_MARGIN_LEFT,
                ATTR_LAYOUT_MARGIN_RIGHT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        String name = attribute.getLocalName();
        if (name == null) {
            return;
        }

        String opposite = getOppositeAttribute(name);
        if (opposite == null) {
            return;
        }

        // Check if the opposite attribute is present
        if (element.hasAttributeNS(ANDROID_URI, opposite)) {
            // Both sides specified, no issue
            return;
        }

        String message = getSymmetryMessage(name, opposite);

        Location location = context.getLocation(attribute);

        LintMap map = new LintMap();
        map.put(KEY_HAS_RTL, mUsesRtl);

        context.report(
                new Incident(
                        ISSUE,
                        attribute,
                        location,
                        message,
                        null)
                        .withMap(map));
    }

    @Nullable
    private static String getOppositeAttribute(@NonNull String attribute) {
        switch (attribute) {
            case ATTR_PADDING_LEFT:
                return ATTR_PADDING_RIGHT;
            case ATTR_PADDING_RIGHT:
                return ATTR_PADDING_LEFT;
            case ATTR_LAYOUT_MARGIN_LEFT:
                return ATTR_LAYOUT_MARGIN_RIGHT;
            case ATTR_LAYOUT_MARGIN_RIGHT:
                return ATTR_LAYOUT_MARGIN_LEFT;
            default:
                return null;
        }
    }

    @NonNull
    private static String getSymmetryMessage(@NonNull String attribute, @NonNull String opposite) {
        return "When specifying `" + attribute + "` you should probably also specify `"
                + opposite + "` for right-to-left layout symmetry";
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
                String name = node.getIdentifier();
                // Check if this reference is to an RTL-related attribute constant
                if (isRtlAttributeReference(name)) {
                    mUsesRtl = true;
                }
            }
        };
    }

    private static boolean isRtlAttributeReference(@NonNull String name) {
        // Look for references to RTL-related attribute names
        return name.contains("Start")
                || name.contains("End")
                || name.contains("paddingStart")
                || name.contains("paddingEnd")
                || name.contains("marginStart")
                || name.contains("marginEnd")
                || name.contains("layoutDirection")
                || name.contains("textDirection")
                || name.contains("textAlignment")
                || name.equals("LAYOUT_DIRECTION_RTL")
                || name.equals("LAYOUT_DIRECTION_LOCALE")
                || name.equals("TEXT_DIRECTION_RTL")
                || name.equals("TEXT_DIRECTION_LOCALE")
                || name.equals("TEXT_DIRECTION_FIRST_STRONG_RTL");
    }
}