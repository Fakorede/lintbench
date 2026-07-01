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
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
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
import org.jetbrains.uast.UastUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class OverdrawDetector extends LayoutDetector implements Detector.UastScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    OverdrawDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE, Scope.MANIFEST));

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Overdraw: Painting regions more than once",
                    "If you set a background drawable on a root view, then you should use a "
                            + "custom theme where the theme background is null. Otherwise, the theme "
                            + "background will be painted first, only to have your custom background "
                            + "completely cover it; this is called \"overdraw\".\n"
                            + "\n"
                            + "NOTE: This detector relies on figuring out which layouts are associated "
                            + "with which activities based on scanning the Java code, and it's currently "
                            + "doing that using an inexact pattern matching algorithm. Therefore, it can "
                            + "incorrectly conclude which activity the layout is associated with and then "
                            + "wrongly complain that a background-theme is hidden.\n"
                            + "\n"
                            + "If you want your custom background on multiple pages, then you should "
                            + "consider making a custom theme with your custom background and just using "
                            + "that theme instead of a root element background.\n"
                            + "\n"
                            + "Of course it's possible that your custom drawable is translucent and you "
                            + "want it to be mixed with the background. However, you will get better "
                            + "performance if you pre-mix the background with your drawable and use that "
                            + "resulting image or color as a custom theme background instead.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, List<BackgroundInfo>> mLayoutToBackgrounds = new HashMap<>();
    private final Map<String, String> mJavaActivityToLayout = new HashMap<>();
    private final Map<String, String> mManifestActivityToTheme = new HashMap<>();
    private final Map<String, String> mThemeToParent = new HashMap<>();
    private final Map<String, Boolean> mThemeSendsNullBackground = new HashMap<>();
    private String mPackageName = "";

    private static class BackgroundInfo {
        final Location location;
        final String value;

        BackgroundInfo(Location location, String value) {
            this.location = location;
            this.value = value;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if ("background".equals(name)) {
            Element element = attribute.getOwnerElement();
            if (element.getParentNode().getNodeType() == Node.DOCUMENT_NODE) {
                String layoutName = context.file.getName();
                if (layoutName.endsWith(".xml")) {
                    layoutName = layoutName.substring(0, layoutName.length() - 4);
                }
                Location location = context.getLocation(attribute);
                String value = attribute.getValue();
                List<BackgroundInfo> list = mLayoutToBackgrounds.computeIfAbsent(layoutName, k -> new ArrayList<>());
                list.add(new BackgroundInfo(location, value));
            }
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("background");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("style", "activity", "application", "manifest");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("style".equals(tagName)) {
            String styleName = element.getAttribute("name");
            String parent = element.getAttribute("parent");
            if (parent.isEmpty()) {
                int index = styleName.lastIndexOf('.');
                if (index != -1) {
                    parent = styleName.substring(0, index);
                }
            } else {
                parent = getThemeName(parent);
            }
            if (!styleName.isEmpty()) {
                mThemeToParent.put(styleName, parent);
            }
            NodeList items = element.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                String name = item.getAttribute("name");
                if ("android:windowBackground".equals(name) || "windowBackground".equals(name)) {
                    String value = item.getTextContent().trim();
                    if ("@null".equals(value) || "@empty".equals(value)) {
                        mThemeSendsNullBackground.put(styleName, true);
                    } else {
                        mThemeSendsNullBackground.put(styleName, false);
                    }
                }
            }
        } else if ("activity".equals(tagName)) {
            String name = getAndroidAttribute(element, "name");
            if (!name.isEmpty()) {
                if (name.startsWith(".")) {
                    name = mPackageName + name;
                } else if (!name.contains(".")) {
                    name = mPackageName + "." + name;
                }
                String theme = getAndroidAttribute(element, "theme");
                if (!theme.isEmpty()) {
                    mManifestActivityToTheme.put(name, getThemeName(theme));
                }
            }
        } else if ("application".equals(tagName)) {
            String theme = getAndroidAttribute(element, "theme");
            if (!theme.isEmpty()) {
                mManifestActivityToTheme.put("", getThemeName(theme));
            }
        } else if ("manifest".equals(tagName)) {
            mPackageName = element.getAttribute("package");
        }
    }

    private static String getAndroidAttribute(Element element, String localName) {
        String value = element.getAttributeNS("http://schemas.android.com/apk/res/android", localName);
        if (value.isEmpty()) {
            value = element.getAttribute("android:" + localName);
        }
        return value;
    }

    private static String getThemeName(String theme) {
        if (theme.startsWith("@style/")) {
            return theme.substring("@style/".length());
        }
        if (theme.startsWith("@android:style/")) {
            return theme.substring("@android:style/".length());
        }
        return theme;
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.Activity");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
    }

    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        String argStr = node.asSourceString();
        if (argStr.contains("R.layout.")) {
            int index = argStr.indexOf("R.layout.");
            String layout = argStr.substring(index + "R.layout.".length()).trim();
            layout = cleanLayoutName(layout);
            if (!layout.isEmpty()) {
                UClass cls = UastUtils.getContainingUClass(node);
                if (cls != null) {
                    String qualifiedName = cls.getQualifiedName();
                    if (qualifiedName != null) {
                        mJavaActivityToLayout.put(layout, qualifiedName);
                    }
                }
            }
        }
    }

    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        String name = node.getMethodName();
        if ("setContentView".equals(name) || "inflate".equals(name)) {
            List<UExpression> args = node.getValueArguments();
            for (UExpression arg : args) {
                String argStr = arg.asSourceString();
                if (argStr.contains("R.layout.")) {
                    int index = argStr.indexOf("R.layout.");
                    String layout = argStr.substring(index + "R.layout.".length()).trim();
                    layout = cleanLayoutName(layout);
                    if (!layout.isEmpty()) {
                        UClass cls = UastUtils.getContainingUClass(node);
                        if (cls != null) {
                            String qualifiedName = cls.getQualifiedName();
                            if (qualifiedName != null) {
                                mJavaActivityToLayout.put(layout, qualifiedName);
                            }
                        }
                    }
                }
            }
        }
    }

    private static String cleanLayoutName(String raw) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                sb.append(c);
            } else {
                break;
            }
        }
        return sb.toString();
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                OverdrawDetector.this.visitCallExpression(context, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                OverdrawDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<BackgroundInfo>> entry : mLayoutToBackgrounds.entrySet()) {
            String layoutName = entry.getKey();
            List<BackgroundInfo> backgrounds = entry.getValue();

            String activity = mJavaActivityToLayout.get(layoutName);
            if (activity != null) {
                String theme = mManifestActivityToTheme.get(activity);
                if (theme == null) {
                    theme = mManifestActivityToTheme.get("");
                }

                if (theme != null) {
                    if (!isThemeBackgroundNull(theme)) {
                        for (BackgroundInfo info : backgrounds) {
                            context.report(
                                    ISSUE,
                                    info.location,
                                    "Possible overdraw: Root element paints background "
                                            + info.value
                                            + " over a theme background "
                                            + theme);
                        }
                    }
                }
            }
        }
    }

    private boolean isThemeBackgroundNull(String theme) {
        Boolean sendsNull = mThemeSendsNullBackground.get(theme);
        if (sendsNull != null) {
            return sendsNull;
        }
        String parent = mThemeToParent.get(theme);
        if (parent != null && !parent.isEmpty()) {
            return isThemeBackgroundNull(parent);
        }
        return false;
    }
}