package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.TextFormat;
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

public class RtlDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String PADDING = "padding";
    private static final String PADDING_LEFT = "paddingLeft";
    private static final String PADDING_RIGHT = "paddingRight";
    private static final String LAYOUT_MARGIN = "layout_margin";
    private static final String LAYOUT_MARGIN_LEFT = "layout_marginLeft";
    private static final String LAYOUT_MARGIN_RIGHT = "layout_marginRight";

    public static final Issue ISSUE =
            Issue.create(
                    "RtlSymmetry",
                    "Padding/Margin RTL Symmetry",
                    "If you specify padding or margin on the left side of a layout, you should"
                            + " probably also specify padding or margin on the right side (and vice"
                            + " versa) for right-to-left layout symmetry.",
                    Category.RTL,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            RtlDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                PADDING_LEFT, PADDING_RIGHT, LAYOUT_MARGIN_LEFT, LAYOUT_MARGIN_RIGHT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        Element owner = attribute.getOwnerElement();
        if (owner == null) {
            return;
        }

        if (hasGeneric(owner, name) || hasCounterpart(owner, name)) {
            return;
        }

        String counterpart = getCounterpartName(name);
        String message =
                "To support right-to-left layouts, consider adding "
                        + counterpart
                        + " in addition to "
                        + name;
        context.report(ISSUE, attribute, context.getLocation(attribute), message);
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return null;
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return null;
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context, @NonNull USimpleNameReferenceExpression expression) {
        String name = expression.getIdentifier();
        boolean isRelative;
        if ("setPadding".equals(name) || "setMargins".equals(name)) {
            isRelative = false;
        } else if ("setPaddingRelative".equals(name)) {
            isRelative = true;
        } else {
            return;
        }

        UElement parent = expression.getUastParent();
        if (!(parent instanceof UCallExpression)) {
            return;
        }

        UCallExpression call = (UCallExpression) parent;
        List<UExpression> args = call.getValueArguments();
        int leftIndex = 0;
        int rightIndex = 2;
        if (args.size() <= rightIndex) {
            return;
        }

        UExpression leftArg = args.get(leftIndex);
        UExpression rightArg = args.get(rightIndex);
        if (leftArg instanceof ULiteralExpression && rightArg instanceof ULiteralExpression) {
            Object left = ((ULiteralExpression) leftArg).getValue();
            Object right = ((ULiteralExpression) rightArg).getValue();
            if (!java.util.Objects.equals(left, right)) {
                String side1 = isRelative ? "start" : "left";
                String side2 = isRelative ? "end" : "right";
                context.report(
                        ISSUE,
                        call,
                        context.getLocation(call),
                        "Padding/margin values should be symmetrical for RTL layouts; the "
                                + side1
                                + " and "
                                + side2
                                + " arguments differ");
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context,
            @NonNull Incident incident,
            @NonNull Severity severity,
            @Nullable TextFormat format) {
        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {}

    private static boolean hasGeneric(@NonNull Element element, @NonNull String name) {
        if (name.startsWith(PADDING)) {
            return hasAttribute(element, PADDING);
        } else if (name.startsWith(LAYOUT_MARGIN)) {
            return hasAttribute(element, LAYOUT_MARGIN);
        }
        return false;
    }

    private static boolean hasCounterpart(@NonNull Element element, @NonNull String name) {
        String counterpart = getCounterpartName(name);
        return counterpart != null && hasAttribute(element, counterpart);
    }

    @Nullable
    private static String getCounterpartName(@NonNull String name) {
        switch (name) {
            case PADDING_LEFT:
                return PADDING_RIGHT;
            case PADDING_RIGHT:
                return PADDING_LEFT;
            case LAYOUT_MARGIN_LEFT:
                return LAYOUT_MARGIN_RIGHT;
            case LAYOUT_MARGIN_RIGHT:
                return LAYOUT_MARGIN_LEFT;
            default:
                return null;
        }
    }

    private static boolean hasAttribute(@NonNull Element element, @NonNull String localName) {
        NamedNodeMap attrs = element.getAttributes();
        if (attrs == null) {
            return false;
        }
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            if (localName.equals(attr.getLocalName())) {
                return true;
            }
        }
        return false;
    }
}