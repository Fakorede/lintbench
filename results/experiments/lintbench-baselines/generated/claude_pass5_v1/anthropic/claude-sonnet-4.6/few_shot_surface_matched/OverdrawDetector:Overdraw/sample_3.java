package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
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
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class OverdrawDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String ANDROID_ACTIVITY = "android.app.Activity";
    private static final String SET_CONTENT_VIEW = "setContentView";
    private static final String SET_THEME = "setTheme";
    private static final String R_LAYOUT = "R.layout.";
    private static final String ATTR_WINDOW_BACKGROUND = "windowBackground";
    private static final String NULL_RESOURCE = "@null";

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Overdraw: Painting regions more than once",
                    "If you set a background drawable on a root view, then you should use a "
                            + "custom theme where the theme background is null. Otherwise, the "
                            + "theme background will be painted first, only to have your custom "
                            + "background completely cover it; this is called \"overdraw\".\n"
                            + "\n"
                            + "NOTE: This detector relies on figuring out which layouts are "
                            + "associated with which activities based on scanning the Java code, "
                            + "and it's currently doing that using an inexact pattern matching "
                            + "algorithm. Therefore, it can incorrectly conclude which activity "
                            + "the layout is associated with and then wrongly complain that a "
                            + "background-theme is hidden.\n"
                            + "\n"
                            + "If you want your custom background on multiple pages, then you "
                            + "should consider making a custom theme with your custom background "
                            + "and just using that theme instead of a root element background.\n"
                            + "\n"
                            + "Of course it's possible that your custom drawable is translucent "
                            + "and you want it to be mixed with the background. However, you will "
                            + "get better performance if you pre-mix the background with your "
                            + "drawable and use that resulting image or color as a custom theme "
                            + "background instead.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    new Implementation(
                            OverdrawDetector.class,
                            EnumSet.of(Scope.JAVA_FILE, Scope.ALL_RESOURCE_FILES)));

    // Map from layout name to background attribute location
    private final Map<String, Location> mLayoutsWithBackgrounds = new HashMap<>();

    // Map from activity class name to layout name
    private final Map<String, String> mActivityToLayout = new HashMap<>();

    // Map from activity class name to theme name
    private final Map<String, String> mActivityToTheme = new HashMap<>();

    // Map from theme name to whether it has a null window background
    private final Map<String, Boolean> mThemeToNullBackground = new HashMap<>();

    // Map from activity to its manifest theme
    private final Map<String, String> mManifestActivityThemes = new HashMap<>();

    // Application theme from manifest
    private String mApplicationTheme = null;

    // Current layout file being analyzed
    private String mCurrentLayoutName = null;

    // Current activity class being analyzed
    private String mCurrentActivityClass = null;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_BACKGROUND, ATTR_THEME, ATTR_WINDOW_BACKGROUND);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY, TAG_APPLICATION, "style");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            if (xmlContext.getResourceFolderType() == ResourceFolderType.LAYOUT) {
                String fileName = context.file.getName();
                if (fileName.endsWith(DOT_XML)) {
                    mCurrentLayoutName = fileName.substring(0, fileName.length() - DOT_XML.length());
                } else {
                    mCurrentLayoutName = fileName;
                }
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String attributeName = attribute.getLocalName();
        if (attributeName == null) {
            attributeName = attribute.getName();
        }

        ResourceFolderType folderType = context.getResourceFolderType();

        if (ATTR_BACKGROUND.equals(attributeName)
                && folderType == ResourceFolderType.LAYOUT) {
            // Check if this is the root element
            if (attribute.getOwnerElement().getParentNode()
                    instanceof org.w3c.dom.Document) {
                String background = attribute.getValue();
                if (background != null && !background.isEmpty()) {
                    if (mCurrentLayoutName != null) {
                        mLayoutsWithBackgrounds.put(
                                mCurrentLayoutName, context.getLocation(attribute));
                    }
                }
            }
        } else if (ATTR_WINDOW_BACKGROUND.equals(attributeName)) {
            String value = attribute.getValue();
            if (value != null) {
                // Get the style name from the parent element
                Element element = attribute.getOwnerElement();
                String styleName = element.getAttribute(ATTR_NAME);
                if (styleName != null && !styleName.isEmpty()) {
                    boolean isNull = NULL_RESOURCE.equals(value)
                            || value.equals("@android:null");
                    mThemeToNullBackground.put(styleName, isNull);
                    // Also store with normalized name
                    String normalizedName = normalizeThemeName(styleName);
                    if (!normalizedName.equals(styleName)) {
                        mThemeToNullBackground.put(normalizedName, isNull);
                    }
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        ResourceFolderType folderType = context.getResourceFolderType();

        if (TAG_ACTIVITY.equals(tagName) && folderType == null) {
            // Manifest activity element
            String activityName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
            if (activityName != null && !activityName.isEmpty()
                    && theme != null && !theme.isEmpty()) {
                mManifestActivityThemes.put(activityName, theme);
            }
        } else if (TAG_APPLICATION.equals(tagName) && folderType == null) {
            // Manifest application element
            String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
            if (theme != null && !theme.isEmpty()) {
                mApplicationTheme = theme;
            }
        } else if ("style".equals(tagName) && folderType == ResourceFolderType.VALUES) {
            // Style element - check for windowBackground item
            String styleName = element.getAttribute(ATTR_NAME);
            String parent = element.getAttribute(ATTR_PARENT);
            if (styleName != null && !styleName.isEmpty()) {
                // Check child items for windowBackground
                org.w3c.dom.NodeList children = element.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    org.w3c.dom.Node child = children.item(i);
                    if (child instanceof Element) {
                        Element item = (Element) child;
                        String itemName = item.getAttribute(ATTR_NAME);
                        if (ATTR_WINDOW_BACKGROUND.equals(itemName)
                                || ("android:" + ATTR_WINDOW_BACKGROUND).equals(itemName)) {
                            String value = item.getTextContent();
                            if (value != null) {
                                value = value.trim();
                                boolean isNull = NULL_RESOURCE.equals(value)
                                        || "@android:null".equals(value);
                                mThemeToNullBackground.put(styleName, isNull);
                                String normalizedName = normalizeThemeName(styleName);
                                if (!normalizedName.equals(styleName)) {
                                    mThemeToNullBackground.put(normalizedName, isNull);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(ANDROID_ACTIVITY);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        mCurrentActivityClass = declaration.getQualifiedName();
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context,
            @NonNull UCallExpression call) {
        String methodName = call.getMethodName();
        if (SET_CONTENT_VIEW.equals(methodName)) {
            List<UExpression> args = call.getValueArguments();
            if (!args.isEmpty()) {
                String layoutName = extractLayoutName(args.get(0).asSourceString());
                if (layoutName != null && mCurrentActivityClass != null) {
                    mActivityToLayout.put(mCurrentActivityClass, layoutName);
                }
            }
        } else if (SET_THEME.equals(methodName)) {
            List<UExpression> args = call.getValueArguments();
            if (!args.isEmpty()) {
                String themeName = extractStyleName(args.get(0).asSourceString());
                if (themeName != null && mCurrentActivityClass != null) {
                    mActivityToTheme.put(mCurrentActivityClass, themeName);
                }
            }
        }
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        // No-op: used as a hook if needed
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // For each layout with a background, find which activity uses it,
        // and check if that activity has a theme with a non-null window background
        for (Map.Entry<String, Location> entry : mLayoutsWithBackgrounds.entrySet()) {
            String layoutName = entry.getKey();
            Location location = entry.getValue();

            // Find the activity that uses this layout
            String activityClass = null;
            for (Map.Entry<String, String> actEntry : mActivityToLayout.entrySet()) {
                if (layoutName.equals(actEntry.getValue())) {
                    activityClass = actEntry.getKey();
                    break;
                }
            }

            if (activityClass == null) {
                // No activity found for this layout, skip
                continue;
            }

            // Determine theme for this activity
            String theme = mActivityToTheme.get(activityClass);
            if (theme == null) {
                theme = mManifestActivityThemes.get(activityClass);
            }
            if (theme == null) {
                // Try simple name
                String simpleName = getSimpleName(activityClass);
                theme = mManifestActivityThemes.get(simpleName);
            }
            if (theme == null) {
                theme = mApplicationTheme;
            }

            if (theme == null) {
                // No theme info, report as possible overdraw
                context.report(
                        ISSUE,
                        location,
                        "Possible overdraw: Root element sets a background that may be hidden "
                                + "by the theme background. To eliminate overdraw, use a custom "
                                + "theme with a null background (windowBackground=@null)");
                continue;
            }

            // Check if theme has null background
            String normalizedTheme = normalizeThemeName(theme);
            Boolean hasNullBackground = mThemeToNullBackground.get(theme);
            if (hasNullBackground == null) {
                hasNullBackground = mThemeToNullBackground.get(normalizedTheme);
            }

            if (hasNullBackground == null || !hasNullBackground) {
                // Theme does not have null background — overdraw possible
                context.report(
                        ISSUE,
                        location,
                        "Possible overdraw: Root element sets a background that may be hidden "
                                + "by the theme background (`"
                                + theme
                                + "`). To eliminate overdraw, use a custom theme with a null "
                                + "background (windowBackground=@null)");
            }
        }
    }

    @Nullable
    private static String extractLayoutName(@NonNull String expression) {
        // Matches patterns like R.layout.foo or layout.foo
        int index = expression.lastIndexOf("R.layout.");
        if (index >= 0) {
            String rest = expression.substring(index + "R.layout.".length()).trim();
            // Remove any trailing non-identifier characters
            StringBuilder sb = new StringBuilder();
            for (char c : rest.toCharArray()) {
                if (Character.isLetterOrDigit(c) || c == '_') {
                    sb.append(c);
                } else {
                    break;
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        }
        return null;
    }

    @Nullable
    private static String extractStyleName(@NonNull String expression) {
        // Matches patterns like R.style.MyTheme or @style/MyTheme
        int index = expression.lastIndexOf("R.style.");
        if (index >= 0) {
            String rest = expression.substring(index + "R.style.".length()).trim();
            StringBuilder sb = new StringBuilder();
            for (char c : rest.toCharArray()) {
                if (Character.isLetterOrDigit(c) || c == '_') {
                    sb.append(c);
                } else {
                    break;
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        }
        return null;
    }

    @NonNull
    private static String normalizeThemeName(@NonNull String theme) {
        // Strip @style/, @android:style/, etc.
        if (theme.startsWith("@")) {
            int slashIndex = theme.indexOf('/');
            if (slashIndex >= 0) {
                return theme.substring(slashIndex + 1);
            }
        }
        return theme;
    }

    @NonNull
    private static String getSimpleName(@NonNull String qualifiedName) {
        int dotIndex = qualifiedName.lastIndexOf('.');
        if (dotIndex >= 0) {
            return qualifiedName.substring(dotIndex + 1);
        }
        return qualifiedName;
    }
}