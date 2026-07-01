package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.jetbrains.annotations.NonNull;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private Map<File, Map<String, Location>> folderResources = new HashMap<>();

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        folderResources.clear();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.VALUES) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent == null || !"resources".equals(parent.getNodeName())) {
            return;
        }

        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        String tag = element.getTagName();
        String type = tag;
        if ("item".equals(tag)) {
            String itemType = element.getAttribute("type");
            if (itemType != null && !itemType.isEmpty()) {
                type = itemType;
            }
        }

        String key = type + "/" + name;
        File folder = context.file.getParentFile();
        if (folder == null) {
            return;
        }

        Map<String, Location> seen = folderResources.computeIfAbsent(folder, f -> new HashMap<>());
        Location first = seen.get(key);
        if (first != null) {
            String message = String.format("Duplicate definition of resource `%s`", name);
            Location location = context.getLocation(element);
            location.setSecondary(first);
            context.report(ISSUE, element, location, message);
        } else {
            seen.put(key, context.getLocation(element));
        }
    }
}