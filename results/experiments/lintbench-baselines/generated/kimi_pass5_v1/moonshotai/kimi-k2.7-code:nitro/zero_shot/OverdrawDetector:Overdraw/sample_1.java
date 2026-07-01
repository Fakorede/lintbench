package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OverdrawDetector extends Detector implements Detector.XmlScanner, Detector.SourceCodeScanner {

    private static final String SET_CONTENT_VIEW = "setContentView";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_BACKGROUND = "background";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_THEME = "theme";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_STYLE = "style";
    private static final String TAG_ITEM = "item";
    private static final String R_LAYOUT_PREFIX = "R.layout.";

    public static final Issue ISSUE = Issue.create(
            "Overdraw",
            "Overdraw: Painting regions more than once",
            "If you set a background drawable on a root view, then you should use a custom theme where the theme background is null. Otherwise, the theme background will be painted first, only to have your custom background completely cover it; this is called \"overdraw\".",
            Category.PERFORMANCE,
            6,
            Severity.WARNING,
            new Implementation(OverdrawDetector.class, EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE))
    );

    private Map<String, String> mClassToLayout;
    private Map<String, List<String>> mLayoutToClasses;
    private List<BackgroundInfo> mRootBackgrounds;

    public OverdrawDetector() {
    }

    @NotNull
    @Override
    public Collection<Scope> getApplicableFiles() {
        return EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE);
    }

    @Override
    public void beforeCheckProject(@NotNull Context context) {
        mClassToLayout = new HashMap<>();
        mLayoutToClasses = new HashMap<>();
        mRootBackgrounds = new ArrayList<>();
    }

    @Override
    public void afterCheckProject(@NotNull Context context) {
        if (mRootBackgrounds == null || mLayoutToClasses == null) {
            return;
        }

        for (BackgroundInfo info : mRootBackgrounds) {
            List<String> classes = mLayoutToClasses.get(info.layoutName);
            if (classes == null || classes.isEmpty()) {
                continue;
            }

            for (String activityClass : classes) {
                if (activityHasWindowBackground(context, activityClass)) {
                    context.report(
                            ISSUE,
                            info.location,
                            "Possible overdraw: this layout's root view has a background. If the hosting activity's theme also paints a window background, the theme background will be drawn first and then covered by this background."
                    );
                    break;
                }
            }
        }

        mClassToLayout = null;
        mLayoutToClasses = null;
        mRootBackgrounds = null;
    }

    // ---- Java scanning: map activities to setContentView layout resources ----

    @NotNull
    @Override
    public List<String> getApplicableCallNames() {
        return Collections.singletonList(SET_CONTENT_VIEW);
    }

    @Override
    public void visitCall(@NotNull JavaContext context, @NotNull UCallExpression node) {
        UClass cls = UastUtils.getParentOfType(node, UClass.class, false);
        if (cls == null) {
            return;
        }

        String qualifiedName = cls.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        JavaEvaluator evaluator = context.getEvaluator();
        if (!evaluator.extendsClass(cls, "android.app.Activity", false)
                && !evaluator.extendsClass(cls, "android.support.v7.app.AppCompatActivity", false)
                && !evaluator.extendsClass(cls, "androidx.appcompat.app.AppCompatActivity", false)) {
            return;
        }

        List<UExpression> args = node.getValueArguments();
        if (args.isEmpty()) {
            return;
        }

        UExpression firstArg = args.get(0);
        String source = firstArg.asSourceString();
        if (source == null) {
            return;
        }

        if (source.startsWith(R_LAYOUT_PREFIX)) {
            String layoutName = source.substring(R_LAYOUT_PREFIX.length());
            mClassToLayout.put(qualifiedName, layoutName);
            mLayoutToClasses.computeIfAbsent(layoutName, k -> new ArrayList<>()).add(qualifiedName);
        }
    }

    // ---- XML scanning: collect root-view background attributes ----

    @NotNull
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_BACKGROUND);
    }

    @Override
    public void visitAttribute(@NotNull XmlContext context, @NotNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        Element element = attribute.getOwnerElement();
        Element root = element.getOwnerDocument().getDocumentElement();
        if (element != root) {
            return;
        }

        String tag = element.getTagName();
        if ("merge".equals(tag)) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()
                || value.equals("@null")
                || value.equals("@android:color/transparent")
                || value.equals("#00000000")) {
            return;
        }

        String fileName = context.file.getName();
        int dot = fileName.lastIndexOf('.');
        String layoutName = dot > 0 ? fileName.substring(0, dot) : fileName;

        Location location = context.getLocation(attribute);
        mRootBackgrounds.add(new BackgroundInfo(layoutName, location));
    }

    // ---- Manifest/theme helpers ----

    private boolean activityHasWindowBackground(@NotNull Context context, @NotNull String className) {
        Document manifest = context.getMainProject().getMergingManifest();
        if (manifest == null) {
            return true;
        }

        Element manifestRoot = manifest.getDocumentElement();
        String packageName = manifestRoot.getAttribute("package");
        String targetClass = getManifestClassName(packageName, className);

        String themeRef = null;

        NodeList activities = manifestRoot.getElementsByTagName(TAG_ACTIVITY);
        for (int i = 0, n = activities.getLength(); i < n; i++) {
            Element activity = (Element) activities.item(i);
            String name = activity.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (name.isEmpty()) {
                name = activity.getAttribute(ATTR_NAME);
            }
            String normalized = getManifestClassName(packageName, name);
            if (targetClass.equals(normalized)) {
                String t = activity.getAttributeNS(ANDROID_URI, ATTR_THEME);
                if (!t.isEmpty()) {
                    themeRef = t;
                    break;
                }
            }
        }

        if (themeRef == null || themeRef.isEmpty()) {
            NodeList apps = manifestRoot.getElementsByTagName(TAG_APPLICATION);
            if (apps.getLength() > 0) {
                themeRef = ((Element) apps.item(0)).getAttributeNS(ANDROID_URI, ATTR_THEME);
            }
        }

        if (themeRef == null || themeRef.isEmpty()) {
            return true;
        }
        if ("@null".equals(themeRef)) {
            return false;
        }

        String styleName = getStyleName(themeRef);
        return styleName == null || !isWindowBackgroundNull(context, styleName);
    }

    @NotNull
    private static String getManifestClassName(@Nullable String packageName, @NotNull String className) {
        if (className.isEmpty()) {
            return className;
        }
        if (className.startsWith(".")) {
            return (packageName != null ? packageName : "") + className;
        }
        if (!className.contains(".") && packageName != null && !packageName.isEmpty()) {
            return packageName + "." + className;
        }
        return className;
    }

    @Nullable
    private static String getStyleName(@NotNull String themeRef) {
        int slash = themeRef.lastIndexOf('/');
        if (slash >= 0 && slash < themeRef.length() - 1) {
            return themeRef.substring(slash + 1);
        }
        return themeRef;
    }

    private boolean isWindowBackgroundNull(@NotNull Context context, @NotNull String styleName) {
        for (File folder : context.getMainProject().getResourceFolders()) {
            File values = new File(folder, "values");
            if (!values.isDirectory()) {
                continue;
            }
            File[] files = values.listFiles();
            if (files == null) {
                continue;
            }
            for (File file : files) {
                String name = file.getName();
                if (name.startsWith("styles") && name.endsWith(".xml")) {
                    Document doc = XmlUtils.parseDocumentSilently(file, true);
                    if (doc != null && isStyleWindowBackgroundNull(doc, styleName, new HashSet<>())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isStyleWindowBackgroundNull(@NotNull Document doc, @NotNull String styleName,
                                                @NotNull Set<String> seen) {
        if (!seen.add(styleName)) {
            return false;
        }

        NodeList styles = doc.getElementsByTagName(TAG_STYLE);
        for (int i = 0, n = styles.getLength(); i < n; i++) {
            Element style = (Element) styles.item(i);
            String name = style.getAttribute(ATTR_NAME);
            if (!styleName.equals(name)) {
                continue;
            }

            NodeList items = style.getElementsByTagName(TAG_ITEM);
            for (int j = 0, m = items.getLength(); j < m; j++) {
                Element item = (Element) items.item(j);
                String itemName = item.getAttribute(ATTR_NAME);
                if ("android:windowBackground".equals(itemName) || "windowBackground".equals(itemName)) {
                    String value = item.getTextContent();
                    if (value != null) {
                        value = value.trim();
                        if ("@null".equals(value) || "@android:color/transparent".equals(value)) {
                            return true;
                        }
                    }
                }
            }

            String parent = style.getAttribute("parent");
            if (!parent.isEmpty()) {
                String parentName = getStyleName(parent);
                if (parentName != null && isStyleWindowBackgroundNull(doc, parentName, seen)) {
                    return true;
                }
            }

            return false;
        }

        return false;
    }

    private static class BackgroundInfo {
        @NotNull
        final String layoutName;
        @NotNull
        final Location location;

        BackgroundInfo(@NotNull String layoutName, @NotNull Location location) {
            this.layoutName = layoutName;
            this.location = location;
        }
    }
}