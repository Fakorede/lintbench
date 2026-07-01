package com.android.tools.lint.checks;

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
import com.android.tools.lint.detector.api.XmlScannerConstants;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    private static final String RESOURCES_TAG = "resources";
    private static final String ITEM_TAG = "item";
    private static final String NAME_ATTR = "name";
    private static final String TYPE_ATTR = "type";

    private final Map<String, List<ResourceEntry>> mResources = new HashMap<>();

    private static class ResourceEntry {
        final String folderPath;
        final String type;
        final String name;
        final Location location;

        ResourceEntry(String folderPath, String type, String name, Location location) {
            this.folderPath = folderPath;
            this.type = type;
            this.name = name;
            this.location = location;
        }
    }

    @Override
    public void beforeCheckEachProject(@NotNull Context context) {
        mResources.clear();
    }

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    @NotNull
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void beforeCheckFile(@NotNull XmlContext context) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null || folderType == ResourceFolderType.VALUES) {
            return;
        }

        File file = context.file;
        File folder = file.getParentFile();
        if (folder == null) {
            return;
        }

        String folderPath = folder.getAbsolutePath();
        String fileName = file.getName();
        int dot = fileName.lastIndexOf('.');
        String name = dot > 0 ? fileName.substring(0, dot) : fileName;
        String type = folderType.name().toLowerCase(Locale.US);

        add(folderPath, type, name, Location.create(file));
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.VALUES) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent == null || !RESOURCES_TAG.equals(parent.getNodeName())) {
            return;
        }

        String tag = element.getTagName();
        if (tag == null) {
            return;
        }

        if ("eat-comment".equals(tag) || "skip".equals(tag) ||
                "public".equals(tag) || "public-padding".equals(tag) ||
                "java-symbol".equals(tag) || "add-resource".equals(tag)) {
            return;
        }

        String type;
        if (ITEM_TAG.equals(tag)) {
            type = element.getAttribute(TYPE_ATTR);
            if (type.isEmpty()) {
                return;
            }
        } else {
            type = tag;
            if ("string-array".equals(type) || "integer-array".equals(type) || "array".equals(type)) {
                type = "array";
            }
        }

        String name = element.getAttribute(NAME_ATTR);
        if (name.isEmpty()) {
            return;
        }

        File folder = context.file.getParentFile();
        if (folder == null) {
            return;
        }
        String folderPath = folder.getAbsolutePath();

        add(folderPath, type, name, context.getLocation(element));
    }

    private void add(String folderPath, String type, String name, Location location) {
        String key = folderPath + "#" + type + "/" + name;
        List<ResourceEntry> list = mResources.get(key);
        if (list == null) {
            list = new ArrayList<>();
            mResources.put(key, list);
        }
        list.add(new ResourceEntry(folderPath, type, name, location));
    }

    @Override
    public void afterCheckEachProject(@NotNull Context context) {
        for (List<ResourceEntry> entries : mResources.values()) {
            if (entries.size() < 2) {
                continue;
            }

            ResourceEntry first = entries.get(0);
            List<Location> secondaries = new ArrayList<>();
            for (int i = 1; i < entries.size(); i++) {
                secondaries.add(entries.get(i).location);
            }

            Location location = Location.create(
                    first.location.getFile(),
                    first.location.getContents(),
                    first.location.getRange(),
                    secondaries
            );

            context.report(
                    ISSUE,
                    location,
                    "Duplicate definition of " + first.type + "/" + first.name
                            + " in " + first.folderPath
            );
        }
    }

    public static final Issue ISSUE = Issue.create(
            "DuplicateDefinition",
            "Duplicate definitions of resources",
            "You can define a resource multiple times in different resource folders; that's how "
                    + "string translations are done, for example. However, defining the same resource "
                    + "more than once in the same resource folder is likely an error, for example "
                    + "attempting to add a new resource without realizing that the name is already used, "
                    + "and so on.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    DuplicateResourceDetector.class,
                    Scope.RESOURCE_FILE_SCOPE,
                    Scope.ALL_RESOURCES_FILES
            )
    );
}