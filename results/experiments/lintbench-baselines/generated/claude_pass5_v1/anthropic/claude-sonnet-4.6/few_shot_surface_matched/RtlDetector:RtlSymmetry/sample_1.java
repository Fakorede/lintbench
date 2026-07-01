package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
import static com.android.SdkConstants.ATTR_PADDING_LEFT;
import static com.android.SdkConstants.ATTR_PADDING_RIGHT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiElement;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String KEY_FILE = "file";
    private static final String KEY_ATTR = "attr";

    private static final String ATTR_LAYOUT_MARGIN_START = "layout_marginStart";
    private static final String ATTR_LAYOUT_MARGIN_END = "layout_marginEnd";
    private static final String ATTR_PADDING_START = "paddingStart";
    private static final String ATTR_PADDING_END = "paddingEnd";

    // Map from left attribute to its right counterpart
    private static final Map<String, String> LEFT_TO_RIGHT = new HashMap<>();
    // Map from right attribute to its left counterpart
    private static final Map<String, String> RIGHT_TO_LEFT = new HashMap<>();

    static {
        LEFT_TO_RIGHT.put(ATTR_PADDING_LEFT, ATTR_PADDING_RIGHT);
        LEFT_TO_RIGHT.put(ATTR_LAYOUT_MARGIN_LEFT, ATTR_LAYOUT_MARGIN_RIGHT);
        RIGHT_TO_LEFT.put(ATTR_PADDING_RIGHT, ATTR_PADDING_LEFT);
        RIGHT_TO_LEFT.put(ATTR_LAYOUT_MARGIN_RIGHT, ATTR_LAYOUT_MARGIN_LEFT);
    }

    // Set of RTL-aware attribute names referenced in Java/Kotlin source
    private boolean mUsesRtlAttributes = false;

    private static final String[] RTL_ATTRS = {
        ATTR_LAYOUT_MARGIN_START,
        ATTR_LAYOUT_MARGIN_END,
        ATTR_PADDING_START,
        ATTR_PADDING_END,
        "paddingStart",
        "paddingEnd",
        "layout_marginStart",
        "layout_marginEnd",
        "marginStart",
        "marginEnd",
        "textAlignment",
        "textDirection",
        "layoutDirection",
        "gravity",
        "START",
        "END",
        "TEXT_ALIGNMENT_TEXT_START",
        "TEXT_ALIGNMENT_TEXT_END",
        "TEXT_ALIGNMENT_VIEW_START",
        "TEXT_ALIGNMENT_VIEW_END",
        "LAYOUT_DIRECTION_RTL",
        "LAYOUT_DIRECTION_LTR",
        "LAYOUT_DIRECTION_LOCALE",
        "LAYOUT_DIRECTION_INHERIT",
    };

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
                    new Implementation(
                            RtlDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE),
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE));

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
            name = attribute.getName();
        }

        Element element = attribute.getOwnerElement();
        NamedNodeMap attrs = element.getAttributes();

        String opposite = LEFT_TO_RIGHT.get(name);
        if (opposite == null) {
            opposite = RIGHT_TO_LEFT.get(name);
        }

        if (opposite == null) {
            return;
        }

        // Check if the opposite attribute is present
        boolean hasOpposite =
                attrs.getNamedItemNS(attribute.getNamespaceURI(), opposite) != null
                        || attrs.getNamedItem(opposite) != null
                        || attrs.getNamedItem("android:" + opposite) != null;

        if (hasOpposite) {
            return;
        }

        String message;
        if (LEFT_TO_RIGHT.containsKey(name)) {
            message =
                    String.format(
                            "Attribute `%1$s` does not have a corresponding `%2$s` attribute",
                            name, opposite);
        } else {
            message =
                    String.format(
                            "Attribute `%1$s` does not have a corresponding `%2$s` attribute",
                            name, opposite);
        }

        Location location = context.getLocation(attribute);
        Incident incident = new Incident(ISSUE, attribute, location, message);
        LintMap map = new LintMap();
        map.put(KEY_ATTR, name);
        context.report(incident, map);
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // If the project uses RTL-aware attributes in source code, suppress warnings
        // about left/right since they may be intentional
        if (mUsesRtlAttributes) {
            String attr = map.getString(KEY_ATTR, null);
            if (attr != null) {
                // Suppress the warning since the developer is RTL-aware
                return false;
            }
        }
        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Nothing specific needed here; filtering is done in filterIncident
    }

    // SourceCodeScanner implementation

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return List.of(USimpleNameReferenceExpression.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                String name = node.getIdentifier();
                for (String rtlAttr : RTL_ATTRS) {
                    if (rtlAttr.equals(name)) {
                        mUsesRtlAttributes = true;
                        break;
                    }
                }
            }
        };
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        String name = node.getIdentifier();
        for (String rtlAttr : RTL_ATTRS) {
            if (rtlAttr.equals(name)) {
                mUsesRtlAttributes = true;
                break;
            }
        }
    }

    // Inner UElementHandler class
    public abstract static class UElementHandler
            extends com.android.tools.lint.detector.api.UastCallVisitor {

        public void visitSimpleNameReferenceExpression(
                @NonNull USimpleNameReferenceExpression node) {}
    }
}