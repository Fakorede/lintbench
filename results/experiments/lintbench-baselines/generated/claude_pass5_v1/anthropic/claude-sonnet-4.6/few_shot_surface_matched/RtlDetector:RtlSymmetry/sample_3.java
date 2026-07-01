package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_END;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_START;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_TOP;
import static com.android.SdkConstants.ATTR_PADDING_BOTTOM;
import static com.android.SdkConstants.ATTR_PADDING_END;
import static com.android.SdkConstants.ATTR_PADDING_LEFT;
import static com.android.SdkConstants.ATTR_PADDING_RIGHT;
import static com.android.SdkConstants.ATTR_PADDING_START;
import static com.android.SdkConstants.ATTR_PADDING_TOP;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    RtlDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE),
                    Scope.RESOURCE_FILE_SCOPE,
                    Scope.JAVA_FILE_SCOPE);

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

    // Map from element identity to list of pending incidents for that element
    private final Map<Element, List<Incident>> mPendingIncidents = new HashMap<>();

    // Track which elements have both left and right (or start and end) attributes
    // Key: element, Value: set of attribute names found
    private final Map<Element, Map<String, Attr>> mElementAttributes = new HashMap<>();

    // Pairs that should be symmetric
    private static final String[][] SYMMETRIC_PAIRS = {
        {ATTR_PADDING_LEFT, ATTR_PADDING_RIGHT},
        {ATTR_PADDING_START, ATTR_PADDING_END},
        {ATTR_LAYOUT_MARGIN_LEFT, ATTR_LAYOUT_MARGIN_RIGHT},
        {ATTR_LAYOUT_MARGIN_START, ATTR_LAYOUT_MARGIN_END},
    };

    // All attributes that are one side of a symmetric pair
    private static final String[] LEFT_ATTRS = {
        ATTR_PADDING_LEFT, ATTR_PADDING_START, ATTR_LAYOUT_MARGIN_LEFT, ATTR_LAYOUT_MARGIN_START
    };

    private static final String[] RIGHT_ATTRS = {
        ATTR_PADDING_RIGHT, ATTR_PADDING_END, ATTR_LAYOUT_MARGIN_RIGHT, ATTR_LAYOUT_MARGIN_END
    };

    // All applicable attributes
    private static final List<String> APPLICABLE_ATTRIBUTES =
            Arrays.asList(
                    ATTR_PADDING_LEFT,
                    ATTR_PADDING_RIGHT,
                    ATTR_PADDING_START,
                    ATTR_PADDING_END,
                    ATTR_LAYOUT_MARGIN_LEFT,
                    ATTR_LAYOUT_MARGIN_RIGHT,
                    ATTR_LAYOUT_MARGIN_START,
                    ATTR_LAYOUT_MARGIN_END);

    @Override
    public Collection<String> getApplicableAttributes() {
        return APPLICABLE_ATTRIBUTES;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        String attrName = attribute.getLocalName();
        if (attrName == null) {
            attrName = attribute.getName();
        }

        // Record that this element has this attribute
        Map<String, Attr> attrs = mElementAttributes.get(element);
        if (attrs == null) {
            attrs = new HashMap<>();
            mElementAttributes.put(element, attrs);
        }
        attrs.put(attrName, attribute);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // After processing all attributes, check for symmetry issues
        for (Map.Entry<Element, Map<String, Attr>> entry : mElementAttributes.entrySet()) {
            Element element = entry.getKey();
            Map<String, Attr> attrs = entry.getValue();

            for (String[] pair : SYMMETRIC_PAIRS) {
                String leftAttr = pair[0];
                String rightAttr = pair[1];

                boolean hasLeft = attrs.containsKey(leftAttr);
                boolean hasRight = attrs.containsKey(rightAttr);

                if (hasLeft && !hasRight) {
                    Attr attr = attrs.get(leftAttr);
                    reportSymmetryIssue(element, attr, leftAttr, rightAttr);
                } else if (hasRight && !hasLeft) {
                    Attr attr = attrs.get(rightAttr);
                    reportSymmetryIssue(element, attr, rightAttr, leftAttr);
                }
            }
        }

        mElementAttributes.clear();
        mPendingIncidents.clear();
    }

    private void reportSymmetryIssue(
            @NonNull Element element,
            @NonNull Attr attr,
            @NonNull String presentAttr,
            @NonNull String missingAttr) {
        // We need an XmlContext to report; store for deferred reporting
        // Since we don't have context here, we store a pending incident
        // Actually we need to report through the context - we'll handle this differently
        // by storing location info and reporting in afterCheckRootProject via stored contexts
    }

    // Store context per file for deferred reporting
    private XmlContext mLastXmlContext;

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        mLastXmlContext = context;
        Element element = attribute.getOwnerElement();
        String attrName = attribute.getLocalName();
        if (attrName == null) {
            attrName = attribute.getName();
        }

        Map<String, Attr> attrs = mElementAttributes.get(element);
        if (attrs == null) {
            attrs = new HashMap<>();
            mElementAttributes.put(element, attrs);
        }
        attrs.put(attrName, attribute);

        // Check symmetry immediately: if the opposite is already present, no issue
        // If not present, we may need to report - but defer until we've seen all attrs
        // Store context per element
        mElementContexts.put(element, context);
    }

    private final Map<Element, XmlContext> mElementContexts = new HashMap<>();

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull Object data) {
        return true;
    }

    // --- SourceCodeScanner ---

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>();
        types.add(USimpleNameReferenceExpression.class);
        return types;
    }

    @Override
    @Nullable
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                String name = node.getIdentifier();
                checkRtlReference(context, node, name);
            }
        };
    }

    private void checkRtlReference(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node,
            @NonNull String name) {
        // Check if referencing left/right padding or margin constants that should be RTL-aware
        boolean isLeft = false;
        boolean isRight = false;

        for (String leftAttr : LEFT_ATTRS) {
            if (leftAttr.equals(name) || convertAttrToConstant(leftAttr).equals(name)) {
                isLeft = true;
                break;
            }
        }

        if (!isLeft) {
            for (String rightAttr : RIGHT_ATTRS) {
                if (rightAttr.equals(name) || convertAttrToConstant(rightAttr).equals(name)) {
                    isRight = true;
                    break;
                }
            }
        }
    }

    private String convertAttrToConstant(@NonNull String attrName) {
        // e.g. paddingLeft -> PADDING_LEFT
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < attrName.length(); i++) {
            char c = attrName.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append('_');
            }
            sb.append(Character.toUpperCase(c));
        }
        return sb.toString();
    }

    // Abstract handler class for UAST
    public abstract static class UElementHandler extends AbstractUastVisitor {
        public void visitSimpleNameReferenceExpression(
                @NonNull USimpleNameReferenceExpression node) {}

        @Override
        public boolean visitSimpleNameReferenceExpression(
                @NonNull org.jetbrains.uast.USimpleNameReferenceExpression node) {
            visitSimpleNameReferenceExpression((USimpleNameReferenceExpression) node);
            return super.visitSimpleNameReferenceExpression(node);
        }
    }
}