package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;

public class ResourcePrefixDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resource prefix that all resources in the project must conform to. " +
            "This makes it easier to ensure that you don't accidentally combine resources from different libraries, " +
            "since they all end up in the same shared app namespace.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ResourcePrefixDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return true;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
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

        boolean isValues = folderType == ResourceFolderType.VALUES;

        if (!isValues && element.getParentNode() != null && element.getParentNode().getNodeType() == Node.DOCUMENT_NODE) {
            String name = context.getResourceName();
            if (name != null && !name.startsWith(prefix)) {
                context.report(ISSUE, element, context.getLocation(element),
                        String.format("Resource name should start with prefix `%1$s`", prefix));
            }
        } else if (isValues) {
            String name = element.getAttribute("name");
            if (name != null && !name.isEmpty()) {
                if (!name.startsWith(prefix)) {
                    Attr nameAttr = element.getAttributeNode("name");
                    context.report(ISSUE, nameAttr, context.getLocation(nameAttr),
                            String.format("Resource name should start with prefix `%1$s`", prefix));
                }
            }
        }
    }
}