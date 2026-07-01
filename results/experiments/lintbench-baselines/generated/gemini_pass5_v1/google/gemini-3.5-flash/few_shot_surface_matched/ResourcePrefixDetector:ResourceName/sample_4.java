package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

public class ResourcePrefixDetector extends Detector implements XmlScanner, BinaryResourceScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ResourceName",
                    "Resource with Wrong Prefix",
                    "In Gradle projects you can specify a resource prefix that all resources "
                            + "in the project must conform to. This makes it easier to ensure that you don't "
                            + "accidentally combine resources from different libraries, since they all end "
                            + "up in the same shared app namespace.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            ResourcePrefixDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE)));

    private String activePrefix = null;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(XmlScanner.ALL);
    }

    @Override
    public void beforeCheckEachProject(Context context) {
        activePrefix = context.getProject().getResourcePrefix();
    }

    @Override
    public void afterCheckEachProject(Context context) {
        activePrefix = null;
    }

    @Override
    public void beforeCheckFile(Context context) {
        if (activePrefix == null) {
            return;
        }
        ResourceFolderType folderType = ResourceFolderType.getFolderType(context.file.getParentFile().getName());
        if (folderType != null && folderType != ResourceFolderType.VALUES) {
            String name = context.file.getName();
            int dot = name.indexOf('.');
            if (dot != -1) {
                name = name.substring(0, dot);
            }
            if (!name.startsWith(activePrefix)) {
                context.report(
                        ISSUE,
                        Location.create(context.file),
                        String.format("The resource name `%s` must begin with the prefix `%s`", name, activePrefix));
            }
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (activePrefix == null) {
            return;
        }
        ResourceFolderType folderType = context.getFolderType();
        if (folderType != ResourceFolderType.VALUES) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent == null) {
            return;
        }
        String parentTag = parent.getNodeName();
        boolean isResourceDefinition = "resources".equals(parentTag)
                || ("declare-styleable".equals(parentTag) && "attr".equals(element.getTagName()));

        if (!isResourceDefinition) {
            return;
        }

        if (!element.hasAttribute("name")) {
            return;
        }

        Attr nameAttr = element.getAttributeNode("name");
        if (nameAttr == null) {
            return;
        }

        String name = nameAttr.getValue();
        if (!name.startsWith(activePrefix)) {
            context.report(
                    ISSUE,
                    nameAttr,
                    context.getLocation(nameAttr),
                    String.format("Resource named `%s` does not start with the project's resource prefix `%s`", name, activePrefix));
        }
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        if (activePrefix == null) {
            return;
        }
        ResourceFolderType folderType = ResourceFolderType.getFolderType(context.file.getParentFile().getName());
        if (folderType != null && folderType != ResourceFolderType.VALUES) {
            String name = context.file.getName();
            int dot = name.indexOf('.');
            if (dot != -1) {
                name = name.substring(0, dot);
            }
            if (!name.startsWith(activePrefix)) {
                context.report(
                        ISSUE,
                        Location.create(context.file),
                        String.format("The resource name `%s` must begin with the prefix `%s`", name, activePrefix));
            }
        }
    }
}