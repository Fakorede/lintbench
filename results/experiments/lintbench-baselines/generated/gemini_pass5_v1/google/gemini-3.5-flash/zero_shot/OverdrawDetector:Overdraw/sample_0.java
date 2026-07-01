package com.android.tools.lint.checks;

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
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OverdrawDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "Overdraw",
            "Overdraw: Painting regions more than once",
            "If you set a background drawable on a root view, then you should use a  " +
            "custom theme where the theme background is null. Otherwise, the theme background  " +
            "will be painted first, only to have your custom background completely cover it;  " +
            "this is called \"overdraw\".\n\n" +
            "NOTE: This detector relies on figuring out which layouts are associated with  " +
            "which activities based on scanning the Java code, and it's currently doing that  " +
            "using an inexact pattern matching algorithm. Therefore, it can incorrectly  " +
            "conclude which activity the layout is associated with and then wrongly complain  " +
            "that a background-theme is hidden.\n\n" +
            "If you want your custom background on multiple pages, then you should consider  " +
            "making a custom theme with your custom background and just using that theme  " +
            "instead of a root element background.\n\n" +
            "Of course it's possible that your custom drawable is translucent and you want  " +
            "it to be mixed with the background. However, you will get better performance  " +
            "if you pre-mix the background with your drawable and use that resulting image or  " +
            "color as a custom theme background instead.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(OverdrawDetector.class, Scope.JAVA_AND_RESOURCE_FILES)
    );

    private final List<PendingLayout> mPendingLayouts = new ArrayList<>();
    private final Map<String, String> mLayoutToActivity = new HashMap<>();
    private final Map<String, String> mActivityToTheme = new HashMap<>();
    private String mApplicationTheme = null;
    private final Map<String, Boolean> mThemeToNullWindowBackground = new HashMap<>();
    private final Map<String, String> mThemeParents = new HashMap<>();

    private static class PendingLayout {
        final String layoutName;
        final String backgroundValue;
        final Location location;

        PendingLayout(String layoutName, String backgroundValue, Location location) {
            this.layoutName = layoutName;
            this.backgroundValue = backgroundValue;
            this.location = location;
        }
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        ResourceFolderType folderType = context.getFolderType();
        if (folderType == ResourceFolderType.LAYOUT) {
            Element root = document.getDocumentElement();
            if (root != null) {
                Attr backgroundAttr = root.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "background");
                if (backgroundAttr != null) {
                    String backgroundValue = backgroundAttr.getValue();
                    if (backgroundValue != null && !backgroundValue.isEmpty() && !backgroundValue.equals("@null")) {
                        String fileName = context.file.getName();
                        int dot = fileName.lastIndexOf('.');
                        String layoutName = dot != -1 ? fileName.substring(0, dot) : fileName;
                        Location location = context.getValueLocation(backgroundAttr);
                        mPendingLayouts.add(new PendingLayout(layoutName, backgroundValue, location));
                    }
                }
            }
        } else if (folderType == ResourceFolderType.VALUES) {
            NodeList styles = document.getElementsByTagName("style");
            for (int i = 0; i < styles.getLength(); i++) {
                Node node = styles.item(i);
                if (node instanceof Element) {
                    Element style = (Element) node;
                    String styleName = style.getAttribute("name");
                    if (styleName.isEmpty()) continue;

                    String parent = style.getAttribute("parent");
                    if (!parent.isEmpty()) {
                        mThemeParents.put(styleName, normalizeTheme(parent));
                    }

                    NodeList items = style.getElementsByTagName("item");
                    for (int j = 0; j < items.getLength(); j++) {
                        Node itemNode = items.item(j);
                        if (itemNode instanceof Element) {
                            Element item = (Element) itemNode;
                            String itemName = item.getAttribute("name");
                            if ("android:windowBackground".equals(itemName) || "windowBackground".equals(itemName)) {
                                String itemValue = item.getTextContent().trim();
                                boolean isNull = "@null".equals(itemValue) || "null".equals(itemValue);
                                mThemeToNullWindowBackground.put(styleName, isNull);
                            }
                        }
                    }
                }
            }
        } else if (folderType == null) {
            if ("AndroidManifest.xml".equals(context.file.getName())) {
                Element root = document.getDocumentElement();
                if (root != null) {
                    NodeList apps = root.getElementsByTagName("application");
                    if (apps.getLength() > 0) {
                        Element app = (Element) apps.item(0);
                        String appTheme = app.getAttributeNS("http://schemas.android.com/apk/res/android", "theme");
                        if (!appTheme.isEmpty()) {
                            mApplicationTheme = normalizeTheme(appTheme);
                        }

                        NodeList activities = app.getElementsByTagName("activity");
                        for (int i = 0; i < activities.getLength(); i++) {
                            Element activity = (Element) activities.item(i);
                            String activityName = activity.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                            if (!activityName.isEmpty()) {
                                String fqName = activityName;
                                if (fqName.startsWith(".")) {
                                    String pkg = root.getAttribute("package");
                                    fqName = pkg + fqName;
                                }
                                String actTheme = activity.getAttributeNS("http://schemas.android.com/apk/res/android", "theme");
                                if (!actTheme.isEmpty()) {
                                    mActivityToTheme.put(fqName, normalizeTheme(actTheme));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("setContentView");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        List<UExpression> args = node.getValueArguments();
        if (!args.isEmpty()) {
            UExpression firstArg = args.get(0);
            String layoutName = extractLayoutName(firstArg);
            if (layoutName != null) {
                UClass surroundingClass = UastUtils.getContainingClass(node);
                if (surroundingClass != null) {
                    String fqName = surroundingClass.getQualifiedName();
                    if (fqName != null) {
                        mLayoutToActivity.put(layoutName, fqName);
                    }
                }
            }
        }
    }

    private String extractLayoutName(UExpression arg) {
        String source = arg.asSourceString();
        int layoutIdx = source.indexOf(".layout.");
        if (layoutIdx != -1) {
            return source.substring(layoutIdx + ".layout.".length()).trim();
        }
        return null;
    }

    private String normalizeTheme(String theme) {
        if (theme == null) return null;
        if (theme.startsWith("@style/")) {
            return theme.substring("@style/".length());
        }
        if (theme.startsWith("@android:style/")) {
            return theme.substring("@android:style/".length());
        }
        return theme;
    }

    private String getThemeForActivity(String activityFqName) {
        if (activityFqName == null) return null;
        String theme = mActivityToTheme.get(activityFqName);
        if (theme != null) return theme;

        String simpleName = activityFqName;
        int lastDot = activityFqName.lastIndexOf('.');
        if (lastDot != -1) {
            simpleName = activityFqName.substring(lastDot + 1);
        }

        for (Map.Entry<String, String> entry : mActivityToTheme.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith("." + simpleName) || key.equals(simpleName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean hasNullWindowBackground(String themeName) {
        Set<String> visited = new HashSet<>();
        while (themeName != null && visited.add(themeName)) {
            Boolean val = mThemeToNullWindowBackground.get(themeName);
            if (val != null) {
                return val;
            }
            themeName = mThemeParents.get(themeName);
        }
        return false;
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (PendingLayout layout : mPendingLayouts) {
            String activityClass = mLayoutToActivity.get(layout.layoutName);
            String theme = null;
            if (activityClass != null) {
                theme = getThemeForActivity(activityClass);
            }
            if (theme == null) {
                theme = mApplicationTheme;
            }

            boolean hasNullTheme = false;
            if (theme != null) {
                hasNullTheme = hasNullWindowBackground(theme);
            }

            if (!hasNullTheme) {
                context.report(ISSUE, layout.location,
                        "Possible overdraw: Root element paints background '" + layout.backgroundValue +
                        "' over a theme that also defines a background");
            }
        }
    }
}