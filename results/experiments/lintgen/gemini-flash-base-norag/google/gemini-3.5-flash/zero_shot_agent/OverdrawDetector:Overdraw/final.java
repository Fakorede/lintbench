package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
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
import com.intellij.psi.PsiClassType;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OverdrawDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
        "Overdraw",
        "Overdraw: Painting regions more than once",
        "If you set a background drawable on a root view, then you should use a custom theme " +
        "where the theme background is null. Otherwise, the theme background will be painted " +
        "first, only to have your custom background completely cover it; this is called \"overdraw\".\n\n" +
        "NOTE: This detector relies on figuring out which layouts are associated with which " +
        "activities based on scanning the Java code, and it's currently doing that using an " +
        "inexact pattern matching algorithm. Therefore, it can incorrectly conclude which " +
        "activity the layout is associated with and then wrongly complain that a background-theme " +
        "is hidden.\n\n" +
        "If you want your custom background on multiple pages, then you should consider making " +
        "a custom theme with your custom background and just using that theme instead of a " +
        "root element background.\n\n" +
        "Of course it's possible that your custom drawable is translucent and you want it to " +
        "be mixed with the background. However, you will get better performance if you pre-mix " +
        "the background with your drawable and use that resulting image or color as a custom " +
        "theme background instead.",
        Category.PERFORMANCE,
        3,
        Severity.WARNING,
        new Implementation(
            OverdrawDetector.class,
            EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE, Scope.JAVA_FILE)
        )
    );

    private final Map<String, List<LayoutInfo>> layoutToBackgrounds = new HashMap<>();
    private final Map<String, Set<String>> activityToLayouts = new HashMap<>();
    private final Map<String, String> activityToTheme = new HashMap<>();
    private final Set<String> themesWithNullBackground = new HashSet<>();
    private final Map<String, String> themeToParent = new HashMap<>();
    private String applicationTheme = null;

    public OverdrawDetector() {
        themesWithNullBackground.add("Theme.Translucent");
        themesWithNullBackground.add("Theme.Translucent.NoTitleBar");
        themesWithNullBackground.add("Theme.Translucent.NoTitleBar.Fullscreen");
        themesWithNullBackground.add("Theme.NoDisplay");
        themesWithNullBackground.add("Theme.Wallpaper");
        themesWithNullBackground.add("Theme.Wallpaper.NoTitleBar");
        themesWithNullBackground.add("Theme.Wallpaper.NoTitleBar.Fullscreen");
        themesWithNullBackground.add("Theme.MaterialComponents.BottomSheetDialog");
        themesWithNullBackground.add("Theme.Design.BottomSheetDialog");
        themesWithNullBackground.add("Theme.AppCompat.Dialog");
    }

    private static class LayoutInfo {
        final String layoutName;
        final Location location;
        final String backgroundValue;

        LayoutInfo(String layoutName, Location location, String backgroundValue) {
            this.layoutName = layoutName;
            this.location = location;
            this.backgroundValue = backgroundValue;
        }
    }

    private void registerLayoutWithBackground(String layoutName, Location location, String backgroundValue) {
        layoutToBackgrounds.computeIfAbsent(layoutName, k -> new ArrayList<>())
                .add(new LayoutInfo(layoutName, location, backgroundValue));
    }

    private void registerActivityLayout(String activityClass, String layoutName) {
        activityToLayouts.computeIfAbsent(activityClass, k -> new HashSet<>()).add(layoutName);
    }

    private void registerActivityTheme(String activityClass, String theme) {
        activityToTheme.put(activityClass, theme);
    }

    private void registerApplicationTheme(String theme) {
        applicationTheme = theme;
    }

    private void registerThemeWithNullBackground(String themeName) {
        themesWithNullBackground.add(normalizeThemeName(themeName));
    }

    private void registerThemeParent(String themeName, String parent) {
        themeToParent.put(normalizeThemeName(themeName), normalizeThemeName(parent));
    }

    private String normalizeThemeName(String theme) {
        if (theme == null) return null;
        if (theme.startsWith("@style/")) {
            return theme.substring("@style/".length());
        }
        if (theme.startsWith("@android:style/")) {
            return theme.substring("@android:style/".length());
        }
        return theme;
    }

    private boolean isNullBackgroundTheme(String themeName) {
        if (themeName == null) return false;
        String normalized = normalizeThemeName(themeName);
        if (themesWithNullBackground.contains(normalized)) {
            return true;
        }
        String parent = themeToParent.get(normalized);
        if (parent != null && isNullBackgroundTheme(parent)) {
            return true;
        }
        int lastDot = normalized.lastIndexOf('.');
        if (lastDot != -1) {
            String parentImplicit = normalized.substring(0, lastDot);
            if (isNullBackgroundTheme(parentImplicit)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        File file = context.file;
        String fileName = file.getName();
        if (fileName.equals("AndroidManifest.xml")) {
            Element rootElement = document.getDocumentElement();
            if (rootElement == null) return;
            String pkg = rootElement.getAttribute("package");
            NodeList activityNodes = document.getElementsByTagName("activity");
            for (int i = 0; i < activityNodes.getLength(); i++) {
                Element activityEl = (Element) activityNodes.item(i);
                String activityClass = activityEl.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                String theme = activityEl.getAttributeNS("http://schemas.android.com/apk/res/android", "theme");
                if (!activityClass.isEmpty()) {
                    String fqcn = activityClass;
                    if (activityClass.startsWith(".")) {
                        fqcn = pkg + activityClass;
                    } else if (!activityClass.contains(".")) {
                        fqcn = pkg + "." + activityClass;
                    }
                    if (!theme.isEmpty()) {
                        registerActivityTheme(fqcn, theme);
                    }
                }
            }
            NodeList appNodes = document.getElementsByTagName("application");
            if (appNodes.getLength() > 0) {
                Element appEl = (Element) appNodes.item(0);
                String theme = appEl.getAttributeNS("http://schemas.android.com/apk/res/android", "theme");
                if (!theme.isEmpty()) {
                    registerApplicationTheme(theme);
                }
            }
        } else {
            ResourceFolderType folderType = context.getResourceFolderType();
            if (folderType == ResourceFolderType.LAYOUT) {
                Element root = document.getDocumentElement();
                if (root != null) {
                    Attr backgroundAttr = root.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "background");
                    if (backgroundAttr != null) {
                        String value = backgroundAttr.getValue();
                        if (!value.equals("@null") && !value.equals("@android:color/transparent")) {
                            String layoutName = fileName.substring(0, fileName.lastIndexOf('.'));
                            registerLayoutWithBackground(layoutName, context.getLocation(backgroundAttr), value);
                        }
                    }
                }
            } else if (folderType == ResourceFolderType.VALUES) {
                NodeList styleNodes = document.getElementsByTagName("style");
                for (int i = 0; i < styleNodes.getLength(); i++) {
                    Element styleEl = (Element) styleNodes.item(i);
                    String styleName = styleEl.getAttribute("name");
                    if (styleName.isEmpty()) continue;

                    String parent = styleEl.getAttribute("parent");
                    if (!parent.isEmpty()) {
                        registerThemeParent(styleName, parent);
                    }

                    NodeList itemNodes = styleEl.getElementsByTagName("item");
                    for (int j = 0; j < itemNodes.getLength(); j++) {
                        Element itemEl = (Element) itemNodes.item(j);
                        String itemName = itemEl.getAttribute("name");
                        if ("android:windowBackground".equals(itemName) || "windowBackground".equals(itemName)) {
                            String value = itemEl.getTextContent().trim();
                            if ("@null".equals(value) || "@android:color/transparent".equals(value) || "null".equals(value)) {
                                registerThemeWithNullBackground(styleName);
                            }
                        }
                    }
                }
            }
        }
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass uClass) {
                String qualifiedName = uClass.getQualifiedName();
                if (qualifiedName == null) return;

                boolean isActivity = false;
                String name = uClass.getName();
                if (name != null && name.endsWith("Activity")) {
                    isActivity = true;
                } else {
                    for (PsiClassType type : uClass.getSuperTypes()) {
                        String superName = type.getClassName();
                        if (superName != null && (superName.contains("Activity") || superName.contains("AppCompatActivity"))) {
                            isActivity = true;
                            break;
                        }
                    }
                }

                if (isActivity) {
                    String source = uClass.asSourceString();
                    if (source != null) {
                        Matcher matcher = Pattern.compile("R\\.layout\\.([a-zA-Z0-9_]+)").matcher(source);
                        while (matcher.find()) {
                            String layoutName = matcher.group(1);
                            registerActivityLayout(qualifiedName, layoutName);
                        }
                    }
                }
            }
        };
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        Map<String, Set<String>> layoutToActivities = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : activityToLayouts.entrySet()) {
            String activity = entry.getKey();
            for (String layout : entry.getValue()) {
                layoutToActivities.computeIfAbsent(layout, k -> new HashSet<>()).add(activity);
            }
        }

        for (Map.Entry<String, List<LayoutInfo>> entry : layoutToBackgrounds.entrySet()) {
            String layoutName = entry.getKey();
            List<LayoutInfo> infos = entry.getValue();

            Set<String> activities = layoutToActivities.get(layoutName);
            if (activities == null || activities.isEmpty()) {
                continue;
            }

            for (String activity : activities) {
                String theme = activityToTheme.get(activity);
                if (theme == null) {
                    theme = applicationTheme;
                }

                if (theme == null || !isNullBackgroundTheme(theme)) {
                    for (LayoutInfo info : infos) {
                        String message = String.format(
                            "Possible overdraw: Root element of layout `%s` has a background `%s`, " +
                            "but the associated activity `%s` has a theme `%s` which does not have a null background.",
                            layoutName, info.backgroundValue, activity, theme != null ? theme : "default"
                        );
                        context.report(ISSUE, info.location, message);
                    }
                    break;
                }
            }
        }
    }
}