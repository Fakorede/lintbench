package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class ResourcePrefixDetector extends Detector implements XmlScanner {
    public static final Issue ISSUE = Issue.create(
        "ResourceName",
        "Resource with Wrong Prefix",
        "In Gradle projects you can specify a resource prefix that all resources in the project must conform to. This makes it easier to ensure that you don't accidentally combine resources from different libraries, since they all end up in the same shared app namespace.",
        Category.CORRECTNESS, 6, Severity.WARNING,
        new Implementation(ResourcePrefixDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String prefix = context.getProject().getProperty("android.resourcePrefix");
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.VALUES) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent == null || !"resources".equals(parent.getNodeName())) {
            return;
        }

        String name = element.getAttribute("name");
        if (name != null && !name.isEmpty() && !name.startsWith(prefix)) {
            String message = String.format(
                "Resource name `%s` does not start with the project's resource prefix `%s`",
                name, prefix);
            Attr nameAttr = element.getAttributeNode("name");
            context.report(ISSUE, element, context.getLocation(nameAttr != null ? nameAttr : element), message);
        }
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        String prefix = context.getProject().getProperty("android.resourcePrefix");
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null || folderType == ResourceFolderType.VALUES) {
            return;
        }

        String fileName = context.file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;

        int qualifierIndex = baseName.indexOf('-');
        if (qualifierIndex > 0) {
            baseName = baseName.substring(0, qualifierIndex);
        }

        if (!baseName.startsWith(prefix)) {
            String message = String.format(
                "Resource file `%s` does not start with the project's resource prefix `%s`",
                fileName, prefix);
            context.report(ISSUE, context.getLocation(document.getDocumentElement()), message);
        }
    }
}