package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Collection;
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
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class OverdrawDetector extends LayoutDetector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(OverdrawDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Overdraw: Painting regions more than once",
                    "If you set a background drawable on a root view, then you should use a "
                            + "custom theme where the theme background is null. Otherwise, the theme "
                            + "background will be painted first, only to have your custom background "
                            + "completely cover it; this is called \"overdraw\".\n\n"
                            + "NOTE: This detector relies on figuring out which layouts are associated "
                            + "with which activities based on scanning the Java code, and it's "
                            + "currently doing that using an inexact pattern matching algorithm. "
                            + "Therefore, it can incorrectly conclude which activity the layout is "
                            + "associated with and then wrongly complain that a background-theme is "
                            + "hidden.\n\n"
                            + "If you want your custom background on multiple pages, then you should "
                            + "consider making a custom theme with your custom background and just "
                            + "using that theme instead of a root element background.\n\n"
                            + "Of course it's possible that your custom drawable is translucent and "
                            + "you want it to be mixed with the background. However, you will get "
                            + "better performance if you pre-mix the background with your drawable "
                            + "and use that resulting image or color as a custom theme background instead.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, Attr> mLayoutsWithBackgrounds = new HashMap<>();
    private final Map<String, XmlContext> mLayoutContexts = new HashMap<>();
    private final Set<String> mStylesWithNullBackground = new HashSet<>();
    private final Map<String, List<String>> mClassToLayouts = new HashMap<>();
    private final Map<String, String> mManifestThemes = new HashMap<>();
    private final Set<String> mAllActivities = new HashSet<>();
    private String mDefaultTheme = null;

    public OverdrawDetector() {
        mStylesWithNullBackground.add("Theme.Translucent");
        mStylesWithNullBackground.add("Theme.Translucent.NoTitleBar");
        mStylesWithNullBackground.add("Theme.Translucent.NoTitleBar.Fullscreen");
        mStylesWithNullBackground.add("Theme.Material.Light.NoActionBar.Translucent");
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        parseManifest(context);

        Map<String, List<String>> layoutToClasses = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : mClassToLayouts.entrySet()) {
            String fqcn = entry.getKey();
            for (String layout : entry.getValue()) {
                layoutToClasses.computeIfAbsent(layout, k -> new ArrayList<>()).add(fqcn);
            }
        }

        for (Map.Entry<String, Attr> entry : mLayoutsWithBackgrounds.entrySet()) {
            String layoutName = entry.getKey();
            Attr backgroundAttr = entry.getValue();
            XmlContext xmlContext = mLayoutContexts.get(layoutName);

            if (xmlContext == null) {
                continue;
            }

            List<String> activities = layoutToClasses.get(layoutName);
            if (activities == null || activities.isEmpty()) {
                if (mDefaultTheme != null && !isNullOrTransparentTheme(mDefaultTheme)) {
                    reportOverdraw(xmlContext, backgroundAttr, null, mDefaultTheme);
                }
            } else {
                for (String activity : activities) {
                    String theme = mManifestThemes.get(activity);
                    if (theme == null) {
                        theme = mDefaultTheme;
                    }
                    if (theme != null && !isNullOrTransparentTheme(theme)) {
                        reportOverdraw(xmlContext, backgroundAttr, activity, theme);
                        break;
                    }
                }
            }
        }
    }

    private void parseManifest(Context context) {
        List<java.io.File> manifestFiles = context.getProject().getManifestFiles();
        for (java.io.File file : manifestFiles) {
            try {
                javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                org.w3c.dom.Document doc = factory.newDocumentBuilder().parse(file);
                
                NodeList appElements = doc.getElementsByTagName("application");
                if (appElements.getLength() > 0) {
                    Element app = (Element) appElements.item(0);
                    String appTheme = getThemeName(app.getAttributeNS("http://schemas.android.com/apk/res/android", "theme"));
                    if (appTheme != null) {
                        mDefaultTheme = appTheme;
                    }
                }
                
                NodeList activityElements = doc.getElementsByTagName("activity");
                for (int i = 0; i < activityElements.getLength(); i++) {
                    Element activity = (Element) activityElements.item(i);
                    String name = activity.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                    String theme = activity.getAttributeNS("http://schemas.android.com/apk/res/android", "theme");
                    if (name != null && !name.isEmpty()) {
                        String fqcn = resolveClassName(context, name);
                        if (theme != null && !theme.isEmpty()) {
                            mManifestThemes.put(fqcn, getThemeName(theme));
                        } else if (mDefaultTheme != null) {
                            mManifestThemes.put(fqcn, mDefaultTheme);
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }
    }

    private String resolveClassName(Context context, String name) {
        if (name.startsWith(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                return pkg + name;
            }
        } else if (!name.contains(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                return pkg + "." + name;
            }
        }
        return name;
    }

    private String getThemeName(String themeAttr) {
        if (themeAttr == null || themeAttr.isEmpty()) {
            return null;
        }
        int index = themeAttr.lastIndexOf('/');
        if (index != -1) {
            return themeAttr.substring(index + 1);
        }
        return themeAttr;
    }

    private boolean isNullOrTransparentTheme(String themeName) {
        if (themeName == null) {
            return false;
        }
        if (mStylesWithNullBackground.contains(themeName)) {
            return true;
        }
        String lower = themeName.toLowerCase(java.util.Locale.US);
        return lower.contains("translucent") || lower.contains("transparent");
    }

    private void reportOverdraw(XmlContext context, Attr attribute, String activity, String theme) {
        String message;
        if (activity != null) {
            message = String.format(
                    "Possible overdraw: Root element has background, but the theme of activity `%1$s` (`%2$s`) does not set `android:windowBackground` to `@null`",
                    activity, theme);
        } else {
            message = String.format(
                    "Possible overdraw: Root element has background, but the default theme (`%1$s`) does not set `android:windowBackground` to `@null`",
                    theme);
        }
        context.report(ISSUE, attribute, context.getLocation(attribute), message);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // No-op, handled in visitElement
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No-op
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getResourceFolderType() == ResourceFolderType.LAYOUT) {
            if (element.getParentNode() instanceof org.w3c.dom.Document) {
                Attr backgroundAttr = element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "background");
                if (backgroundAttr != null) {
                    String fileName = context.file.getName();
                    int lastDot = fileName.lastIndexOf('.');
                    String layoutName = lastDot != -1 ? fileName.substring(0, lastDot) : fileName;
                    mLayoutsWithBackgrounds.put(layoutName, backgroundAttr);
                    mLayoutContexts.put(layoutName, context);
                }
            }
        } else if (context.getResourceFolderType() == ResourceFolderType.VALUES) {
            if ("item".equals(element.getTagName())) {
                String name = element.getAttribute("name");
                if ("android:windowBackground".equals(name) || "windowBackground".equals(name)) {
                    String value = element.getTextContent().trim();
                    if ("@null".equals(value) || "@android:color/transparent".equals(value) || "#00000000".equals(value)) {
                        Element parent = (Element) element.getParentNode();
                        if (parent != null && "style".equals(parent.getTagName())) {
                            String styleName = parent.getAttribute("name");
                            if (styleName != null && !styleName.isEmpty()) {
                                mStylesWithNullBackground.add(styleName);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return java.util.Arrays.asList("android.app.Activity", "androidx.appcompat.app.AppCompatActivity");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String fqcn = declaration.getQualifiedName();
        if (fqcn != null) {
            mAllActivities.add(fqcn);
        }
    }

    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        String name = node.getIdentifier();
        UElement parent = node.getUastParent();
        if (parent != null) {
            String s = parent.asSourceString();
            if (s.contains("R.layout.")) {
                String layoutName = getLayoutName(parent instanceof UExpression ? (UExpression) parent : null);
                if (layoutName == null) {
                    layoutName = name;
                }
                UClass uClass = getContainingUClass(node);
                if (uClass != null) {
                    String fqcn = uClass.getQualifiedName();
                    if (fqcn != null) {
                        mClassToLayouts.computeIfAbsent(fqcn, k -> new ArrayList<>()).add(layoutName);
                    }
                }
            }
        }
    }

    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        String methodName = node.getMethodName();
        if ("setContentView".equals(methodName) || "inflate".equals(methodName)) {
            List<UExpression> args = node.getValueArguments();
            for (UExpression arg : args) {
                String layoutName = getLayoutName(arg);
                if (layoutName != null) {
                    UClass uClass = getContainingUClass(node);
                    if (uClass != null) {
                        String fqcn = uClass.getQualifiedName();
                        if (fqcn != null) {
                            mClassToLayouts.computeIfAbsent(fqcn, k -> new ArrayList<>()).add(layoutName);
                        }
                    }
                }
            }
        }
    }

    private UClass getContainingUClass(UElement element) {
        UElement current = element.getUastParent();
        while (current != null) {
            if (current instanceof UClass) {
                return (UClass) current;
            }
            current = current.getUastParent();
        }
        return null;
    }

    private String getLayoutName(UExpression expression) {
        if (expression == null) {
            return null;
        }
        String s = expression.toString();
        int index = s.lastIndexOf("R.layout.");
        if (index != -1) {
            String name = s.substring(index + "R.layout.".length()).trim();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (Character.isJavaIdentifierPart(c)) {
                    sb.append(c);
                } else {
                    break;
                }
            }
            return sb.toString();
        }
        return null;
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Arrays.asList(UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                OverdrawDetector.this.visitSimpleNameReferenceExpression(context, node);
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                OverdrawDetector.this.visitCallExpression(context, node);
            }
        };
    }
}