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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "DuplicateDefinition",
            "Duplicate definitions of resources",
            "You can define a resource multiple times in different resource folders; that's how "
                    + "string translations are done, for example. However, defining the same "
                    + "resource more than once in the same resource folder is likely an error, "
                    + "for example attempting to add a new resource without realizing that the "
                    + "name is already used, and so on.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private Map<String, List<ResourceDefinition>> mDefinitions;

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void beforeCheckEachProject(Context context) {
        mDefinitions = new HashMap<>();
    }

    @Override
    public void visitDocument(XmlContext context) {
        Document document = context.document;
        if (document == null) {
            return;
        }

        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        File resourceFolder = context.file.getParentFile();
        if (resourceFolder == null) {
            return;
        }

        ResourceFolderType folderType = ResourceFolderType.getFolderType(resourceFolder.getName());
        if (folderType == null) {
            return;
        }

        String folderPath = resourceFolder.getAbsolutePath();

        if (folderType == ResourceFolderType.VALUES) {
            NodeList children = root.getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                Node node = children.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                Element child = (Element) node;
                String tag = child.getTagName();

                if ("eat-comment".equals(tag) || "skip".equals(tag)) {
                    continue;
                }

                String name = child.getAttribute("name");
                if (name == null || name.isEmpty()) {
                    continue;
                }

                String type = tag;
                if ("item".equals(tag)) {
                    type = child.getAttribute("type");
                    if (type == null || type.isEmpty()) {
                        continue;
                    }
                }

                addDefinition(folderPath, type, name, context.getLocation(child));
            }
        } else {
            String fileName = context.file.getName();
            int dot = fileName.lastIndexOf('.');
            String name = dot > 0 ? fileName.substring(0, dot) : fileName;
            addDefinition(folderPath, folderType.getName(), name, Location.create(context.file));
        }
    }

    @Override
    public void afterCheckProject(Context context) {
        for (List<ResourceDefinition> list : mDefinitions.values()) {
            if (list.size() <= 1) {
                continue;
            }

            for (ResourceDefinition def : list) {
                String message = "Duplicate definition of resource `" + def.type + "/" + def.name + "`";
                context.report(ISSUE, def.location, message);
            }
        }
    }

    private void addDefinition(String folderPath, String type, String name, Location location) {
        String key = folderPath + ":" + type + "/" + name;
        List<ResourceDefinition> list = mDefinitions.get(key);
        if (list == null) {
            list = new ArrayList<>();
            mDefinitions.put(key, list);
        }
        list.add(new ResourceDefinition(type, name, location));
    }

    private static class ResourceDefinition {
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