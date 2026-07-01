package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.*;
import org.jetbrains.uast.*;
import org.w3c.dom.*;

import java.util.*;

public class OverdrawDetector extends LayoutDetector implements Detector.UastScanner {
    public static final Issue ISSUE = Issue.create(
            "Overdraw",
            "Painting regions more than once",
            "If you set a background drawable on a root view, then you should use a " +
            "custom theme where the theme background is null. Otherwise, the theme background " +
            "will be painted first, only to have your custom background completely cover it; " +
            "this is called \"overdraw\".\n\n" +
            "NOTE: This detector relies on figuring out which layouts are associated with " +
            "which activities based on scanning the Java code, and it's currently doing that " +
            "using an inexact pattern matching algorithm. Therefore, it can incorrectly " +
            "conclude which activity the layout is associated with and then wrongly complain " +
            "that a background-theme is hidden.\n\n" +
            "If you want your custom background on multiple pages, then you should consider " +
            "making a custom theme with your custom background and just using that theme " +
            "instead of a root element background.\n\n" +
            "Of course it's possible that your custom drawable is translucent and you want " +
            "it to be mixed with the background. However, you will get better performance " +
            "if you pre-mix the background with your drawable and use that resulting image or " +
            "color as a custom theme background instead.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(OverdrawDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE))
    );

    private final Map<String, String> layoutToActivity = new HashMap<>();

    @Override
    public void beforeCheckRootProject(Context context) {
        layoutToActivity.clear();
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UCallExpression.class);
    }

    @Override
    public void visitCall(JavaContext context, UCallExpression call) {
        String methodName = call.getMethodName();
        if (!"setContentView".equals(methodName)) {
            return;
        }

        List<UExpression> args = call.getValueArguments();
        if (args.size() != 1) {
            return;
        }

        UExpression arg = args.get(0);
        String resource = context.getEvaluator().getResourceName(arg);
        if (resource != null && resource.startsWith("layout/")) {
            String layoutName = resource.substring("layout/".length());
            UClass cls = UastUtils.getContainingUClass(call);
            if (cls != null && cls.getName() != null) {
                layoutToActivity.put(layoutName, cls.getName());
            }
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_BACKGROUND);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent != null && parent.getNodeType() != Node.DOCUMENT_NODE) {
            return;
        }

        if (SdkConstants.TAG_MERGE.equals(element.getTagName())) {
            return;
        }

        String message = "Possible overdraw: Root element paints background `" + attribute.getValue() + "`";
        context.report(ISSUE, attribute, context.getLocation(attribute), message);
    }
}