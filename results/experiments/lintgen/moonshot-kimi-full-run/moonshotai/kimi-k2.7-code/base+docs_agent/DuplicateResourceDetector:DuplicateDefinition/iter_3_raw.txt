package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_RESOURCES;
import static com.android.SdkConstants.TAG_STYLE;

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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class DuplicateResourceDetector extends Detector implements XmlScanner, ResourceFolderScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateDefinition",
            "Duplicate definitions of resources",
            "You can define a resource multiple times in different resource folders; that's how "
                    + "string translations are done, for example. However, defining the same "
                    + "resource more than once in the same resource folder is likely an error, "
                    + "for example attempting to add a new resource without realizing that the "
                    + "name is already used, and so on.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(DuplicateResourceDetector.class,
                    Scope.RESOURCE_FILE_SCOPE, Scope.RESOURCE_FOLDER_SCOPE)
    );

    private static final String STYLE_PREFIX = "style/";

    private Map<String, List<Resource>> mDefinitions;

    private static class Resource {
        final File file;
        final Location location;
        final String type;
        final String name;

        Resource(File file, Location location, String type, String name) {
            this.file = file;
            this.location = location;
            this.type = type;
            this.name = name;
        }
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mDefinitions = new HashMap<>();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (List<Resource> entries : mDefinitions.values()) {
            if (entries.size() > 1) {
                Resource first = entries.get(0);
                for (int i = 1; i < entries.size(); i++) {
                    Resource duplicate = entries.get(i);
                    String message = String.format(
                            "Duplicate definition of `%1$s/%2$s` (%3$s)",
                            first.type,
                            first.name,
                            first.file.getName());
                    context.report(ISSUE, duplicate.location, message);
                }
            }
        }
        mDefinitions = null;
    }

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_RESOURCES, TAG_STYLE);
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

        if (TAG_STYLE.equals(element.getTagName())) {
            String styleName = element.getAttribute(ATTR_NAME);
            if (styleName.isEmpty()) {
                return;
            }
            Node child = element.getFirstChild();
            while (child != null) {
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element childElement = (Element) child;
                    if (TAG_ITEM.equals(childElement.getTagName())) {
                        String name = childElement.getAttribute(ATTR_NAME);
                        if (!name.isEmpty()) {
                            String key = folderPath + '/' + STYLE_PREFIX + styleName + '/' + name;
                            addDefinition(context, childElement, "style", name, key);
                        }
                    }
                }
                child = child.getNextSibling();
            }
            return;
        }

        // <resources>
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

                    String key = folderPath + '/' + type + '/' + name;
                    addDefinition(context, childElement, type, name, key);
                }
            }
            child = child.getNextSibling();
        }
    }

    private void addDefinition(@NonNull XmlContext context, @NonNull Element element,
            @NonNull String type, @NonNull String name, @NonNull String key) {
        List<Resource> list = mDefinitions.get(key);
        if (list == null) {
            list = new ArrayList<>();
            mDefinitions.put(key, list);
        }
        list.add(new Resource(context.file, context.getLocation(element), type, name));
    }

    @Override
    public void checkResourceFile(@NonNull ResourceContext context, @NonNull File file) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null || folderType == ResourceFolderType.VALUES) {
            return;
        }

        File folder = file.getParentFile();
        if (folder == null) {
            return;
        }
        String folderPath = folder.getPath();

        String baseName = getBaseName(file);
        if (baseName.isEmpty() || baseName.startsWith(".")) {
            return;
        }

        String type = folderType.name().toLowerCase(Locale.US);
        String key = folderPath + '/' + type + '/' + baseName;
        List<Resource> list = mDefinitions.get(key);
        if (list == null) {
            list = new ArrayList<>();
            mDefinitions.put(key, list);
        }
        list.add(new Resource(file, Location.create(file), type, baseName));
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