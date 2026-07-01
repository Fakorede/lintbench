package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateDefinition",
                    "Duplicate definitions of resources",
                    "You can define a resource multiple times in different resource folders; that's how string translations are done, for example. However, defining the same resource more than once in the same resource folder is likely an error, for example attempting to add a new resource without realizing that the name is already used, and so on.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final Map<String, Map<String, List<Location>>> mDefinitions = new HashMap<>();

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mDefinitions.clear();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("name");
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        ResourceFolderType folderType =
                ResourceFolderType.getFolderType(context.file.getParentFile().getName());
        if (folderType == null || folderType == ResourceFolderType.VALUES) {
            return;
        }

        String baseName = getBaseName(context.file.getName());
        if (baseName.isEmpty()) {
            return;
        }

        String resourceKey = folderType.getName() + "/" + baseName;
        addLocation(context.file.getParent(), resourceKey, Location.create(context.file));
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String localName = attribute.getLocalName();
        if (localName == null) {
            localName = attribute.getName();
        }
        if (!"name".equals(localName)) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent == null || !(parent instanceof Element)) {
            return;
        }
        if (!"resources".equals(parent.getNodeName())) {
            return;
        }

        String type = getResourceType(element);
        if (type == null) {
            return;
        }

        String name = attribute.getValue();
        if (name == null || name.isEmpty()) {
            return;
        }

        String resourceKey = type + "/" + name;
        addLocation(context.file.getParent(), resourceKey, context.getLocation(element));
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (Map<String, List<Location>> folderMap : mDefinitions.values()) {
            for (Map.Entry<String, List<Location>> entry : folderMap.entrySet()) {
                List<Location> locations = entry.getValue();
                if (locations.size() <= 1) {
                    continue;
                }

                String message =
                        "Duplicate definition of resource `"
                                + entry.getKey()
                                + "` (defined "
                                + locations.size()
                                + " times in the same resource folder)";
                for (Location location : locations) {
                    context.report(ISSUE, location, message);
                }
            }
        }
    }

    private void addLocation(String folderPath, String resourceKey, Location location) {
        Map<String, List<Location>> folderMap = mDefinitions.get(folderPath);
        if (folderMap == null) {
            folderMap = new HashMap<>();
            mDefinitions.put(folderPath, folderMap);
        }

        List<Location> locations = folderMap.get(resourceKey);
        if (locations == null) {
            locations = new ArrayList<>();
            folderMap.put(resourceKey, locations);
        }

        locations.add(location);
    }

    private String getResourceType(Element element) {
        String tag = element.getNodeName();
        if ("item".equals(tag)) {
            Attr typeAttr = element.getAttributeNode("type");
            return typeAttr != null ? typeAttr.getValue() : null;
        }

        if ("resources".equals(tag)
                || "skip".equals(tag)
                || "eat-comment".equals(tag)
                || "public".equals(tag)
                || "java-symbol".equals(tag)
                || "add-resource".equals(tag)) {
            return null;
        }

        return tag;
    }

    private String getBaseName(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index >= 0 ? fileName.substring(0, index) : fileName;
    }
}