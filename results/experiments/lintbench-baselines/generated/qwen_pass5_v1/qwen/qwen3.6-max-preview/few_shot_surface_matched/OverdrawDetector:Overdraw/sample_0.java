package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.jetbrains.uast.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.util.*;

import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.tools.lint.detector.api.ResourceFolderType.LAYOUT;

public class OverdrawDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Overdraw: Painting regions more than once",
                    "If you set a background drawable on a root view, then you should use a custom theme "
                            + "where the theme background is null. Otherwise, the theme background will be painted "
                            + "first, only to have your custom background completely cover it; this is called \"overdraw\".\n\n"
                            + "NOTE: This detector relies on figuring out which layouts are associated with which activities "
                            + "based on scanning the Java code, and it's currently doing that using an inexact pattern matching "
                            + "algorithm. Therefore, it can incorrectly conclude which activity the layout is associated with "
                            + "and then wrongly complain that a background-theme is hidden.\n\n"
                            + "If you want your custom background on multiple pages, then you should consider making a custom "
                            + "theme with your custom background and just using that theme instead of a root element background.\n\n"
                            + "Of course it's possible that your custom drawable is translucent and you want it to be mixed with "
                            + "the background. However, you will get better performance if you pre-mix the background with your "
                            + "drawable and use that resulting image or color as a custom theme background instead.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    new Implementation(OverdrawDetector.class, Scope.JAVA_AND_RESOURCE_FILES));

    private final Map<String, XmlContext> layoutContexts = new HashMap<>();
    private final Map<String, Attr> layoutAttributes = new HashMap<>();
    private final Set<String> layoutsUsedByActivities = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == LAYOUT;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // Per-file initialization if needed
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Root element background tracking is handled in visitAttribute
    }

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_BACKGROUND);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element owner = attribute.getOwnerElement();
        if (owner.getParentNode() == owner.getOwnerDocument()) {
            String layoutName = context.file.getName().replace(".xml", "");
            layoutContexts.put(layoutName, context);
            layoutAttributes.put(layoutName, attribute);
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "androidx.appcompat.app.AppCompatActivity",
                "android.support.v7.app.AppCompatActivity");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Activity class tracking is handled via call expressions
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UCallExpression.class, UClass.class);
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        String methodName = node.getMethodName();
        if ("setContentView".equals(methodName) || "inflate".equals(methodName)) {
            List<UExpression> args = node.getValueArguments();
            if (!args.isEmpty()) {
                String source = args.get(0).asSourceString();
                int layoutIdx = source.indexOf("R.layout.");
                if (layoutIdx != -1) {
                    String layoutName = source.substring(layoutIdx + 9);
                    int dotIdx = layoutName.indexOf('.');
                    if (dotIdx != -1) {
                        layoutName = layoutName.substring(0, dotIdx);
                    }
                    layoutsUsedByActivities.add(layoutName);
                }
            }
        }
    }

    @Override
    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull UReferenceExpression reference) {
        // Reserved for explicit reference tracking if needed
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (String layoutName : layoutContexts.keySet()) {
            if (layoutsUsedByActivities.contains(layoutName)) {
                XmlContext xmlContext = layoutContexts.get(layoutName);
                Attr attr = layoutAttributes.get(layoutName);
                if (xmlContext != null && attr != null) {
                    xmlContext.report(
                            ISSUE,
                            attr,
                            xmlContext.getLocation(attr),
                            "Possible overdraw: Root element paints background `" + attr.getValue()
                                    + "` which may be covered by a theme background. Consider using a theme with "
                                    + "a null background or pre-mixing the drawable.");
                }
            }
        }
        layoutContexts.clear();
        layoutAttributes.clear();
        layoutsUsedByActivities.clear();
    }
}