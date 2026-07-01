package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
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

    private static final String ATTR_LAYOUT_MARGIN_START = "layout_marginStart";
    private static final String ATTR_LAYOUT_MARGIN_END = "layout_marginEnd";
    private static final String ATTR_PADDING_START = "paddingStart";
    private static final String ATTR_PADDING_END = "paddingEnd";

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
                    new Implementation(
                            RtlDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE),
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE));

    // Maps element to a map of attribute name -> XmlContext+Attr location info
    // We store pending incidents that need symmetry checking
    private final Map<Element, Map<String, Attr>> mPendingAttributes = new HashMap<>();
    private final Map<Element, XmlContext> mElementContexts = new HashMap<>();
    private final List<Incident> mPendingIncidents = new ArrayList<>();

    // RTL field references seen in Java/Kotlin source
    private boolean mUsesRtlInCode = false;

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                ATTR_PADDING_LEFT,
                ATTR_PADDING_RIGHT,
                ATTR_LAYOUT_MARGIN_LEFT,
                ATTR_LAYOUT_MARGIN_RIGHT,
                ATTR_PADDING_START,
                ATTR_PADDING_END,
                ATTR_LAYOUT_MARGIN_START,
                ATTR_LAYOUT_MARGIN_END);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
            if (name != null && name.contains(":")) {
                name = name.substring(name.indexOf(':') + 1);
            }
        }

        Map<String, Attr> attrs = mPendingAttributes.get(element);
        if (attrs == null) {
            attrs = new HashMap<>();
            mPendingAttributes.put(element, attrs);
            mElementContexts.put(element, context);
        }
        attrs.put(name, attribute);
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull Object cookie) {
        // If RTL attributes or code usage is detected, suppress the warning
        if (cookie instanceof Boolean) {
            return !(Boolean) cookie;
        }
        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<Element, Map<String, Attr>> entry : mPendingAttributes.entrySet()) {
            Element element = entry.getKey();
            Map<String, Attr> attrs = entry.getValue();
            XmlContext xmlContext = mElementContexts.get(element);

            checkSymmetry(
                    xmlContext,
                    attrs,
                    ATTR_PADDING_LEFT,
                    ATTR_PADDING_RIGHT,
                    "paddingLeft",
                    "paddingRight");
            checkSymmetry(
                    xmlContext,
                    attrs,
                    ATTR_PADDING_RIGHT,
                    ATTR_PADDING_LEFT,
                    "paddingRight",
                    "paddingLeft");
            checkSymmetry(
                    xmlContext,
                    attrs,
                    ATTR_LAYOUT_MARGIN_LEFT,
                    ATTR_LAYOUT_MARGIN_RIGHT,
                    "layout_marginLeft",
                    "layout_marginRight");
            checkSymmetry(
                    xmlContext,
                    attrs,
                    ATTR_LAYOUT_MARGIN_RIGHT,
                    ATTR_LAYOUT_MARGIN_LEFT,
                    "layout_marginRight",
                    "layout_marginLeft");
            checkSymmetry(
                    xmlContext,
                    attrs,
                    ATTR_PADDING_START,
                    ATTR_PADDING_END,
                    "paddingStart",
                    "paddingEnd");
            checkSymmetry(
                    xmlContext,
                    attrs,
                    ATTR_PADDING_END,
                    ATTR_PADDING_START,
                    "paddingEnd",
                    "paddingStart");
            checkSymmetry(
                    xmlContext,
                    attrs,
                    ATTR_LAYOUT_MARGIN_START,
                    ATTR_LAYOUT_MARGIN_END,
                    "layout_marginStart",
                    "layout_marginEnd");
            checkSymmetry(
                    xmlContext,
                    attrs,
                    ATTR_LAYOUT_MARGIN_END,
                    ATTR_LAYOUT_MARGIN_START,
                    "layout_marginEnd",
                    "layout_marginStart");
        }

        mPendingAttributes.clear();
        mElementContexts.clear();
    }

    private void checkSymmetry(
            @NonNull XmlContext context,
            @NonNull Map<String, Attr> attrs,
            @NonNull String presentAttr,
            @NonNull String missingAttr,
            @NonNull String presentName,
            @NonNull String missingName) {
        if (attrs.containsKey(presentAttr) && !attrs.containsKey(missingAttr)) {
            Attr attr = attrs.get(presentAttr);
            if (attr == null) {
                return;
            }
            Location location = context.getLocation(attr);
            String message =
                    String.format(
                            "When specifying `%1$s` you should probably also specify `%2$s` for"
                                    + " right-to-left layout symmetry",
                            presentName, missingName);
            context.report(ISSUE, attr, location, message);
        }
    }

    // SourceCodeScanner implementation

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>();
        types.add(USimpleNameReferenceExpression.class);
        return types;
    }

    @Override
    @Nullable
    public UastHandler createUastHandler(@NonNull JavaContext context) {
        return new UastHandler(context);
    }

    private class UastHandler extends AbstractUastVisitor {
        private final JavaContext mContext;

        UastHandler(JavaContext context) {
            mContext = context;
        }

        @Override
        public boolean visitSimpleNameReferenceExpression(
                @NonNull USimpleNameReferenceExpression node) {
            String name = node.getIdentifier();
            if (name.contains("Rtl")
                    || name.contains("rtl")
                    || name.contains("Start")
                    || name.contains("End")
                    || name.contains("layoutDirection")
                    || name.contains("LayoutDirection")) {
                mUsesRtlInCode = true;
            }
            return super.visitSimpleNameReferenceExpression(node);
        }
    }

    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        String name = node.getIdentifier();
        if (name.contains("Rtl")
                || name.contains("rtl")
                || name.contains("Start")
                || name.contains("End")
                || name.contains("layoutDirection")
                || name.contains("LayoutDirection")) {
            mUsesRtlInCode = true;
        }
    }
}