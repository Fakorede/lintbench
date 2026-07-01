package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RequiredAttributeDetector extends ResourceXmlDetector {

    private static final String GRID_LAYOUT = "GridLayout";

    private static final Set<String> NON_VIEW_TAGS;
    static {
        Set<String> set = new HashSet<>();
        set.add("include");
        set.add("merge");
        set.add("requestFocus");
        set.add("request-focus");
        set.add("layout");
        set.add("data");
        set.add("import");
        set.add("variable");
        set.add("eat-comment");
        NON_VIEW_TAGS = Collections.unmodifiableSet(set);
    }

    private static final Implementation IMPLEMENTATION = new Implementation(
            RequiredAttributeDetector.class,
            Scope.RESOURCE_FILE_SCOPE
    );

    public static final Issue ISSUE = Issue.create(
            "RequiredSize",
            "Missing `layout_width` or `layout_height` attributes",
            "All views must specify an explicit `layout_width` and `layout_height` attribute. There is a runtime check for this, so if you fail to specify a size, an exception is thrown at runtime.\n\n"
                    + "It's possible to specify these widths via styles as well. GridLayout, as a special case, does not require you to specify a size.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            IMPLEMENTATION
    );

    private final Map<String, Set<String>> mStyleAttributes = new HashMap<>();
    private final Map<String, String> mStyleParents = new HashMap<>();
    private final List<PendingSize> mPending = new ArrayList<>();

    @Override
    public void beforeCheckProject(Context context) {
        mStyleAttributes.clear();
        mStyleParents.clear();
        mPending.clear();
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == ResourceFolderType.VALUES) {
            collectStyle(element);
        } else if (folderType == ResourceFolderType.LAYOUT) {
            checkLayout(context, element);
        }
    }

    @Override
    public void afterCheckProject(Context context) {
        for (PendingSize pending : mPending) {
            Set<String> styleAttrs = resolveStyleAttributes(pending.style);
            List<String> missing = new ArrayList<>(pending.missing);
            missing.removeAll(styleAttrs);
            if (!missing.isEmpty()) {
                reportMissing(context, pending.file, pending.element, missing);
            }
        }
        mPending.clear();
    }

    private void collectStyle(Element element) {
        if (!SdkConstants.TAG_STYLE.equals(element.getTagName())) {
            return;
        }
        String name = element.getAttribute(SdkConstants.ATTR_NAME);
        if (name.isEmpty()) {
            return;
        }

        Set<String> attrs = new HashSet<>();
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE
                    || !SdkConstants.TAG_ITEM.equals(child.getNodeName())) {
                continue;
            }
            Element item = (Element) child;
            String attrName = item.getAttribute(SdkConstants.ATTR_NAME);
            if (attrName.isEmpty()) {
                continue;
            }
            attrs.add(normalizeAttributeName(attrName));
        }
        mStyleAttributes.put(name, attrs);

        String parent = element.getAttribute(SdkConstants.ATTR_PARENT);
        if (parent.isEmpty() && name.contains(".")) {
            parent = name.substring(0, name.lastIndexOf('.'));
        }
        if (!parent.isEmpty()) {
            mStyleParents.put(name, parent);
        }
    }

    private void checkLayout(XmlContext context, Element element) {
        if (!isView(element) || isGridLayout(element)) {
            return;
        }

        List<String> missing = new ArrayList<>(2);
        if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH)) {
            missing.add(SdkConstants.ATTR_LAYOUT_WIDTH);
        }
        if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT)) {
            missing.add(SdkConstants.ATTR_LAYOUT_HEIGHT);
        }
        if (missing.isEmpty()) {
            return;
        }

        String style = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_STYLE);
        if (style.isEmpty()) {
            reportMissing(context, context.file, element, missing);
        } else if (getStyleName(style) != null) {
            mPending.add(new PendingSize(context.file, element, style, missing));
        }
    }

    private boolean isView(Element element) {
        String tag = element.getTagName();
        if (tag.contains(".")) {
            return true;
        }
        if (NON_VIEW_TAGS.contains(tag)) {
            return false;
        }
        if ("view".equals(tag) || "fragment".equals(tag) || "Fragment".equals(tag)) {
            return true;
        }
        return tag.length() > 0 && Character.isUpperCase(tag.charAt(0));
    }

    private boolean isGridLayout(Element element) {
        String tag = element.getTagName();
        return GRID_LAYOUT.equals(tag) || tag.endsWith(".GridLayout");
    }

    private Set<String> resolveStyleAttributes(String styleValue) {
        String name = getStyleName(styleValue);
        if (name == null) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>();
        collectStyleAttributes(name, result, new HashSet<String>());
        return result;
    }

    private void collectStyleAttributes(String name, Set<String> result, Set<String> visited) {
        if (!visited.add(name)) {
            return;
        }
        Set<String> attrs = mStyleAttributes.get(name);
        if (attrs != null) {
            result.addAll(attrs);
        }
        String parent = mStyleParents.get(name);
        if (parent != null) {
            collectStyleAttributes(parent, result, visited);
        } else if (name.contains(".")) {
            String inferred = name.substring(0, name.lastIndexOf('.'));
            collectStyleAttributes(inferred, result, visited);
        }
    }

    private String getStyleName(String styleValue) {
        if (styleValue == null || styleValue.isEmpty()) {
            return null;
        }
        if (styleValue.startsWith("@style/")) {
            return styleValue.substring("@style/".length());
        }
        return null;
    }

    private String normalizeAttributeName(String name) {
        int index = name.lastIndexOf(':');
        return index != -1 ? name.substring(index + 1) : name;
    }

    private void reportMissing(Context context, File file, Element element, List<String> missing) {
        String message;
        if (missing.size() == 1) {
            message = "Missing `" + missing.get(0) + "` attribute";
        } else {
            message = "Missing `layout_width` and `layout_height` attributes";
        }
        context.report(ISSUE, Location.create(file, element), message);
    }

    private static class PendingSize {
        final File file;
        final Element element;
        final String style;
        final List<String> missing;

        PendingSize(File file, Element element, String style, List<String> missing) {
            this.file = file;
            this.element = element;
            this.style = style;
            this.missing = new ArrayList<>(missing);
        }
    }
}