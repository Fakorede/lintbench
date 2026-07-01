package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.util.UastUtils;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OverdrawDetector extends Detector implements Detector.XmlScanner, Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
        "Overdraw",
        "Painting regions more than once",
        "If you set a background drawable on a root view, then you should use a custom theme where the theme background is null. Otherwise, the theme background will be painted first, only to have your custom background completely cover it; this is called \"overdraw\".\n\n" +
        "NOTE: This detector relies on figuring out which layouts are associated with which activities based on scanning the Java code, and it's currently doing that using an inexact pattern matching algorithm. Therefore, it can incorrectly conclude which activity the layout is associated with and then wrongly complain that a background-theme is hidden.\n\n" +
        "If you want your custom background on multiple pages, then you should consider making a custom theme with your custom background and just using that theme instead of a root element background.\n\n" +
        "Of course it's possible that your custom drawable is translucent and you want it to be mixed with the background. However, you will get better performance if you pre-mix the background with your drawable and use that resulting image or color as a custom theme background instead.",
        Category.PERFORMANCE,
        3,
        Severity.WARNING,
        new Implementation(OverdrawDetector.class, Scope.JAVA_FILE, Scope.RESOURCE_FILE)
    );

    private final Map<String, String> layoutToActivity = new HashMap<>();

    @Override
    public void beforeCheckProject(Context context) {
        layoutToActivity.clear();
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UCallExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(UCallExpression node) {
                String methodName = node.getMethodName();
                if ("setContentView".equals(methodName) || "inflate".equals(methodName)) {
                    List<UExpression> args = node.getValueArguments();
                    if (!args.isEmpty()) {
                        UExpression arg = args.get(0);
                        String resourceUrl = context.getResourceUrl(arg);
                        if (resourceUrl != null && resourceUrl.startsWith("@layout/")) {
                            String layoutName = resourceUrl.substring("@layout/".length());
                            UClass containingClass = UastUtils.getContainingUClass(node);
                            if (containingClass != null) {
                                String className = containingClass.getQualifiedName();
                                if (className != null) {
                                    layoutToActivity.put(layoutName, className);
                                }
                            }
                        }
                    }
                }
            }
        };
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(null);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (element.getOwnerDocument().getDocumentElement() == element) {
            if (element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BACKGROUND)) {
                String fileName = context.file.getName();
                int dotIndex = fileName.lastIndexOf('.');
                String layoutName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
                String activity = layoutToActivity.get(layoutName);
                if (activity != null) {
                    String background = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BACKGROUND);
                    if (background != null
                            && !background.equals("@android:color/transparent")
                            && !background.equals("@null")
                            && !background.equals("?android:attr/windowBackground")) {
                        context.report(ISSUE, element, context.getLocation(element),
                                "Possible overdraw: Root element paints background `" + background
                                        + "` with a theme that also paints a background (inferred theme is `" + activity + "`)");
                    }
                }
            }
        }
    }
}