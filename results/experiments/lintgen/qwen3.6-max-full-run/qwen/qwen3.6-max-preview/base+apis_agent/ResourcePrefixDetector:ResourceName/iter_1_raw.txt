package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;

public class ResourcePrefixDetector extends Detector implements ResourceFolderScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resource prefix that all resources " +
            "in the project must conform to. This makes it easier to ensure that you don't " +
            "accidentally combine resources from different libraries, since they all end " +
            "up in the same shared app namespace.",
            Category.CORRECTNESS, 6, Severity.WARNING,
            new Implementation(ResourcePrefixDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void checkFolder(ResourceContext context, String folderName) {
        // Folder-level scanning hook. Resource prefix validation for file names
        // is typically handled by the build system or other scanners.
        // This detector focuses on XML value resources via visitElement.
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        String tag = element.getTagName();
        if ("resources".equals(tag) || "eat-comment".equals(tag) || "skip".equals(tag)) {
            return;
        }

        String name = element.getAttribute("name");
        if (name != null && !name.isEmpty() && !name.startsWith(prefix)) {
            Attr nameAttr = element.getAttributeNode("name");
            context.report(ISSUE, nameAttr, context.getLocation(nameAttr),
                    "Resource does not start with required prefix \"" + prefix + "\"");
        }
    }
}