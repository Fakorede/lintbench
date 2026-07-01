package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderContext;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
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
            EnumSet.of(Scope.RESOURCE_FOLDER_SCOPE, Scope.RESOURCE_FILE_SCOPE)
        )
    );

    private static final String TAG_RESOURCES = "resources";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_TYPE = "type";
    private static final String TAG_ITEM = "item";

    private Map<Key, List<ResourceDefinition>> mDefinitions;

    @Override
    public void beforeCheckEachProject(@NotNull Context context) {
        mDefinitions = new HashMap<>();
    }

    @Override
    public void afterCheckEachProject(@NotNull Context context) {
        for (Map.Entry<Key, List<ResourceDefinition>> entry : mDefinitions.entrySet()) {
            List<ResourceDefinition> definitions = entry.getValue();
            if (definitions.size() > 1) {
                Key key = entry.getKey();
                String message = String.format(
                    "Duplicate definition of %1$s resource `%2$s` in folder `%3$s`",
                    key.type, key.name, key.folder
                );
                Location location = definitions.get(0).location;
                Location current = location;
                for (int i = 1; i < definitions.size(); i++) {
                    Location next = definitions.get(i).location;
                    current.setNext(next);
                    current = next;
                }
                context.report(DUPLICATE_DEFINITION, location, message);
            }
        }
        mDefinitions = null;
    }

    @Override
    public void checkFolder(@NotNull ResourceFolderContext context) {
        File folder = context.getFolder();
        String folderPath = folder.getPath();
        String folderBaseName = getBaseFolderName(folder.getName());
        if ("values".equals(folderBaseName)) {
            return;
        }
        File[] files = context.getFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            String name = getBaseName(file.getName());
            if (name == null || name.isEmpty()) {
                continue;
            }
            record(folderPath, folderBaseName, name, Location.create(file));
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
        List<ResourceDefinition> list = mDefinitions.get(key);
        if (list == null) {
            list = new ArrayList<>();
            mDefinitions.put(key, list);
        }
        list.add(new ResourceDefinition(type, name, location));
    }

    private static String getValueResourceType(@NotNull Element element) {
        String tag = element.getTagName();
        if (TAG_ITEM.equals(tag)) {
            String itemType = element.getAttribute(ATTR_TYPE);
            return itemType.isEmpty() ? null : itemType;
        }
        if ("declare-styleable".equals(tag)) {
            return "styleable";
        }
        return tag;
    }

    private static String getBaseFolderName(@NotNull String folderName) {
        int dash = folderName.indexOf('-');
        return dash == -1 ? folderName : folderName.substring(0, dash);
    }

    private static String getBaseName(@NotNull String fileName) {
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

    private static final class ResourceDefinition {
        final String type;
        final String name;
        final Location location;

        ResourceDefinition(String type, String name, Location location) {
            this.type = type;
            this.name = name;
            this.location = location;
        }
    }
}