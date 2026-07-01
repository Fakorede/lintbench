package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.LayoutDetector;
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
        return Arrays.asList(
                "paddingLeft",
                "paddingRight",
                "layout_marginLeft",
                "layout_marginRight");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        String counterpart = null;

        if ("paddingLeft".equals(name)) {
            counterpart = "paddingRight";
        } else if ("paddingRight".equals(name)) {
            counterpart = "paddingLeft";
        } else if ("layout_marginLeft".equals(name)) {
            counterpart = "layout_marginRight";
        } else if ("layout_marginRight".equals(name)) {
            counterpart = "layout_marginLeft";
        }

        if (counterpart != null) {
            Element element = attribute.getOwnerElement();
            if (!element.hasAttributeNS(ANDROID_URI, counterpart)) {
                String message = String.format(
                        "When you define `%s` you should probably also define `%s` for right-to-left symmetry",
                        name, counterpart);
                context.report(ISSUE, attribute, context.getLocation(attribute), message);
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
                    UElement parent = node.getUastParent();
                    while (parent != null && !(parent instanceof UCallExpression)) {
                        parent = parent.getUastParent();
                    }

                    if (parent instanceof UCallExpression) {
                        UCallExpression call = (UCallExpression) parent;
                        if (call.getValueArgumentCount() == 4) {
                            List<UExpression> args = call.getValueArguments();
                            UExpression left = args.get(0);
                            UExpression right = args.get(2);

                            Object leftVal = left.evaluate();
                            Object rightVal = right.evaluate();

                            if (leftVal != null && rightVal != null && !leftVal.equals(rightVal)) {
                                context.report(
                                        ISSUE,
                                        context.getLocation(call),
                                        "Ensure left and right parameters are symmetric for RTL support");
                            }
                        }
                    }
                }
            }
        };
    }
}