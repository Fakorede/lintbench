package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import org.jetbrains.uast.*;

public class RtlDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    RtlDetector.class,
                    java.util.EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "RtlSymmetry",
                    "Padding and margin symmetry",
                    "If you specify padding or margin on the left side of a layout, "
                            + "you should probably also specify padding or margin on the right "
                            + "side (and vice versa) for right-to-left layout symmetry.",
                    Category.RTL,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public boolean filterIncident(Context context, Incident incident, LintMap map) {
        // No cross-file filtering is required for this check.
        return true;
    }

    @Override
    public void afterCheckRootProject(Context context) {
        // All reporting is done while visiting XML attributes.
    }

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Arrays.asList(
                "paddingLeft",
                "paddingRight",
                "paddingStart",
                "paddingEnd",
                "layout_marginLeft",
                "layout_marginRight",
                "layout_marginStart",
                "layout_marginEnd");
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String name = attribute.getLocalName();
        String counterpart = getCounterpart(name);
        if (counterpart == null) {
            return;
        }

        org.w3c.dom.Element owner = attribute.getOwnerElement();
        if (hasAndroidAttribute(owner, counterpart)
                || hasAndroidAttribute(owner, getGlobal(name))) {
            return;
        }

        String message =
                "For RTL symmetry, add " + counterpart + " in addition to " + name;
        context.report(ISSUE, attribute, context.getLocation(attribute), message);
    }

    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
                // Java source checks are not required for padding/margin XML symmetry.
            }
        };
    }

    private static String getCounterpart(String name) {
        switch (name) {
            case "paddingLeft":
                return "paddingRight";
            case "paddingRight":
                return "paddingLeft";
            case "paddingStart":
                return "paddingEnd";
            case "paddingEnd":
                return "paddingStart";
            case "layout_marginLeft":
                return "layout_marginRight";
            case "layout_marginRight":
                return "layout_marginLeft";
            case "layout_marginStart":
                return "layout_marginEnd";
            case "layout_marginEnd":
                return "layout_marginStart";
            default:
                return null;
        }
    }

    private static String getGlobal(String name) {
        return name.startsWith("layout_margin") ? "layout_margin" : "padding";
    }

    private static boolean hasAndroidAttribute(org.w3c.dom.Element owner, String localName) {
        org.w3c.dom.NamedNodeMap attrs = owner.getAttributes();
        int length = attrs.getLength();
        for (int i = 0; i < length; i++) {
            org.w3c.dom.Node attr = attrs.item(i);
            if (ANDROID_URI.equals(attr.getNamespaceURI())
                    && localName.equals(attr.getLocalName())) {
                return true;
            }
        }
        return false;
    }
}