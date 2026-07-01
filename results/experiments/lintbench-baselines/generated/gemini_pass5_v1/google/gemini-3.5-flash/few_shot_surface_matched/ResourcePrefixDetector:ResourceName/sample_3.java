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

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

public class ResourcePrefixDetector extends Detector implements XmlScanner, BinaryResourceScanner {

    public static final Implementation IMPLEMENTATION = new Implementation(
            ResourcePrefixDetector.class,
            EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE)
    );

    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resource prefix that all resources "
                    + "in the project must conform to. This makes it easier to ensure that "
                    + "you don't accidentally combine resources from different libraries, "
                    + "since they all end up in the same shared app namespace.",
            Category.CORRECTNESS,
            8,
            Severity.FATAL,
            IMPLEMENTATION
    );

    private String activePrefix;

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
    public void beforeCheckFile(XmlContext context) {
        if (activePrefix == null || activePrefix.isEmpty()) {
            return;
        }
        com.android.resources.ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != null && folderType != com.android.resources.ResourceFolderType.VALUES) {
            String name = context.file.getName();
            int dot = name.indexOf('.');
            String baseName = dot != -1 ? name.substring(0, dot) : name;
            if (!baseName.startsWith(activePrefix)) {
                context.report(
                        ISSUE,
                        Location.create(context.file),
                        "Resource name `" + baseName + "` does not start with the project's resource prefix `" + activePrefix + "`"
                );
            }
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (activePrefix == null || activePrefix.isEmpty()) {
            return;
        }
        if (context.getResourceFolderType() != com.android.resources.ResourceFolderType.VALUES) {
            return;
        }
        Node parent = element.getParentNode();
        if (parent != null && "resources".equals(parent.getNodeName())) {
            Attr nameAttr = element.getAttributeNode("name");
            if (nameAttr != null) {
                String name = nameAttr.getValue();
                if (!name.startsWith(activePrefix)) {
                    context.report(
                            ISSUE,
                            nameAttr,
                            context.getLocation(nameAttr),
                            "Resource name `" + name + "` does not start with the project's resource prefix `" + activePrefix + "`"
                    );
                }
            }
        }
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        if (activePrefix == null || activePrefix.isEmpty()) {
            return;
        }
        String name = context.file.getName();
        int dot = name.indexOf('.');
        String baseName = dot != -1 ? name.substring(0, dot) : name;
        if (!baseName.startsWith(activePrefix)) {
            context.report(
                    ISSUE,
                    Location.create(context.file),
                    "Resource name `" + baseName + "` does not start with the project's resource prefix `" + activePrefix + "`"
            );
        }
    }
}