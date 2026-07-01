package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.*;

import static com.android.SdkConstants.*;

public class OverdrawDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "Overdraw",
            "Overdraw: Painting regions more than once",
            "If you set a background drawable on a root view, then you should use a " +
            "custom theme where the theme background is null. Otherwise, the theme background " +
            "will be painted first, only to have your custom background completely cover it; " +
            "this is called \"overdraw\".\n\n" +
            "NOTE: This detector relies on figuring out which layouts are associated with " +
            "which activities based on scanning the Java code, and it's currently doing that " +
            "using an inexact pattern matching algorithm. Therefore, it can incorrectly " +
            "conclude which activity the layout is associated with and then wrongly complain " +
            "that a background-theme is hidden.\n\n" +
            "If you want your custom background on multiple pages, then you should consider " +
            "making a custom theme with your custom background and just using that theme " +
            "instead of a root element background.\n\n" +
            "Of course it's possible that your custom drawable is translucent and you want " +
            "it to be mixed with the background. However, you will get better performance " +
            "if you pre-mix the background with your drawable and use that resulting image or " +
            "color as a custom theme background instead.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(
                    OverdrawDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE, Scope.RESOURCE_FILE)
            )
    );

    /** Map from activity class name to theme name */
    private Map<String, String> mActivityToTheme;

    /** Map from theme name to whether it has a null/transparent background */
    private Map<String, Boolean> mThemeToNullBackground;

    /** Map from layout name to the location of the background attribute */
    private Map<String, Location> mLayoutToBackground;

    /** Map from layout name to the XML element that has the background */
    private Map<String, Element> mLayoutToBackgroundElement;

    /** Map from layout name to the context (file) */
    private Map<String, Context> mLayoutToContext;

    /** Map from activity class name to layout name */
    private Map<String, String> mActivityToLayout;

    /** Map from layout name to activity */
    private Map<String, String> mLayoutToActivity;

    /** The application theme */
    private String mApplicationTheme;

    /** Styles that extend other styles */
    private Map<String, String> mStyleParents;

    /** Styles that set windowBackground to null or transparent */
    private Set<String> mNullBackgroundStyles;

    /** Styles that set windowBackground to something non-null */
    private Set<String> mNonNullBackgroundStyles;

    public OverdrawDetector() {
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mActivityToTheme = new HashMap<>();
        mThemeToNullBackground = new HashMap<>();
        mLayoutToBackground = new HashMap<>();
        mLayoutToBackgroundElement = new HashMap<>();
        mLayoutToContext = new HashMap<>();
        mActivityToLayout = new HashMap<>();
        mLayoutToActivity = new HashMap<>();
        mStyleParents = new HashMap<>();
        mNullBackgroundStyles = new HashSet<>();
        mNonNullBackgroundStyles = new HashSet<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Now that we have all the information, check for overdraw
        if (mLayoutToBackground.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Location> entry : mLayoutToBackground.entrySet()) {
            String layout = entry.getKey();
            Location location = entry.getValue();

            // Find the activity for this layout
            String activity = mLayoutToActivity.get(layout);
            if (activity == null) {
                // Can't determine activity, skip
                continue;
            }

            // Find the theme for this activity
            String theme = mActivityToTheme.get(activity);
            if (theme == null) {
                theme = mApplicationTheme;
            }

            if (theme == null) {
                continue;
            }

            // Check if the theme has a non-null background
            if (!hasNullBackground(theme)) {
                Context layoutContext = mLayoutToContext.get(layout);
                if (layoutContext != null) {
                    layoutContext.report(ISSUE, location,
                            "Possible overdraw: Root element sets a background that will be " +
                            "covered by the theme's window background. " +
                            "Consider using a custom theme with a null window background, " +
                            "or removing the background attribute from the root element.");
                }
            }
        }
    }

    private boolean hasNullBackground(String theme) {
        // Normalize theme name
        String normalized = normalizeThemeName(theme);

        // Check cache
        Boolean cached = mThemeToNullBackground.get(normalized);
        if (cached != null) {
            return cached;
        }

        // Check if explicitly set to null
        if (mNullBackgroundStyles.contains(normalized)) {
            mThemeToNullBackground.put(normalized, true);
            return true;
        }

        // Check if explicitly set to non-null
        if (mNonNullBackgroundStyles.contains(normalized)) {
            mThemeToNullBackground.put(normalized, false);
            return false;
        }

        // Check parent
        String parent = mStyleParents.get(normalized);
        if (parent != null) {
            boolean parentResult = hasNullBackground(parent);
            mThemeToNullBackground.put(normalized, parentResult);
            return parentResult;
        }

        // Default: assume theme has a background (conservative)
        mThemeToNullBackground.put(normalized, false);
        return false;
    }

    private String normalizeThemeName(String theme) {
        if (theme == null) {
            return null;
        }
        // Strip @style/ or @android:style/ prefix
        if (theme.startsWith("@style/")) {
            return theme.substring("@style/".length());
        } else if (theme.startsWith("@android:style/")) {
            return theme.substring("@android:style/".length());
        } else if (theme.startsWith("@")) {
            int slash = theme.indexOf('/');
            if (slash != -1) {
                return theme.substring(slash + 1);
            }
        }
        return theme;
    }

    // ---- XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_APPLICATION,
                TAG_ACTIVITY,
                "style"
        );
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == ResourceFolderType.LAYOUT) {
            checkLayoutDocument(context, document);
        }
    }

    private void checkLayoutDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        Attr backgroundAttr = root.getAttributeNodeNS(ANDROID_URI, ATTR_BACKGROUND);
        if (backgroundAttr == null) {
            return;
        }

        String background = backgroundAttr.getValue();
        if (background == null || background.isEmpty()) {
            return;
        }

        // Get layout name from file
        String layoutName = getLayoutName(context.file);
        if (layoutName == null) {
            return;
        }

        mLayoutToBackground.put(layoutName, context.getLocation(backgroundAttr));
        mLayoutToBackgroundElement.put(layoutName, root);
        mLayoutToContext.put(layoutName, context);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();

        if (context.getResourceFolderType() == null) {
            // Manifest file
            visitManifestElement(context, element, tag);
        } else if (context.getResourceFolderType() == ResourceFolderType.VALUES) {
            visitValuesElement(context, element, tag);
        }
    }

    private void visitManifestElement(@NonNull XmlContext context, @NonNull Element element,
            @NonNull String tag) {
        if (TAG_APPLICATION.equals(tag)) {
            String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
            if (theme != null && !theme.isEmpty()) {
                mApplicationTheme = normalizeThemeName(theme);
            }
        } else if (TAG_ACTIVITY.equals(tag)) {
            String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (name != null && !name.isEmpty()) {
                // Normalize activity name
                String pkg = context.getProject().getPackage();
                if (name.startsWith(".") && pkg != null) {
                    name = pkg + name;
                } else if (!name.contains(".") && pkg != null) {
                    name = pkg + "." + name;
                }
                if (theme != null && !theme.isEmpty()) {
                    mActivityToTheme.put(name, normalizeThemeName(theme));
                }
            }
        }
    }

    private void visitValuesElement(@NonNull XmlContext context, @NonNull Element element,
            @NonNull String tag) {
        if ("style".equals(tag)) {
            String name = element.getAttribute("name");
            if (name == null || name.isEmpty()) {
                return;
            }
            // Normalize name (replace . with _? No, keep as-is but handle parent)
            String parent = element.getAttribute("parent");
            if (parent != null && !parent.isEmpty()) {
                mStyleParents.put(name, normalizeThemeName(parent));
            } else {
                // Check for implicit parent via dot notation
                int lastDot = name.lastIndexOf('.');
                if (lastDot != -1) {
                    mStyleParents.put(name, name.substring(0, lastDot));
                }
            }

            // Check for windowBackground item
            NodeList items = element.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                String itemName = item.getAttribute("name");
                if ("android:windowBackground".equals(itemName) || "windowBackground".equals(itemName)) {
                    String value = item.getTextContent();
                    if (value != null) {
                        value = value.trim();
                    }
                    if (isNullOrTransparent(value)) {
                        mNullBackgroundStyles.add(name);
                    } else {
                        mNonNullBackgroundStyles.add(name);
                    }
                    break;
                }
            }
        }
    }

    private boolean isNullOrTransparent(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        if ("@null".equals(value) || "@android:color/transparent".equals(value)) {
            return true;
        }
        if (value.startsWith("#")) {
            // Check for transparent color
            if (value.equals("#00000000") || value.equalsIgnoreCase("#0000")) {
                return true;
            }
            // Check alpha channel
            if (value.length() == 9) { // #AARRGGBB
                String alpha = value.substring(1, 3);
                try {
                    int alphaVal = Integer.parseInt(alpha, 16);
                    if (alphaVal == 0) {
                        return true;
                    }
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        return false;
    }

    @Nullable
    private static String getLayoutName(@NonNull File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            return name.substring(0, dot);
        }
        return name;
    }

    // ---- SourceCodeScanner ----

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("setContentView");
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context,
            @NonNull org.jetbrains.uast.UCallExpression call,
            @NonNull com.intellij.psi.PsiMethod method) {
        List<org.jetbrains.uast.UExpression> args = call.getValueArguments();
        if (args.isEmpty()) {
            return;
        }

        org.jetbrains.uast.UExpression firstArg = args.get(0);
        String resourceName = getLayoutResourceName(firstArg);
        if (resourceName == null) {
            return;
        }

        // Get the containing class (activity)
        org.jetbrains.uast.UClass containingClass = getContainingClass(call);
        if (containingClass == null) {
            return;
        }

        String qualifiedName = containingClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        mActivityToLayout.put(qualifiedName, resourceName);
        mLayoutToActivity.put(resourceName, qualifiedName);
    }

    @Nullable
    private String getLayoutResourceName(@NonNull org.jetbrains.uast.UExpression expression) {
        // Try to resolve R.layout.xxx reference
        if (expression instanceof org.jetbrains.uast.UReferenceExpression) {
            org.jetbrains.uast.UReferenceExpression ref =
                    (org.jetbrains.uast.UReferenceExpression) expression;
            String text = expression.asSourceString();
            if (text != null && text.contains("R.layout.")) {
                int idx = text.lastIndexOf('.');
                if (idx != -1) {
                    return text.substring(idx + 1);
                }
            }
            // Try resolved name
            com.intellij.psi.PsiElement resolved = ref.resolve();
            if (resolved instanceof com.intellij.psi.PsiField) {
                com.intellij.psi.PsiField field = (com.intellij.psi.PsiField) resolved;
                com.intellij.psi.PsiClass containingClass = field.getContainingClass();
                if (containingClass != null && "layout".equals(containingClass.getName())) {
                    return field.getName();
                }
            }
        }
        return null;
    }

    @Nullable
    private org.jetbrains.uast.UClass getContainingClass(
            @NonNull org.jetbrains.uast.UCallExpression call) {
        org.jetbrains.uast.UElement parent = call.getUastParent();
        while (parent != null) {
            if (parent instanceof org.jetbrains.uast.UClass) {
                return (org.jetbrains.uast.UClass) parent;
            }
            parent = parent.getUastParent();
        }
        return null;
    }
}