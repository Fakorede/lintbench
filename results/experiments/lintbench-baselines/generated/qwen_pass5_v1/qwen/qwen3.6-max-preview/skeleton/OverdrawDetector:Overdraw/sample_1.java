package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private final Map<String, Location> mLayoutLocations = new HashMap<>();
    private final Map<String, String> mLayoutBackgroundValues = new HashMap<>();
    private final Set<String> mReferencedLayouts = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (String layoutName : mLayoutLocations.keySet()) {
            Location location = mLayoutLocations.get(layoutName);
            String bgValue = mLayoutBackgroundValues.get(layoutName);
            String message = "Possible overdraw: Root element paints background `" + bgValue +
                    "` with a theme that also paints a background (inferred theme is `?android:windowBackground`)";
            context.report(ISSUE, location, message);
        }
        mLayoutLocations.clear();
        mLayoutBackgroundValues.clear();
        mReferencedLayouts.clear();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!"http://schemas.android.com/apk/res/android".equals(attribute.getNamespaceURI())) {
            return;
        }
        Element element = attribute.getOwnerElement();
        if (element.getParentNode() != null && element.getParentNode().getNodeType() == Node.DOCUMENT_NODE) {
            String fileName = context.file.getName();
            mLayoutLocations.put(fileName, context.getLocation(attribute));
            mLayoutBackgroundValues.put(fileName, attribute.getValue());
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
        // No per-file state to reset
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Root background tracking is handled in visitAttribute
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "android.app.Fragment",
                "androidx.fragment.app.Fragment",
                "android.support.v4.app.Fragment"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Activity/Fragment class tracking handled via super class filtering
    }

    @Override
    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        // Catch standalone R.layout references if needed
        String identifier = node.getIdentifier();
        if (identifier != null && identifier.startsWith("R.layout.")) {
            String layoutName = identifier.substring("R.layout.".length()) + ".xml";
            mReferencedLayouts.add(layoutName);
        }
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        String methodName = node.getMethodName();
        if ("setContentView".equals(methodName) || "inflate".equals(methodName)) {
            List<UExpression> args = node.getValueArguments();
            if (!args.isEmpty()) {
                String source = args.get(0).asSourceString();
                if (source != null && source.contains("R.layout.")) {
                    int idx = source.indexOf("R.layout.");
                    String ref = source.substring(idx + "R.layout.".length());
                    int dot = ref.indexOf('.');
                    if (dot != -1) {
                        ref = ref.substring(0, dot);
                    }
                    mReferencedLayouts.add(ref + ".xml");
                }
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UClass.class, UCallExpression.class, USimpleNameReferenceExpression.class);
    }
}