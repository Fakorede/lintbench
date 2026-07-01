package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ResourcePrefixDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resource prefix that all resources " +
            "in the project must conform to. This makes it easier to ensure that " +
            "you don't accidentally combine resources from different libraries, " +
            "since they all end up in the same shared app namespace.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(ResourcePrefixDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null) {
            return;
        }

        if (folderType != ResourceFolderType.VALUES) {
            String name = LintUtils.getBaseName(context.file.getName());
            if (!name.startsWith(prefix)) {
                context.report(ISSUE, document, context.getLocation(document),
                        String.format("Resource file name `%1$s` does not start with project prefix `%2$s`", name, prefix));
            }
        } else {
            Element root = document.getDocumentElement();
            if (root != null && root.getTagName().equals("resources")) {
                NodeList children = root.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node child = children.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        Element element = (Element) child;
                        Attr nameAttr = element.getAttributeNode("name");
                        if (nameAttr != null) {
                            String name = nameAttr.getValue();
                            if (!name.startsWith(prefix)) {
                                String tag = element.getTagName();
                                if ("public".equals(tag) || "skip".equals(tag)) {
                                    continue;
                                }
                                context.report(ISSUE, nameAttr, context.getLocation(nameAttr),
                                        String.format("Resource name `%1$s` does not start with project prefix `%2$s`", name, prefix));
                            }
                        }
                    }
                }
            }
        }
    }
}