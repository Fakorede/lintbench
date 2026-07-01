package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.TAG_STYLE;
import static com.android.SdkConstants.VALUE_NULL;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class OverdrawDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String ACTIVITY = "android.app.Activity";
    private static final String SET_CONTENT_VIEW = "setContentView";

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Painting regions more than once",
                    "If you set a background drawable on a root view, then you should use a "
                            + "custom theme where the theme background is null. Otherwise, the theme background "
                            + "will be painted first, only to have your custom background completely cover it; "
                            + "this is called \"overdraw\".\n"
                            + "\n"
                            + "NOTE: This detector relies on figuring out which layouts are associated with "
                            + "which activities based on scanning the Java code, and it's currently doing that "
                            + "using an inexact pattern matching algorithm. Therefore, it can incorrectly "
                            + "conclude which activity the layout is associated with and then wrongly complain "
                            + "that a background-theme is hidden.\n"
                            + "\n"
                            + "If you want your custom background on multiple pages, then you should consider "
                            + "making a custom theme with your custom background and just using that theme "
                            + "instead of a root element background.\n"
                            + "\n"
                            + "Of course it's possible that your custom drawable is translucent and you want "
                            + "it to be mixed with the background. However, you will get better performance "
                            + "if you pre-mix the background with your drawable and use that resulting image or "
                            + "color as a custom theme background instead.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    new Implementation(
                            OverdrawDetector.class,
                            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE, Scope.RESOURCE_FILE)));

    /** Map from activity class name to theme name */
    private Map<String, String> mActivityToTheme;

    /** Map from theme name to whether it has a null/transparent background */
    private Map<String, Boolean> mThemeToBackgroundNull;

    /** Map from layout name to location of background attribute on root element */
    private Map<String, Location> mLayoutToBackground;

    /** Map from layout name to the root element tag */
    private Map<String, String> mLayoutToRootTag;

    /** Map from activity class name to layout name */
    private Map<String, String> mActivityToLayout;

    /** Map from layout name to list of activities */
    private Map<String, List<String>> mLayoutToActivity;

    /** Current activity being visited */
    private String mCurrentActivity;

    public OverdrawDetector() {}

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.VALUES;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_BACKGROUND, ATTR_THEME, ATTR_PARENT, ATTR_NAME);
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_STYLE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // Nothing to do here
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String attributeName = attribute.getLocalName();
        if (attributeName == null) {
            attributeName = attribute.getName();
        }

        ResourceFolderType folderType = context.getResourceFolderType();

        if (ATTR_BACKGROUND.equals(attributeName) && folderType == ResourceFolderType.LAYOUT) {
            // Check if this is the root element
            Element element = attribute.getOwnerElement();
            if (element.getParentNode() == element.getOwnerDocument().getDocumentElement()
                    || element.getParentNode() == element.getOwnerDocument()) {
                // This is the root element - record the background
                String layoutName = getLayoutName(context.file);
                if (layoutName != null) {
                    if (mLayoutToBackground == null) {
                        mLayoutToBackground = new HashMap<>();
                    }
                    String background = attribute.getValue();
                    if (background != null && !background.isEmpty() && !VALUE_NULL.equals(background)) {
                        mLayoutToBackground.put(layoutName, context.getLocation(attribute));
                    }
                }
            }
        } else if (ATTR_THEME.equals(attributeName)) {
            // In the manifest, activity elements can have a theme attribute
            // We handle this via visitElement for activity nodes
            // but also handle it here for completeness
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (TAG_STYLE.equals(element.getTagName())) {
            // Check if this style has a null background
            String styleName = element.getAttribute(ATTR_NAME);
            if (styleName != null && !styleName.isEmpty()) {
                // Look for windowBackground item children
                org.w3c.dom.NodeList children = element.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    org.w3c.dom.Node child = children.item(i);
                    if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                        Element item = (Element) child;
                        String itemName = item.getAttribute(ATTR_NAME);
                        if ("android:windowBackground".equals(itemName) || "windowBackground".equals(itemName)) {
                            String value = item.getTextContent();
                            if (value != null) {
                                value = value.trim();
                                boolean isNull = VALUE_NULL.equals(value)
                                        || "@null".equals(value)
                                        || "@android:color/transparent".equals(value);
                                if (mThemeToBackgroundNull == null) {
                                    mThemeToBackgroundNull = new HashMap<>();
                                }
                                mThemeToBackgroundNull.put(styleName, isNull);
                                // Also store with full prefix
                                mThemeToBackgroundNull.put(STYLE_RESOURCE_PREFIX + styleName, isNull);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    @Nullable
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(ACTIVITY);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        mCurrentActivity = declaration.getQualifiedName();
    }

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>();
        types.add(UCallExpression.class);
        types.add(USimpleNameReferenceExpression.class);
        return types;
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        String methodName = node.getMethodName();
        if (SET_CONTENT_VIEW.equals(methodName)) {
            List<UElement> args = node.getValueArguments();
            if (args != null && !args.isEmpty()) {
                UElement arg = args.get(0);
                String argText = arg.asSourceString();
                if (argText != null && argText.contains("R.layout.")) {
                    // Extract layout name
                    int idx = argText.lastIndexOf('.');
                    if (idx >= 0) {
                        String layoutName = argText.substring(idx + 1).trim();
                        if (!layoutName.isEmpty() && mCurrentActivity != null) {
                            if (mActivityToLayout == null) {
                                mActivityToLayout = new HashMap<>();
                            }
                            mActivityToLayout.put(mCurrentActivity, layoutName);

                            if (mLayoutToActivity == null) {
                                mLayoutToActivity = new HashMap<>();
                            }
                            List<String> activities = mLayoutToActivity.get(layoutName);
                            if (activities == null) {
                                activities = new ArrayList<>();
                                mLayoutToActivity.put(layoutName, activities);
                            }
                            activities.add(mCurrentActivity);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        // Not needed for this detector's core logic
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mLayoutToBackground == null || mLayoutToBackground.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Location> entry : mLayoutToBackground.entrySet()) {
            String layoutName = entry.getKey();
            Location location = entry.getValue();

            // Find which activities use this layout
            List<String> activities = null;
            if (mLayoutToActivity != null) {
                activities = mLayoutToActivity.get(layoutName);
            }

            if (activities == null || activities.isEmpty()) {
                // No known activity association; skip
                continue;
            }

            for (String activity : activities) {
                // Check if this activity has a theme with non-null background
                String theme = null;
                if (mActivityToTheme != null) {
                    theme = mActivityToTheme.get(activity);
                }

                if (theme == null) {
                    // No explicit theme; assume default theme has a background
                    // Report overdraw
                    context.report(
                            ISSUE,
                            location,
                            "Possible overdraw: Root element sets a background drawable, "
                                    + "but the activity already has a theme background. "
                                    + "Consider using a custom theme with a null background "
                                    + "(`android:windowBackground`) instead.");
                } else {
                    // Check if the theme has a null background
                    Boolean bgNull = null;
                    if (mThemeToBackgroundNull != null) {
                        bgNull = mThemeToBackgroundNull.get(theme);
                        if (bgNull == null) {
                            // Try without prefix
                            String stripped = theme;
                            if (stripped.startsWith(STYLE_RESOURCE_PREFIX)) {
                                stripped = stripped.substring(STYLE_RESOURCE_PREFIX.length());
                            } else if (stripped.startsWith("@style/")) {
                                stripped = stripped.substring("@style/".length());
                            }
                            bgNull = mThemeToBackgroundNull.get(stripped);
                        }
                    }

                    if (bgNull == null || !bgNull) {
                        // Theme has a background (or unknown), report overdraw
                        context.report(
                                ISSUE,
                                location,
                                "Possible overdraw: Root element sets a background drawable, "
                                        + "but the activity's theme also has a background. "
                                        + "Consider using a custom theme with a null background "
                                        + "(`android:windowBackground`) instead.");
                    }
                }
            }
        }
    }

    /**
     * Extracts the layout name from a layout file.
     */
    @Nullable
    private static String getLayoutName(@NonNull File file) {
        String name = file.getName();
        if (name.endsWith(DOT_XML)) {
            return name.substring(0, name.length() - DOT_XML.length());
        }
        return null;
    }
}