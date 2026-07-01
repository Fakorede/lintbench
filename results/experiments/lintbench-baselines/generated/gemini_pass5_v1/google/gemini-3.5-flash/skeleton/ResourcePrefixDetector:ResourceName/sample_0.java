package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class ResourcePrefixDetector extends Detector implements XmlScanner, BinaryResourceScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ResourcePrefixDetector.class, EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "ResourceName",
                    "Resource with Wrong Prefix",
                    "In Gradle projects you can specify a resource prefix that all resources "
                            + "in the project must conform to. This makes it easier to ensure that "
                            + "you don't accidentally combine resources from different libraries, "
                            + "since they all end up in the same shared app namespace.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("resources");
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
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        if (context.file.getName().endsWith(".xml")) {
            ResourceFolderType folderType = context.getResourceFolderType();
            if (folderType != null && folderType != ResourceFolderType.VALUES) {
                String name = context.file.getName();
                int dot = name.indexOf('.');
                String baseName = dot != -1 ? name.substring(0, dot) : name;
                if (!baseName.startsWith(prefix)) {
                    context.report(
                            ISSUE,
                            Location.create(context.file),
                            "The resource name `" + baseName + "` must begin with the prefix `" + prefix + "`");
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (childElement.hasAttribute("name")) {
                    String name = childElement.getAttribute("name");
                    if (!name.startsWith(prefix)) {
                        context.report(
                                ISSUE,
                                childElement,
                                context.getNameLocation(childElement),
                                "The resource name `" + name + "` must begin with the prefix `" + prefix + "`");
                    }
                }
            }
        }
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != null && folderType != ResourceFolderType.VALUES) {
            String name = context.file.getName();
            int dot = name.indexOf('.');
            String baseName = dot != -1 ? name.substring(0, dot) : name;
            if (!baseName.startsWith(prefix)) {
                context.report(
                        ISSUE,
                        Location.create(context.file),
                        "The resource name `" + baseName + "` must begin with the prefix `" + prefix + "`");
            }
        }
    }
}