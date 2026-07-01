package com.android.tools.lint.checks;

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
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class OverdrawDetector extends LayoutDetector implements SourceCodeScanner {

    private static final String ATTR_BACKGROUND = "background";
    private static final String ATTR_THEME = "theme";
    private static final String ATTR_STYLE = "style";
    private static final String SET_CONTENT_VIEW = "setContentView";
    private static final String R_LAYOUT = "R.layout.";
    private static final String ANDROID_APP_ACTIVITY = "android.app.Activity";

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
                            EnumSet.of(Scope.JAVA_FILE, Scope.ALL_RESOURCE_FILES)));

    /** Map from layout name to the background attribute value set on its root element */
    private Map<String, String> mLayoutToBackground;

    /** Map from layout name to the location of the background attribute */
    private Map<String, Location> mLayoutToLocation;

    /** Map from layout name to the theme set on its root element */
    private Map<String, String> mLayoutToTheme;

    /** Map from activity class name to the layout it uses */
    private Map<String, String> mActivityToLayout;

    /** Map from activity class name to the theme it uses (from manifest or style) */
    private Map<String, String> mActivityToTheme;

    /** The current activity being analyzed */
    private String mCurrentActivity;

    /** Themes that have a null/transparent window background */
    private List<String> mBlankThemes;

    /** Whether we've seen a setContentView call in the current method */
    private String mLastLayout;

    public OverdrawDetector() {}

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_BACKGROUND, ATTR_THEME, "windowBackground");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("style", "item");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mLastLayout = null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        ResourceFolderType folderType = context.getResourceFolderType();

        if (folderType == ResourceFolderType.LAYOUT) {
            String name = attribute.getLocalName();
            if (ATTR_BACKGROUND.equals(name)) {
                // Check if this is a root element
                Element element = attribute.getOwnerElement();
                if (element.getParentNode() == element.getOwnerDocument().getDocumentElement()
                        || element.getParentNode() == element.getOwnerDocument()) {
                    // This is the root element
                    String layoutName = getLayoutName(context);
                    if (layoutName != null) {
                        String background = attribute.getValue();
                        if (background != null && !background.isEmpty()) {
                            if (mLayoutToBackground == null) {
                                mLayoutToBackground = new HashMap<>();
                                mLayoutToLocation = new HashMap<>();
                            }
                            mLayoutToBackground.put(layoutName, background);
                            mLayoutToLocation.put(layoutName, context.getLocation(attribute));
                        }
                    }
                }
            } else if (ATTR_THEME.equals(name)) {
                Element element = attribute.getOwnerElement();
                if (element.getParentNode() == element.getOwnerDocument().getDocumentElement()
                        || element.getParentNode() == element.getOwnerDocument()) {
                    String layoutName = getLayoutName(context);
                    if (layoutName != null) {
                        String theme = attribute.getValue();
                        if (theme != null && !theme.isEmpty()) {
                            if (mLayoutToTheme == null) {
                                mLayoutToTheme = new HashMap<>();
                            }
                            mLayoutToTheme.put(layoutName, theme);
                        }
                    }
                }
            }
        } else if (folderType == ResourceFolderType.VALUES) {
            // Track windowBackground items that are set to null/@null
            String name = attribute.getLocalName();
            if ("name".equals(name)) {
                String value = attribute.getValue();
                if (value != null && value.contains("windowBackground")) {
                    // Check if this item's value is @null or similar
                    Element owner = attribute.getOwnerElement();
                    String textContent = owner.getTextContent();
                    if (textContent != null) {
                        textContent = textContent.trim();
                        if ("@null".equals(textContent) || "null".equals(textContent)) {
                            // The parent style has a null window background
                            Element styleElement = (Element) owner.getParentNode();
                            if (styleElement != null && "style".equals(styleElement.getTagName())) {
                                Attr nameAttr = styleElement.getAttributeNode("name");
                                if (nameAttr != null) {
                                    String styleName = nameAttr.getValue();
                                    if (styleName != null) {
                                        if (mBlankThemes == null) {
                                            mBlankThemes = new ArrayList<>();
                                        }
                                        mBlankThemes.add(styleName);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.VALUES) {
            return;
        }

        String tag = element.getTagName();
        if ("style".equals(tag)) {
            // Check if this style has windowBackground set to @null
            String styleName = element.getAttribute("name");
            if (styleName == null || styleName.isEmpty()) {
                return;
            }

            // Check child items for windowBackground = @null
            org.w3c.dom.NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                org.w3c.dom.Node child = children.item(i);
                if (child instanceof Element) {
                    Element item = (Element) child;
                    if ("item".equals(item.getTagName())) {
                        String itemName = item.getAttribute("name");
                        if (itemName != null && itemName.contains("windowBackground")) {
                            String textContent = item.getTextContent();
                            if (textContent != null) {
                                textContent = textContent.trim();
                                if ("@null".equals(textContent) || "null".equals(textContent)) {
                                    if (mBlankThemes == null) {
                                        mBlankThemes = new ArrayList<>();
                                    }
                                    if (!mBlankThemes.contains(styleName)) {
                                        mBlankThemes.add(styleName);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if ("item".equals(tag)) {
            String itemName = element.getAttribute("name");
            if (itemName != null && itemName.contains("windowBackground")) {
                String textContent = element.getTextContent();
                if (textContent != null) {
                    textContent = textContent.trim();
                    if ("@null".equals(textContent) || "null".equals(textContent)) {
                        Element parent = (Element) element.getParentNode();
                        if (parent != null && "style".equals(parent.getTagName())) {
                            String styleName = parent.getAttribute("name");
                            if (styleName != null && !styleName.isEmpty()) {
                                if (mBlankThemes == null) {
                                    mBlankThemes = new ArrayList<>();
                                }
                                if (!mBlankThemes.contains(styleName)) {
                                    mBlankThemes.add(styleName);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Nullable
    private String getLayoutName(@NonNull XmlContext context) {
        File file = context.file;
        String name = file.getName();
        if (name.endsWith(".xml")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }

    // SourceCodeScanner methods

    @Override
    @Nullable
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(ANDROID_APP_ACTIVITY);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        mCurrentActivity = declaration.getQualifiedName();
    }

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        String methodName = node.getMethodName();
        if (SET_CONTENT_VIEW.equals(methodName)) {
            List<org.jetbrains.uast.UExpression> args = node.getValueArguments();
            if (!args.isEmpty()) {
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
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        // Used to track R.layout references in setContentView calls
        // The actual tracking is done in visitCallExpression
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mLayoutToBackground == null || mLayoutToBackground.isEmpty()) {
            return;
        }

        // For each layout that has a background on the root element,
        // check if there's an activity using it that also has a non-blank theme
        for (Map.Entry<String, String> entry : mLayoutToBackground.entrySet()) {
            String layoutName = entry.getKey();
            String background = entry.getValue();

            // Find activities that use this layout
            String activityForLayout = null;
            if (mActivityToLayout != null) {
                for (Map.Entry<String, String> actEntry : mActivityToLayout.entrySet()) {
                    if (layoutName.equals(actEntry.getValue())) {
                        activityForLayout = actEntry.getKey();
                        break;
                    }
                }
            }

            // Check if the layout itself has a theme set that is blank
            String layoutTheme = mLayoutToTheme != null ? mLayoutToTheme.get(layoutName) : null;
            if (layoutTheme != null && isBlankTheme(layoutTheme)) {
                // The layout has a blank theme, so no overdraw
                continue;
            }

            // Check if the activity has a blank theme
            String activityTheme =
                    (activityForLayout != null && mActivityToTheme != null)
                            ? mActivityToTheme.get(activityForLayout)
                            : null;
            if (activityTheme != null && isBlankTheme(activityTheme)) {
                // The activity has a blank theme, so no overdraw
                continue;
            }

            // Report overdraw issue
            Location location = mLayoutToLocation != null ? mLayoutToLocation.get(layoutName) : null;
            if (location != null) {
                context.report(
                        ISSUE,
                        location,
                        "Possible overdraw: Root element sets a background `"
                                + background
                                + "` with a theme that also sets a background. "
                                + "To cancel the theme background, use a custom theme with "
                                + "`windowBackground` set to `@null`");
            }
        }
    }

    private boolean isBlankTheme(@NonNull String theme) {
        if (mBlankThemes != null) {
            // Normalize theme reference
            String normalized = theme;
            if (normalized.startsWith("@style/")) {
                normalized = normalized.substring("@style/".length());
            } else if (normalized.startsWith("@android:style/")) {
                normalized = normalized.substring("@android:style/".length());
            }
            for (String blankTheme : mBlankThemes) {
                if (blankTheme.equals(normalized) || blankTheme.equals(theme)) {
                    return true;
                }
            }
        }
        // Well-known blank themes
        return theme.contains("Translucent")
                || theme.contains("NoDisplay")
                || theme.endsWith(".Null");
    }
}