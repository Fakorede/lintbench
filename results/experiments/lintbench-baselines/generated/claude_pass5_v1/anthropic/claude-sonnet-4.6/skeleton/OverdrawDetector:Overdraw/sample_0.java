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
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiMethod;
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
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class OverdrawDetector extends LayoutDetector {

    private static final String SET_CONTENT_VIEW = "setContentView";
    private static final String GET_WINDOW = "getWindow";
    private static final String SET_BACKGROUND_DRAWABLE = "setBackgroundDrawable";
    private static final String SET_BACKGROUND = "setBackground";

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
    private Map<String, String> mLayoutsWithBackgrounds;

    /** Map from activity class name to layout name set via setContentView */
    private Map<String, List<String>> mActivityToLayouts;

    /** Map from activity class name to theme name */
    private Map<String, String> mActivityThemes;

    /** Map from style name to parent style name */
    private Map<String, String> mStyleParents;

    /** Set of style names that have a null/no background */
    private Set<String> mBlankThemes;

    /** The current activity being analyzed */
    private String mCurrentActivity;

    /** Location for layouts that need reporting */
    private Map<String, Location> mLayoutLocations;

    /** Whether we're currently inside a setContentView call argument */
    private boolean mInSetContentView;

    /** Current layout resource name being visited */
    private String mCurrentLayoutName;

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
        return Arrays.asList(TAG_STYLE, TAG_ACTIVITY);
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.Activity");
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        String name = file.getName();
        if (name.endsWith(DOT_JAVA)) {
            // Reset current activity tracking
            mCurrentActivity = null;
        } else {
            // For layout files, record the layout name
            String fileName = file.getName();
            int dot = fileName.lastIndexOf('.');
            if (dot != -1) {
                mCurrentLayoutName = fileName.substring(0, dot);
            } else {
                mCurrentLayoutName = fileName;
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_STYLE.equals(tag)) {
            // Handled via visitAttribute for the parent attribute and child items
            checkStyleElement(context, element);
        } else if (TAG_ACTIVITY.equals(tag)) {
            checkActivityElement(context, element);
        }
    }

    private void checkStyleElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if this style sets windowBackground to null/@null
        String styleName = element.getAttribute(ATTR_NAME);
        if (styleName == null || styleName.isEmpty()) {
            return;
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element item = (Element) child;
                String itemName = item.getAttribute(ATTR_NAME);
                if ("android:windowBackground".equals(itemName)) {
                    String value = item.getTextContent().trim();
                    if ("@null".equals(value) || "null".equals(value)) {
                        if (mBlankThemes == null) {
                            mBlankThemes = new HashSet<>();
                        }
                        mBlankThemes.add(styleName);
                    }
                }
            }
        }

        // Record parent
        String parent = element.getAttribute(ATTR_PARENT);
        if (parent != null && !parent.isEmpty()) {
            if (mStyleParents == null) {
                mStyleParents = new HashMap<>();
            }
            mStyleParents.put(styleName, parent);
        } else {
            // Check for implicit parent via dot notation
            int lastDot = styleName.lastIndexOf('.');
            if (lastDot != -1) {
                String implicitParent = styleName.substring(0, lastDot);
                if (mStyleParents == null) {
                    mStyleParents = new HashMap<>();
                }
                mStyleParents.put(styleName, implicitParent);
            }
        }
    }

    private void checkActivityElement(@NonNull XmlContext context, @NonNull Element element) {
        // Get activity name and theme from AndroidManifest
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);

        if (name != null && !name.isEmpty() && theme != null && !theme.isEmpty()) {
            if (mActivityThemes == null) {
                mActivityThemes = new HashMap<>();
            }
            // Normalize theme name
            String normalizedTheme = normalizeThemeName(theme);
            mActivityThemes.put(name, normalizedTheme);
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
        }

        ResourceFolderType folderType = context.getResourceFolderType();

        if (ATTR_BACKGROUND.equals(name) && folderType == ResourceFolderType.LAYOUT) {
            // Check if this is the root element
            Element element = attribute.getOwnerElement();
            if (isRootElement(element)) {
                String value = attribute.getValue();
                if (value != null && !value.isEmpty() && !"@null".equals(value)) {
                    // Record this layout as having a background
                    String layoutName = mCurrentLayoutName;
                    if (layoutName != null) {
                        if (mLayoutsWithBackgrounds == null) {
                            mLayoutsWithBackgrounds = new HashMap<>();
                        }
                        mLayoutsWithBackgrounds.put(layoutName, value);
                        if (mLayoutLocations == null) {
                            mLayoutLocations = new HashMap<>();
                        }
                        mLayoutLocations.put(layoutName, context.getLocation(attribute));
                    }
                }
            }
        } else if (ATTR_PARENT.equals(name) && folderType == ResourceFolderType.VALUES) {
            // Record style parent relationships
            Element element = attribute.getOwnerElement();
            if (TAG_STYLE.equals(element.getTagName())) {
                String styleName = element.getAttribute(ATTR_NAME);
                String parentValue = attribute.getValue();
                if (styleName != null && !styleName.isEmpty()
                        && parentValue != null && !parentValue.isEmpty()) {
                    if (mStyleParents == null) {
                        mStyleParents = new HashMap<>();
                    }
                    mStyleParents.put(styleName, normalizeThemeName(parentValue));
                }
            }
        } else if (ATTR_THEME.equals(name) && folderType == ResourceFolderType.VALUES) {
            // Handled in visitElement for activities
        }
    }

    private boolean isRootElement(@NonNull Element element) {
        Node parent = element.getParentNode();
        return parent == null || parent.getNodeType() == Node.DOCUMENT_NODE;
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Track the current activity class
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            mCurrentActivity = qualifiedName;
        }
    }

    public void visitCallExpression(@NonNull JavaContext context,
            @NonNull UCallExpression expression) {
        String methodName = expression.getMethodName();
        if (SET_CONTENT_VIEW.equals(methodName)) {
            List<UExpression> args = expression.getValueArguments();
            if (!args.isEmpty()) {
                UExpression arg = args.get(0);
                String argText = arg.asSourceString();
                // Try to extract layout name from R.layout.xxx
                String layoutName = extractLayoutName(argText);
                if (layoutName != null && mCurrentActivity != null) {
                    if (mActivityToLayouts == null) {
                        mActivityToLayouts = new HashMap<>();
                    }
                    List<String> layouts = mActivityToLayouts.get(mCurrentActivity);
                    if (layouts == null) {
                        layouts = new ArrayList<>();
                        mActivityToLayouts.put(mCurrentActivity, layouts);
                    }
                    if (!layouts.contains(layoutName)) {
                        layouts.add(layoutName);
                    }
                }
            }
        }
    }

    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression expression) {
        // Used to track R.layout references inside setContentView
        // This is handled in visitCallExpression
    }

    @Nullable
    private String extractLayoutName(@NonNull String expression) {
        // Handle R.layout.foo or similar patterns
        int layoutIdx = expression.indexOf("R.layout.");
        if (layoutIdx != -1) {
            String rest = expression.substring(layoutIdx + "R.layout.".length());
            // Extract identifier
            StringBuilder sb = new StringBuilder();
            for (char c : rest.toCharArray()) {
                if (Character.isLetterOrDigit(c) || c == '_') {
                    sb.append(c);
                } else {
                    break;
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }
        return null;
    }

    @NonNull
    private String normalizeThemeName(@NonNull String theme) {
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

    private boolean isBlankTheme(@NonNull String themeName) {
        if (mBlankThemes != null && mBlankThemes.contains(themeName)) {
            return true;
        }
        // Check parent chain
        if (mStyleParents != null) {
            Set<String> visited = new HashSet<>();
            String current = themeName;
            while (current != null && !visited.contains(current)) {
                visited.add(current);
                if (mBlankThemes != null && mBlankThemes.contains(current)) {
                    return true;
                }
                current = mStyleParents.get(current);
            }
        }
        return false;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mLayoutsWithBackgrounds == null || mLayoutsWithBackgrounds.isEmpty()) {
            return;
        }

        // For each layout with a background, check if the associated activity
        // uses a theme that also has a background
        for (Map.Entry<String, String> entry : mLayoutsWithBackgrounds.entrySet()) {
            String layoutName = entry.getKey();
            String background = entry.getValue();

            // Find activities that use this layout
            List<String> activities = getActivitiesForLayout(layoutName);

            if (activities == null || activities.isEmpty()) {
                // No activity found; still report potential overdraw if we can
                // Skip - we can't determine the theme without an activity
                continue;
            }

            for (String activity : activities) {
                String theme = getThemeForActivity(activity);
                if (theme == null) {
                    // Unknown theme - skip
                    continue;
                }

                // Check if the theme has a blank/null background
                if (!isBlankTheme(theme)) {
                    // The theme has a background and the layout also has a background
                    // This is overdraw
                    Location location = mLayoutLocations != null
                            ? mLayoutLocations.get(layoutName)
                            : null;
                    if (location != null) {
                        context.report(
                                ISSUE,
                                location,
                                "Possible overdraw: Root element sets a background that "
                                        + "also appears to be set by the theme. If the theme "
                                        + "background is intentional, consider using a "
                                        + "transparent theme or setting the theme's "
                                        + "`windowBackground` to `@null`.");
                    }
                }
            }
        }
    }

    @Nullable
    private List<String> getActivitiesForLayout(@NonNull String layoutName) {
        if (mActivityToLayouts == null) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : mActivityToLayouts.entrySet()) {
            if (entry.getValue().contains(layoutName)) {
                result.add(entry.getKey());
            }
        }
        return result.isEmpty() ? null : result;
    }

    @Nullable
    private String getThemeForActivity(@NonNull String activityName) {
        if (mActivityThemes == null) {
            return null;
        }
        // Try exact match first
        String theme = mActivityThemes.get(activityName);
        if (theme != null) {
            return theme;
        }
        // Try simple name match
        for (Map.Entry<String, String> entry : mActivityThemes.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith(activityName) || activityName.endsWith(key)) {
                return entry.getValue();
            }
            // Simple class name match
            int lastDot = key.lastIndexOf('.');
            String simpleName = lastDot != -1 ? key.substring(lastDot + 1) : key;
            int lastDot2 = activityName.lastIndexOf('.');
            String simpleName2 = lastDot2 != -1 ? activityName.substring(lastDot2 + 1) : activityName;
            if (simpleName.equals(simpleName2)) {
                return entry.getValue();
            }
        }
        return null;
    }
}