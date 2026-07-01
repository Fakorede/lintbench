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

    private static final String KEY_ATTR = "attr";

    public static final Issue ISSUE =
            Issue.create(
                    "RtlSymmetry",
                    "Padding and margin symmetry",
                    "If you specify padding or margin on the left side of a layout, you should"
                            + " probably also specify padding on the right side (and vice versa)"
                            + " for right-to-left layout symmetry.",
                    Category.RTL,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            RtlDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE),
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE));

    private static final Map<String, String> ATTR_PAIRS = new HashMap<>();

    static {
        ATTR_PAIRS.put(ATTR_PADDING_LEFT, ATTR_PADDING_RIGHT);
        ATTR_PAIRS.put(ATTR_PADDING_RIGHT, ATTR_PADDING_LEFT);
        ATTR_PAIRS.put(ATTR_LAYOUT_MARGIN_LEFT, ATTR_LAYOUT_MARGIN_RIGHT);
        ATTR_PAIRS.put(ATTR_LAYOUT_MARGIN_RIGHT, ATTR_LAYOUT_MARGIN_LEFT);
    }

    // R.attr field names that correspond to left/right attributes
    private static final Map<String, String> JAVA_ATTR_PAIRS = new HashMap<>();

    static {
        JAVA_ATTR_PAIRS.put("paddingLeft", "paddingRight");
        JAVA_ATTR_PAIRS.put("paddingRight", "paddingLeft");
        JAVA_ATTR_PAIRS.put("layout_marginLeft", "layout_marginRight");
        JAVA_ATTR_PAIRS.put("layout_marginRight", "layout_marginLeft");
    }

    private final Map<String, Incident> mPendingIncidents = new HashMap<>();

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

        String counterpart = ATTR_PAIRS.get(name);
        if (counterpart == null) {
            return;
        }

        Element element = attribute.getOwnerElement();
        NamedNodeMap attributes = element.getAttributes();

        // Check if the counterpart attribute exists on the same element
        boolean hasCounterpart = false;
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String attrName = attr.getLocalName();
            if (attrName == null) {
                attrName = attr.getName();
            }
            if (counterpart.equals(attrName)) {
                hasCounterpart = true;
                break;
            }
        }

        if (!hasCounterpart) {
            String message =
                    "When you define `"
                            + name
                            + "` you should probably also define `"
                            + counterpart
                            + "` for right-to-left symmetry";
            Incident incident =
                    new Incident(
                            ISSUE,
                            attribute,
                            context.getLocation(attribute),
                            message,
                            context.fix().build());
            LintMap map = new LintMap();
            map.put(KEY_ATTR, name);
            context.report(incident, map);
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Allow all incidents to pass through; filtering is not needed beyond default behavior
        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Nothing additional to do after checking the root project
    }

    // SourceCodeScanner implementation

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
                RtlDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        String name = node.getIdentifier();
        String counterpart = JAVA_ATTR_PAIRS.get(name);
        if (counterpart == null) {
            return;
        }

        PsiElement resolved = node.resolve();
        if (resolved == null) {
            return;
        }

        // We flag usage of left/right padding or margin attributes in Java/Kotlin source
        // to encourage use of start/end equivalents for RTL support
        String message =
                "Consider using `"
                        + toStartEnd(name)
                        + "` instead of `"
                        + name
                        + "` for better right-to-left support";

        context.report(ISSUE, node, context.getLocation(node), message);
    }

    private static String toStartEnd(String name) {
        if (name.contains("Left")) {
            return name.replace("Left", "Start");
        } else if (name.contains("Right")) {
            return name.replace("Right", "End");
        }
        return name;
    }

    // Minimal UElementHandler stub (inline anonymous class used in createUastHandler above)
    private abstract static class UElementHandler
            extends org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor {
        public void visitSimpleNameReferenceExpression(
                @NonNull USimpleNameReferenceExpression node) {}

        @Override
        public boolean visitSimpleNameReferenceExpression(
                @NonNull USimpleNameReferenceExpression node,
                @NonNull org.jetbrains.uast.visitor.UastVisitor visitor) {
            visitSimpleNameReferenceExpression(node);
            return true;
        }
    }

    // Needed for Collections reference
    private static final class Collections {
        static <T> List<T> singletonList(T item) {
            return java.util.Collections.singletonList(item);
        }
    }
}