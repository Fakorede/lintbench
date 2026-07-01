package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
                    "You can define a resource multiple times in different resource folders; "
                            + "that is how string translations are done, for example. However, "
                            + "defining the same resource more than once in the same resource "
                            + "folder is likely an error, for example attempting to add a new "
                            + "resource without realizing that the name is already used. Note "
                            + "that a resource name is unique per resource type, so two resources "
                            + "of different types can share the same name.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final Map<File, Map<String, Location>> mLocations = new HashMap<>();

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("name");
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mLocations.clear();
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File folder = context.file.getParentFile();
        if (folder != null && !mLocations.containsKey(folder)) {
            mLocations.put(folder, new HashMap<String, Location>());
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!"name".equals(attribute.getName())) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent == null || !"resources".equals(parent.getNodeName())) {
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

        File folder = context.file.getParentFile();
        if (folder == null) {
            return;
        }

        Map<String, Location> folderMap = mLocations.get(folder);
        if (folderMap == null) {
            return;
        }

        String key = type + "/" + name;
        Location previous = folderMap.get(key);
        Location current = context.getLocation(attribute);
        if (previous != null) {
            String message =
                    String.format(
                            "Duplicate definition of resource `%1$s/%2$s`; it was previously defined in `%3$s`",
                            type,
                            name,
                            previous.getFile().getName());
            context.report(ISSUE, attribute, current, message);
        } else {
            folderMap.put(key, current);
        }
    }

    private static String getResourceType(@NonNull Element element) {
        String type = element.getNodeName();
        if ("item".equals(type)) {
            Attr typeAttr = element.getAttributeNode("type");
            if (typeAttr != null) {
                String value = typeAttr.getValue();
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
            return null;
        }
        return type;
    }
}