package com.android.tools.lint.checks;

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
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.UastUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OverdrawDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "Overdraw",
            "Painting regions more than once",
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
            new Implementation(OverdrawDetector.class, EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE, Scope.MANIFEST))
    );

    private final Map<String, Location> mPendingLayouts = new HashMap<>();
    private final Map<String, Set<String>> mLayoutToActivities = new HashMap<>();
    private final Map<String, String> mThemeMap = new HashMap<>();
    private final Map<String, Boolean> mThemeHasNullBackground = new HashMap<>();
    private final Map<String, String> mStyleParents = new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == ResourceFolderType.LAYOUT) {
            if (element.getParentNode() instanceof Document) {
                String background = element.getAttributeNS("http://schemas.android.com/apk/res/android", "background");
                if (background != null && !background.isEmpty()) {
                    String layoutName = context.file.getName();
                    if (layoutName.endsWith(".xml")) {
                        layoutName = layoutName.substring(0, layoutName.length() - 4);
                    }
                    Attr backgroundAttr = element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "background");
                    if (backgroundAttr != null) {
                        mPendingLayouts.put(layoutName, context.getLocation(backgroundAttr));
                    }
                }
            }
        } else if (folderType == ResourceFolderType.VALUES) {
            String tagName = element.getTagName();
            if ("style".equals(tagName)) {
                String styleName = element.getAttribute("name");
                if (styleName != null && !styleName.isEmpty()) {
                    styleName = "@style/" + styleName;
                    String parent = element.getAttribute("parent");
                    if (parent != null && !parent.isEmpty()) {
                        if (!parent.startsWith("@style/") && !parent.startsWith("@android:style/")) {
                            parent = "@style/" + parent;
                        }
                        mStyleParents.put(styleName, parent);
                    }
                    NodeList children = element.getChildNodes();
                    for (int i = 0; i < children.getLength(); i++) {
                        Node child = children.item(i);
                        if (child instanceof Element) {
                            Element item = (Element) child;
                            if ("item".equals(item.getTagName())) {
                                String itemName = item.getAttribute("name");
                                if ("android:windowBackground".equals(itemName) || "windowBackground".equals(itemName)) {
                                    String value = item.getTextContent().trim();
                                    boolean isNull = "@null".equals(value) || "@android:color/transparent".equals(value) || "#00000000".equals(value);
                                    mThemeHasNullBackground.put(styleName, isNull);
                                }
                            }
                        }
                    }
                }
            }
        } else if (folderType == null && context.file.getName().equals("AndroidManifest.xml")) {
            String tagName = element.getTagName();
            if ("application".equals(tagName)) {
                String theme = element.getAttributeNS("http://schemas.android.com/apk/res/android", "theme");
                if (theme != null && !theme.isEmpty()) {
                    mThemeMap.put("application", theme);
                }
            } else if ("activity".equals(tagName)) {
                String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                String theme = element.getAttributeNS("http://schemas.android.com/apk/res/android", "theme");
                if (name != null && !name.isEmpty()) {
                    if (name.startsWith(".")) {
                        String pkg = context.getProject().getPackage();
                        if (pkg != null) {
                            name = pkg + name;
                        }
                    }
                    if (theme != null && !theme.isEmpty()) {
                        mThemeMap.put(name, theme);
                    }
                }
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression expression) {
                UElement parent = expression.getUastParent();
                if (parent instanceof UQualifiedReferenceExpression) {
                    UQualifiedReferenceExpression qref = (UQualifiedReferenceExpression) parent;
                    String fullRef = qref.asSourceString();
                    if (fullRef.contains("R.layout.")) {
                        String layoutName = fullRef.substring(fullRef.lastIndexOf('.') + 1).trim();
                        UClass uClass = UastUtils.getContainingUClass(expression);
                        if (uClass != null) {
                            String className = uClass.getQualifiedName();
                            if (className != null) {
                                mLayoutToActivities.computeIfAbsent(layoutName, k -> new HashSet<>()).add(className);
                            }
                        }
                    }
                }
            }
        };
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (Map.Entry<String, Location> entry : mPendingLayouts.entrySet()) {
            String layoutName = entry.getKey();
            Location location = entry.getValue();

            Set<String> activities = mLayoutToActivities.get(layoutName);
            boolean overdraw = false;
            String themeUsed = null;

            if (activities != null && !activities.isEmpty()) {
                for (String activity : activities) {
                    String theme = mThemeMap.get(activity);
                    if (theme == null) {
                        theme = mThemeMap.get("application");
                    }
                    if (theme != null) {
                        themeUsed = theme;
                        if (!hasNullBackground(theme)) {
                            overdraw = true;
                            break;
                        }
                    } else {
                        overdraw = true;
                        break;
                    }
                }
            } else {
                String theme = mThemeMap.get("application");
                if (theme != null) {
                    themeUsed = theme;
                    if (!hasNullBackground(theme)) {
                        overdraw = true;
                    }
                } else {
                    overdraw = true;
                }
            }

            if (overdraw) {
                String message = "Possible overdraw: Root element has background, but the theme ("
                        + (themeUsed != null ? themeUsed : "default")
                        + ") also defines a background. Consider using a theme with a null background.";
                context.report(ISSUE, location, message);
            }
        }
    }

    private boolean hasNullBackground(String theme) {
        String currentTheme = theme;
        int maxDepth = 10;
        while (currentTheme != null && maxDepth-- > 0) {
            Boolean hasNull = mThemeHasNullBackground.get(currentTheme);
            if (hasNull != null) {
                return hasNull;
            }
            currentTheme = mStyleParents.get(currentTheme);
        }
        return false;
    }
}