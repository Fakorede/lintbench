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
import org.w3c.dom.NamedNodeMap;

public class RtlDetector extends LayoutDetector {

    private static final String KEY_REQUIRES_RTL = "requiresRtl";

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

    // Pairs of left/right attributes that should be symmetric
    private static final String[] LEFT_ATTRS = {
        ATTR_PADDING_LEFT,
        ATTR_LAYOUT_MARGIN_LEFT,
        "paddingLeft",
        "layout_marginLeft"
    };

    private static final String[] RIGHT_ATTRS = {
        ATTR_PADDING_RIGHT,
        ATTR_LAYOUT_MARGIN_RIGHT,
        "paddingRight",
        "layout_marginRight"
    };

    // Map from left attr to its corresponding right attr
    private static String getCounterpart(String attr) {
        if (attr.equals(ATTR_PADDING_LEFT) || attr.equals("paddingLeft")) {
            return ATTR_PADDING_RIGHT;
        } else if (attr.equals(ATTR_LAYOUT_MARGIN_LEFT) || attr.equals("layout_marginLeft")) {
            return ATTR_LAYOUT_MARGIN_RIGHT;
        } else if (attr.equals(ATTR_PADDING_RIGHT) || attr.equals("paddingRight")) {
            return ATTR_PADDING_LEFT;
        } else if (attr.equals(ATTR_LAYOUT_MARGIN_RIGHT) || attr.equals("layout_marginRight")) {
            return ATTR_LAYOUT_MARGIN_LEFT;
        }
        return null;
    }

    private static boolean isLeftAttr(String attr) {
        return attr.equals(ATTR_PADDING_LEFT)
                || attr.equals("paddingLeft")
                || attr.equals(ATTR_LAYOUT_MARGIN_LEFT)
                || attr.equals("layout_marginLeft");
    }

    private static boolean isRightAttr(String attr) {
        return attr.equals(ATTR_PADDING_RIGHT)
                || attr.equals("paddingRight")
                || attr.equals(ATTR_LAYOUT_MARGIN_RIGHT)
                || attr.equals("layout_marginRight");
    }

    /** Whether the project uses RTL support (targetSdkVersion >= 17) */
    private boolean mUsesRtl = false;

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Only report the incident if RTL is being used
        if (map.getBoolean(KEY_REQUIRES_RTL, false)) {
            return true;
        }
        return false;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Nothing special needed here
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                ATTR_PADDING_LEFT,
                ATTR_PADDING_RIGHT,
                ATTR_LAYOUT_MARGIN_LEFT,
                ATTR_LAYOUT_MARGIN_RIGHT,
                "paddingLeft",
                "paddingRight",
                "layout_marginLeft",
                "layout_marginRight");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
            // Strip namespace prefix if present
            int colon = name.indexOf(':');
            if (colon >= 0) {
                name = name.substring(colon + 1);
            }
        }

        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        // Find the counterpart attribute name
        String counterpart = getCounterpart(name);
        if (counterpart == null) {
            return;
        }

        // Check if the element already has the counterpart attribute
        NamedNodeMap attrs = element.getAttributes();
        boolean hasCounterpart = false;
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            String attrName = attr.getLocalName();
            if (attrName == null) {
                attrName = attr.getName();
                int colon = attrName.indexOf(':');
                if (colon >= 0) {
                    attrName = attrName.substring(colon + 1);
                }
            }
            if (attrName.equals(counterpart)) {
                hasCounterpart = true;
                break;
            }
        }

        if (!hasCounterpart) {
            boolean isLeft = isLeftAttr(name);
            String side = isLeft ? "left" : "right";
            String otherSide = isLeft ? "right" : "left";
            String message =
                    String.format(
                            "Attribute `%1$s` does not have a corresponding `%2$s` attribute",
                            name, counterpart);

            Location location = context.getLocation(attribute);
            Incident incident =
                    new Incident(ISSUE, attribute, location, message);
            LintMap map = new LintMap();
            map.put(KEY_REQUIRES_RTL, true);
            context.report(incident, map);
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
                // Check for references to paddingLeft/paddingRight/marginLeft/marginRight
                // in Java/Kotlin code - these may indicate RTL issues
                String name = node.getIdentifier();
                if (isLeftAttr(name) || isRightAttr(name)) {
                    String counterpart = getCounterpart(name);
                    if (counterpart != null) {
                        // We note the usage but don't report here directly,
                        // as we can't easily check for the counterpart in code context.
                        // The XML check is the primary detection mechanism.
                    }
                }
            }
        };
    }
}