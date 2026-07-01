package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class OverdrawDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(OverdrawDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

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
                    IMPLEMENTATION);

    private final Map<String, List<Location>> mLayoutToBackgroundLocations = new HashMap<>();
    private final Map<String, Set<String>> mLayoutToActivities = new HashMap<>();
    private final Set<String> mSafeActivities = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<Location>> entry : mLayoutToBackgroundLocations.entrySet()) {
            String layoutName = entry.getKey();
            List<Location> locations = entry.getValue();

            Set<String> activities = mLayoutToActivities.get(layoutName);
            boolean hasUnsafeActivity = false;

            if (activities != null) {
                for (String activity : activities) {
                    if (!mSafeActivities.contains(activity)) {
                        hasUnsafeActivity = true;
                        break;
                    }
                }
            }

            if (activities == null || hasUnsafeActivity) {
                String message = "Possible overdraw: Root element has background, but the theme "
                        + "may also paint a background. Consider using a theme with a null "
                        + "background or removing the root background.";
                for (Location location : locations) {
                    context.report(ISSUE, location, message);
                }
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        if (element != null) {
            Node parent = element.getParentNode();
            if (parent instanceof Document) {
                String value = attribute.getValue();
                if (value != null && !value.equals("@null")) {
                    String layoutName = context.file.getName();
                    if (layoutName.endsWith(".xml")) {
                        layoutName = layoutName.substring(0, layoutName.length() - 4);
                    }
                    Location location = context.getLocation(attribute);
                    registerRootBackground(layoutName, location);
                }
            }
        }
    }

    private void registerRootBackground(String layoutName, Location location) {
        List<Location> locations = mLayoutToBackgroundLocations.get(layoutName);
        if (locations == null) {
            locations = new ArrayList<>();
            mLayoutToBackgroundLocations.put(layoutName, locations);
        }
        locations.add(location);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("background");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No-op
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No-op
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList("android.app.Activity", "androidx.appcompat.app.AppCompatActivity", "support.v7.app.AppCompatActivity");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Handled via UAST traversal
    }

    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        String layoutName = getLayoutName(node);
        if (layoutName != null) {
            UClass containingClass = getContainingClass(node);
            if (containingClass != null) {
                String className = containingClass.getQualifiedName();
                if (className != null) {
                    associateLayoutWithActivity(layoutName, className);
                }
            }
        }
    }

    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        String methodName = node.getMethodName();
        if (methodName != null) {
            if (methodName.equals("setContentView") || methodName.equals("inflate")) {
                List<UExpression> args = node.getValueArguments();
                if (!args.isEmpty()) {
                    UExpression firstArg = args.get(0);
                    String layoutName = getLayoutName(firstArg);
                    if (layoutName != null) {
                        UClass containingClass = getContainingClass(node);
                        if (containingClass != null) {
                            String className = containingClass.getQualifiedName();
                            if (className != null) {
                                associateLayoutWithActivity(layoutName, className);
                            }
                        }
                    }
                }
            } else if (methodName.equals("setBackgroundDrawable") || methodName.equals("setBackgroundDrawableResource")) {
                List<UExpression> args = node.getValueArguments();
                if (!args.isEmpty()) {
                    UExpression arg = args.get(0);
                    String argStr = arg.asSourceString();
                    if ("null".equals(argStr) || "0".equals(argStr) || "@null".equals(argStr)) {
                        UClass containingClass = getContainingClass(node);
                        if (containingClass != null) {
                            String className = containingClass.getQualifiedName();
                            if (className != null) {
                                mSafeActivities.add(className);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                OverdrawDetector.this.visitCallExpression(context, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                OverdrawDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    private String getLayoutName(UExpression expression) {
        if (expression == null) {
            return null;
        }
        String s = expression.asSourceString();
        if (s == null) {
            return null;
        }
        int index = s.indexOf("R.layout.");
        if (index != -1) {
            int start = index + "R.layout.".length();
            int end = start;
            while (end < s.length() && Character.isJavaIdentifierPart(s.charAt(end))) {
                end++;
            }
            if (end > start) {
                return s.substring(start, end);
            }
        }
        return null;
    }

    private UClass getContainingClass(UElement node) {
        UElement parent = node.getUastParent();
        while (parent != null) {
            if (parent instanceof UClass) {
                return (UClass) parent;
            }
            parent = parent.getUastParent();
        }
        return null;
    }

    private void associateLayoutWithActivity(String layoutName, String activityClass) {
        Set<String> activities = mLayoutToActivities.get(layoutName);
        if (activities == null) {
            activities = new HashSet<>();
            mLayoutToActivities.put(layoutName, activities);
        }
        activities.add(activityClass);
    }
}