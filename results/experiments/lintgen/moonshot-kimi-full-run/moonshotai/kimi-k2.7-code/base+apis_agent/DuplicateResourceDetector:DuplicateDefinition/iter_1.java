package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DuplicateResourceDetector extends Detector implements ResourceFolderScanner, XmlScanner {

    public static final Issue DUPLICATE_DEFINITION = Issue.create(
        "DuplicateDefinition",
        "Duplicate resource definitions",
        "Defining the same resource more than once in the same resource folder is likely an error.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(
            DuplicateResourceDetector.class,
            Scope.RESOURCE_FILE_SCOPE,
            Scope.RESOURCE_FOLDER_SCOPE
        )
    );

    private static final String TAG_RESOURCES = "resources";
    private static final String TAG_ITEM = "item";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_TYPE = "type";

    private Map<Key, List<Location>> mDefinitions;

    @Override
    public void beforeCheckEachProject(@NotNull Context context) {
        mDefinitions = new HashMap<>();
    }

    @Override
    public void afterCheckEachProject(@NotNull Context context) {
        for (Map.Entry<Key, List<Location>> entry : mDefinitions.entrySet()) {
            List<Location> locations = entry.getValue();
            if (locations.size() > 1) {
                Key key = entry.getKey();
                String message = String.format(
                    "Duplicate definition of %1$s resource `%2$s` in folder `%3$s`",
                    key.type, key.name, new File(key.folder).getName()
                );
                Location location = locations.get(0);
                Incident incident = new Incident(DUPLICATE_DEFINITION, location, message);
                for (int i = 1; i < locations.size(); i++) {
                    incident.at(locations.get(i));
                }
                context.report(incident);
            }
        }
        mDefinitions = null;
    }

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void checkFolder(@NotNull ResourceContext context, @NotNull String folderName) {
        String baseFolderName = getBaseFolderName(folderName);
        if ("values".equals(baseFolderName)) {
            return;
        }
        ResourceType folderType = ResourceType.getByName(baseFolderName);
        if (folderType == null) {
            return;
        }
        File folder = context.getFolder();
        if (folder == null) {
            return;
        }
        String folderPath = folder.getPath();
        File[] files = context.getFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            String fileName = file.getName();
            if (fileName.startsWith(".")) {
                continue;
            }
            String name = getBaseFileName(fileName);
            if (name.isEmpty()) {
                continue;
            }
            record(folderPath, folderType.getName(), name, Location.create(file));
        }
    }

    @NotNull
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_RESOURCES);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        if (!TAG_RESOURCES.equals(element.getTagName())) {
            return;
        }
        File folder = context.file.getParentFile();
        if (folder == null) {
            return;
        }
        String folderPath = folder.getPath();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) node;
            String name = child.getAttribute(ATTR_NAME);
            if (name.isEmpty()) {
                continue;
            }
            String type = getValueResourceType(child);
            if (type == null || type.isEmpty()) {
                continue;
            }
            record(folderPath, type, name, context.getLocation(child));
        }
    }

    private void record(@NotNull String folder, @NotNull String type, @NotNull String name, @NotNull Location location) {
        Key key = new Key(folder, type, name);
        List<Location> list = mDefinitions.get(key);
        if (list == null) {
            list = new ArrayList<>();
            mDefinitions.put(key, list);
        }
        list.add(location);
    }

    @Nullable
    private static String getValueResourceType(@NotNull Element element) {
        String tag = element.getTagName();
        ResourceType type = null;
        if (TAG_ITEM.equals(tag)) {
            String itemType = element.getAttribute(ATTR_TYPE);
            type = ResourceType.getByName(itemType);
        } else if ("declare-styleable".equals(tag)) {
            type = ResourceType.STYLEABLE;
        } else if ("string-array".equals(tag) || "integer-array".equals(tag)) {
            type = ResourceType.ARRAY;
        } else {
            type = ResourceType.getByName(tag);
        }
        return type == null ? null : type.getName();
    }

    @NotNull
    private static String getBaseFolderName(@NotNull String folderName) {
        int dash = folderName.indexOf('-');
        return dash == -1 ? folderName : folderName.substring(0, dash);
    }

    @NotNull
    private static String getBaseFileName(@NotNull String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }

    private static final class Key {
        final String folder;
        final String type;
        final String name;

        Key(String folder, String type, String name) {
            this.folder = folder;
            this.type = type;
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key)) return false;
            Key other = (Key) o;
            return folder.equals(other.folder)
                && type.equals(other.type)
                && name.equals(other.name);
        }

        @Override
        public int hashCode() {
            int result = folder.hashCode();
            result = 31 * result + type.hashCode();
            result = 31 * result + name.hashCode();
            return result;
        }
    }
}