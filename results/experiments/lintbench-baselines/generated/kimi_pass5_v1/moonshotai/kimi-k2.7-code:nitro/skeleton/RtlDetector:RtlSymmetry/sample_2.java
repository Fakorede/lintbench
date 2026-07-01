package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.LayoutDetector;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(RtlDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "RtlSymmetry",
                    "Padding and margin symmetry",
                    "If you specify padding or margin on the left side of a layout, "
                            + "you should probably also specify padding on the right side "
                            + "(and vice versa) for right-to-left layout symmetry.",
                    Category.RTL,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String PADDING = "padding";
    private static final String PADDING_LEFT = "paddingLeft";
    private static final String PADDING_RIGHT = "paddingRight";
    private static final String LAYOUT_MARGIN = "layout_margin";
    private static final String LAYOUT_MARGIN_LEFT = "layout_marginLeft";
    private static final String LAYOUT_MARGIN_RIGHT = "layout_marginRight";

    private static final List<String> JAVA_SYMMETRY_NAMES =
            Arrays.asList("leftMargin", "rightMargin", "paddingLeft", "paddingRight");

    private final Map<Element, Map<String, Attr>> mElementAttributes = new HashMap<>();
    private final Map<Element, XmlContext> mElementContexts = new HashMap<>();
    private final Map<JavaContext, Map<String, USimpleNameReferenceExpression>> mJavaReferences =
            new HashMap<>();

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<Element, Map<String, Attr>> entry : mElementAttributes.entrySet()) {
            Element element = entry.getKey();
            Map<String, Attr> attributes = entry.getValue();
            XmlContext xmlContext = mElementContexts.get(element);
            if (xmlContext == null) {
                continue;
            }

            if (hasGenericAttribute(element, PADDING) || hasGenericAttribute(element, LAYOUT_MARGIN)) {
                continue;
            }

            checkXmlSymmetry(xmlContext, attributes, PADDING_LEFT, PADDING_RIGHT);
            checkXmlSymmetry(xmlContext, attributes, LAYOUT_MARGIN_LEFT, LAYOUT_MARGIN_RIGHT);
        }
        mElementAttributes.clear();
        mElementContexts.clear();

        for (Map.Entry<JavaContext, Map<String, USimpleNameReferenceExpression>> entry :
                mJavaReferences.entrySet()) {
            JavaContext javaContext = entry.getKey();
            Map<String, USimpleNameReferenceExpression> references = entry.getValue();
            checkJavaSymmetry(javaContext, references, "leftMargin", "rightMargin");
            checkJavaSymmetry(javaContext, references, "rightMargin", "leftMargin");
            checkJavaSymmetry(javaContext, references, "paddingLeft", "paddingRight");
            checkJavaSymmetry(javaContext, references, "paddingRight", "paddingLeft");
        }
        mJavaReferences.clear();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                PADDING_LEFT,
                PADDING_RIGHT,
                LAYOUT_MARGIN_LEFT,
                LAYOUT_MARGIN_RIGHT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        mElementAttributes.computeIfAbsent(element, k -> new HashMap<>()).put(name, attribute);
        mElementContexts.putIfAbsent(element, context);
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
                if (name == null || !JAVA_SYMMETRY_NAMES.contains(name)) {
                    return;
                }
                mJavaReferences
                        .computeIfAbsent(context, k -> new HashMap<>())
                        .put(name, node);
            }
        };
    }

    private boolean hasGenericAttribute(Element element, String name) {
        return element.getAttributeNode(name) != null
                || element.getAttributeNode("android:" + name) != null
                || element.getAttributeNode("app:" + name) != null
                || element.getAttributeNode("tools:" + name) != null;
    }

    private void checkXmlSymmetry(
            XmlContext context, Map<String, Attr> attributes, String leftName, String rightName) {
        Attr left = attributes.get(leftName);
        Attr right = attributes.get(rightName);
        if (left != null && right == null) {
            reportXml(context, left, rightName);
        } else if (right != null && left == null) {
            reportXml(context, right, leftName);
        }
    }

    private void reportXml(XmlContext context, Attr attribute, String missingName) {
        String name = attribute.getLocalName();
        String message =
                "To support right-to-left layouts, consider adding android:"
                        + missingName
                        + " in addition to android:"
                        + name;
        context.report(ISSUE, attribute, context.getLocation(attribute), message);
    }

    private void checkJavaSymmetry(
            JavaContext context,
            Map<String, USimpleNameReferenceExpression> references,
            String presentName,
            String missingName) {
        USimpleNameReferenceExpression present = references.get(presentName);
        USimpleNameReferenceExpression counterpart = references.get(missingName);
        if (present != null && counterpart == null) {
            reportJava(context, present, missingName);
        }
    }

    private void reportJava(
            JavaContext context, USimpleNameReferenceExpression node, String missingName) {
        String message =
                "To support right-to-left layouts, consider also using "
                        + missingName
                        + " when using "
                        + node.getIdentifier();
        context.report(ISSUE, node, context.getLocation(node), message);
    }
}