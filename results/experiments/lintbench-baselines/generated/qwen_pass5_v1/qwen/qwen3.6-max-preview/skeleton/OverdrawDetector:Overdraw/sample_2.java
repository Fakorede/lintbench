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
import com.android.tools.lint.detector.api.Location;
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
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.USimpleReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class OverdrawDetector extends LayoutDetector implements Detector.UastScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(OverdrawDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Overdraw: Painting regions more than once",
                    "If you set a background drawable on a root view, then you should use a custom theme where the theme background is null. " +
                    "Otherwise, the theme background will be painted first, only to have your custom background completely cover it; " +
                    "this is called \"overdraw\".\n\n" +
                    "NOTE: This detector relies on figuring out which layouts are associated with which activities based on scanning " +
                    "the Java code, and it's currently doing that using an inexact pattern matching algorithm. Therefore, it can " +
                    "incorrectly conclude which activity the layout is associated with and then wrongly complain that a background-theme is hidden.\n\n" +
                    "If you want your custom background on multiple pages, then you should consider making a custom theme with your " +
                    "custom background and just using that theme instead of a root element background.\n\n" +
                    "Of course it's possible that your custom drawable is translucent and you want it to be mixed with the background. " +
                    "However, you will get better performance if you pre-mix the background with your drawable and use that resulting " +
                    "image or color as a custom theme background instead.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ATTR_BACKGROUND = "background";

    private final Map<String, Location> layoutsWithRootBackground = new HashMap<>();
    private final Set<String> layoutsUsedInActivities = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Location> entry : layoutsWithRootBackground.entrySet()) {
            if (layoutsUsedInActivities.contains(entry.getKey())) {
                context.report(ISSUE, entry.getValue(),
                        "Possible overdraw: Root element paints background; consider using a custom theme with a null window background instead.");
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element owner = attribute.getOwnerElement();
        if (owner != null && owner.getParentNode() != null && owner.getParentNode().getNodeType() == Node.DOCUMENT_NODE) {
            String fileName = context.file.getName();
            int dot = fileName.lastIndexOf('.');
            String layoutName = (dot != -1) ? fileName.substring(0, dot) : fileName;
            layoutsWithRootBackground.put(layoutName, context.getLocation(attribute));
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_BACKGROUND);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // Aggregation happens across files; no per-file reset required
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Logic handled in visitAttribute
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "androidx.appcompat.app.AppCompatActivity",
                "android.support.v7.app.AppCompatActivity"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Logic handled in visitCallExpression
    }

    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleReferenceExpression node) {
        // Not used for this detector
    }

    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        String methodName = node.getMethodName();
        if ("setContentView".equals(methodName) || "inflate".equals(methodName)) {
            List<UExpression> args = node.getValueArguments();
            if (!args.isEmpty()) {
                String text = args.get(0).asSourceString();
                int idx = text.indexOf("R.layout.");
                if (idx != -1) {
                    String name = text.substring(idx + 9);
                    int end = 0;
                    while (end < name.length() && Character.isJavaIdentifierPart(name.charAt(end))) {
                        end++;
                    }
                    name = name.substring(0, end);
                    if (!name.isEmpty()) {
                        layoutsUsedInActivities.add(name);
                    }
                }
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UCallExpression.class);
    }
}