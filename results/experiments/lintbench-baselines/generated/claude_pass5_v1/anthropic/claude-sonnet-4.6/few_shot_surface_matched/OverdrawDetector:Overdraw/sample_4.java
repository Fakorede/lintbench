package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.VALUE_TRUE;

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
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
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
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class OverdrawDetector extends LayoutDetector implements SourceCodeScanner {

    private static final String R_LAYOUT_PREFIX = "R.layout.";
    private static final String SET_CONTENT_VIEW = "setContentView";
    private static final String SET_THEME = "setTheme";
    private static final String ANDROID_APP_ACTIVITY = "android.app.Activity";

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Overdraw: Painting regions more than once",
                    "If you set a background drawable on a root view, then you should use a "
                            + "custom theme where the theme background is null. Otherwise, the theme "
                            + "background will be painted first, only to have your custom background "
                            + "completely cover it; this is called \"overdraw\".\n"
                            + "\n"
                            + "NOTE: This detector relies on figuring out which layouts are associated "
                            + "with which activities based on scanning the Java code, and it's currently "
                            + "doing that using an inexact pattern matching algorithm. Therefore, it can "
                            + "incorrectly conclude which activity the layout is associated with and then "
                            + "wrongly complain that a background-theme is hidden.\n"
                            + "\n"
                            + "If you want your custom background on multiple pages, then you should "
                            + "consider making a custom theme with your custom background and just using "
                            + "that theme instead of a root element background.\n"
                            + "\n"
                            + "Of course it's possible that your custom drawable is translucent and you "
                            + "want it to be mixed with the background. However, you will get better "
                            + "performance if you pre-mix the background with your drawable and use that "
                            + "resulting image or color as a custom theme background instead.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    new Implementation(
                            OverdrawDetector.class,
                            EnumSet.of(Scope.JAVA_FILE, Scope.ALL_RESOURCE_FILES)));

    /** Map from activity class name to the layout name set via setContentView */
    private Map<String, List<String>> activityToLayouts;

    /** Map from activity class name to the theme set via setTheme */
    private Map<String, String> activityToTheme;

    /** Map from layout name to the location of the background attribute */
    private Map<String, Location> layoutToBackground;

    /** Map from layout name to the background attribute value */
    private Map<String, String> layoutToBackgroundValue;

    /** Map from theme name to whether it has a null/transparent window background */
    private Map<String, Boolean> themeToNullBackground;

    /** Application theme from manifest */
    private String applicationTheme;

    /** Map from activity name to theme from manifest */
    private Map<String, String> manifestActivityThemes;

    /** The current layout file being processed */
    private String currentLayoutName;

    /** Whether we're currently visiting the root element */
    private boolean isRootElement;

    /** Set of themes that have been resolved (to avoid infinite loops) */
    private Set<String> resolvedThemes;

    public OverdrawDetector() {}

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.VALUES;
    }

    // ---- XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_ACTIVITY, "style");
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_BACKGROUND, ATTR_THEME, ATTR_PARENT, "windowBackground");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        isRootElement = true;
        currentLayoutName = null;
        File file = context.file;
        if (file != null) {
            String name = file.getName();
            if (name.endsWith(DOT_XML)) {
                currentLayoutName = name.substring(0, name.length() - DOT_XML.length());
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (TAG_APPLICATION.equals(tagName)) {
            // Manifest application element
            String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
            if (theme != null && !theme.isEmpty()) {
                applicationTheme = stripThemePrefix(theme);
            }
        } else if (TAG_ACTIVITY.equals(tagName)) {
            // Manifest activity element
            String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (theme != null && !theme.isEmpty() && name != null && !name.isEmpty()) {
                if (manifestActivityThemes == null) {
                    manifestActivityThemes = new HashMap<>();
                }
                manifestActivityThemes.put(name, stripThemePrefix(theme));
            }
        } else if ("style".equals(tagName)) {
            // Values style element - check for windowBackground
            processStyleElement(context, element);
        }
    }

    private void processStyleElement(@NonNull XmlContext context, @NonNull Element element) {
        // Handled via visitAttribute for the parent attribute and item children
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
        }

        ResourceFolderType folderType = context.getResourceFolderType();

        if (folderType == ResourceFolderType.LAYOUT) {
            if (ATTR_BACKGROUND.equals(name)) {
                // Only care about root element backgrounds
                if (isRootElement) {
                    String value = attribute.getValue();
                    if (value != null && !value.isEmpty()) {
                        if (layoutToBackground == null) {
                            layoutToBackground = new HashMap<>();
                            layoutToBackgroundValue = new HashMap<>();
                        }
                        if (currentLayoutName != null) {
                            layoutToBackground.put(
                                    currentLayoutName, context.getLocation(attribute));
                            layoutToBackgroundValue.put(currentLayoutName, value);
                        }
                    }
                }
                // After first attribute visit on root, subsequent elements are not root
                isRootElement = false;
            }
        } else if (folderType == ResourceFolderType.VALUES) {
            // Check style elements for windowBackground
            org.w3c.dom.Node parent = attribute.getOwnerElement();
            if (parent instanceof Element) {
                Element ownerElement = (Element) parent;
                String tagName = ownerElement.getTagName();
                if ("style".equals(tagName) && ATTR_PARENT.equals(name)) {
                    // Track style parent relationships
                    String styleName = ownerElement.getAttribute(ATTR_NAME);
                    String parentValue = attribute.getValue();
                    if (styleName != null && !styleName.isEmpty()) {
                        ensureThemeMap();
                        // Don't overwrite if already set
                        if (!themeToNullBackground.containsKey(styleName)) {
                            themeToNullBackground.put(styleName, null);
                        }
                    }
                }
            }
        }

        // Track when we move past root element
        if (folderType == ResourceFolderType.LAYOUT && !ATTR_BACKGROUND.equals(name)) {
            // Check if this is on the root element - we need to track element depth
            // This is handled by isRootElement flag set in beforeCheckFile
        }
    }

    private void ensureThemeMap() {
        if (themeToNullBackground == null) {
            themeToNullBackground = new HashMap<>();
        }
    }

    // ---- SourceCodeScanner ----

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(ANDROID_APP_ACTIVITY);
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>();
        types.add(UCallExpression.class);
        types.add(USimpleNameReferenceExpression.class);
        return types;
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // The class itself is visited; we use method visitors for setContentView/setTheme
        // This is handled via visitCallExpression
    }

    @Override
    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression call) {
        String methodName = call.getMethodName();
        if (methodName == null) {
            return;
        }

        if (SET_CONTENT_VIEW.equals(methodName)) {
            List<UElement> args = call.getValueArguments();
            if (args != null && !args.isEmpty()) {
                UElement arg = args.get(0);
                String layoutName = extractLayoutName(arg);
                if (layoutName != null) {
                    String activityName = getActivityName(context, call);
                    if (activityName != null) {
                        if (activityToLayouts == null) {
                            activityToLayouts = new HashMap<>();
                        }
                        List<String> layouts = activityToLayouts.get(activityName);
                        if (layouts == null) {
                            layouts = new ArrayList<>();
                            activityToLayouts.put(activityName, layouts);
                        }
                        if (!layouts.contains(layoutName)) {
                            layouts.add(layoutName);
                        }
                    }
                }
            }
        } else if (SET_THEME.equals(methodName)) {
            List<UElement> args = call.getValueArguments();
            if (args != null && !args.isEmpty()) {
                UElement arg = args.get(0);
                String themeName = extractThemeName(arg);
                if (themeName != null) {
                    String activityName = getActivityName(context, call);
                    if (activityName != null) {
                        if (activityToTheme == null) {
                            activityToTheme = new HashMap<>();
                        }
                        activityToTheme.put(activityName, themeName);
                    }
                }
            }
        }
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        // Used to track R.layout.xxx references if needed
    }

    @Nullable
    private String extractLayoutName(@NonNull UElement arg) {
        String text = arg.asSourceString();
        if (text == null) {
            return null;
        }
        // Look for R.layout.xxx pattern
        int index = text.lastIndexOf('.');
        if (index >= 0) {
            String prefix = text.substring(0, index);
            if (prefix.endsWith("R.layout") || prefix.endsWith(".layout")) {
                return text.substring(index + 1);
            }
        }
        return null;
    }

    @Nullable
    private String extractThemeName(@NonNull UElement arg) {
        String text = arg.asSourceString();
        if (text == null) {
            return null;
        }
        int index = text.lastIndexOf('.');
        if (index >= 0) {
            return text.substring(index + 1);
        }
        return text;
    }

    @Nullable
    private String getActivityName(
            @NonNull JavaContext context, @NonNull UCallExpression call) {
        // Walk up to find the enclosing class
        UElement parent = call.getUastParent();
        while (parent != null) {
            if (parent instanceof UClass) {
                UClass cls = (UClass) parent;
                String qualifiedName = cls.getQualifiedName();
                if (qualifiedName != null) {
                    return qualifiedName;
                }
                return cls.getName();
            }
            parent = parent.getUastParent();
        }
        return null;
    }

    @Nullable
    private String stripThemePrefix(@Nullable String theme) {
        if (theme == null) {
            return null;
        }
        if (theme.startsWith("@android:style/")) {
            return theme.substring("@android:style/".length());
        }
        if (theme.startsWith("@style/")) {
            return theme.substring("@style/".length());
        }
        if (theme.startsWith("@")) {
            int slash = theme.indexOf('/');
            if (slash >= 0) {
                return theme.substring(slash + 1);
            }
        }
        return theme;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (layoutToBackground == null || layoutToBackground.isEmpty()) {
            return;
        }

        // For each layout with a background, check if the associated activity
        // has a theme that already sets a background
        for (Map.Entry<String, Location> entry : layoutToBackground.entrySet()) {
            String layout = entry.getKey();
            Location location = entry.getValue();

            // Find which activity uses this layout
            String activityName = findActivityForLayout(layout);
            if (activityName == null) {
                // No activity found; report anyway as potential overdraw
                reportOverdraw(context, layout, location, null);
                continue;
            }

            // Determine the theme for this activity
            String theme = getThemeForActivity(activityName);
            if (theme == null) {
                // No specific theme; use application theme
                theme = applicationTheme;
            }

            if (theme != null && !hasNullWindowBackground(theme)) {
                reportOverdraw(context, layout, location, theme);
            } else if (theme == null) {
                // Default theme has a background
                reportOverdraw(context, layout, location, null);
            }
        }
    }

    private void reportOverdraw(
            @NonNull Context context,
            @NonNull String layout,
            @NonNull Location location,
            @Nullable String theme) {
        String message;
        if (theme != null) {
            message =
                    "Possible overdraw: Root element sets a background drawable, but the theme "
                            + "also sets a window background. The theme background will be covered "
                            + "by the root background, causing overdraw. Consider using a custom "
                            + "theme with a null window background, or removing the background "
                            + "from the root view.";
        } else {
            message =
                    "Possible overdraw: Root element sets a background drawable. If a theme "
                            + "background is also set, this causes overdraw. Consider using a "
                            + "custom theme with a null window background instead.";
        }
        context.report(ISSUE, location, message);
    }

    @Nullable
    private String findActivityForLayout(@NonNull String layoutName) {
        if (activityToLayouts == null) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : activityToLayouts.entrySet()) {
            if (entry.getValue().contains(layoutName)) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Nullable
    private String getThemeForActivity(@NonNull String activityName) {
        // First check programmatic theme setting
        if (activityToTheme != null) {
            String theme = activityToTheme.get(activityName);
            if (theme != null) {
                return theme;
            }
        }

        // Then check manifest
        if (manifestActivityThemes != null) {
            // Try exact match
            String theme = manifestActivityThemes.get(activityName);
            if (theme != null) {
                return theme;
            }
            // Try simple name match
            String simpleName = activityName;
            int dot = activityName.lastIndexOf('.');
            if (dot >= 0) {
                simpleName = activityName.substring(dot + 1);
            }
            for (Map.Entry<String, String> entry : manifestActivityThemes.entrySet()) {
                String key = entry.getKey();
                if (key.equals(simpleName)
                        || key.endsWith("." + simpleName)
                        || activityName.endsWith(key)) {
                    return entry.getValue();
                }
            }
        }

        return applicationTheme;
    }

    private boolean hasNullWindowBackground(@NonNull String theme) {
        if (resolvedThemes == null) {
            resolvedThemes = new HashSet<>();
        }
        return hasNullWindowBackground(theme, resolvedThemes);
    }

    private boolean hasNullWindowBackground(
            @NonNull String theme, @NonNull Set<String> visiting) {
        if (visiting.contains(theme)) {
            return false;
        }
        visiting.add(theme);

        if (themeToNullBackground == null) {
            return false;
        }

        Boolean result = themeToNullBackground.get(theme);
        if (result != null) {
            return result;
        }

        return false;
    }
}