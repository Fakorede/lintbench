package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final String ATTR_PADDING_LEFT = "paddingLeft";
    private static final String ATTR_PADDING_RIGHT = "paddingRight";
    private static final String ATTR_MARGIN_LEFT = "layout_marginLeft";
    private static final String ATTR_MARGIN_RIGHT = "layout_marginRight";

    private static final Implementation IMPLEMENTATION =
            new Implementation(RtlDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "RtlSymmetry",
                    "Padding and margin symmetry",
                    "To support right-to-left (RTL) layouts, padding and margins should be "
                            + "symmetric. If you specify a padding or margin on the left side of a "
                            + "layout, you should probably also specify the corresponding padding "
                            + "or margin on the right side (and vice versa). Otherwise the layout "
                            + "may look unbalanced when displayed in an RTL locale.",
                    Category.RTL,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, Boolean> mReported = new HashMap<>();

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        mReported.clear();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                ATTR_PADDING_LEFT,
                ATTR_PADDING_RIGHT,
                ATTR_MARGIN_LEFT,
                ATTR_MARGIN_RIGHT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = getLocalName(attribute);
        String counterpart = getCounterpart(name);
        if (counterpart == null) {
            return;
        }

        String namespace = attribute.getNamespaceURI();
        if (namespace == null || !namespace.equals(ANDROID_URI)) {
            return;
        }

        Element owner = attribute.getOwnerElement();
        if (owner == null) {
            return;
        }

        if (!owner.hasAttributeNS(ANDROID_URI, counterpart)) {
            String message =
                    "To maintain right-to-left layout symmetry, when specifying "
                            + name
                            + " you should also specify "
                            + counterpart;
            String key =
                    context.file.getName()
                            + ":"
                            + getPath(owner)
                            + ":"
                            + name;
            if (!mReported.containsKey(key)) {
                mReported.put(key, Boolean.TRUE);
                context.report(
                        ISSUE,
                        attribute,
                        context.getValueLocation(attribute),
                        message);
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
                // No Java source checks are needed for RtlSymmetry.
            }
        };
    }

    private static String getLocalName(@NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
            int colon = name.indexOf(':');
            if (colon != -1) {
                name = name.substring(colon + 1);
            }
        }
        return name;
    }

    private static String getCounterpart(@NonNull String name) {
        if (ATTR_PADDING_LEFT.equals(name)) {
            return ATTR_PADDING_RIGHT;
        } else if (ATTR_PADDING_RIGHT.equals(name)) {
            return ATTR_PADDING_LEFT;
        } else if (ATTR_MARGIN_LEFT.equals(name)) {
            return ATTR_MARGIN_RIGHT;
        } else if (ATTR_MARGIN_RIGHT.equals(name)) {
            return ATTR_MARGIN_LEFT;
        }
        return null;
    }

    private static String getPath(@NonNull Element element) {
        StringBuilder sb = new StringBuilder();
        for (Node node = element;
                node != null && node.getNodeType() == Node.ELEMENT_NODE;
                node = node.getParentNode()) {
            Element e = (Element) node;
            int index = 0;
            for (Node sibling = e.getPreviousSibling();
                    sibling != null;
                    sibling = sibling.getPreviousSibling()) {
                if (sibling.getNodeType() == Node.ELEMENT_NODE
                        && sibling.getNodeName().equals(e.getNodeName())) {
                    index++;
                }
            }
            String segment = e.getTagName();
            if (index > 0) {
                segment = segment + "[" + index + "]";
            }
            if (sb.length() == 0) {
                sb.insert(0, segment);
            } else {
                sb.insert(0, segment + "/");
            }
        }
        return sb.toString();
    }
}