package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DuplicateResourceDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "DuplicateDefinition",
            "Duplicate definitions of resources",
            "You can define a resource multiple times in different resource folders; " +
            "that's how string translations are done, for example. However, defining " +
            "the same resource more than once in the same resource folder is likely an error, " +
            "for example attempting to add a new resource without realizing that the name is " +
            "already used, and so on.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private Map<File, Map<String, Location>> mFolderResources = new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttribute(SdkConstants.ATTR_NAME);
        if (name.isEmpty()) {
            return;
        }

        String tag = element.getTagName();
        if ("resources".equals(tag) || "public".equals(tag) || "declare-styleable".equals(tag) ||
            "eat-comment".equals(tag) || "skip".equals(tag)) {
            return;
        }

        String type = tag;
        if (SdkConstants.TAG_ITEM.equals(type)) {
            type = element.getAttribute(SdkConstants.ATTR_TYPE);
            if (type.isEmpty()) {
                return;
            }
        }

        type = normalizeType(type);
        String key = type + '/' + name;

        File folder = context.file.getParentFile();
        if (folder == null) {
            return;
        }

        Map<String, Location> resources = mFolderResources.get(folder);
        if (resources == null) {
            resources = new HashMap<>();
            mFolderResources.put(folder, resources);
        }

        Node nameAttr = element.getAttributeNode(SdkConstants.ATTR_NAME);
        Location location = context.getLocation(nameAttr);

        Location existing = resources.get(key);
        if (existing != null) {
            location.setSecondary(existing);
            context.report(ISSUE, location, String.format("Duplicate resource name \"%s\"", name));
        } else {
            resources.put(key, location);
        }
    }

    private static String normalizeType(String type) {
        if ("string-array".equals(type) || "integer-array".equals(type)) {
            return "array";
        }
        return type;
    }

    @Override
    public void afterCheckProject(Context context) {
        mFolderResources.clear();
    }
}