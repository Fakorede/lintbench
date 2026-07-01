package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public class DuplicateResourceDetector extends Detector implements ResourceFolderScanner, XmlScanner {

    private static final String ISSUE_ID = "DuplicateDefinition";
    private static final String TAG_RESOURCES = "resources";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_TYPE = "type";

    public static final Issue DUPLICATE_DEFINITION = Issue.create(
            ISSUE_ID,
            "Duplicate definitions of resources",
            "You can define a resource multiple times in different resource folders; that's how "
                    + "string translations are done, for example. However, defining the same "
                    + "resource more than once in the same resource folder is likely an error, "
                    + "for example attempting to add a new resource without realizing that the "
                    + "name is already used, and so on.",
            Category.CORRECTNESS,
            7,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, EnumSet.of(Scope.RESOURCE_FILE_SCOPE)));

    private Map<String, Location> mSeenValues;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mSeenValues = new HashMap<>();
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        mSeenValues = null;
    }

    @Override
    public void checkFolder(@NonNull Context context, @NonNull ResourceFolderType folderType,
            @NonNull File folder) {
        if (folderType == ResourceFolderType.VALUES) {
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        Map<String, File> seen = new HashMap<>();
        for (File file : files) {
            if (file.isDirectory()) {
                continue;
            }

            String fileName = file.getName();
            if (fileName.startsWith(".")) {
                continue;
            }

            int dot = fileName.lastIndexOf('.');
            String resourceName = dot == -1 ? fileName : fileName.substring(0, dot);
            if (resourceName.isEmpty()) {
                continue;
            }

            File first = seen.put(resourceName, file);
            if (first != null) {
                Location firstLocation = Location.create(first);
                Location location = Location.create(file);
                location.setSecondary(firstLocation);
                context.report(DUPLICATE_DEFINITION, location,
                        String.format("%1$s `%2$s` has already been defined in this folder",
                                folderType.getName(), resourceName));
            }
        }
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_RESOURCES);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.VALUES) {
            return;
        }

        if (!TAG_RESOURCES.equals(element.getNodeName())) {
            return;
        }

        File folder = context.file.getParentFile();
        if (folder == null) {
            return;
        }

        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                child = child.getNextSibling();
                continue;
            }

            Element childElement = (Element) child;
            String name = childElement.getAttribute(ATTR_NAME);
            if (name.isEmpty()) {
                child = child.getNextSibling();
                continue;
            }

            String typeName = getResourceTypeName(childElement);
            String key = folder.getPath() + "/" + typeName + "/" + name;

            Location current = context.getLocation(childElement);
            Location first = mSeenValues.get(key);
            if (first == null) {
                mSeenValues.put(key, current);
            } else {
                current.setSecondary(first);
                context.report(DUPLICATE_DEFINITION, childElement, current,
                        String.format("%1$s `%2$s` has already been defined in this values folder",
                                typeName, name));
            }

            child = child.getNextSibling();
        }
    }

    private static String getResourceTypeName(@NonNull Element element) {
        String tag = element.getNodeName();
        if ("item".equals(tag)) {
            String type = element.getAttribute(ATTR_TYPE);
            if (!type.isEmpty()) {
                return type;
            }
        }

        ResourceType type = ResourceType.fromXmlTag(tag);
        if (type != null) {
            return type.getName();
        }

        return tag;
    }
}