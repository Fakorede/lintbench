package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Attr;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateDefinition",
                    "Duplicate definitions of resources",
                    "You can define a resource multiple times in different resource folders; "
                            + "that's how string translations are done, for example. However, "
                            + "defining the same resource more than once in the same resource "
                            + "folder is likely an error, for example attempting to add a new "
                            + "resource without realizing that the name is already used, and so on.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final java.util.Map<java.io.File, java.util.Map<String, com.android.tools.lint.detector.api.Location>> map =
            new java.util.HashMap<>();

    @Override
    public Collection<String> getApplicableAttributes() {
        return java.util.Collections.singletonList("name");
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        map.clear();
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getValue();
        if (name == null || name.isEmpty()) {
            return;
        }

        org.w3c.dom.Element element = attribute.getOwnerElement();
        org.w3c.dom.Node parent = element.getParentNode();
        if (parent != null && "resources".equals(parent.getNodeName())) {
            String type = element.getTagName();
            if ("item".equals(type)) {
                type = element.getAttribute("type");
            }
            if (type == null || type.isEmpty() || "public".equals(type) || "public-group".equals(type)) {
                return;
            }

            String key = type + "/" + name;
            java.io.File folder = context.getFolder();
            if (folder != null) {
                java.util.Map<String, com.android.tools.lint.detector.api.Location> folderMap = map.get(folder);
                if (folderMap == null) {
                    folderMap = new java.util.HashMap<>();
                    map.put(folder, folderMap);
                }
                if (folderMap.containsKey(key)) {
                    com.android.tools.lint.detector.api.Location originalLocation = folderMap.get(key);
                    com.android.tools.lint.detector.api.Location currentLocation = context.getLocation(attribute);
                    if (originalLocation != null) {
                        originalLocation.setMessage("Original definition here");
                        currentLocation.setSecondary(originalLocation);
                    }
                    context.report(ISSUE, attribute, currentLocation, "Duplicate definition of resource `" + key + "`");
                } else {
                    folderMap.put(key, context.getLocation(attribute));
                }
            }
        }
    }
}