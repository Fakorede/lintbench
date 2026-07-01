package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_STYLE;
import static com.android.SdkConstants.VALUE_NULL;

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

public class OverdrawDetector extends LayoutDetector {

    private static final String SET_CONTENT_VIEW = "setContentView";
    private static final String R_LAYOUT_PREFIX = "R.layout.";

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
     * Map from layout name (without extension) to the background attribute value set on the root
     * element of that layout.
     */
    private Map<String, String> mLayoutToBackground;

    /**
     * Map from activity class name to the layout name used (without extension).
     */
    private Map<String, String> mActivityToLayout;

    /**
     * Map from activity class name to the theme used (from the manifest).
     */
    private Map<String, String> mActivityToTheme;

    /**
     * The application theme (from the manifest).
     */
    private String mApplicationTheme;

    /**
     * Map from style name to parent style name.
     */
    private Map<String, String> mStyleParents;

    /**
     * Set of style names that have a null/transparent window background.
     */
    private List<String> mBlankThemes;

    /**
     * Map from layout name to the location of the background attribute.
     */
    private Map<String, Location> mBackgroundLocations;

    /**
     * The current activity class being visited.
     */
    private String mCurrentActivity;

    public OverdrawDetector() {}

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.VALUES
                || folderType == ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_BACKGROUND, ATTR_THEME, ATTR_PARENT);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY, TAG_APPLICATION, TAG_STYLE);
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
        if (name.endsWith(DOT_XML)) {
            // Reset current layout tracking
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_APPLICATION.equals(tag)) {
            // Handled in visitAttribute for android:theme
        } else if (TAG_ACTIVITY.equals(tag)) {
            // Handled in visitAttribute for android:theme and android:name
        } else if (TAG_STYLE.equals(tag)) {
            // Handled in visitAttribute for parent
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        String value = attribute.getValue();
        Element element = attribute.getOwnerElement();
        String tag = element.getTagName();

        ResourceFolderType folderType = context.getResourceFolderType();

        if (folderType == ResourceFolderType.LAYOUT) {
            if (ATTR_BACKGROUND.equals(name) && ANDROID_URI.equals(attribute.getNamespaceURI())) {
                // Check if this is a root element (parent is the document element)
                if (element.getParentNode() == element.getOwnerDocument().getDocumentElement()
                        || element.getParentNode() == element.getOwnerDocument()) {
                    // This is the root element of the layout
                    String layoutName = getLayoutName(context);
                    if (layoutName != null && value != null && !value.isEmpty()) {
                        if (mLayoutToBackground == null) {
                            mLayoutToBackground = new HashMap<>();
                        }
                        if (mBackgroundLocations == null) {
                            mBackgroundLocations = new HashMap<>();
                        }
                        mLayoutToBackground.put(layoutName, value);
                        mBackgroundLocations.put(layoutName, context.getLocation(attribute));
                    }
                }
            }
        } else if (folderType == ResourceFolderType.VALUES) {
            if (TAG_STYLE.equals(tag)) {
                if (ATTR_PARENT.equals(name)) {
                    String styleName = element.getAttribute(ATTR_NAME);
                    if (styleName != null && !styleName.isEmpty()) {
                        if (mStyleParents == null) {
                            mStyleParents = new HashMap<>();
                        }
                        mStyleParents.put(styleName, value);
                    }
                }
            }
        } else if (context.getResourceFolderType() == null) {
            // Manifest file
            if (ATTR_THEME.equals(name) && ANDROID_URI.equals(attribute.getNamespaceURI())) {
                if (TAG_APPLICATION.equals(tag)) {
                    mApplicationTheme = value;
                } else if (TAG_ACTIVITY.equals(tag)) {
                    String activityName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (activityName != null && !activityName.isEmpty()) {
                        if (mActivityToTheme == null) {
                            mActivityToTheme = new HashMap<>();
                        }
                        mActivityToTheme.put(activityName, value);
                    }
                }
            }
        }

        // Handle style windowBackground = @null
        if (folderType == ResourceFolderType.VALUES && TAG_STYLE.equals(tag)) {
            // Check child items for windowBackground
            // This is handled via XML child elements, but we check the parent attribute here
        }
    }

    @Nullable
    private String getLayoutName(@NonNull XmlContext context) {
        String fileName = context.file.getName();
        if (fileName.endsWith(DOT_XML)) {
            return fileName.substring(0, fileName.length() - DOT_XML.length());
        }
        return null;
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        mCurrentActivity = declaration.getQualifiedName();
    }

    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        // Used to detect R.layout.xxx references
    }

    public void visitCallExpression(
            @NonNull JavaContext context,
            @NonNull UCallExpression node) {
        String methodName = node.getMethodName();
        if (SET_CONTENT_VIEW.equals(methodName)) {
            List<UElement> args = node.getValueArguments();
            if (args != null && !args.isEmpty()) {
                String argText = args.get(0).asSourceString();
                if (argText != null && argText.startsWith("R.layout.")) {
                    String layoutName = argText.substring("R.layout.".length());
                    if (mCurrentActivity != null) {
                        if (mActivityToLayout == null) {
                            mActivityToLayout = new HashMap<>();
                        }
                        mActivityToLayout.put(mCurrentActivity, layoutName);
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

        // For each layout that has a background on the root element,
        // check if the associated activity has a theme with a non-null window background.
        for (Map.Entry<String, String> entry : mLayoutToBackground.entrySet()) {
            String layoutName = entry.getKey();
            String background = entry.getValue();

            // Find which activity uses this layout
            String activityName = findActivityForLayout(layoutName);

            // Find the theme for this activity
            String theme = findThemeForActivity(activityName);

            if (theme != null && !isBlankTheme(theme)) {
                // The theme has a background and the layout also sets a background
                // This is overdraw
                Location location =
                        mBackgroundLocations != null
                                ? mBackgroundLocations.get(layoutName)
                                : null;
                if (location == null) {
                    location = Location.create(context.file);
                }
                context.report(
                        ISSUE,
                        location,
                        "Possible overdraw: Root element sets a background drawable "
                                + "(`"
                                + background
                                + "`) while the activity also has a "
                                + "theme background. Consider using a theme with a null "
                                + "background, or remove the background attribute from "
                                + "the root element.");
            }
        }
    }

    @Nullable
    private String findActivityForLayout(@NonNull String layoutName) {
        if (mActivityToLayout != null) {
            for (Map.Entry<String, String> entry : mActivityToLayout.entrySet()) {
                if (layoutName.equals(entry.getValue())) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    @Nullable
    private String findThemeForActivity(@Nullable String activityName) {
        if (activityName != null && mActivityToTheme != null) {
            String theme = mActivityToTheme.get(activityName);
            if (theme != null) {
                return theme;
            }
        }
        return mApplicationTheme;
    }

    private boolean isBlankTheme(@NonNull String theme) {
        if (mBlankThemes != null && mBlankThemes.contains(theme)) {
            return true;
        }

        if (mStyleParents != null) {
            // Check style hierarchy
            String current = theme;
            // Strip @style/ prefix
            if (current.startsWith(STYLE_RESOURCE_PREFIX)) {
                current = current.substring(STYLE_RESOURCE_PREFIX.length());
            } else if (current.startsWith("@android:style/")) {
                current = current.substring("@android:style/".length());
            }

            int depth = 0;
            while (current != null && depth < 20) {
                if (mBlankThemes != null && mBlankThemes.contains(current)) {
                    return true;
                }
                String parent = mStyleParents.get(current);
                if (parent == null) {
                    break;
                }
                if (parent.startsWith(STYLE_RESOURCE_PREFIX)) {
                    parent = parent.substring(STYLE_RESOURCE_PREFIX.length());
                } else if (parent.startsWith("@android:style/")) {
                    parent = parent.substring("@android:style/".length());
                }
                current = parent;
                depth++;
            }
        }

        return false;
    }

    /**
     * Mark a theme as having a blank (null) window background.
     */
    public void registerBlankTheme(@NonNull String theme) {
        if (mBlankThemes == null) {
            mBlankThemes = new ArrayList<>();
        }
        mBlankThemes.add(theme);
    }
}