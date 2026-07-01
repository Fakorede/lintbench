package com.android.tools.lint.checks;

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

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

public class ResourcePrefixDetector extends Detector implements XmlScanner, BinaryResourceScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ResourceName",
                    "Resource with Wrong Prefix",
                    "In Gradle projects you can specify a resource prefix that all resources "
                            + "in the project must conform to. This makes it easier to ensure that you don't "
                            + "accidentally combine resources from different libraries, since they all end "
                            + "in the same shared app namespace.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            ResourcePrefixDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE)
                    )
            );

    private String prefix = null;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "string",
                "color",
                "dimen",
                "style",
                "declare-styleable",
                "integer",
                "bool",
                "array",
                "string-array",
                "integer-array",
                "plurals",
                "item",
                "attr"
        );
    }

    @Override
    public void beforeCheckEachProject(Context context) {
        prefix = context.getProject().getResourcePrefix();
    }

    @Override
    public void afterCheckEachProject(Context context) {
        prefix = null;
    }

    @Override
    public void beforeCheckFile(Context context) {
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;
        com.android.resources.ResourceFolderType folderType = xmlContext.getFolderType();
        if (folderType == null) {
            return;
        }
        if (folderType != com.android.resources.ResourceFolderType.VALUES) {
            String resourceName = getResourceName(xmlContext.file.getName());
            if (!resourceName.startsWith(prefix)) {
                xmlContext.report(
                        ISSUE,
                        Location.create(xmlContext.file),
                        String.format("Resource '%s' does not start with the project's resource prefix '%s'", resourceName, prefix)
                );
            }
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        if (context.getFolderType() != com.android.resources.ResourceFolderType.VALUES) {
            return;
        }
        Node parent = element.getParentNode();
        if (parent == null || !"resources".equals(parent.getNodeName())) {
            return;
        }
        if (element.hasAttribute("name")) {
            String name = element.getAttribute("name");
            if (!name.startsWith(prefix)) {
                Attr nameAttr = element.getAttributeNode("name");
                Location location = nameAttr != null ? context.getLocation(nameAttr) : context.getLocation(element);
                context.report(
                        ISSUE,
                        element,
                        location,
                        String.format("Resource '%s' does not start with the project's resource prefix '%s'", name, prefix)
                );
            }
        }
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        String resourceName = getResourceName(context.file.getName());
        if (!resourceName.startsWith(prefix)) {
            context.report(
                    ISSUE,
                    Location.create(context.file),
                    String.format("Resource '%s' does not start with the project's resource prefix '%s'", resourceName, prefix)
            );
        }
    }

    private String getResourceName(String filename) {
        int dot = filename.indexOf('.');
        if (dot != -1) {
            return filename.substring(0, dot);
        }
        return filename;
    }
}