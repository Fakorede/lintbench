package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.*;
import org.w3c.dom.*;

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

    private Map<String, String> mActivityToTheme;
    private Map<String, Location> mLayoutToBackgroundLocation;
    private Map<String, XmlContext> mLayoutToContext;
    private Map<String, String> mLayoutToActivity;
    private String mApplicationTheme;
    private Map<String, String> mStyleParents;
    private Set<String> mNullBackgroundStyles;
    private Set<String> mNonNullBackgroundStyles;
    private Map<String, Boolean> mNullBackgroundCache;

    public OverdrawDetector() {
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mActivityToTheme = new HashMap<>();
        mLayoutToBackgroundLocation = new HashMap<>();
        mLayoutToContext = new HashMap<>();
        mLayoutToActivity = new HashMap<>();
        mStyleParents = new HashMap<>();
        mNullBackgroundStyles = new HashSet<>();
        mNonNullBackgroundStyles = new HashSet<>();
        mNullBackgroundCache = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mLayoutToBackgroundLocation.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Location> entry : mLayoutToBackgroundLocation.entrySet()) {
            String layout = entry.getKey();
            Location location = entry.getValue();

            String activity = mLayoutToActivity.get(layout);
            if (activity == null) {
                continue;
            }

            String theme = mActivityToTheme.get(activity);
            if (theme == null) {
                theme = mApplicationTheme;
            }

            // If no theme is specified, the default Android theme has a non-null background
            // so we should report overdraw. Only skip if we can confirm null background.
            if (theme != null && hasNullBackground(theme)) {
                continue;
            }

            XmlContext layoutContext = mLayoutToContext.get(layout);
            if (layoutContext != null) {
                layoutContext.report(ISSUE, location,
                        "Possible overdraw: Root element sets a background that will be " +
                        "covered by the theme's window background. " +
                        "Consider using a custom theme with a null window background, " +
                        "or removing the background attribute from the root element.");
            }
        }
    }

    private boolean hasNullBackground(@NonNull String theme) {
        Boolean cached = mNullBackgroundCache.get(theme);
        if (cached != null) {
            return cached;
        }

        mNullBackgroundCache.put(theme, false);

        if (mNullBackgroundStyles.contains(theme)) {
            mNullBackgroundCache.put(theme, true);
            return true;
        }

        if (mNonNullBackgroundStyles.contains(theme)) {
            mNullBackgroundCache.put(theme, false);
            return false;
        }

        String parent = mStyleParents.get(theme);
        if (parent != null) {
            boolean result = hasNullBackground(parent);
            mNullBackgroundCache.put(theme, result);
            return result;
        }

        return false;
    }

    @NonNull
    private String normalizeThemeName(@NonNull String theme) {
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

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_APPLICATION,
                TAG_ACTIVITY,
                "style"
        );
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_BACKGROUND);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ATTR_BACKGROUND.equals(attribute.getLocalName())) {
            return;
        }
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        Element element = attribute.getOwnerElement();
        Document document = element.getOwnerDocument();
        if (document == null || !element.equals(document.getDocumentElement())) {
            return;
        }

        String background = attribute.getValue();
        if (background == null || background.isEmpty()) {
            return;
        }

        String layoutName = getLayoutName(context.file);
        if (layoutName == null) {
            return;
        }

        mLayoutToBackgroundLocation.put(layoutName, context.getLocation(attribute));
        mLayoutToContext.put(layoutName, context);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        ResourceFolderType folderType = context.getResourceFolderType();

        if (TAG_APPLICATION.equals(tag) && folderType == null) {
            String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
            if (theme != null && !theme.isEmpty()) {
                mApplicationTheme = normalizeThemeName(theme);
            }
        } else if (TAG_ACTIVITY.equals(tag) && folderType == null) {
            String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (name != null && !name.isEmpty()) {
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
        } else if ("style".equals(tag) && folderType == ResourceFolderType.VALUES) {
            String name = element.getAttribute(ATTR_NAME);
            if (name == null || name.isEmpty()) {
                return;
            }

            String parent = element.getAttribute(ATTR_PARENT);
            if (parent != null && !parent.isEmpty()) {
                mStyleParents.put(name, normalizeThemeName(parent));
            } else {
                int lastDot = name.lastIndexOf('.');
                if (lastDot != -1) {
                    mStyleParents.put(name, name.substring(0, lastDot));
                }
            }

            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element item = (Element) child;
                    if ("item".equals(item.getTagName())) {
                        String itemName = item.getAttribute(ATTR_NAME);
                        if ("android:windowBackground".equals(itemName)
                                || "windowBackground".equals(itemName)) {
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
        }
    }

    private boolean isNullOrTransparent(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        if ("@null".equals(value) || "@android:color/transparent".equals(value)) {
            return true;
        }
        if (value.startsWith("#")) {
            if (value.equalsIgnoreCase("#00000000") || value.equalsIgnoreCase("#0000")) {
                return true;
            }
            if (value.length() == 9) {
                try {
                    int alpha = Integer.parseInt(value.substring(1, 3), 16);
                    if (alpha == 0) {
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

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("setContentView");
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        List<UExpression> args = call.getValueArguments();
        if (args.isEmpty()) {
            return;
        }

        UExpression firstArg = args.get(0);
        String resourceName = getLayoutResourceName(firstArg);
        if (resourceName == null) {
            return;
        }

        UClass containingClass = getContainingUClass(call);
        if (containingClass == null) {
            return;
        }

        String qualifiedName = containingClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        mLayoutToActivity.put(resourceName, qualifiedName);
    }

    @Nullable
    private String getLayoutResourceName(@NonNull UExpression expression) {
        if (expression instanceof UReferenceExpression) {
            UReferenceExpression ref = (UReferenceExpression) expression;
            PsiElement resolved = ref.resolve();
            if (resolved instanceof PsiField) {
                PsiField field = (PsiField) resolved;
                com.intellij.psi.PsiClass containingClass = field.getContainingClass();
                if (containingClass != null && "layout".equals(containingClass.getName())) {
                    return field.getName();
                }
            }
        }

        String text = expression.asSourceString();
        if (text != null && text.contains("R.layout.")) {
            int idx = text.lastIndexOf('.');
            if (idx != -1) {
                String name = text.substring(idx + 1).trim();
                if (!name.isEmpty()) {
                    return name;
                }
            }
        }

        return null;
    }

    @Nullable
    private UClass getContainingUClass(@NonNull UElement element) {
        UElement parent = element.getUastParent();
        while (parent != null) {
            if (parent instanceof UClass) {
                return (UClass) parent;
            }
            parent = parent.getUastParent();
        }
        return null;
    }
}