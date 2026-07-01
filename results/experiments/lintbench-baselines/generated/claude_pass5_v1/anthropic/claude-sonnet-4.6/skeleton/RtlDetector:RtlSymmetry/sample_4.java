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

    // Pairs: (left attribute, right attribute)
    private static final String[][] ATTRIBUTE_PAIRS = {
        {ATTR_PADDING_LEFT, ATTR_PADDING_RIGHT},
        {ATTR_LAYOUT_MARGIN_LEFT, ATTR_LAYOUT_MARGIN_RIGHT},
    };

    // Track whether we've seen RTL support attributes in Java files
    private boolean mUsesRtlAttributes = false;

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Only report the incident if RTL attributes are used (i.e., the app supports RTL)
        if (map.getBoolean(KEY_REQUIRES_RTL, false)) {
            return true;
        }
        return false;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Nothing special needed here; incidents are reported during visitAttribute
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
        String name = attribute.getLocalName();
        if (name == null) {
            return;
        }

        Element element = attribute.getOwnerElement();
        NamedNodeMap attrs = element.getAttributes();

        // For each known pair, check if we have one side but not the other
        for (String[] pair : ATTRIBUTE_PAIRS) {
            String leftAttr = pair[0];
            String rightAttr = pair[1];

            if (name.equals(leftAttr)) {
                // We have the left attribute; check if right is missing
                if (attrs.getNamedItemNS(ANDROID_URI, rightAttr) == null) {
                    String message =
                            String.format(
                                    "When specifying `%1$s` you should probably also specify"
                                            + " `%2$s` for right-to-left layout symmetry",
                                    leftAttr, rightAttr);
                    LintMap map = new LintMap();
                    map.put(KEY_REQUIRES_RTL, true);
                    context.report(
                            new Incident(
                                    ISSUE,
                                    attribute,
                                    context.getLocation(attribute),
                                    message,
                                    null),
                            map);
                }
                return;
            } else if (name.equals(rightAttr)) {
                // We have the right attribute; check if left is missing
                if (attrs.getNamedItemNS(ANDROID_URI, leftAttr) == null) {
                    String message =
                            String.format(
                                    "When specifying `%1$s` you should probably also specify"
                                            + " `%2$s` for right-to-left layout symmetry",
                                    rightAttr, leftAttr);
                    LintMap map = new LintMap();
                    map.put(KEY_REQUIRES_RTL, true);
                    context.report(
                            new Incident(
                                    ISSUE,
                                    attribute,
                                    context.getLocation(attribute),
                                    message,
                                    null),
                            map);
                }
                return;
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
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                String name = node.getIdentifier();
                // Check if code references RTL-related attributes
                if (name.contains("paddingLeft")
                        || name.contains("paddingRight")
                        || name.contains("marginLeft")
                        || name.contains("marginRight")
                        || name.contains("layout_marginLeft")
                        || name.contains("layout_marginRight")) {
                    mUsesRtlAttributes = true;
                }
            }
        };
    }
}