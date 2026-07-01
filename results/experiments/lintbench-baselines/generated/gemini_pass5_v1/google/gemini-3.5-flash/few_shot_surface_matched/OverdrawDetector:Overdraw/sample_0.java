package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Location;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.visitor.UastVisitor;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class OverdrawDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Overdraw: Painting regions more than once",
                    "If you set a background drawable on a root view, then you should use a "
                            + "custom theme where the theme background is null. Otherwise, the theme "
                            + "background will be painted first, only to have your custom background "
                            + "completely cover it; this is called \"overdraw\".\n\n"
                            + "NOTE: This detector relies on figuring out which layouts are associated with "
                            + "which activities based on scanning the Java code, and it's currently doing that "
                            + "using an inexact pattern matching algorithm. Therefore, it can incorrectly "
                            + "conclude which activity the layout is associated with and then wrongly complain "
                            + "that a background-theme is hidden.\n\n"
                            + "If you want your custom background on multiple pages, then you should consider "
                            + "making a custom theme with your custom background and just using that theme "
                            + "instead of a root element background.\n\n"
                            + "Of course it's possible that your custom drawable is translucent and you want "
                            + "it to be mixed with the background. However, you will get better performance "
                            + "if you pre-mix the background with your drawable and use that resulting image or "
                            + "color as a custom theme background instead.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    new Implementation(
                            OverdrawDetector.class,
                            java.util.EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE)));

    private final java.util.Map<String, RootBackgroundInfo> mRootBackgrounds = new java.util.HashMap<>();
    private final java.util.Map<String, String> mLayoutToActivity = new java.util.HashMap<>();

    private static class RootBackgroundInfo {
        final XmlContext context;
        final org.w3c.dom.Attr attribute;
        final Location location;
        final String backgroundValue;

        RootBackgroundInfo(XmlContext context, org.w3c.dom.Attr attribute, Location location, String backgroundValue) {
            this.context = context;
            this.attribute = attribute;
            this.location = location;
            this.backgroundValue = backgroundValue;
        }
    }

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public boolean appliesTo(Context context, java.io.File file) {
        return true;
    }

    @Override
    public void beforeCheckFile(Context context) {
        super.beforeCheckFile(context);
    }

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Collections.singletonList("android:background");
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        if ("android:background".equals(attribute.getName()) || "background".equals(attribute.getLocalName())) {
            org.w3c.dom.Element element = attribute.getOwnerElement();
            if (element != null && element.getParentNode() instanceof org.w3c.dom.Document) {
                String layoutName = context.file.getName();
                if (layoutName.endsWith(".xml")) {
                    layoutName = layoutName.substring(0, layoutName.length() - 4);
                }
                String value = attribute.getValue();
                mRootBackgrounds.put(layoutName, new RootBackgroundInfo(context, attribute, context.getLocation(attribute), value));
            }
        }
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.emptyList();
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        // Required override
    }

    @Override
    public java.util.List<String> applicableSuperClasses() {
        return java.util.Arrays.asList("android.app.Activity", "androidx.fragment.app.Fragment");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        // Required override
    }

    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Arrays.asList(
                USimpleNameReferenceExpression.class,
                UCallExpression.class
        );
    }

    @Override
    public UastVisitor createUastHandler(JavaContext context) {
        return new AbstractUastVisitor() {
            @Override
            public boolean visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
                OverdrawDetector.this.visitSimpleNameReferenceExpression(context, node);
                return super.visitSimpleNameReferenceExpression(node);
            }

            @Override
            public boolean visitCallExpression(UCallExpression node) {
                OverdrawDetector.this.visitCallExpression(context, node);
                return super.visitCallExpression(node);
            }
        };
    }

    public void visitSimpleNameReferenceExpression(JavaContext context, USimpleNameReferenceExpression node) {
        String identifier = node.getIdentifier();
        UElement parent = node.getUastParent();
        if (parent instanceof UQualifiedReferenceExpression) {
            String receiverStr = ((UQualifiedReferenceExpression) parent).getReceiver().toString();
            if (receiverStr.endsWith("R.layout")) {
                UClass uClass = UastUtils.getContainingUClass(node);
                if (uClass != null) {
                    String activityClass = uClass.getQualifiedName();
                    if (activityClass != null) {
                        mLayoutToActivity.put(identifier, activityClass);
                    }
                }
            }
        }
    }

    public void visitCallExpression(JavaContext context, UCallExpression node) {
        for (UExpression argument : node.getValueArguments()) {
            String argString = argument.toString();
            if (argString.contains("R.layout.")) {
                int index = argString.indexOf("R.layout.");
                String layoutName = argString.substring(index + "R.layout.".length()).trim();
                int end = 0;
                while (end < layoutName.length() && Character.isJavaIdentifierPart(layoutName.charAt(end))) {
                    end++;
                }
                if (end > 0) {
                    layoutName = layoutName.substring(0, end);
                    UClass uClass = UastUtils.getContainingUClass(node);
                    if (uClass != null) {
                        String activityClass = uClass.getQualifiedName();
                        if (activityClass != null) {
                            mLayoutToActivity.put(layoutName, activityClass);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (java.util.Map.Entry<String, RootBackgroundInfo> entry : mRootBackgrounds.entrySet()) {
            String layoutName = entry.getKey();
            RootBackgroundInfo info = entry.getValue();
            if (mLayoutToActivity.containsKey(layoutName)) {
                String activity = mLayoutToActivity.get(layoutName);
                info.context.report(
                        ISSUE,
                        info.attribute,
                        info.location,
                        String.format("Possible overdraw: Root element has background \"%1$s\", but the associated activity `%2$s` has a theme background", info.backgroundValue, activity)
                );
            }
        }
    }
}