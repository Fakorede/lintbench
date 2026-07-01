package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

public class ResourcePrefixDetector extends Detector implements XmlScanner, BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resource prefix that all resources " +
            "in the project must conform to. This makes it easier to ensure that you don't " +
            "accidentally combine resources from different libraries, since they all end " +
            "up in the same shared app namespace.",
            Category.CORRECTNESS,
            5,
            Severity.FATAL,
            new Implementation(ResourcePrefixDetector.class, EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE))
    );

    private String prefix;

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        prefix = context.getProject().getResourcePrefix();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        prefix = null;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            ResourceFolderType folderType = xmlContext.getResourceFolderType();
            if (folderType != null && folderType != ResourceFolderType.VALUES) {
                String fileName = context.file.getName();
                int dot = fileName.lastIndexOf('.');
                String resourceName = dot > 0 ? fileName.substring(0, dot) : fileName;
                if (!resourceName.startsWith(prefix)) {
                    context.report(ISSUE, Location.create(context.file),
                            "Resource name does not start with required prefix `" + prefix + "`");
                }
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("resources");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.VALUES) {
            return;
        }

        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childEl = (Element) child;
                String name = childEl.getAttribute("name");
                if (name != null && !name.isEmpty() && !name.startsWith(prefix)) {
                    context.report(ISSUE, childEl, context.getLocation(childEl),
                            "Resource name does not start with required prefix `" + prefix + "`");
                }
            }
            child = child.getNextSibling();
        }
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        String fileName = context.file.getName();
        int dot = fileName.lastIndexOf('.');
        String resourceName = dot > 0 ? fileName.substring(0, dot) : fileName;
        if (!resourceName.startsWith(prefix)) {
            context.report(ISSUE, Location.create(context.file),
                    "Resource name does not start with required prefix `" + prefix + "`");
        }
    }
}