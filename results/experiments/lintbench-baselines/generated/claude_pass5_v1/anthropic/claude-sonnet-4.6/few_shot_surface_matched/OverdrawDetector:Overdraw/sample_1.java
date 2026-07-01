package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;

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
import com.android.tools.lint.detector.api.XmlScanner;
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

public class OverdrawDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String R_LAYOUT_PREFIX = "R.layout.";
    private static final String SET_CONTENT_VIEW = "setContentView";
    private static final String ACTIVITY_CLASS = "android.app.Activity";
    private static final String WINDOW_BACKGROUND = "windowBackground";

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
                    new Implementation(
                            OverdrawDetector.class,
                            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE, Scope.RESOURCE_FILE)));

    /** Map from layout name to the location of a background attribute in that layout */
    private Map<String, Location> mLayoutsWithBackgrounds;

    /** Map from activity class name to layout name */
    private Map<String, String> mActivityToLayout;

    /** Map from activity class name to theme name */
    private Map<String, String> mActivityToTheme;

    /** Map from theme name to whether it has a null/transparent window background */
    private Map<String, Boolean> mThemeToNullBackground;

    /** Application-level theme */
    private String mApplicationTheme;

    /** Current layout file name being processed */
    private String mCurrentLayoutName;

    /** Set of activity classes that extend Activity */
    private Set<String> mActivities;

    public OverdrawDetector() {}

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_ACTIVITY, "style");
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_BACKGROUND, ATTR_THEME, ATTR_PARENT, ATTR_NAME);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        String name = context.file.getName();
        if (name.endsWith(DOT_XML)) {
            mCurrentLayoutName = name.substring(0, name.length() - DOT_XML.length());
        } else {
            mCurrentLayoutName = null;
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (tag.equals(TAG_APPLICATION)) {
            Attr themeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_THEME);
            if (themeAttr != null) {
                mApplicationTheme = themeAttr.getValue();
            }
        } else if (tag.equals(TAG_ACTIVITY)) {
            Attr nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
            Attr themeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_THEME);
            if (nameAttr != null && themeAttr != null) {
                String activityName = nameAttr.getValue();
                String theme = themeAttr.getValue();
                if (mActivityToTheme == null) {
                    mActivityToTheme = new HashMap<>();
                }
                mActivityToTheme.put(activityName, theme);
            }
        } else if (tag.equals("style")) {
            // Handled in visitAttribute
        }
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
                // Check if this is the root element
                Element element = attribute.getOwnerElement();
                if (element.getParentNode() == element.getOwnerDocument().getDocumentElement()
                        || element.getParentNode() instanceof org.w3c.dom.Document
                        || element.getParentNode().getNodeType() == org.w3c.dom.Node.DOCUMENT_NODE
                        || isRootElement(element)) {
                    String background = attribute.getValue();
                    if (background != null && !background.isEmpty()) {
                        if (mLayoutsWithBackgrounds == null) {
                            mLayoutsWithBackgrounds = new HashMap<>();
                        }
                        if (mCurrentLayoutName != null) {
                            mLayoutsWithBackgrounds.put(
                                    mCurrentLayoutName,
                                    context.getLocation(attribute));
                        }
                    }
                }
            }
        } else if (folderType == ResourceFolderType.VALUES) {
            // Check style items for windowBackground = @null
            if ("name".equals(name)) {
                // This is the name attribute of a style element or item
                String value = attribute.getValue();
                Element ownerElement = attribute.getOwnerElement();
                if ("style".equals(ownerElement.getTagName())) {
                    // Check children for windowBackground item
                    checkStyleForNullBackground(ownerElement, value);
                } else if ("item".equals(ownerElement.getTagName())) {
                    if (WINDOW_BACKGROUND.equals(value) || value.endsWith(WINDOW_BACKGROUND)) {
                        String content = ownerElement.getTextContent();
                        if (content != null) {
                            content = content.trim();
                            if ("@null".equals(content) || "@android:null".equals(content)) {
                                // Find the parent style name
                                Element parentStyle = (Element) ownerElement.getParentNode();
                                if (parentStyle != null && "style".equals(parentStyle.getTagName())) {
                                    String styleName = parentStyle.getAttribute(ATTR_NAME);
                                    if (styleName != null && !styleName.isEmpty()) {
                                        if (mThemeToNullBackground == null) {
                                            mThemeToNullBackground = new HashMap<>();
                                        }
                                        mThemeToNullBackground.put(styleName, Boolean.TRUE);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkStyleForNullBackground(@NonNull Element styleElement, @NonNull String styleName) {
        org.w3c.dom.NodeList children = styleElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element item = (Element) child;
                if ("item".equals(item.getTagName())) {
                    String itemName = item.getAttribute(ATTR_NAME);
                    if (WINDOW_BACKGROUND.equals(itemName)
                            || (itemName != null && itemName.endsWith(WINDOW_BACKGROUND))) {
                        String content = item.getTextContent();
                        if (content != null) {
                            content = content.trim();
                            if ("@null".equals(content) || "@android:null".equals(content)) {
                                if (mThemeToNullBackground == null) {
                                    mThemeToNullBackground = new HashMap<>();
                                }
                                mThemeToNullBackground.put(styleName, Boolean.TRUE);
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isRootElement(@NonNull Element element) {
        return element.getParentNode() == null
                || element.getParentNode().getNodeType() == org.w3c.dom.Node.DOCUMENT_NODE
                || !(element.getParentNode() instanceof Element);
    }

    // SourceCodeScanner implementation

    @Override
    @Nullable
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(ACTIVITY_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (mActivities == null) {
            mActivities = new HashSet<>();
        }
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            mActivities.add(qualifiedName);
        }
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
            if (args.size() == 1) {
                UElement arg = args.get(0);
                String argText = arg.asSourceString();
                if (argText != null && argText.startsWith(R_LAYOUT_PREFIX)) {
                    String layoutName = argText.substring(R_LAYOUT_PREFIX.length());
                    // Find the containing class
                    UClass containingClass = getContainingClass(node);
                    if (containingClass != null) {
                        String className = containingClass.getQualifiedName();
                        if (className != null) {
                            if (mActivityToLayout == null) {
                                mActivityToLayout = new HashMap<>();
                            }
                            mActivityToLayout.put(className, layoutName);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        // Used to track R.layout.xxx references in setContentView calls
        // This is handled in visitCallExpression
    }

    @Nullable
    private UClass getContainingClass(@NonNull UElement node) {
        UElement parent = node.getUastParent();
        while (parent != null) {
            if (parent instanceof UClass) {
                return (UClass) parent;
            }
            parent = parent.getUastParent();
        }
        return null;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mLayoutsWithBackgrounds == null || mLayoutsWithBackgrounds.isEmpty()) {
            return;
        }

        // For each layout with a background, find the associated activity
        // and check if that activity uses a theme with null window background
        for (Map.Entry<String, Location> entry : mLayoutsWithBackgrounds.entrySet()) {
            String layoutName = entry.getKey();
            Location location = entry.getValue();

            // Find which activity uses this layout
            String activityName = findActivityForLayout(layoutName);

            // Find the theme for this activity
            String theme = findThemeForActivity(activityName);

            // Check if the theme has a null window background
            if (theme != null && hasNullWindowBackground(theme)) {
                // Theme has null background, no overdraw issue
                continue;
            }

            // If there's no theme with null background, report overdraw
            if (theme == null) {
                // Using application theme or default theme
                theme = mApplicationTheme;
            }

            if (theme != null && hasNullWindowBackground(theme)) {
                continue;
            }

            // Report the issue
            context.report(
                    ISSUE,
                    location,
                    "Possible overdraw: Root element sets a background that is "
                            + "also defined in the theme; consider making the "
                            + "theme background transparent or removing the root background.");
        }
    }

    @Nullable
    private String findActivityForLayout(@NonNull String layoutName) {
        if (mActivityToLayout == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : mActivityToLayout.entrySet()) {
            if (layoutName.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Nullable
    private String findThemeForActivity(@Nullable String activityName) {
        if (activityName == null || mActivityToTheme == null) {
            return null;
        }
        // Try exact match first
        String theme = mActivityToTheme.get(activityName);
        if (theme != null) {
            return theme;
        }
        // Try simple name match
        for (Map.Entry<String, String> entry : mActivityToTheme.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith(activityName) || activityName.endsWith(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean hasNullWindowBackground(@Nullable String theme) {
        if (theme == null || mThemeToNullBackground == null) {
            return false;
        }
        // Normalize theme name
        String themeName = theme;
        if (themeName.startsWith(STYLE_RESOURCE_PREFIX)) {
            themeName = themeName.substring(STYLE_RESOURCE_PREFIX.length());
        } else if (themeName.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
            themeName = themeName.substring(ANDROID_STYLE_RESOURCE_PREFIX.length());
        } else if (themeName.startsWith("@style/")) {
            themeName = themeName.substring("@style/".length());
        } else if (themeName.startsWith("@android:style/")) {
            themeName = themeName.substring("@android:style/".length());
        }

        Boolean result = mThemeToNullBackground.get(themeName);
        if (result != null) {
            return result;
        }
        // Also check original
        result = mThemeToNullBackground.get(theme);
        return result != null && result;
    }
}