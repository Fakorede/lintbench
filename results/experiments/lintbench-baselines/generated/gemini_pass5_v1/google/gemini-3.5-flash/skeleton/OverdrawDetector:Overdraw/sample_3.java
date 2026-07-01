package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class OverdrawDetector extends LayoutDetector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(OverdrawDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Overdraw: Painting regions more than once",
                    "If you set a background drawable on a root view, then you should use a " +
                    "custom theme where the theme background is null. Otherwise, the theme " +
                    "background will be painted first, only to have your custom background " +
                    "completely cover it; this is called \"overdraw\".\n\n" +
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
                    IMPLEMENTATION);

    private final java.util.Map<String, LayoutBackgroundInfo> mLayoutsWithBackground = new java.util.HashMap<>();
    private final java.util.Map<String, java.util.List<String>> mLayoutToActivity = new java.util.HashMap<>();
    private final java.util.Map<String, StyleInfo> mStyles = new java.util.HashMap<>();
    private final java.util.Map<String, String> mActivityToTheme = new java.util.HashMap<>();
    private boolean mManifestParsed = false;

    private static class LayoutBackgroundInfo {
        final String name;
        final String value;
        final com.android.tools.lint.detector.api.Location location;

        LayoutBackgroundInfo(String name, String value, com.android.tools.lint.detector.api.Location location) {
            this.name = name;
            this.value = value;
            this.location = location;
        }
    }

    private static class StyleInfo {
        final String name;
        final String parent;
        final boolean hasNullBackground;

        StyleInfo(String name, String parent, boolean hasNullBackground) {
            this.name = name;
            this.parent = parent;
            this.hasNullBackground = hasNullBackground;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (!mManifestParsed) {
            parseManifest(context);
            mManifestParsed = true;
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (context.getResourceFolderType() == ResourceFolderType.LAYOUT) {
            Element element = attribute.getOwnerElement();
            if (element != null && element.getParentNode() instanceof Document) {
                String layoutName = getLayoutName(context);
                String backgroundValue = attribute.getValue();
                LayoutBackgroundInfo info = new LayoutBackgroundInfo(layoutName, backgroundValue, context.getLocation(attribute));
                mLayoutsWithBackground.put(layoutName, info);
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getResourceFolderType() == ResourceFolderType.VALUES) {
            if ("style".equals(element.getTagName())) {
                String styleName = element.getAttribute("name");
                if (!styleName.isEmpty()) {
                    String parent = element.getAttribute("parent");
                    if (parent.isEmpty()) {
                        parent = null;
                    }
                    boolean hasNullBackground = false;
                    NodeList items = element.getElementsByTagName("item");
                    for (int i = 0; i < items.getLength(); i++) {
                        Element item = (Element) items.item(i);
                        String name = item.getAttribute("name");
                        if ("android:windowBackground".equals(name) || "windowBackground".equals(name)) {
                            String value = item.getTextContent().trim();
                            if ("@null".equals(value) || "@empty".equals(value) || "null".equals(value)) {
                                hasNullBackground = true;
                            }
                        }
                    }
                    StyleInfo styleInfo = new StyleInfo(styleName, parent, hasNullBackground);
                    mStyles.put(styleName, styleInfo);
                }
            }
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return java.util.Collections.singletonList("background");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("style");
    }

    @Override
    public List<String> applicableSuperClasses() {
        return java.util.Arrays.asList(
            "android.app.Activity",
            "androidx.appcompat.app.AppCompatActivity",
            "android.support.v7.app.AppCompatActivity"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Handled by UastHandler
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Arrays.asList(
            org.jetbrains.uast.USimpleNameReferenceExpression.class,
            org.jetbrains.uast.UCallExpression.class
        );
    }

    @Override
    public com.android.tools.lint.client.api.UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new com.android.tools.lint.client.api.UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(@NonNull org.jetbrains.uast.USimpleNameReferenceExpression node) {
                OverdrawDetector.this.visitSimpleNameReferenceExpression(context, node);
            }

            @Override
            public void visitCallExpression(@NonNull org.jetbrains.uast.UCallExpression node) {
                OverdrawDetector.this.visitCallExpression(context, node);
            }
        };
    }

    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull org.jetbrains.uast.USimpleNameReferenceExpression node) {
        String identifier = node.getIdentifier();
        UElement parent = node.getUastParent();
        if (parent instanceof org.jetbrains.uast.UQualifiedReferenceExpression) {
            org.jetbrains.uast.UQualifiedReferenceExpression qualified = (org.jetbrains.uast.UQualifiedReferenceExpression) parent;
            String receiverText = qualified.getReceiver().asSourceString();
            if (receiverText.endsWith("R.layout") || receiverText.contains("R.layout.")) {
                String layoutName = identifier;
                UClass enclosingClass = getEnclosingClass(node);
                if (enclosingClass != null) {
                    String className = enclosingClass.getQualifiedName();
                    if (className != null) {
                        addLayoutToActivity(layoutName, className);
                    }
                }
            }
        }
    }

    public void visitCallExpression(@NonNull JavaContext context, @NonNull org.jetbrains.uast.UCallExpression node) {
        String methodName = node.getMethodName();
        if ("setContentView".equals(methodName)) {
            List<org.jetbrains.uast.UExpression> args = node.getValueArguments();
            if (!args.isEmpty()) {
                org.jetbrains.uast.UExpression firstArg = args.get(0);
                String argText = firstArg.asSourceString();
                int lastDot = argText.lastIndexOf('.');
                if (lastDot != -1 && argText.contains("R.layout")) {
                    String layoutName = argText.substring(lastDot + 1).trim();
                    UClass enclosingClass = getEnclosingClass(node);
                    if (enclosingClass != null) {
                        String className = enclosingClass.getQualifiedName();
                        if (className != null) {
                            addLayoutToActivity(layoutName, className);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (java.util.Map.Entry<String, LayoutBackgroundInfo> entry : mLayoutsWithBackground.entrySet()) {
            String layoutName = entry.getKey();
            LayoutBackgroundInfo info = entry.getValue();
            
            java.util.List<String> activities = mLayoutToActivity.get(layoutName);
            if (activities != null) {
                for (String activity : activities) {
                    String theme = mActivityToTheme.get(activity);
                    if (theme == null || !themeHasNullBackground(theme)) {
                        String message = String.format(
                            "Possible overdraw: Root element of `%1$s` specifies a background: `%2$s`, " +
                            "but the theme of activity `%3$s` (`%4$s`) does not have a null/empty background",
                            layoutName + ".xml", info.value, activity, theme != null ? theme : "default"
                        );
                        context.report(ISSUE, info.location, message);
                        break;
                    }
                }
            }
        }
    }

    private String getLayoutName(XmlContext context) {
        String name = context.file.getName();
        int dot = name.indexOf('.');
        if (dot != -1) {
            return name.substring(0, dot);
        }
        return name;
    }

    private UClass getEnclosingClass(UElement node) {
        UElement current = node;
        while (current != null) {
            if (current instanceof UClass) {
                return (UClass) current;
            }
            current = current.getUastParent();
        }
        return null;
    }

    private void addLayoutToActivity(String layoutName, String activityClass) {
        List<String> activities = mLayoutToActivity.computeIfAbsent(layoutName, k -> new java.util.ArrayList<>());
        if (!activities.contains(activityClass)) {
            activities.add(activityClass);
        }
    }

    private boolean themeHasNullBackground(String themeName) {
        if (themeName == null || themeName.isEmpty()) {
            return false;
        }
        String cleanName = themeName;
        if (cleanName.startsWith("@style/")) {
            cleanName = cleanName.substring("@style/".length());
        } else if (cleanName.startsWith("@android:style/")) {
            cleanName = cleanName.substring("@android:style/".length());
        }
        return checkStyleNullBackground(cleanName, 0);
    }

    private boolean checkStyleNullBackground(String styleName, int depth) {
        if (depth > 20) {
            return false;
        }
        StyleInfo info = mStyles.get(styleName);
        if (info != null) {
            if (info.hasNullBackground) {
                return true;
            }
            if (info.parent != null && !info.parent.isEmpty()) {
                return themeHasNullBackground(info.parent);
            }
        }
        int lastDot = styleName.lastIndexOf('.');
        if (lastDot != -1) {
            String implicitParent = styleName.substring(0, lastDot);
            return checkStyleNullBackground(implicitParent, depth + 1);
        }
        return false;
    }

    private void parseManifest(Context context) {
        for (java.io.File file : context.getProject().getManifestFiles()) {
            try {
                CharSequence contents = context.getClient().readFile(file);
                if (contents == null || contents.length() == 0) {
                    continue;
                }
                javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
                org.w3c.dom.Document doc = builder.parse(new java.io.ByteArrayInputStream(contents.toString().getBytes("UTF-8")));
                if (doc != null) {
                    org.w3c.dom.Element root = doc.getDocumentElement();
                    if (root != null) {
                        String pkg = root.getAttribute("package");
                        org.w3c.dom.NodeList appList = root.getElementsByTagName("application");
                        String appTheme = null;
                        if (appList.getLength() > 0) {
                            org.w3c.dom.Element app = (org.w3c.dom.Element) appList.item(0);
                            appTheme = app.getAttributeNS("http://schemas.android.com/apk/res/android", "theme");
                            if (appTheme.isEmpty()) {
                                appTheme = app.getAttribute("android:theme");
                            }
                        }
                        org.w3c.dom.NodeList actList = root.getElementsByTagName("activity");
                        for (int i = 0; i < actList.getLength(); i++) {
                            org.w3c.dom.Element act = (org.w3c.dom.Element) actList.item(i);
                            String name = act.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                            if (name.isEmpty()) {
                                name = act.getAttribute("android:name");
                            }
                            String theme = act.getAttributeNS("http://schemas.android.com/apk/res/android", "theme");
                            if (theme.isEmpty()) {
                                theme = act.getAttribute("android:theme");
                            }
                            if (theme.isEmpty() && appTheme != null && !appTheme.isEmpty()) {
                                theme = appTheme;
                            }
                            if (!name.isEmpty()) {
                                if (name.startsWith(".")) {
                                    name = pkg + name;
                                } else if (!name.contains(".")) {
                                    name = pkg + "." + name;
                                }
                                mActivityToTheme.put(name, theme);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }
    }
}