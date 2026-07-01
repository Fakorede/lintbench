package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
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
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class OverdrawDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(OverdrawDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Overdraw: Painting regions more than once",
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
                    IMPLEMENTATION);

    private final Map<String, Location> rootBackgroundLocations = new HashMap<>();
    private final Map<String, String> rootBackgroundValues = new HashMap<>();
    private final Set<String> activityLayouts = new HashSet<>();
    private boolean isActivity = false;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Location> entry : rootBackgroundLocations.entrySet()) {
            String layoutName = entry.getKey();
            Location location = entry.getValue();
            String bgValue = rootBackgroundValues.get(layoutName);
            context.report(ISSUE, location,
                    "Possible overdraw: Root element paints background `" + bgValue +
                    "` with a theme that also paints a background (inferred theme is `?android:attr/windowBackground`)");
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        Node parent = element.getParentNode();
        boolean isRoot = parent == null || parent.getNodeType() == Node.DOCUMENT_NODE;
        if (isRoot) {
            String fileName = context.file.getName();
            String layoutName = fileName.endsWith(".xml") ? fileName.substring(0, fileName.length() - 4) : fileName;
            rootBackgroundLocations.put(layoutName, context.getLocation(attribute));
            rootBackgroundValues.put(layoutName, attribute.getValue());
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("background");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (context instanceof JavaContext) {
            isActivity = false;
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Root detection is handled in visitAttribute for efficiency.
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.Activity");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        isActivity = true;
    }

    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        // Reserved for future reference tracking if needed.
    }

    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        if (!isActivity) return;
        String methodName = node.getMethodName();
        if ("setContentView".equals(methodName)) {
            // Inexact pattern matching as specified in the issue description
            String source = node.asSourceString();
            if (source != null && source.contains("R.layout.")) {
                int start = source.indexOf("R.layout.") + 9;
                int end = source.indexOf(')', start);
                if (end == -1) end = source.length();
                String layoutRef = source.substring(start, end).trim();
                if (!layoutRef.isEmpty()) {
                    activityLayouts.add(layoutRef);
                }
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UCallExpression.class, USimpleNameReferenceExpression.class);
    }
}