package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
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
                    "If you specify padding or margin on the left side of a layout, you should " +
                    "probably also specify padding on the right side (and vice versa) for " +
                    "right-to-left layout symmetry.",
                    Category.RTL,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_PADDING_LEFT = "paddingLeft";
    private static final String ATTR_PADDING_RIGHT = "paddingRight";
    private static final String ATTR_MARGIN_LEFT = "layout_marginLeft";
    private static final String ATTR_MARGIN_RIGHT = "layout_marginRight";

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_PADDING_LEFT, ATTR_PADDING_RIGHT, ATTR_MARGIN_LEFT, ATTR_MARGIN_RIGHT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        String counterpart = null;
        if (ATTR_PADDING_LEFT.equals(name)) {
            counterpart = ATTR_PADDING_RIGHT;
        } else if (ATTR_PADDING_RIGHT.equals(name)) {
            counterpart = ATTR_PADDING_LEFT;
        } else if (ATTR_MARGIN_LEFT.equals(name)) {
            counterpart = ATTR_MARGIN_RIGHT;
        } else if (ATTR_MARGIN_RIGHT.equals(name)) {
            counterpart = ATTR_MARGIN_LEFT;
        }

        if (counterpart != null) {
            Element element = attribute.getOwnerElement();
            if (!element.hasAttributeNS(ANDROID_URI, counterpart)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                    "When you define `" + name + "` you should also define `" + counterpart + "` for right-to-left layout symmetry");
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
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                String name = node.getIdentifier();
                if ("setPadding".equals(name) || "setMargins".equals(name)) {
                    UElement parent = node.getParent();
                    if (parent instanceof UCallExpression) {
                        UCallExpression call = (UCallExpression) parent;
                        List<UExpression> args = call.getValueArguments();
                        if (args.size() >= 4) {
                            Object left = args.get(0).evaluate();
                            Object right = args.get(2).evaluate();
                            if (left != null && right != null && !left.equals(right)) {
                                context.report(ISSUE, node, context.getLocation(node),
                                    "When you set padding/margin left to " + left + " you should also set right to the same value for RTL symmetry");
                            }
                        }
                    }
                }
            }
        };
    }
}