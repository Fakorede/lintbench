package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DuplicateResourceDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateDefinition",
            "Duplicate definitions of resources",
            "You can define a resource multiple times in different resource folders; " +
            "that's how string translations are done, for example. However, defining the same resource " +
            "more than once in the same resource folder is likely an error, for example " +
            "attempting to add a new resource without realizing that the name is already used, and so on.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, Scope.ALL_RESOURCE_FILES)
    );

    private final Map<String, Map<String, Location>> folderResources = new ConcurrentHashMap<>();

    @Override
    public void beforeCheckProject(Context context) {
        folderResources.clear();
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parent = element.getParentNode();
        if (parent == null || parent.getNodeType() != Node.ELEMENT_NODE || !"resources".equals(parent.getNodeName())) {
            return;
        }

        String tagName = element.getTagName();
        if ("resources".equals(tagName) || "eat-comment".equals(tagName) || "skip".equals(tagName)) {
            return;
        }

        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        String normalizedName = name.replace('.', '_');

        String type = tagName;
        if ("item".equals(tagName)) {
            String itemType = element.getAttribute("type");
            if (itemType != null && !itemType.isEmpty()) {
                type = itemType;
            }
        }

        String key = type + "/" + normalizedName;
        String folderPath = context.file.getParent();
        if (folderPath == null) {
            folderPath = context.file.getPath();
        }

        Map<String, Location> seen = folderResources.computeIfAbsent(folderPath, f -> new ConcurrentHashMap<>());
        Location first = seen.get(key);
        if (first != null) {
            String message = String.format("Duplicate definition of resource `%s`", name);
            Location location = context.getLocation(element);
            location.setSecondary(first);
            context.report(ISSUE, location, message);
        } else {
            seen.put(key, context.getLocation(element));
        }
    }
}