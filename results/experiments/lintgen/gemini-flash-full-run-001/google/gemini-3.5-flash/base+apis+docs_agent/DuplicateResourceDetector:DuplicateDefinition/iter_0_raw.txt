package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DuplicateResourceDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateDefinition",
            "Duplicate definitions of resources",
            "Defining the same resource more than once in the same resource folder is "
                    + "likely an error, for example attempting to add a new resource without "
                    + "realizing that the name is already used.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    DuplicateResourceDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private final Map<File, Set<String>> mFolderToResources = new HashMap<>();

    @Override
    public void beforeCheckEachProject(Context context) {
        mFolderToResources.clear();
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.VALUES) {
            return;
        }

        Element root = document.getDocumentElement();
        if (root == null || !"resources".equals(root.getTagName())) {
            return;
        }

        File folder = context.file.getParentFile();
        if (folder == null) {
            return;
        }

        Set<String> definedResources = mFolderToResources.computeIfAbsent(folder, k -> new HashSet<>());

        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) node;
            String name = element.getAttribute("name");
            if (name == null || name.isEmpty()) {
                continue;
            }

            String tagName = element.getTagName();
            String type;
            if ("item".equals(tagName)) {
                type = element.getAttribute("type");
                if (type == null || type.isEmpty()) {
                    continue;
                }
            } else {
                type = tagName;
            }

            if ("eat-comment".equals(type) || "skip".equals(type)) {
                continue;
            }

            String key = type + "/" + name;
            if (definedResources.contains(key)) {
                Location location = context.getNameLocation(element);
                context.report(
                        ISSUE,
                        element,
                        location,
                        String.format("Duplicate definition of %s '%s'", type, name)
                );
            } else {
                definedResources.add(key);
            }
        }
    }
}