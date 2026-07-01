package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_RESOURCES;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class DuplicateResourceDetector extends Detector implements XmlScanner, ResourceFolderScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateDefinition",
            "Duplicate resource definitions",
            "Defining the same resource more than once in the same resource folder is likely an error. "
                    + "Resources may be defined in different folders (for example for translations), "
                    + "but a duplicate definition within the same folder should be removed.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private Map<String, List<ResourceEntry>> mDefinitions;

    private static class ResourceEntry {
        final String type;
        final String name;
        final String folder;
        final Location location;

        ResourceEntry(String type, String name, String folder, Location location) {
            this.type = type;
            this.name = name;
            this.folder = folder;
            this.location = location;
        }
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mDefinitions = new HashMap<>();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (List<ResourceEntry> entries : mDefinitions.values()) {
            if (entries.size() > 1) {
                ResourceEntry first = entries.get(0);
                for (int i = 1; i < entries.size(); i++) {
                    ResourceEntry duplicate = entries.get(i);
                    String message = String.format(
                            "Duplicate definition of `%1$s/%2$s`; first defined in `%3$s`",
                            first.type,
                            first.name,
                            first.location.getFile().getName());
                    context.report(ISSUE, duplicate.location, message);
                }
            }
        }
        mDefinitions = null;
    }

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_RESOURCES);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.VALUES) {
            return;
        }

        File folder = context.file.getParentFile();
        if (folder == null) {
            return;
        }
        String folderPath = folder.getPath();

        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String name = childElement.getAttribute(ATTR_NAME);
                if (!name.isEmpty()) {
                    String type = childElement.getTagName();
                    if (TAG_ITEM.equals(type)) {
                        String itemType = childElement.getAttribute(ATTR_TYPE);
                        if (!itemType.isEmpty()) {
                            type = itemType;
                        } else {
                            child = child.getNextSibling();
                            continue;
                        }
                    }

                    String key = folderPath + "/" + type + "/" + name;
                    Location location = context.getLocation(childElement);
                    mDefinitions.computeIfAbsent(key, k -> new ArrayList<>())
                            .add(new ResourceEntry(type, name, folderPath, location));
                }
            }
            child = child.getNextSibling();
        }
    }

    public void checkResourceFile(@NonNull ResourceContext context) {
        recordNonValueResource(context);
    }

    public void checkFile(@NonNull ResourceContext context) {
        recordNonValueResource(context);
    }

    private void recordNonValueResource(@NonNull ResourceContext context) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null || folderType == ResourceFolderType.VALUES) {
            return;
        }

        File folder = context.file.getParentFile();
        if (folder == null) {
            return;
        }
        String folderPath = folder.getPath();

        File file = context.file;
        String baseName = getBaseName(file);
        if (baseName.isEmpty() || baseName.startsWith(".")) {
            return;
        }

        String type = folderType.name().toLowerCase(Locale.US);
        String key = folderPath + "/" + type + "/" + baseName;
        Location location = Location.create(file);
        mDefinitions.computeIfAbsent(key, k -> new ArrayList<>())
                .add(new ResourceEntry(type, baseName, folderPath, location));
    }

    private static String getBaseName(File file) {
        String name = file.getName();
        if (name.endsWith(".9.png")) {
            return name.substring(0, name.length() - ".9.png".length());
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}