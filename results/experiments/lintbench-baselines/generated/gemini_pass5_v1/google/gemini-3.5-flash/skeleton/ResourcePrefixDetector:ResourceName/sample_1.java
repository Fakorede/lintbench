package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.*;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ResourcePrefixDetector extends Detector implements Detector.XmlScanner, Detector.BinaryResourceScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ResourcePrefixDetector.class, EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "ResourceName",
                    "Resource with Wrong Prefix",
                    "In Gradle projects you can specify a resource prefix that all resources in the "
                            + "project must conform to. This makes it easier to ensure that you don't "
                            + "accidentally combine resources from different libraries, since they all "
                            + "end up in the same shared app namespace.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singleton("resources");
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (context instanceof XmlContext) {
            checkResourceFile((XmlContext) context);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String tagName = childElement.getTagName();
                if ("skip".equals(tagName) || "public".equals(tagName) || "eat-comment".equals(tagName)) {
                    continue;
                }
                String name = childElement.getAttribute("name");
                if (name != null && !name.isEmpty()) {
                    if (!name.startsWith(prefix)) {
                        context.report(
                                ISSUE,
                                childElement,
                                context.getNameLocation(childElement),
                                "Resource name `" + name + "` does not start with the project's resource prefix `" + prefix + "`"
                        );
                    }
                }
            }
        }
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        checkResourceFile(context);
    }

    private void checkResourceFile(@NonNull ResourceContext context) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        com.android.resources.ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null || folderType == com.android.resources.ResourceFolderType.VALUES) {
            return;
        }

        String fileName = context.file.getName();
        String resourceName;
        int dot = fileName.indexOf('.');
        if (dot != -1) {
            resourceName = fileName.substring(0, dot);
        } else {
            resourceName = fileName;
        }

        if (!resourceName.startsWith(prefix)) {
            context.report(
                    ISSUE,
                    Location.create(context.file),
                    "Resource file `" + fileName + "` does not start with the project's resource prefix `" + prefix + "`"
            );
        }
    }
}