package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.DOT_JAVA;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_STYLE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OverdrawDetector extends LayoutDetector {

    private static final String WINDOW_BACKGROUND = "windowBackground";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    OverdrawDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Overdraw: Painting regions more than once",
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
                    IMPLEMENTATION);

    /**
     * Map from layout name to the background attribute value set on the root element in that
     * layout.
     */
    private Map<String, String> mLayoutToBackground;

    /**
     * Map from activity class name to the layout set for that activity (by setContentView calls).
     */
    private Map<String, String> mActivityToLayout;

    /**
     * Map from activity class name to the theme set for that activity (in the manifest or by
     * setTheme calls).
     */
    private Map<String, String> mActivityToTheme;

    /**
     * Map from style name to parent style name.
     */
    private Map<String, String> mStyleParents;

    /**
     * Map from style name to whether it sets windowBackground to null.
     */
    private Map<String, Boolean> mStyleNullBackground;

    /**
     * The current activity being analyzed in Java scanning phase.
     */
    private String mCurrentActivity;

    /**
     * Location for the background attribute in the layout.
     */
    private Map<String, Location> mLayoutToBackgroundLocation;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_BACKGROUND, ATTR_THEME);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_STYLE);
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.Activity");
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(USimpleNameReferenceExpression.class, UCallExpression.class);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // Reset current layout background tracking
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (ATTR_BACKGROUND.equals(name)) {
            // Check if this is a root element in a layout file
            if (context.getResourceFolderType() == ResourceFolderType.LAYOUT) {
                Element element = attribute.getOwnerElement();
                Node parent = element.getParentNode();
                if (parent == null || parent.getNodeType() == Node.DOCUMENT_NODE) {
                    // This is the root element
                    String background = attribute.getValue();
                    if (background != null && !background.isEmpty()) {
                        String layoutName = getLayoutName(context.file);
                        if (mLayoutToBackground == null) {
                            mLayoutToBackground = new HashMap<>();
                        }
                        if (mLayoutToBackgroundLocation == null) {
                            mLayoutToBackgroundLocation = new HashMap<>();
                        }
                        mLayoutToBackground.put(layoutName, background);
                        mLayoutToBackgroundLocation.put(layoutName, context.getLocation(attribute));
                    }
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (TAG_STYLE.equals(element.getTagName())) {
            String styleName = element.getAttribute(ATTR_NAME);
            if (styleName == null || styleName.isEmpty()) {
                return;
            }
            String parent = element.getAttribute(ATTR_PARENT);
            if (parent != null && !parent.isEmpty()) {
                if (mStyleParents == null) {
                    mStyleParents = new HashMap<>();
                }
                mStyleParents.put(styleName, parent);
            }

            // Check if this style sets windowBackground to @null
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element item = (Element) child;
                    if (TAG_ITEM.equals(item.getTagName())) {
                        String itemName = item.getAttribute(ATTR_NAME);
                        if (WINDOW_BACKGROUND.equals(itemName) || 
                                ("android:" + WINDOW_BACKGROUND).equals(itemName)) {
                            String value = getTextContent(item);
                            if (value != null) {
                                value = value.trim();
                                boolean isNull = "@null".equals(value);
                                if (mStyleNullBackground == null) {
                                    mStyleNullBackground = new HashMap<>();
                                }
                                mStyleNullBackground.put(styleName, isNull);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Track the current activity class
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            mCurrentActivity = qualifiedName;
        }
    }

    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression expression) {
        // Handle R.layout.xxx references to map activities to layouts
        String name = expression.getIdentifier();
        if (name != null && mCurrentActivity != null) {
            // We'll handle this in visitCallExpression
        }
    }

    public void visitCallExpression(
            @NonNull JavaContext context,
            @NonNull UCallExpression expression) {
        if (mCurrentActivity == null) {
            return;
        }

        String methodName = expression.getMethodName();
        if ("setContentView".equals(methodName)) {
            List<org.jetbrains.uast.UExpression> args = expression.getValueArguments();
            if (!args.isEmpty()) {
                org.jetbrains.uast.UExpression arg = args.get(0);
                String argText = arg.asSourceString();
                // Try to extract layout name from R.layout.xxx
                if (argText != null && argText.contains("R.layout.")) {
                    String layoutName = argText.substring(argText.lastIndexOf('.') + 1);
                    if (mActivityToLayout == null) {
                        mActivityToLayout = new HashMap<>();
                    }
                    mActivityToLayout.put(mCurrentActivity, layoutName);
                }
            }
        } else if ("setTheme".equals(methodName)) {
            List<org.jetbrains.uast.UExpression> args = expression.getValueArguments();
            if (!args.isEmpty()) {
                org.jetbrains.uast.UExpression arg = args.get(0);
                String argText = arg.asSourceString();
                if (argText != null && argText.contains("R.style.")) {
                    String themeName = argText.substring(argText.lastIndexOf('.') + 1);
                    if (mActivityToTheme == null) {
                        mActivityToTheme = new HashMap<>();
                    }
                    mActivityToTheme.put(mCurrentActivity, themeName);
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mLayoutToBackground == null || mLayoutToBackground.isEmpty()) {
            return;
        }

        if (mActivityToLayout == null || mActivityToLayout.isEmpty()) {
            return;
        }

        // For each activity that has a layout with a background, check if the theme
        // has a non-null windowBackground
        for (Map.Entry<String, String> entry : mActivityToLayout.entrySet()) {
            String activity = entry.getKey();
            String layout = entry.getValue();

            if (!mLayoutToBackground.containsKey(layout)) {
                continue;
            }

            // Check if the activity has a theme with a non-null windowBackground
            String theme = mActivityToTheme != null ? mActivityToTheme.get(activity) : null;

            if (theme != null) {
                // Check if this theme sets windowBackground to null
                if (isNullBackground(theme)) {
                    // Theme has null background, no overdraw issue
                    continue;
                }
            }

            // Report the issue
            Location location = mLayoutToBackgroundLocation != null
                    ? mLayoutToBackgroundLocation.get(layout)
                    : null;

            if (location != null) {
                String background = mLayoutToBackground.get(layout);
                context.report(
                        ISSUE,
                        location,
                        String.format(
                                "Possible overdraw: Root element sets a background `%1$s` with "
                                        + "a theme that also sets `windowBackground`. "
                                        + "If the theme background is intentionally invisible, "
                                        + "set `windowBackground` to `@null`",
                                background));
            }
        }
    }

    private boolean isNullBackground(String styleName) {
        if (mStyleNullBackground == null) {
            return false;
        }
        // Normalize style name
        styleName = normalizeStyleName(styleName);

        // Check visited styles to avoid infinite loops
        List<String> visited = new ArrayList<>();
        return isNullBackgroundRecursive(styleName, visited);
    }

    private boolean isNullBackgroundRecursive(String styleName, List<String> visited) {
        if (styleName == null || visited.contains(styleName)) {
            return false;
        }
        visited.add(styleName);

        if (mStyleNullBackground != null) {
            Boolean nullBg = mStyleNullBackground.get(styleName);
            if (nullBg != null) {
                return nullBg;
            }
        }

        // Check parent
        if (mStyleParents != null) {
            String parent = mStyleParents.get(styleName);
            if (parent != null) {
                parent = normalizeStyleName(parent);
                return isNullBackgroundRecursive(parent, visited);
            }
        }

        return false;
    }

    private static String normalizeStyleName(String name) {
        if (name == null) {
            return null;
        }
        if (name.startsWith(STYLE_RESOURCE_PREFIX)) {
            name = name.substring(STYLE_RESOURCE_PREFIX.length());
        } else if (name.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
            name = name.substring(ANDROID_STYLE_RESOURCE_PREFIX.length());
        }
        // Replace dots with underscores for style names like AppTheme.NoActionBar
        return name;
    }

    private static String getLayoutName(File file) {
        String name = file.getName();
        if (name.endsWith(DOT_JAVA)) {
            name = name.substring(0, name.length() - DOT_JAVA.length());
        } else if (name.endsWith(".xml")) {
            name = name.substring(0, name.length() - ".xml".length());
        }
        return name;
    }

    @Nullable
    private static String getTextContent(@NonNull Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                return child.getNodeValue();
            }
        }
        return null;
    }
}