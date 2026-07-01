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
import java.util.ArrayList;
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

    // Attributes that have a corresponding "opposite" attribute for RTL symmetry
    private static final String ATTR_LAYOUT_MARGIN_START = "layout_marginStart";
    private static final String ATTR_LAYOUT_MARGIN_END = "layout_marginEnd";
    private static final String ATTR_PADDING_START = "paddingStart";
    private static final String ATTR_PADDING_END = "paddingEnd";

    // Map from left attributes to their right counterparts
    private static final List<String[]> PAIRS =
            Arrays.asList(
                    new String[] {ATTR_PADDING_LEFT, ATTR_PADDING_RIGHT},
                    new String[] {ATTR_LAYOUT_MARGIN_LEFT, ATTR_LAYOUT_MARGIN_RIGHT},
                    new String[] {ATTR_PADDING_START, ATTR_PADDING_END},
                    new String[] {ATTR_LAYOUT_MARGIN_START, ATTR_LAYOUT_MARGIN_END});

    // Whether the project uses RTL support (has targetSdkVersion >= 17 and supportsRtl=true)
    private boolean mUsesRtl;

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        if (map.getBoolean(KEY_REQUIRES_RTL, false)) {
            return mUsesRtl;
        }
        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Check if the project supports RTL
        mUsesRtl = context.getProject().getTargetSdk() >= 17;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        List<String> attrs = new ArrayList<>();
        for (String[] pair : PAIRS) {
            attrs.add(pair[0]);
            attrs.add(pair[1]);
        }
        return attrs;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            return;
        }

        // Find the pair for this attribute
        String opposite = null;
        boolean isLeft = false;
        for (String[] pair : PAIRS) {
            if (name.equals(pair[0])) {
                opposite = pair[1];
                isLeft = true;
                break;
            } else if (name.equals(pair[1])) {
                opposite = pair[0];
                isLeft = false;
                break;
            }
        }

        if (opposite == null) {
            return;
        }

        // Check if the opposite attribute is present
        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        boolean hasOpposite = false;
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String attrName = attr.getLocalName();
            if (opposite.equals(attrName)) {
                hasOpposite = true;
                break;
            }
        }

        if (!hasOpposite) {
            String message;
            if (isLeft) {
                message =
                        String.format(
                                "When you specify `%1$s` you should probably also specify"
                                        + " `%2$s` for right-to-left layout symmetry",
                                name, opposite);
            } else {
                message =
                        String.format(
                                "When you specify `%1$s` you should probably also specify"
                                        + " `%2$s` for right-to-left layout symmetry",
                                name, opposite);
            }

            Location location = context.getLocation(attribute);
            LintMap map = new LintMap();
            map.put(KEY_REQUIRES_RTL, true);

            Incident incident =
                    new Incident(ISSUE, attribute, location, message, null);
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
                // Check for references to left/right padding or margin constants
                // in Java/Kotlin code (e.g., R.attr.paddingLeft usage)
                String name = node.getIdentifier();
                if (name == null) {
                    return;
                }

                String opposite = null;
                for (String[] pair : PAIRS) {
                    // Convert attribute names to potential constant names
                    String left = attrToConstant(pair[0]);
                    String right = attrToConstant(pair[1]);
                    if (name.equals(left)) {
                        opposite = right;
                        break;
                    } else if (name.equals(right)) {
                        opposite = left;
                        break;
                    }
                }

                // We only flag XML-based issues in visitAttribute; Java checks are informational
                // For this implementation, we skip Java-side reporting to avoid false positives
            }
        };
    }

    @Nullable
    private static String attrToConstant(@NonNull String attrName) {
        // Convert camelCase attribute names to potential constant identifiers
        // e.g., "paddingLeft" -> "paddingLeft", "layout_marginLeft" -> "layout_marginLeft"
        return attrName;
    }
}