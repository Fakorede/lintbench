package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class OverdrawDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "Overdraw",
            "Painting regions more than once",
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
                    Scope.JAVA_AND_RESOURCE_FILES
            )
    );

    private final Map<String, LayoutInfo> layoutsWithRootBackground = new HashMap<>();
    private final Map<String, String> activityToLayout = new HashMap<>();
    private final Map<String, String> layoutToActivity = new HashMap<>();
    private final Map<String, String> activityToTheme = new HashMap<>();
    private final Set<String> themesWithNullBackground = new HashSet<>();
    private final Map<String, String> styleToParent = new HashMap<>();
    private String defaultTheme = null;

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

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == ResourceFolderType.LAYOUT) {
            Element root = document.getDocumentElement();
            if (root != null && root.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BACKGROUND)) {
                Attr backgroundAttr = root.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BACKGROUND);
                if (backgroundAttr != null) {
                    String backgroundValue = backgroundAttr.getValue();
                    String layoutName = Lint.getBaseName(context.file.getName());
                    layoutsWithRootBackground.put(layoutName, new LayoutInfo(layoutName, context.getLocation(backgroundAttr), backgroundValue));
                }
            }
        } else if (folderType == ResourceFolderType.VALUES) {
            NodeList styleNodes = document.getElementsByTagName(SdkConstants.TAG_STYLE);
            for (int i = 0; i < styleNodes.getLength(); i++) {
                Element styleEl = (Element) styleNodes.item(i);
                String styleName = styleEl.getAttribute(SdkConstants.ATTR_NAME);
                if (styleName != null && !styleName.isEmpty()) {
                    String parent = styleEl.getAttribute(SdkConstants.ATTR_PARENT);
                    if (parent == null || parent.isEmpty()) {
                        int lastDot = styleName.lastIndexOf('.');
                        if (lastDot != -1) {
                            parent = styleName.substring(0, lastDot);
                        }
                    }
                    if (parent != null && !parent.isEmpty()) {
                        styleToParent.put(styleName, getShortThemeName(parent));
                    }

                    NodeList itemNodes = styleEl.getElementsByTagName(SdkConstants.TAG_ITEM);
                    for (int j = 0; j < itemNodes.getLength(); j++) {
                        Element itemEl = (Element) itemNodes.item(j);
                        String itemName = itemEl.getAttribute(SdkConstants.ATTR_NAME);
                        if ("android:windowBackground".equals(itemName) || "windowBackground".equals(itemName)) {
                            String value = itemEl.getTextContent().trim();
                            if ("@null".equals(value) || "@android:color/transparent".equals(value)) {
                                themesWithNullBackground.add(styleName);
                            }
                        }
                    }
                }
            }
        } else if (SdkConstants.FN_ANDROID_MANIFEST_XML.equals(context.file.getName())) {
            NodeList activityNodes = document.getElementsByTagName(SdkConstants.TAG_ACTIVITY);
            for (int i = 0; i < activityNodes.getLength(); i++) {
                Element activityEl = (Element) activityNodes.item(i);
                String activityName = activityEl.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                String theme = activityEl.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_THEME);
                if (activityName != null && !activityName.isEmpty()) {
                    if (activityName.startsWith(".")) {
                        String pkg = document.getDocumentElement().getAttribute("package");
                        activityName = pkg + activityName;
                    }
                    if (theme != null && !theme.isEmpty()) {
                        activityToTheme.put(activityName, getShortThemeName(theme));
                    }
                }
            }
            NodeList appNodes = document.getElementsByTagName(SdkConstants.TAG_APPLICATION);
            if (appNodes.getLength() > 0) {
                Element appEl = (Element) appNodes.item(0);
                String theme = appEl.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_THEME);
                if (theme != null && !theme.isEmpty()) {
                    defaultTheme = getShortThemeName(theme);
                }
            }
        }
    }

    private String getShortThemeName(String theme) {
        if (theme.startsWith("@style/")) {
            return theme.substring("@style/".length());
        }
        if (theme.startsWith("@android:style/")) {
            return theme.substring("@android:style/".length());
        }
        return theme;
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("setContentView", "inflate");
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod method) {
        String methodName = method.getName();
        if ("setContentView".equals(methodName)) {
            for (UExpression arg : node.getValueArguments()) {
                String layoutName = getLayoutName(arg);
                if (layoutName != null) {
                    UClass activityClass = UastUtils.getContainingClass(node);
                    if (activityClass != null) {
                        String activityName = activityClass.getQualifiedName();
                        if (activityName != null) {
                            activityToLayout.put(activityName, layoutName);
                            layoutToActivity.put(layoutName, activityName);
                        }
                    }
                }
            }
        } else if ("inflate".equals(methodName)) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null) {
                String qName = containingClass.getQualifiedName();
                if (qName != null && qName.endsWith("Binding")) {
                    String layoutName = getLayoutFromBinding(qName);
                    if (layoutName != null) {
                        UClass activityClass = UastUtils.getContainingClass(node);
                        if (activityClass != null) {
                            String activityName = activityClass.getQualifiedName();
                            if (activityName != null) {
                                activityToLayout.put(activityName, layoutName);
                                layoutToActivity.put(layoutName, activityName);
                            }
                        }
                    }
                }
            }
        }
    }

    private String getLayoutName(UExpression expression) {
        String s = expression.asSourceString();
        int index = s.lastIndexOf("R.layout.");
        if (index != -1) {
            return s.substring(index + "R.layout.".length()).trim();
        }
        return null;
    }

    private String getLayoutFromBinding(String bindingClassName) {
        if (bindingClassName.endsWith("Binding")) {
            String simpleName = bindingClassName;
            int dot = simpleName.lastIndexOf('.');
            if (dot != -1) {
                simpleName = simpleName.substring(dot + 1);
            }
            simpleName = simpleName.substring(0, simpleName.length() - "Binding".length());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < simpleName.length(); i++) {
                char c = simpleName.charAt(i);
                if (Character.isUpperCase(c)) {
                    if (i > 0) {
                        sb.append('_');
                    }
                    sb.append(Character.toLowerCase(c));
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
        return null;
    }

    private boolean hasNullBackground(String theme) {
        String current = theme;
        Set<String> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            if (themesWithNullBackground.contains(current)) {
                return true;
            }
            current = styleToParent.get(current);
        }
        return false;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, LayoutInfo> entry : layoutsWithRootBackground.entrySet()) {
            String layoutName = entry.getKey();
            LayoutInfo info = entry.getValue();

            String activityName = layoutToActivity.get(layoutName);
            if (activityName != null) {
                String theme = activityToTheme.get(activityName);
                if (theme == null) {
                    theme = defaultTheme;
                }

                if (theme != null) {
                    if (!hasNullBackground(theme)) {
                        context.report(
                                ISSUE,
                                info.location,
                                String.format(
                                        "Possible overdraw: Root element has background `%s`, but the theme `%s` does not set the window background to null",
                                        info.backgroundValue,
                                        theme
                                )
                        );
                    }
                } else {
                    context.report(
                            ISSUE,
                            info.location,
                            String.format(
                                    "Possible overdraw: Root element has background `%s`, but the theme does not set the window background to null",
                                    info.backgroundValue
                            )
                    );
                }
            }
        }
    }
}