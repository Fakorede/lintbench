package com.android.tools.lint.checks;

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
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class RtlDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    RtlDetector.class,
                    java.util.EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "RtlSymmetry",
                    "Padding and margin symmetry",
                    "To support right-to-left layouts, when you specify padding or margin on "
                            + "one side of a view you should also specify the corresponding "
                            + "attribute or value on the opposite side.",
                    Category.RTL,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String PADDING = "padding";
    private static final String PADDING_LEFT = "paddingLeft";
    private static final String PADDING_RIGHT = "paddingRight";
    private static final String PADDING_START = "paddingStart";
    private static final String PADDING_END = "paddingEnd";

    private static final String LAYOUT_MARGIN = "layout_margin";
    private static final String LAYOUT_MARGIN_LEFT = "layout_marginLeft";
    private static final String LAYOUT_MARGIN_RIGHT = "layout_marginRight";
    private static final String LAYOUT_MARGIN_START = "layout_marginStart";
    private static final String LAYOUT_MARGIN_END = "layout_marginEnd";

    private static final class FileData {
        final XmlContext context;
        final java.util.Map<org.w3c.dom.Element, java.util.List<org.w3c.dom.Attr>> elements =
                new java.util.HashMap<>();

        FileData(XmlContext context) {
            this.context = context;
        }
    }

    private final java.util.Map<String, FileData> mFileData = new java.util.HashMap<>();

    @Override
    public boolean filterIncident(Context context, Incident incident, LintMap map) {
        return true;
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (FileData data : mFileData.values()) {
            for (java.util.Map.Entry<org.w3c.dom.Element, java.util.List<org.w3c.dom.Attr>> entry
                    : data.elements.entrySet()) {
                java.util.Set<String> names = new java.util.HashSet<>();
                for (org.w3c.dom.Attr attr : entry.getValue()) {
                    String localName = attr.getLocalName();
                    if (localName != null) {
                        names.add(localName);
                    }
                }
                checkPair(data, entry.getValue(), names, PADDING_LEFT, PADDING_RIGHT);
                checkPair(data, entry.getValue(), names, LAYOUT_MARGIN_LEFT, LAYOUT_MARGIN_RIGHT);
                checkPair(data, entry.getValue(), names, PADDING_START, PADDING_END);
                checkPair(data, entry.getValue(), names, LAYOUT_MARGIN_START, LAYOUT_MARGIN_END);
            }
        }
        mFileData.clear();
    }

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Arrays.asList(
                PADDING,
                PADDING_LEFT,
                PADDING_RIGHT,
                PADDING_START,
                PADDING_END,
                LAYOUT_MARGIN,
                LAYOUT_MARGIN_LEFT,
                LAYOUT_MARGIN_RIGHT,
                LAYOUT_MARGIN_START,
                LAYOUT_MARGIN_END);
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            return;
        }
        String path = context.file.getPath();
        FileData data = mFileData.get(path);
        if (data == null) {
            data = new FileData(context);
            mFileData.put(path, data);
        }
        org.w3c.dom.Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }
        java.util.List<org.w3c.dom.Attr> list = data.elements.get(element);
        if (list == null) {
            list = new java.util.ArrayList<>();
            data.elements.put(element, list);
        }
        list.add(attribute);
    }

    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Collections.<Class<? extends UElement>>singletonList(
                UCallExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(UCallExpression node) {
                checkJavaCall(context, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    USimpleNameReferenceExpression node) {
                // Not used for this symmetry check.
            }
        };
    }

    private void checkPair(FileData data, java.util.List<org.w3c.dom.Attr> attrs,
            java.util.Set<String> names, String left, String right) {
        boolean hasLeft = names.contains(left);
        boolean hasRight = names.contains(right);
        if (!hasLeft && !hasRight) {
            return;
        }
        String shorthand = left.startsWith(LAYOUT_MARGIN) ? LAYOUT_MARGIN : PADDING;
        if (names.contains(shorthand)) {
            return;
        }
        if (hasLeft && !hasRight) {
            reportMissing(data, attrs, left, right);
        } else if (hasRight && !hasLeft) {
            reportMissing(data, attrs, right, left);
        }
    }

    private void reportMissing(FileData data, java.util.List<org.w3c.dom.Attr> attrs,
            String present, String missing) {
        for (org.w3c.dom.Attr attr : attrs) {
            if (present.equals(attr.getLocalName())) {
                Location location = data.context.getLocation(attr);
                String message = String.format(
                        "To support right-to-left layouts, add '%s' alongside '%s'",
                        missing, present);
                data.context.report(ISSUE, location, message);
                return;
            }
        }
    }

    private void checkJavaCall(JavaContext context, UCallExpression call) {
        String name = call.getMethodName();
        if (name == null) {
            return;
        }
        int leftIndex;
        int rightIndex;
        String side1;
        String side2;
        if ("setPadding".equals(name)) {
            leftIndex = 0;
            rightIndex = 2;
            side1 = "left";
            side2 = "right";
        } else if ("setPaddingRelative".equals(name)) {
            leftIndex = 0;
            rightIndex = 2;
            side1 = "start";
            side2 = "end";
        } else if ("setMargins".equals(name)) {
            leftIndex = 0;
            rightIndex = 2;
            side1 = "left";
            side2 = "right";
        } else {
            return;
        }

        java.util.List<UExpression> args = call.getValueArguments();
        if (args.size() < 4) {
            return;
        }
        UExpression left = args.get(leftIndex);
        UExpression right = args.get(rightIndex);

        Object leftConstant = ConstantEvaluator.evaluate(context, left);
        Object rightConstant = ConstantEvaluator.evaluate(context, right);
        boolean knownDifferent = false;
        if (leftConstant instanceof Number && rightConstant instanceof Number) {
            if (((Number) leftConstant).doubleValue()
                    == ((Number) rightConstant).doubleValue()) {
                return;
            }
            knownDifferent = true;
        } else if (leftConstant != null && rightConstant != null) {
            if (leftConstant.equals(rightConstant)) {
                return;
            }
            knownDifferent = true;
        }

        if (left.asSourceString().equals(right.asSourceString())) {
            return;
        }

        if (!knownDifferent) {
            return;
        }

        String message = String.format(
                "Asymmetric %s: the %s and %s side values should match for RTL symmetry",
                name, side1, side2);
        context.report(ISSUE, context.getLocation(call), message);
    }
}