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

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
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

public class OverdrawDetector extends LayoutDetector {

    private static final String R_LAYOUT_PREFIX = "R.layout.";
    private static final String SET_CONTENT_VIEW = "setContentView";
    private static final String SET_THEME = "setTheme";

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

    /** Map from layout name to background attribute value */
    private Map<String, String> mLayoutToBackground;

    /** Map from layout name to the XmlContext location */
    private Map<String, Location> mLayoutToLocation;

    /** Map from activity class name to layout name */
    private Map<String, String> mActivityToLayout;

    /** Map from activity class name to theme name */
    private Map<String, String> mActivityToTheme;

    /** Map from style name to parent style name */
    private Map<String, String> mStyleParents;

    /** Set of style names that have a null/transparent background */
    private Set<String> mBlankThemes;

    /** Set of style names that have a non-null background */
    private Set<String> mNonBlankThemes;

    /** The current activity class being analyzed */
    private String mCurrentActivity;

    /** Whether we're in a layout file */
    private boolean mInLayout;

    /** The current layout name being analyzed */
    private String mCurrentLayout;

    public OverdrawDetector() {
    }

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
        return Arrays.asList(TAG_STYLE, TAG_ACTIVITY);
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.Activity");
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>();
        types.add(UCallExpression.class);
        types.add(USimpleNameReferenceExpression.class);
        return types;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        String fileName = file.getName();
        if (fileName.endsWith(DOT_JAVA)) {
            mCurrentActivity = null;
        } else {
            // Check if it's a layout file
            File parent = file.getParentFile();
            if (parent != null) {
                String parentName = parent.getName();
                mInLayout = parentName.startsWith("layout");
                if (mInLayout) {
                    // Extract layout name (without extension)
                    mCurrentLayout = fileName.substring(0, fileName.lastIndexOf('.'));
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (TAG_STYLE.equals(tagName)) {
            // Record style parent relationships and check for windowBackground
            String styleName = element.getAttribute(ATTR_NAME);
            if (styleName != null && !styleName.isEmpty()) {
                String parent = element.getAttribute(ATTR_PARENT);
                if (parent != null && !parent.isEmpty()) {
                    if (mStyleParents == null) {
                        mStyleParents = new HashMap<>();
                    }
                    mStyleParents.put(styleName, parent);
                }

                // Check child items for windowBackground
                NodeList children = element.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node child = children.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        Element item = (Element) child;
                        String name = item.getAttribute(ATTR_NAME);
                        if ("android:windowBackground".equals(name)) {
                            String value = item.getTextContent();
                            if (value != null) {
                                value = value.trim();
                                if (value.equals("@null") || value.equals("@android:color/transparent")) {
                                    if (mBlankThemes == null) {
                                        mBlankThemes = new HashSet<>();
                                    }
                                    mBlankThemes.add(styleName);
                                } else {
                                    if (mNonBlankThemes == null) {
                                        mNonBlankThemes = new HashSet<>();
                                    }
                                    mNonBlankThemes.add(styleName);
                                }
                            }
                        }
                    }
                }
            }
        } else if (TAG_ACTIVITY.equals(tagName)) {
            // Record activity to theme mapping from manifest
            String activityName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
            if (activityName != null && !activityName.isEmpty()
                    && theme != null && !theme.isEmpty()) {
                if (mActivityToTheme == null) {
                    mActivityToTheme = new HashMap<>();
                }
                // Strip @style/ or @android:style/ prefix
                theme = stripStylePrefix(theme);
                mActivityToTheme.put(activityName, theme);
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (ATTR_BACKGROUND.equals(name)) {
            // Only care about root-level backgrounds in layout files
            Element element = attribute.getOwnerElement();
            Node parent = element.getParentNode();
            if (parent == null || parent.getNodeType() != Node.ELEMENT_NODE) {
                // This is the root element
                String background = attribute.getValue();
                if (background != null && !background.isEmpty()) {
                    File file = context.file;
                    String layoutName = file.getName();
                    if (layoutName.contains(".")) {
                        layoutName = layoutName.substring(0, layoutName.lastIndexOf('.'));
                    }
                    if (mLayoutToBackground == null) {
                        mLayoutToBackground = new HashMap<>();
                    }
                    mLayoutToBackground.put(layoutName, background);

                    if (mLayoutToLocation == null) {
                        mLayoutToLocation = new HashMap<>();
                    }
                    mLayoutToLocation.put(layoutName, context.getLocation(attribute));
                }
            }
        } else if (ATTR_THEME.equals(name)) {
            // Activity theme set in manifest or layout
            // Handle in visitElement for TAG_ACTIVITY
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Record the current activity class name
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            mCurrentActivity = qualifiedName;
        }
    }

    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression expression) {
        // Look for R.layout.xxx references
        String identifier = expression.getIdentifier();
        if (identifier != null && mCurrentActivity != null) {
            // Check if this is a layout reference by looking at the parent
            UElement parent = expression.getUastParent();
            if (parent instanceof USimpleNameReferenceExpression) {
                String parentId = ((USimpleNameReferenceExpression) parent).getIdentifier();
                if ("layout".equals(parentId)) {
                    if (mActivityToLayout == null) {
                        mActivityToLayout = new HashMap<>();
                    }
                    mActivityToLayout.put(mCurrentActivity, identifier);
                }
            }
        }
    }

    public void visitCallExpression(
            @NonNull JavaContext context,
            @NonNull UCallExpression expression) {
        String methodName = expression.getMethodName();
        if (SET_CONTENT_VIEW.equals(methodName)) {
            // Try to extract the layout name from setContentView(R.layout.xxx)
            List<UElement> args = new ArrayList<>(expression.getValueArguments());
            if (!args.isEmpty()) {
                UElement arg = args.get(0);
                String argText = arg.asSourceString();
                if (argText != null && argText.startsWith(R_LAYOUT_PREFIX)) {
                    String layoutName = argText.substring(R_LAYOUT_PREFIX.length());
                    if (mCurrentActivity != null) {
                        if (mActivityToLayout == null) {
                            mActivityToLayout = new HashMap<>();
                        }
                        mActivityToLayout.put(mCurrentActivity, layoutName);
                    }
                }
            }
        } else if (SET_THEME.equals(methodName)) {
            // setTheme(R.style.xxx) call
            List<UElement> args = new ArrayList<>(expression.getValueArguments());
            if (!args.isEmpty()) {
                UElement arg = args.get(0);
                String argText = arg.asSourceString();
                if (argText != null) {
                    String themeName = null;
                    if (argText.startsWith("R.style.")) {
                        themeName = argText.substring("R.style.".length());
                    } else if (argText.startsWith("android.R.style.")) {
                        themeName = argText.substring("android.R.style.".length());
                    }
                    if (themeName != null && mCurrentActivity != null) {
                        if (mActivityToTheme == null) {
                            mActivityToTheme = new HashMap<>();
                        }
                        mActivityToTheme.put(mCurrentActivity, themeName);
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mLayoutToBackground == null || mLayoutToBackground.isEmpty()) {
            return;
        }

        // For each layout with a background, check if its associated activity
        // has a theme with a non-null window background
        for (Map.Entry<String, String> entry : mLayoutToBackground.entrySet()) {
            String layout = entry.getKey();
            String background = entry.getValue();

            // Find which activity uses this layout
            String activity = getActivityForLayout(layout);
            if (activity == null) {
                // No activity found - can't determine theme, skip
                continue;
            }

            // Find the theme for this activity
            String theme = getThemeForActivity(activity);
            if (theme == null) {
                // No theme info - skip
                continue;
            }

            // Check if the theme has a blank/null window background
            if (isBlankTheme(theme)) {
                // Theme has null background - no overdraw issue
                continue;
            }

            // The theme has a background and the layout also has a background - potential overdraw
            Location location = mLayoutToLocation != null ? mLayoutToLocation.get(layout) : null;
            if (location == null) {
                continue;
            }

            context.report(
                    ISSUE,
                    location,
                    String.format(
                            "Possible overdraw: Root element sets a background `%1$s` with "
                                    + "a theme that also sets a background; "
                                    + "should your theme set `android:windowBackground` to null?",
                            background));
        }
    }

    @Nullable
    private String getActivityForLayout(@NonNull String layout) {
        if (mActivityToLayout == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : mActivityToLayout.entrySet()) {
            if (layout.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Nullable
    private String getThemeForActivity(@NonNull String activity) {
        if (mActivityToTheme == null) {
            return null;
        }
        return mActivityToTheme.get(activity);
    }

    private boolean isBlankTheme(@NonNull String theme) {
        if (mBlankThemes != null && mBlankThemes.contains(theme)) {
            return true;
        }

        // Check parent themes
        if (mStyleParents != null) {
            Set<String> visited = new HashSet<>();
            String current = theme;
            while (current != null && !visited.contains(current)) {
                visited.add(current);
                if (mBlankThemes != null && mBlankThemes.contains(current)) {
                    return true;
                }
                if (mNonBlankThemes != null && mNonBlankThemes.contains(current)) {
                    return false;
                }
                String parent = mStyleParents.get(current);
                if (parent != null) {
                    parent = stripStylePrefix(parent);
                }
                current = parent;
            }
        }

        // Check if it's an Android built-in theme that we know has a null background
        if (theme.contains("NoBackground") || theme.contains("Wallpaper")) {
            return true;
        }

        return false;
    }

    @NonNull
    private static String stripStylePrefix(@NonNull String style) {
        if (style.startsWith(STYLE_RESOURCE_PREFIX)) {
            return style.substring(STYLE_RESOURCE_PREFIX.length());
        } else if (style.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
            return style.substring(ANDROID_STYLE_RESOURCE_PREFIX.length());
        }
        return style;
    }
}