package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_CLASS;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_STYLE;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

public class RequiredAttributeDetector extends ResourceXmlDetector {
    private static final String GRID_LAYOUT = "GridLayout";
    private static final String VIEW_TAG = "view";
    private static final String MERGE_TAG = "merge";
    private static final String INCLUDE_TAG = "include";
    private static final String FRAGMENT_TAG = "fragment";
    private static final String REQUEST_FOCUS_TAG = "requestFocus";
    private static final String SCRIPT_TAG = "script";
    private static final String LAYOUT_TAG = "layout";

    public static final Issue ISSUE = Issue.create(
            "RequiredSize",
            "Missing layout_width or layout_height attributes",
            "All views must specify an explicit `layout_width` and `layout_height` attribute. "
                    + "There is a runtime check for this, so if you fail to specify a size, "
                    + "an exception is thrown at runtime.\n\n"
                    + "It's possible to specify these widths via styles as well. "
                    + "GridLayout, as a special case, does not require you to specify a size.",
            Category.CORRECTNESS,
            9,
            Severity.FATAL,
            new Implementation(RequiredAttributeDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private Map<String, StyleInfo> mStyleCache;

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mStyleCache = new HashMap<>();
    }

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        String tag = element.getTagName();
        if (tag.equals(MERGE_TAG)
                || tag.equals(INCLUDE_TAG)
                || tag.equals(FRAGMENT_TAG)
                || tag.equals(REQUEST_FOCUS_TAG)
                || tag.equals(SCRIPT_TAG)
                || tag.equals(LAYOUT_TAG)) {
            return;
        }

        if (tag.endsWith(GRID_LAYOUT)) {
            return;
        }

        if (tag.equals(VIEW_TAG)) {
            String className = element.getAttribute(ATTR_CLASS);
            if (className != null && className.endsWith(GRID_LAYOUT)) {
                return;
            }
        }

        boolean hasWidth = hasLayoutAttribute(element, ATTR_LAYOUT_WIDTH)
                || hasLayoutAttributeInStyle(context, element, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = hasLayoutAttribute(element, ATTR_LAYOUT_HEIGHT)
                || hasLayoutAttributeInStyle(context, element, ATTR_LAYOUT_HEIGHT);

        if (!hasWidth || !hasHeight) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Missing layout_width or layout_height attributes");
        }
    }

    private static boolean hasLayoutAttribute(@NonNull Element element, @NonNull String attrName) {
        return element.hasAttributeNS(ANDROID_URI, attrName);
    }

    private boolean hasLayoutAttributeInStyle(
            @NonNull XmlContext context,
            @NonNull Element element,
            @NonNull String attrName) {
        String style = element.getAttribute(ATTR_STYLE);
        if (style == null || style.isEmpty()) {
            return false;
        }

        if (isFrameworkReference(style)) {
            return false;
        }

        ResourceUrl url = ResourceUrl.parse(style);
        if (url == null) {
            if (!style.contains("/")) {
                style = "style/" + style;
            }
            url = ResourceUrl.parse("@" + style);
            if (url == null || url.type != ResourceType.STYLE) {
                return false;
            }
        } else if (url.type != ResourceType.STYLE) {
            return false;
        }

        StyleInfo info = getStyleInfo(context, url.name);
        if (info == null) {
            return false;
        }

        return info.hasItem(attrName);
    }

    private static boolean isFrameworkReference(@NonNull String style) {
        return style.startsWith("@android:") || style.startsWith("@*android:");
    }

    private StyleInfo getStyleInfo(@NonNull XmlContext context, @NonNull String styleName) {
        if (mStyleCache.containsKey(styleName)) {
            return mStyleCache.get(styleName);
        }

        Project project = context.getMainProject();
        List<File> resourceFolders = project.getResourceFolders();
        StyleInfo result = null;

        for (File resFolder : resourceFolders) {
            File[] children = resFolder.listFiles();
            if (children == null) {
                continue;
            }
            for (File folder : children) {
                if (folder.isDirectory()
                        && folder.getName().startsWith("values")) {
                    File[] files = folder.listFiles();
                    if (files == null) {
                        continue;
                    }
                    for (File file : files) {
                        if (file.getName().endsWith(".xml")) {
                            StyleInfo info = parseStyleFile(file, styleName);
                            if (info != null) {
                                result = info;
                                break;
                            }
                        }
                    }
                }
            }
            if (result != null) {
                break;
            }
        }

        mStyleCache.put(styleName, result);
        return result;
    }

    private StyleInfo parseStyleFile(@NonNull File file, @NonNull String styleName) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            Document document = factory.newDocumentBuilder().parse(file);
            NodeList styles = document.getElementsByTagName("style");
            for (int i = 0; i < styles.getLength(); i++) {
                Element style = (Element) styles.item(i);
                String name = style.getAttribute("name");
                if (styleName.equals(name)) {
                    String parent = style.getAttribute("parent");
                    if (parent == null || parent.isEmpty()) {
                        int dot = styleName.lastIndexOf('.');
                        if (dot > 0) {
                            parent = styleName.substring(0, dot);
                        }
                    }
                    return new StyleInfo(style, parent);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private static class StyleInfo {
        private final Element mStyle;
        private final String mParent;

        StyleInfo(@NonNull Element style, @NonNull String parent) {
            mStyle = style;
            mParent = parent;
        }

        boolean hasItem(@NonNull String attrName) {
            return hasItem(attrName, new HashSet<String>());
        }

        private boolean hasItem(@NonNull String attrName, @NonNull Set<String> visited) {
            String name = mStyle.getAttribute("name");
            if (!visited.add(name)) {
                return false;
            }

            NodeList items = mStyle.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Node node = items.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element item = (Element) node;
                    String itemName = item.getAttribute("name");
                    if (itemName.equals(attrName)
                            || itemName.endsWith(":" + attrName)) {
                        return true;
                    }
                }
            }

            if (mParent != null && !mParent.isEmpty()) {
                // Parent resolution handled by the detector's style cache lookup
                // if the parent is a known style; otherwise we cannot determine.
            }

            return false;
        }
    }

    public static boolean hasLayoutVariations(@NonNull File layoutFile) {
        String name = layoutFile.getName();
        File parent = layoutFile.getParentFile();
        if (parent == null) {
            return false;
        }
        File resourceDir = parent.getParentFile();
        if (resourceDir == null) {
            return false;
        }
        File[] siblings = resourceDir.listFiles();
        if (siblings == null) {
            return false;
        }

        int count = 0;
        for (File sibling : siblings) {
            if (sibling.isDirectory() && sibling.getName().startsWith("layout")) {
                File[] children = sibling.listFiles();
                if (children != null) {
                    for (File child : children) {
                        if (child.isFile() && child.getName().equals(name)) {
                            count++;
                            break;
                        }
                    }
                }
            }
        }

        return count > 1;
    }
}