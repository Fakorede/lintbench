package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
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
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class DuplicateResourceDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateDefinition",
                    "Duplicate definitions of resources",
                    "You can define a resource multiple times in different resource folders; that's how "
                            + "string translations are done, for example. However, defining the same resource "
                            + "more than once in the same resource folder is likely an error, for example "
                            + "attempting to add a new resource without realizing that the name is already used, "
                            + "and so on.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private final Map<File, Map<ResourceType, Map<String, Location>>> mDefined =
            new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_NAME);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!SdkConstants.ATTR_NAME.equals(attribute.getName())) {
            return;
        }

        if (attribute.getNamespaceURI() != null) {
            return;
        }

        Element element = attribute.getOwnerElement();
        Node parent = element.getParentNode();
        if (parent == null || !SdkConstants.TAG_RESOURCES.equals(parent.getNodeName())) {
            return;
        }

        String name = attribute.getValue();
        if (name == null || name.isEmpty()) {
            return;
        }

        ResourceType type = getResourceType(element);
        if (type == null) {
            return;
        }

        File folder = context.file.getParentFile();
        if (folder == null) {
            return;
        }

        Map<ResourceType, Map<String, Location>> folderMap = mDefined.get(folder);
        if (folderMap == null) {
            folderMap = new EnumMap<>(ResourceType.class);
            mDefined.put(folder, folderMap);
        }

        Map<String, Location> typeMap = folderMap.get(type);
        if (typeMap == null) {
            typeMap = new HashMap<>();
            folderMap.put(type, typeMap);
        }

        Location currentLocation = context.getLocation(attribute);
        Location firstLocation = typeMap.get(name);
        if (firstLocation == null) {
            typeMap.put(name, currentLocation);
        } else {
            String message =
                    String.format(
                            "%1$s resource \"%2$s\" has already been defined",
                            capitalize(type.getName()), name);
            currentLocation.setSecondary(firstLocation);
            context.report(ISSUE, attribute, currentLocation, message);
        }
    }

    private static ResourceType getResourceType(@NonNull Element element) {
        ResourceType type = ResourceType.fromXmlTag(element.getTagName());
        if (type == null) {
            Attr typeAttr = element.getAttributeNode(SdkConstants.ATTR_TYPE);
            if (typeAttr != null) {
                type = ResourceType.getEnum(typeAttr.getValue());
            }
        }
        return type;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}