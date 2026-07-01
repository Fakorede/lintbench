package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.DOT_JAVA;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
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
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
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
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

public class OverdrawDetector extends LayoutDetector {

    private static final String SET_CONTENT_VIEW = "setContentView";
    private static final String R_LAYOUT_PREFIX = "R.layout.";
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
                            + "this is called \"overdraw\".\n\n"
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

    /** Map from layout name to background attribute value */
    private Map<String, String> mLayoutsWithBackgrounds;

    /** Map from layout name to location of background attribute */
    private Map<String, Location> mBackgroundLocations;

    /** Map from activity class name to layout name */
    private Map<String, String> mActivityToLayout;

    /** Map from activity class name to theme */
    private Map<String, String> mActivityToTheme;

    /** Map from style name to parent style */
    private Map<String, String> mStyleParents;

    /** Map from style name to whether it has a null/transparent window background */
    private Map<String, Boolean> mStylesWithNullBackground;

    /** The application theme */
    private String mAppTheme;

    /** Current activity being analyzed */
    private String mCurrentActivity;

    /** Map from layout name to the activity that uses it (for error reporting) */
    private Map<String, String> mLayoutToActivity;

    public OverdrawDetector() {}

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_BACKGROUND, ATTR_THEME, ATTR_NAME, ATTR_PARENT);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_STYLE, TAG_ITEM, TAG_ACTIVITY, TAG_APPLICATION);
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.Activity");
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>();
        types.add(USimpleNameReferenceExpression.class);
        types.add(UCallExpression.class);
        return types;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        String name = file.getName();
        if (name.endsWith(DOT_JAVA)) {
            mCurrentActivity = null;
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String attributeName = attribute.getLocalName();
        if (attributeName == null) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();

        if (folderType == ResourceFolderType.LAYOUT) {
            if (ATTR_BACKGROUND.equals(attributeName)) {
                // Check if this is a root element
                Element element = attribute.getOwnerElement();
                if (element.getParentNode() == element.getOwnerDocument().getDocumentElement()
                        || element.getParentNode().getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                    // It's the root element
                    String background = attribute.getValue();
                    if (background != null && !background.isEmpty()) {
                        String layoutName = getLayoutName(context);
                        if (mLayoutsWithBackgrounds == null) {
                            mLayoutsWithBackgrounds = new HashMap<>();
                            mBackgroundLocations = new HashMap<>();
                        }
                        mLayoutsWithBackgrounds.put(layoutName, background);
                        mBackgroundLocations.put(layoutName, context.getLocation(attribute));
                    }
                }
            }
        } else if (folderType == ResourceFolderType.VALUES) {
            // Handle theme/style attributes in manifest (handled via visitElement)
        }

        // Handle manifest attributes
        if (ATTR_THEME.equals(attributeName)) {
            String parentTag = attribute.getOwnerElement().getTagName();
            String theme = attribute.getValue();
            if (TAG_ACTIVITY.equals(parentTag)) {
                // Get activity name
                String activityName = attribute.getOwnerElement().getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (activityName != null && !activityName.isEmpty()) {
                    if (mActivityToTheme == null) {
                        mActivityToTheme = new HashMap<>();
                    }
                    mActivityToTheme.put(resolveActivityName(activityName, context), theme);
                }
            } else if (TAG_APPLICATION.equals(parentTag)) {
                mAppTheme = theme;
            }
        } else if (ATTR_NAME.equals(attributeName)) {
            // handled in visitElement
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();

        if (folderType == ResourceFolderType.VALUES) {
            String tagName = element.getTagName();
            if (TAG_STYLE.equals(tagName)) {
                String styleName = element.getAttribute(ATTR_NAME);
                String parentStyle = element.getAttribute(ATTR_PARENT);

                if (styleName != null && !styleName.isEmpty()) {
                    if (parentStyle != null && !parentStyle.isEmpty()) {
                        if (mStyleParents == null) {
                            mStyleParents = new HashMap<>();
                        }
                        mStyleParents.put(styleName, parentStyle);
                    }

                    // Check if this style sets windowBackground to null/@null
                    NodeList items = element.getElementsByTagName(TAG_ITEM);
                    for (int i = 0; i < items.getLength(); i++) {
                        Element item = (Element) items.item(i);
                        String name = item.getAttribute(ATTR_NAME);
                        if (WINDOW_BACKGROUND.equals(name)
                                || ("android:" + WINDOW_BACKGROUND).equals(name)) {
                            String value = item.getTextContent();
                            if (value != null) {
                                value = value.trim();
                                boolean isNull =
                                        "@null".equals(value)
                                                || "null".equals(value)
                                                || "@android:color/transparent".equals(value);
                                if (mStylesWithNullBackground == null) {
                                    mStylesWithNullBackground = new HashMap<>();
                                }
                                mStylesWithNullBackground.put(styleName, isNull);
                            }
                        }
                    }
                }
            }
        }

        // Handle manifest
        String tagName = element.getTagName();
        if (TAG_ACTIVITY.equals(tagName)) {
            String activityName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
            if (activityName != null && !activityName.isEmpty()) {
                String resolved = resolveActivityName(activityName, context);
                if (theme != null && !theme.isEmpty()) {
                    if (mActivityToTheme == null) {
                        mActivityToTheme = new HashMap<>();
                    }
                    mActivityToTheme.put(resolved, theme);
                }
            }
        } else if (TAG_APPLICATION.equals(tagName)) {
            String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
            if (theme != null && !theme.isEmpty()) {
                mAppTheme = theme;
            }
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        mCurrentActivity = declaration.getQualifiedName();
    }

    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        // Track R.layout.xxx references near setContentView calls
        // This is handled in visitCallExpression
    }

    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression node) {
        String methodName = node.getMethodName();
        if (SET_CONTENT_VIEW.equals(methodName)) {
            List<UElement> args = (List<UElement>) node.getValueArguments();
            if (args != null && !args.isEmpty()) {
                String arg = args.get(0).asSourceString();
                if (arg != null && arg.startsWith(R_LAYOUT_PREFIX)) {
                    String layoutName = arg.substring(R_LAYOUT_PREFIX.length());
                    if (mCurrentActivity != null) {
                        if (mActivityToLayout == null) {
                            mActivityToLayout = new HashMap<>();
                        }
                        mActivityToLayout.put(mCurrentActivity, layoutName);
                        if (mLayoutToActivity == null) {
                            mLayoutToActivity = new HashMap<>();
                        }
                        mLayoutToActivity.put(layoutName, mCurrentActivity);
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mLayoutsWithBackgrounds == null || mLayoutsWithBackgrounds.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : mLayoutsWithBackgrounds.entrySet()) {
            String layoutName = entry.getKey();

            // Find the activity using this layout
            String activityName = null;
            if (mLayoutToActivity != null) {
                activityName = mLayoutToActivity.get(layoutName);
            }

            // Find the theme for this activity
            String theme = null;
            if (activityName != null && mActivityToTheme != null) {
                theme = mActivityToTheme.get(activityName);
            }
            if (theme == null) {
                theme = mAppTheme;
            }

            if (theme == null) {
                continue;
            }

            // Check if the theme has a null/transparent window background
            String themeName = stripThemePrefix(theme);
            if (hasNullWindowBackground(themeName)) {
                // Theme already has null background, no overdraw
                continue;
            }

            // Report overdraw
            Location location = mBackgroundLocations != null
                    ? mBackgroundLocations.get(layoutName)
                    : null;

            String message =
                    String.format(
                            "Possible overdraw: Root element sets a background drawable "
                                    + "(`%1$s`) while the activity theme also sets a "
                                    + "background (`%2$s`); the theme background will be "
                                    + "covered by the layout background. See issue "
                                    + "explanation for suggested workarounds.",
                            entry.getValue(),
                            theme);

            if (location != null) {
                context.report(ISSUE, location, message);
            }
        }
    }

    private boolean hasNullWindowBackground(@Nullable String styleName) {
        if (styleName == null) {
            return false;
        }

        // Check if this style explicitly sets windowBackground to null
        if (mStylesWithNullBackground != null) {
            Boolean hasNull = mStylesWithNullBackground.get(styleName);
            if (hasNull != null) {
                return hasNull;
            }
        }

        // Check parent style
        if (mStyleParents != null) {
            String parent = mStyleParents.get(styleName);
            if (parent != null) {
                String parentName = stripThemePrefix(parent);
                if (!parentName.equals(styleName)) {
                    return hasNullWindowBackground(parentName);
                }
            }
        }

        // Check implicit parent (dot notation)
        int lastDot = styleName.lastIndexOf('.');
        if (lastDot > 0) {
            String implicitParent = styleName.substring(0, lastDot);
            return hasNullWindowBackground(implicitParent);
        }

        return false;
    }

    private String stripThemePrefix(@NonNull String theme) {
        if (theme.startsWith(STYLE_RESOURCE_PREFIX)) {
            return theme.substring(STYLE_RESOURCE_PREFIX.length());
        } else if (theme.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
            return theme.substring(ANDROID_STYLE_RESOURCE_PREFIX.length());
        } else if (theme.startsWith("@style/")) {
            return theme.substring("@style/".length());
        } else if (theme.startsWith("@android:style/")) {
            return theme.substring("@android:style/".length());
        }
        return theme;
    }

    private String getLayoutName(@NonNull XmlContext context) {
        String fileName = context.file.getName();
        int dot = fileName.lastIndexOf('.');
        if (dot != -1) {
            return fileName.substring(0, dot);
        }
        return fileName;
    }

    private String resolveActivityName(@NonNull String activityName, @NonNull XmlContext context) {
        if (activityName.startsWith(".")) {
            // Relative name - would need package from manifest, just return as-is
            return activityName;
        }
        return activityName;
    }
}