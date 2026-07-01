package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String PADDING_HORIZONTAL = "paddingHorizontal";
    private static final String LAYOUT_MARGIN_HORIZONTAL = "layout_marginHorizontal";

    private static final Map<String, String> COUNTERPARTS;
    static {
        Map<String, String> map = new HashMap<>();
        map.put(ATTR_PADDING_LEFT, ATTR_PADDING_RIGHT);
        map.put(ATTR_PADDING_RIGHT, ATTR_PADDING_LEFT);
        map.put(ATTR_PADDING_START, ATTR_PADDING_END);
        map.put(ATTR_PADDING_END, ATTR_PADDING_START);
        map.put(ATTR_LAYOUT_MARGIN_LEFT, ATTR_LAYOUT_MARGIN_RIGHT);
        map.put(ATTR_LAYOUT_MARGIN_RIGHT, ATTR_LAYOUT_MARGIN_LEFT);
        map.put(ATTR_LAYOUT_MARGIN_START, ATTR_LAYOUT_MARGIN_END);
        map.put(ATTR_LAYOUT_MARGIN_END, ATTR_LAYOUT_MARGIN_START);
        COUNTERPARTS = Collections.unmodifiableMap(map);
    }

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    RtlDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE));

    public static final Issue ISSUE =
            Issue.create(
                    "RtlSymmetry",
                    "Padding and margin symmetry",
                    "If you specify padding or margin on the left side of a layout, you should "
                            + "probably also specify padding on the right side (and vice versa) "
                            + "for right-to-left layout symmetry.",
                    Category.RTL,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public boolean filterIncident(@NonNull Context context, @NonNull Incident incident) {
        if (incident.getIssue() != ISSUE) {
            return true;
        }
        Project project = context.getMainProject();
        return project.getTargetSdk() >= 17;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return new ArrayList<>(COUNTERPARTS.keySet());
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        String counterpart = COUNTERPARTS.get(name);
        if (counterpart == null) {
            return;
        }

        Element owner = attribute.getOwnerElement();
        if (owner.hasAttributeNS(ANDROID_URI, counterpart)) {
            return;
        }

        if (name.contains("padding") && owner.hasAttributeNS(ANDROID_URI, ATTR_PADDING)) {
            return;
        }
        if (name.contains("margin") && owner.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_MARGIN)) {
            return;
        }
        if (owner.hasAttributeNS(ANDROID_URI, PADDING_HORIZONTAL)
                || owner.hasAttributeNS(ANDROID_URI, LAYOUT_MARGIN_HORIZONTAL)) {
            return;
        }

        String message =
                "To support right-to-left layouts, use "
                        + counterpart
                        + " in addition to "
                        + name;
        context.report(ISSUE, attribute, context.getLocation(attribute), message);
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                String name = node.getIdentifier();
                boolean isPadding = "setPadding".equals(name);
                boolean isMargins = "setMargins".equals(name);
                if (!isPadding && !isMargins) {
                    return;
                }

                UElement parent = node.getUastParent();
                while (parent != null && !(parent instanceof UCallExpression)) {
                    parent = parent.getUastParent();
                }
                if (!(parent instanceof UCallExpression)) {
                    return;
                }

                UCallExpression call = (UCallExpression) parent;
                List<UExpression> args = call.getValueArguments();
                if (args.size() != 4) {
                    return;
                }

                Object left = ConstantEvaluator.evaluate(context, args.get(0));
                Object right = ConstantEvaluator.evaluate(context, args.get(2));
                if (left == null || right == null) {
                    return;
                }

                if (!left.equals(right)) {
                    String kind = isPadding ? "padding" : "margin";
                    String message =
                            "Asymmetric "
                                    + kind
                                    + ": left and right arguments should be equal for RTL symmetry";
                    context.report(ISSUE, call, context.getLocation(call), message);
                }
            }
        };
    }
}