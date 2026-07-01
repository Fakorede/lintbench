package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
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
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks for overdraw issues (painting regions more than once).
 */
public class OverdrawDetector extends Detector implements XmlScanner, SourceCodeScanner {

    private static final String SET_CONTENT_VIEW = "setContentView";
    private static final String WINDOW_BACKGROUND = "windowBackground";

    public static final Issue ISSUE = Issue.create(
            "Overdraw",
            "Overdraw: Painting regions more than once",
            "If you set a background drawable on a root view, then you should use a " +
            "custom theme where the theme background is null. Otherwise, the theme background " +
            "will be painted first, only to have your custom background completely cover it; " +
            "this is called \"overdraw\".\n" +
            "\n" +
            "NOTE: This detector relies on figuring out which layouts are associated with " +
            "which activities based on scanning the Java code, and it's currently doing that " +
            "using an inexact pattern matching algorithm. Therefore, it can incorrectly " +
            "conclude which activity the layout is associated with and then wrongly complain " +
            "that a background-theme is hidden.\n" +
            "\n" +
            "If you want your custom background on multiple pages, then you should consider " +
            "making a custom theme with your custom background and just using that theme " +
            "instead of a root element background.\n" +
            "\n" +
            "Of course it's possible that your custom drawable is translucent and you want " +
            "it to be mixed with the background. However, you will get better performance " +
            "if you pre-mix the background with your drawable and use that resulting image or " +
            "color as a custom theme background instead.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(
                    OverdrawDetector.class,
                    EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.ALL_JAVA_FILES)));

    /**
     * Layouts that set a background drawable on the root element.
     * Maps from layout name (without extension) to the location of the background attribute.
     */
    private Map<String, Location> mLayoutsWithBackgrounds;

    /**
     * Map from activity class name to layout name used in setContentView.
     */
    private Map<String, String> mActivityToLayout;

    /**
     * Map from activity class name to theme name.
     */
    private Map<String, String> mActivityToTheme;

    /**
     * Application-level theme.
     */
    private String mApplicationTheme;

    /**
     * Map from theme name to parent theme name.
     */
    private Map<String, String> mThemeParents;

    /**
     * Set of themes that have a null/blank windowBackground.
     */
    private Set<String> mBlankThemes;

    /**
     * Map from theme name to whether it has a non-null windowBackground.
     * True = has non-null background, False = explicitly set to null
     */
    private Map<String, Boolean> mThemeHasBackground;

    /** Constructs a new {@link OverdrawDetector} */
    public OverdrawDetector() {
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mLayoutsWithBackgrounds = new HashMap<>();
        mActivityToLayout = new HashMap<>();
        mActivityToTheme = new HashMap<>();
        mThemeParents = new HashMap<>();
        mBlankThemes = new HashSet<>();
        mThemeHasBackground = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mLayoutsWithBackgrounds == null || mLayoutsWithBackgrounds.isEmpty()) {
            return;
        }

        // For each layout with a background, find the activity that uses it,
        // then check if that activity has a theme with a non-null windowBackground.
        for (Map.Entry<String, Location> entry : mLayoutsWithBackgrounds.entrySet()) {
            String layout = entry.getKey();
            Location location = entry.getValue();

            // Find the activity that uses this layout
            String activityClass = null;
            for (Map.Entry<String, String> actEntry : mActivityToLayout.entrySet()) {
                if (layout.equals(actEntry.getValue())) {
                    activityClass = actEntry.getKey();
                    break;
                }
            }

            // Determine the theme for this activity
            String theme = null;
            if (activityClass != null) {
                theme = mActivityToTheme.get(activityClass);
            }
            if (theme == null) {
                theme = mApplicationTheme;
            }

            // Check if the theme has a non-null background
            boolean themeHasBackground;
            if (theme != null) {
                themeHasBackground = hasNonNullBackground(theme);
            } else {
                // No theme info - assume default theme has background
                themeHasBackground = true;
            }

            if (themeHasBackground) {
                context.report(ISSUE, location,
                        "Possible overdraw: Root element paints background `" + layout +
                        "` with a theme that also paints a background (first theme background, " +
                        "then layout background). If the theme background is intentionally " +
                        "invisible, set `android:windowBackground=\"@null\"`");
            }
        }
    }

    // ---- Implements XmlScanner ----

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.VALUES
                || folderType == ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_ACTIVITY,
                TAG_APPLICATION,
                TAG_STYLE
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (TAG_STYLE.equals(tagName)) {
            visitStyleElement(context, element);
        } else if (TAG_ACTIVITY.equals(tagName)) {
            visitActivityElement(context, element);
        } else if (TAG_APPLICATION.equals(tagName)) {
            visitApplicationElement(context, element);
        }
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == ResourceFolderType.LAYOUT) {
            // Check if the root element has a background
            Element root = document.getDocumentElement();
            if (root != null) {
                checkRootBackground(context, root);
            }
        }
    }

    private void checkRootBackground(@NonNull XmlContext context, @NonNull Element root) {
        Attr backgroundAttr = root.getAttributeNodeNS(ANDROID_URI, ATTR_BACKGROUND);
        if (backgroundAttr == null) {
            return;
        }

        String value = backgroundAttr.getValue();
        if (value == null || value.isEmpty() || "@null".equals(value)) {
            return;
        }

        File file = context.file;
        String fileName = file.getName();
        // Strip extension
        int dot = fileName.lastIndexOf('.');
        if (dot != -1) {
            fileName = fileName.substring(0, dot);
        }

        Location location = context.getLocation(backgroundAttr);
        mLayoutsWithBackgrounds.put(fileName, location);
    }

    private void visitStyleElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String parent = element.getAttribute(ATTR_PARENT);

        // Also check for implicit parent via dot notation
        if ((parent == null || parent.isEmpty()) && name.contains(".")) {
            int lastDot = name.lastIndexOf('.');
            parent = name.substring(0, lastDot);
        }

        if (parent != null && !parent.isEmpty()) {
            // Normalize parent name
            String normalizedParent = stripStylePrefix(parent);
            mThemeParents.put(name, normalizedParent);
        }

        // Check children for windowBackground item
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element item = (Element) child;
                String itemName = item.getAttribute(ATTR_NAME);
                if (itemName == null) {
                    itemName = item.getAttributeNS(ANDROID_URI, ATTR_NAME);
                }
                if (itemName != null) {
                    // Strip android: prefix if present
                    if (itemName.startsWith("android:")) {
                        itemName = itemName.substring("android:".length());
                    }
                    if (WINDOW_BACKGROUND.equals(itemName)) {
                        String value = item.getTextContent();
                        if (value != null) {
                            value = value.trim();
                        }
                        if (value == null || value.isEmpty() || "@null".equals(value) ||
                                "null".equals(value)) {
                            mBlankThemes.add(name);
                            mThemeHasBackground.put(name, Boolean.FALSE);
                        } else {
                            mThemeHasBackground.put(name, Boolean.TRUE);
                        }
                    }
                }
            }
        }
    }

    private void visitActivityElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        // Resolve fully qualified name
        String pkg = context.getProject().getPackage();
        if (name.startsWith(".") && pkg != null) {
            name = pkg + name;
        } else if (!name.contains(".") && pkg != null) {
            name = pkg + "." + name;
        }

        String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
        if (theme != null && !theme.isEmpty()) {
            theme = stripThemePrefix(theme);
            mActivityToTheme.put(name, theme);
        }
    }

    private void visitApplicationElement(@NonNull XmlContext context, @NonNull Element element) {
        String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
        if (theme != null && !theme.isEmpty()) {
            theme = stripThemePrefix(theme);
            mApplicationTheme = theme;
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return null; // We handle backgrounds via visitDocument
    }

    // ---- Implements SourceCodeScanner ----

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(SET_CONTENT_VIEW);
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        List<org.jetbrains.uast.UExpression> args = call.getValueArguments();
        if (args.isEmpty()) {
            return;
        }

        org.jetbrains.uast.UExpression firstArg = args.get(0);
        String argText = firstArg.asSourceString();

        // Look for R.layout.xxx pattern
        String layoutName = null;
        if (argText.contains(".layout.")) {
            int idx = argText.indexOf(".layout.");
            layoutName = argText.substring(idx + ".layout.".length());
        }

        if (layoutName == null || layoutName.isEmpty()) {
            return;
        }

        // Find the containing class
        UClass containingClass = findContainingClass(call);
        if (containingClass == null) {
            return;
        }

        String qualifiedName = containingClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        mActivityToLayout.put(qualifiedName, layoutName);
    }

    @Nullable
    private UClass findContainingClass(@NonNull UCallExpression call) {
        UElement parent = call.getUastParent();
        while (parent != null) {
            if (parent instanceof UClass) {
                return (UClass) parent;
            }
            parent = parent.getUastParent();
        }
        return null;
    }

    // ---- Helper methods ----

    private boolean hasNonNullBackground(@NonNull String theme) {
        Set<String> visited = new HashSet<>();
        return hasNonNullBackgroundRecursive(theme, visited);
    }

    private boolean hasNonNullBackgroundRecursive(@NonNull String theme,
            @NonNull Set<String> visited) {
        if (visited.contains(theme)) {
            return false;
        }
        visited.add(theme);

        Boolean hasBackground = mThemeHasBackground.get(theme);
        if (hasBackground != null) {
            return hasBackground;
        }

        // Check parent
        String parent = mThemeParents.get(theme);
        if (parent != null) {
            boolean parentResult = hasNonNullBackgroundRecursive(parent, visited);
            if (parentResult) {
                return true;
            }
        }

        // If we don't know, assume it has a background (default Android themes do)
        // This is the conservative approach to avoid missing real overdraw issues
        return true;
    }

    @NonNull
    private static String stripThemePrefix(@NonNull String theme) {
        if (theme.startsWith("@style/")) {
            return theme.substring("@style/".length());
        } else if (theme.startsWith("@android:style/")) {
            return theme.substring("@android:style/".length());
        } else if (theme.startsWith("?attr/")) {
            return theme.substring("?attr/".length());
        } else if (theme.startsWith("@")) {
            int slash = theme.indexOf('/');
            if (slash != -1) {
                return theme.substring(slash + 1);
            }
        }
        return theme;
    }

    @NonNull
    private static String stripStylePrefix(@NonNull String style) {
        if (style.startsWith("@style/")) {
            return style.substring("@style/".length());
        } else if (style.startsWith("@android:style/")) {
            return style.substring("@android:style/".length());
        } else if (style.startsWith("@")) {
            int slash = style.indexOf('/');
            if (slash != -1) {
                return style.substring(slash + 1);
            }
        }
        return style;
    }
}