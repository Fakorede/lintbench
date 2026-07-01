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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;

public class ResourcePrefixDetector extends Detector implements XmlScanner {
    public static final Issue ISSUE = Issue.create(
        "ResourceName",
        "Resource with Wrong Prefix",
        "In Gradle projects you can specify a resource prefix that all resources in the project must conform to. This makes it easier to ensure that you don't accidentally combine resources from different libraries, since they all end up in the same shared app namespace.",
        Category.CORRECTNESS, 6, Severity.WARNING,
        new Implementation(ResourcePrefixDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null) {
            return;
        }

        if (folderType == ResourceFolderType.VALUES) {
            Node parent = element.getParentNode();
            if (parent != null && "resources".equals(parent.getNodeName())) {
                String name = element.getAttribute("name");
                if (name != null && !name.isEmpty() && !name.startsWith(prefix)) {
                    String message = String.format(
                        "Resource name `%s` does not start with the project's resource prefix `%s`",
                        name, prefix);
                    context.report(ISSUE, element, context.getLocation(element.getAttributeNode("name")), message);
                }
            }
        } else {
            if (element.getParentNode() != null && element.getParentNode().getNodeType() == Node.DOCUMENT_NODE) {
                String fileName = context.file.getName();
                int dotIndex = fileName.lastIndexOf('.');
                String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
                if (!baseName.startsWith(prefix)) {
                    String message = String.format(
                        "Resource file `%s` does not start with the project's resource prefix `%s`",
                        fileName, prefix);
                    context.report(ISSUE, context.getLocation(element), message);
                }
            }
        }
    }
}