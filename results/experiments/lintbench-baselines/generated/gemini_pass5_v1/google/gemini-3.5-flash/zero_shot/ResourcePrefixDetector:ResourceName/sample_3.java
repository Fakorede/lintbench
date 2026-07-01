package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ResourcePrefixDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resource prefix that all resources " +
            "in the project must conform to. This makes it easier to ensure that you don't " +
            "accidentally combine resources from different libraries, since they all end " +
            "up in the same shared app namespace.",
            Category.CORRECTNESS,
            8,
            Severity.FATAL,
            new Implementation(
                    ResourcePrefixDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Project project = context.getProject();
        String prefix = project.getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null) {
            return;
        }

        if (folderType != ResourceFolderType.VALUES) {
            String fileName = context.file.getName();
            int dot = fileName.indexOf('.');
            String resourceName = dot != -1 ? fileName.substring(0, dot) : fileName;
            if (!resourceName.startsWith(prefix)) {
                context.report(
                        ISSUE,
                        document,
                        context.getLocation(document),
                        String.format("Resource file name `%1$s` does not start with the project's resource prefix `%2$s`", resourceName, prefix)
                );
            }
        } else {
            Element root = document.getDocumentElement();
            if (root != null && "resources".equals(root.getTagName())) {
                NodeList children = root.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node child = children.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        Element element = (Element) child;
                        Attr nameAttr = element.getAttributeNode("name");
                        if (nameAttr != null) {
                            String name = nameAttr.getValue();
                            if (!name.startsWith(prefix)) {
                                context.report(
                                        ISSUE,
                                        nameAttr,
                                        context.getLocation(nameAttr),
                                        String.format("Resource name `%1$s` does not start with the project's resource prefix `%2$s`", name, prefix)
                                );
                            }
                        }
                    }
                }
            }
        }
    }
}