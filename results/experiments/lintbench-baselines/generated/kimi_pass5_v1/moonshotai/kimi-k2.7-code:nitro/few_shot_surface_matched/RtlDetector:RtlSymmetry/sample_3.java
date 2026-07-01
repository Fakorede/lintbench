package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_END;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_START;
import static com.android.SdkConstants.ATTR_PADDING;
import static com.android.SdkConstants.ATTR_PADDING_END;
import static com.android.SdkConstants.ATTR_PADDING_LEFT;
import static com.android.SdkConstants.ATTR_PADDING_RIGHT;
import static com.android.SdkConstants.ATTR_PADDING_START;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String[][] RTL_PAIRS = {
        {ATTR_PADDING_LEFT, ATTR_PADDING_RIGHT},
        {ATTR_PADDING_START, ATTR_PADDING_END},
        {ATTR_LAYOUT_MARGIN_LEFT, ATTR_LAYOUT_MARGIN_RIGHT},
        {ATTR_LAYOUT_MARGIN_START, ATTR_LAYOUT_MARGIN_END}
    };

    private final Map<Element, Set<String>> mElementAttributes = new HashMap<>();
    private final Map<Element, XmlContext> mElementContexts = new HashMap<>();

    public static final Issue ISSUE =
            Issue.create(
                    "RtlSymmetry",
                    "Padding and margin symmetry",
                    "If you specify padding or margin on the left side of a layout, you should"
                            + " probably also specify padding on the right side (and vice versa)"
                            + " for right-to-left layout symmetry.",
                    Category.RTL,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            RtlDetector.class,
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        List<String> result = new ArrayList<>(RTL_PAIRS.length * 2);
        for (String[] pair : RTL_PAIRS) {
            result.add(pair[0]);
            result.add(pair[1]);
        }
        return result;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        Element owner = attribute.getOwnerElement();
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
            int colon = name.indexOf(':');
            if (colon != -1) {
                name = name.substring(colon + 1);
            }
        }
        mElementAttributes.computeIfAbsent(owner, k -> new HashSet<>()).add(name);
        mElementContexts.put(owner, context);
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (Map.Entry<Element, Set<String>> entry : mElementAttributes.entrySet()) {
            Element element = entry.getKey();
            Set<String> attrs = entry.getValue();

            if (attrs.contains(ATTR_PADDING)) {
                continue;
            }
            if (attrs.contains(ATTR_LAYOUT_MARGIN)) {
                continue;
            }

            for (String[] pair : RTL_PAIRS) {
                boolean hasFirst = attrs.contains(pair[0]);
                boolean hasSecond = attrs.contains(pair[1]);
                if (hasFirst && !hasSecond) {
                    reportAsymmetry(element, pair[0], pair[1]);
                } else if (hasSecond && !hasFirst) {
                    reportAsymmetry(element, pair[1], pair[0]);
                }
            }
        }
        mElementAttributes.clear();
        mElementContexts.clear();
    }

    private void reportAsymmetry(Element element, String presentAttr, String missingAttr) {
        XmlContext context = mElementContexts.get(element);
        if (context == null) {
            return;
        }
        Attr attr = findAttributeByLocalName(element, presentAttr);
        if (attr == null) {
            return;
        }
        String message =
                "To maintain right-to-left symmetry, when "
                        + presentAttr
                        + " is defined, "
                        + missingAttr
                        + " should also be defined";
        context.report(ISSUE, attr, context.getLocation(attr), message);
    }

    private static Attr findAttributeByLocalName(Element element, String localName) {
        org.w3c.dom.NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr) attributes.item(i);
            String name = attr.getLocalName();
            if (name == null) {
                name = attr.getName();
                int colon = name.indexOf(':');
                if (colon != -1) {
                    name = name.substring(colon + 1);
                }
            }
            if (localName.equals(name)) {
                return attr;
            }
        }
        return null;
    }

    @Override
    public boolean filterIncident(Context context, Incident incident) {
        Object scope = incident.getScope();
        if (scope instanceof Attr) {
            Attr attr = (Attr) scope;
            String name = attr.getLocalName();
            if (name == null) {
                name = attr.getName();
            }
            if (name != null
                    && (name.contains("Start") || name.contains("End"))
                    && context.getProject().getMinSdk() < 17) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>(2);
        types.add(UCallExpression.class);
        types.add(USimpleNameReferenceExpression.class);
        return types;
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(UCallExpression node) {
                String method = node.getMethodName();
                if ("setPadding".equals(method) || "setMargins".equals(method)) {
                    List<UExpression> args = node.getValueArguments();
                    if (args.size() == 4) {
                        UExpression left = args.get(0);
                        UExpression right = args.get(2);
                        if (!areEquivalent(left, right)) {
                            String type =
                                    "setPadding".equals(method) ? "padding" : "margin";
                            context.report(
                                    ISSUE,
                                    node,
                                    context.getLocation(node),
                                    "To maintain right-to-left symmetry, the left and right "
                                            + type
                                            + " arguments should match");
                        }
                    }
                }
            }

            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
                // No simple-name symmetry checks are required for this issue.
            }
        };
    }

    private static boolean areEquivalent(UExpression left, UExpression right) {
        if (left == null || right == null) {
            return false;
        }
        if (left instanceof ULiteralExpression && right instanceof ULiteralExpression) {
            Object l = ((ULiteralExpression) left).getValue();
            Object r = ((ULiteralExpression) right).getValue();
            return l != null && l.equals(r);
        }
        return left.asSourceString().equals(right.asSourceString());
    }
}