package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

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

    private final Map<File, Map<String, Set<String>>> mNames = new HashMap<>();

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("name");
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mNames.clear();
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        File folder = file.getParentFile();
        if (folder == null) {
            return;
        }

        ResourceFolderType folderType = ResourceFolderType.getFolderType(folder.getName());
        if (folderType == null || folderType == ResourceFolderType.VALUES) {
            return;
        }

        String baseName = getBaseName(file.getName());
        if (baseName.isEmpty()) {
            return;
        }

        ResourceType type = ResourceType.getEnum(folderType.getName());
        if (type == null) {
            return;
        }

        record(context, folder, type.getName(), baseName, context.getLocation(file));
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!"name".equals(attribute.getName())) {
            return;
        }

        File folder = context.file.getParentFile();
        if (folder == null) {
            return;
        }

        ResourceFolderType folderType = ResourceFolderType.getFolderType(folder.getName());
        if (folderType != ResourceFolderType.VALUES) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        ResourceType type = getValueResourceType(attribute.getOwnerElement());
        if (type == null) {
            return;
        }

        record(context, folder, type.getName(), value, context.getLocation(attribute));
    }

    private ResourceType getValueResourceType(Element element) {
        String tag = element.getTagName();
        if ("item".equals(tag)) {
            Attr typeAttr = element.getAttributeNode("type");
            if (typeAttr != null) {
                return ResourceType.fromXmlTag(typeAttr.getValue());
            }
            return null;
        }
        return ResourceType.fromXmlTag(tag);
    }

    private void record(
            Context context,
            File folder,
            String typeName,
            String name,
            Location location) {
        Map<String, Set<String>> byType = mNames.get(folder);
        if (byType == null) {
            byType = new HashMap<>();
            mNames.put(folder, byType);
        }

        Set<String> names = byType.get(typeName);
        if (names == null) {
            names = new HashSet<>();
            byType.put(typeName, names);
        }

        if (!names.add(name)) {
            context.report(
                    ISSUE,
                    location,
                    "Duplicate " + typeName + " resource `" + name + "` defined in the same folder");
        }
    }

    private static String getBaseName(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index != -1) {
            return fileName.substring(0, index);
        }
        return fileName;
    }
}