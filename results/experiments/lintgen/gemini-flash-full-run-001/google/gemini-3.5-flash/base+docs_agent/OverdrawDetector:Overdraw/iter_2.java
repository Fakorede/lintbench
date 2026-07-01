package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;

import static com.android.SdkConstants.*;

public class OverdrawDetector extends Detector implements SourceCodeScanner, XmlScanner {

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
            EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE, Scope.MANIFEST)
        )
    );

    private final Map<String, Location> layoutBackgrounds = new HashMap<>();
    private final Map<String, List<String>> activityLayouts = new HashMap<>();
    private final Map<String, String> manifestActivityThemes = new HashMap<>();
    private String manifestAppTheme = null;
    private final Map<String, String> styleBackgrounds = new HashMap<>();
    private final Map<String, String> styleParents = new HashMap<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        layoutBackgrounds.clear();
        activityLayouts.clear();
        manifestActivityThemes.clear();
        manifestAppTheme = null;
        styleBackgrounds.clear();
        styleParents.clear();
    }

    // --- XmlScanner ---

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == ResourceFolderType.LAYOUT) {
            Attr backgroundAttr = getBackgroundAttr(root);
            if (backgroundAttr != null) {
                String fileName = context.file.getName();
                if (fileName.endsWith(".xml")) {
                    String layoutName = fileName.substring(0, fileName.length() - 4);
                    Location location = context.getValueLocation(backgroundAttr);
                    layoutBackgrounds.put(layoutName, location);
                }
            }
        } else if (folderType == ResourceFolderType.VALUES) {
            NodeList styles = document.getElementsByTagName(TAG_STYLE);
            for (int i = 0; i < styles.getLength(); i++) {
                Element style = (Element) styles.item(i);
                String styleName = style.getAttribute(ATTR_NAME);
                if (styleName != null && !styleName.isEmpty()) {
                    String parent = style.getAttribute(ATTR_PARENT);
                    if (parent != null && !parent.isEmpty()) {
                        styleParents.put(styleName, parent);
                    } else {
                        int lastDot = styleName.lastIndexOf('.');
                        if (lastDot != -1) {
                            String parentName = styleName.substring(0, lastDot);
                            styleParents.put(styleName, parentName);
                        }
                    }
                    NodeList children = style.getChildNodes();
                    for (int j = 0; j < children.getLength(); j++) {
                        Node child = children.item(j);
                        if (child.getNodeType() == Node.ELEMENT_NODE && TAG_ITEM.equals(child.getNodeName())) {
                            Element item = (Element) child;
                            String name = item.getAttribute(ATTR_NAME);
                            if ("android:windowBackground".equals(name) || "windowBackground".equals(name)) {
                                String value = item.getTextContent().trim();
                                styleBackgrounds.put(styleName, value);
                            }
                        }
                    }
                }
            }
        } else if (FN_ANDROID_MANIFEST_XML.equals(context.file.getName())) {
            NodeList applications = document.getElementsByTagName(TAG_APPLICATION);
            for (int i = 0; i < applications.getLength(); i++) {
                Element app = (Element) applications.item(i);
                String theme = app.getAttributeNS(ANDROID_URI, ATTR_THEME);
                if (theme != null && !theme.isEmpty()) {
                    manifestAppTheme = theme;
                }
            }
            NodeList activities = document.getElementsByTagName(TAG_ACTIVITY);
            for (int i = 0; i < activities.getLength(); i++) {
                Element act = (Element) activities.item(i);
                String name = act.getAttributeNS(ANDROID_URI, ATTR_NAME);
                String theme = act.getAttributeNS(ANDROID_URI, ATTR_THEME);
                if (name != null && !name.isEmpty()) {
                    if (theme != null && !theme.isEmpty()) {
                        manifestActivityThemes.put(name, theme);
                    } else {
                        manifestActivityThemes.put(name, "");
                    }
                }
            }
        }
    }

    @Nullable
    private Attr getBackgroundAttr(Element root) {
        if (root == null) return null;
        Attr attr = root.getAttributeNodeNS(ANDROID_URI, ATTR_BACKGROUND);
        if (attr != null) {
            return attr;
        }
        if ("layout".equals(root.getTagName())) {
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element childElement = (Element) child;
                    if (!"data".equals(childElement.getTagName())) {
                        attr = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_BACKGROUND);
                        if (attr != null) {
                            return attr;
                        }
                    }
                }
            }
        }
        return null;
    }

    // --- SourceCodeScanner ---

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("setContentView", "inflate");
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod method) {
        for (UExpression expression : node.getValueArguments()) {
            String layoutName = extractLayoutName(expression);
            if (layoutName != null) {
                UClass containingClass = getContainingClass(node);
                if (containingClass != null) {
                    String className = containingClass.getQualifiedName();
                    if (className != null) {
                        List<String> list = activityLayouts.computeIfAbsent(layoutName, k -> new ArrayList<>());
                        if (!list.contains(className)) {
                            list.add(className);
                        }
                    }
                }
            }
        }
    }

    @Nullable
    private String extractLayoutName(UExpression expression) {
        String str = expression.asSourceString();
        int index = str.indexOf("R.layout.");
        if (index != -1) {
            String suffix = str.substring(index + 9);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < suffix.length(); i++) {
                char c = suffix.charAt(i);
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

    @Nullable
    private UClass getContainingClass(UElement element) {
        UElement current = element;
        while (current != null) {
            if (current instanceof UClass) {
                return (UClass) current;
            }
            current = current.getUastParent();
        }
        return null;
    }

    // --- Post-processing Analysis ---

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Location> entry : layoutBackgrounds.entrySet()) {
            String layoutName = entry.getKey();
            Location location = entry.getValue();

            List<String> activities = findActivitiesForLayout(layoutName);
            boolean safe = false;
            if (!activities.isEmpty()) {
                boolean allActivitiesSafe = true;
                for (String activity : activities) {
                    String theme = getActivityTheme(activity);
                    if (theme == null || themeHasBackground(theme)) {
                        allActivitiesSafe = false;
                        break;
                    }
                }
                if (allActivitiesSafe) {
                    safe = true;
                }
            } else {
                if (manifestAppTheme != null && !themeHasBackground(manifestAppTheme)) {
                    safe = true;
                }
            }

            if (!safe) {
                String message;
                if (manifestAppTheme != null) {
                    message = String.format(
                        "Possible overdraw: Root element has background, but the theme `%s` " +
                        "does not set `android:windowBackground` to null", manifestAppTheme);
                } else {
                    message = "Possible overdraw: Root element has background, but the theme " +
                        "does not set `android:windowBackground` to null";
                }
                context.report(ISSUE, location, message);
            }
        }
    }

    private List<String> findActivitiesForLayout(String layoutName) {
        List<String> activities = activityLayouts.get(layoutName);
        return activities != null ? activities : Collections.emptyList();
    }

    @Nullable
    private String getActivityTheme(String activityClass) {
        String theme = manifestActivityThemes.get(activityClass);
        if (theme != null) {
            return theme.isEmpty() ? manifestAppTheme : theme;
        }
        for (Map.Entry<String, String> entry : manifestActivityThemes.entrySet()) {
            String manifestName = entry.getKey();
            if (manifestName.startsWith(".")) {
                if (activityClass.endsWith(manifestName)) {
                    String t = entry.getValue();
                    return t.isEmpty() ? manifestAppTheme : t;
                }
            } else {
                if (activityClass.endsWith("." + manifestName)) {
                    String t = entry.getValue();
                    return t.isEmpty() ? manifestAppTheme : t;
                }
            }
        }
        return manifestAppTheme;
    }

    private boolean themeHasBackground(String themeName) {
        if (themeName == null || "@null".equals(themeName) || "null".equals(themeName)) {
            return false;
        }
        if (isKnownTranslucentTheme(themeName)) {
            return false;
        }
        String current = getStyleName(themeName);
        Set<String> visited = new HashSet<>();
        while (current != null && !visited.contains(current)) {
            visited.add(current);
            if (styleBackgrounds.containsKey(current)) {
                String bg = styleBackgrounds.get(current);
                return !"@null".equals(bg) && !"null".equals(bg) && !"@android:color/transparent".equals(bg);
            }
            current = getStyleName(styleParents.get(current));
        }
        return true;
    }

    private boolean isKnownTranslucentTheme(String themeName) {
        if (themeName == null) return false;
        String lower = themeName.toLowerCase(Locale.US);
        return lower.contains("translucent") 
            || lower.contains("nodisplay") 
            || lower.contains("noframe") 
            || lower.contains("wallpaper");
    }

    private String getStyleName(String theme) {
        if (theme == null) {
            return null;
        }
        if (theme.startsWith("@style/")) {
            return theme.substring(7);
        }
        if (theme.startsWith("@android:style/")) {
            return theme.substring(15);
        }
        return theme;
    }
}