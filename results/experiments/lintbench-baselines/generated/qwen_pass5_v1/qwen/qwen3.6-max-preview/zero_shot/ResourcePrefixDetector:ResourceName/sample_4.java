package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class ResourcePrefixDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
        "ResourceName",
        "Resource with Wrong Prefix",
        "In Gradle projects you can specify a resource prefix that all resources " +
        "in the project must conform to. This makes it easier to ensure that you don't " +
        "accidentally combine resources from different libraries, since they all end " +
        "up in the same shared app namespace.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(ResourcePrefixDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void visitDocument(@NotNull XmlContext context, @NotNull Element document) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null) {
            return;
        }

        if (folderType == ResourceFolderType.VALUES) {
            checkValuesElements(context, document, prefix);
        } else {
            String fileName = context.file.getName();
            int dotIndex = fileName.indexOf('.');
            String baseName = dotIndex != -1 ? fileName.substring(0, dotIndex) : fileName;
            if (!baseName.startsWith(prefix)) {
                context.report(ISSUE, document, context.getLocation(document),
                    "Resource name `" + baseName + "` does not start with the project prefix `" + prefix + "`");
            }
        }
    }

    private void checkValuesElements(XmlContext context, Element document, String prefix) {
        Node child = document.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                Attr nameAttr = element.getAttributeNode("name");
                if (nameAttr != null) {
                    String name = nameAttr.getValue();
                    if (!name.startsWith(prefix)) {
                        context.report(ISSUE, element, context.getLocation(nameAttr),
                            "Resource name `" + name + "` does not start with the project prefix `" + prefix + "`");
                    }
                }
            }
            child = child.getNextSibling();
        }
    }
}