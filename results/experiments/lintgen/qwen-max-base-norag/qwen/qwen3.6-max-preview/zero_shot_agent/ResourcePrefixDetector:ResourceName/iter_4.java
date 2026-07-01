package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

public class ResourcePrefixDetector extends Detector implements Detector.XmlScanner, Detector.BinaryResourceScanner {
    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resource prefix that all resources " +
            "in the project must conform to. This makes it easier to ensure that you don't " +
            "accidentally combine resources from different libraries, since they all end " +
            "up in the same shared app namespace.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ResourcePrefixDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("name");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }
        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            return;
        }
        Element parent = (Element) parentNode;
        if (!"resources".equals(parent.getTagName())) {
            return;
        }

        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        String name = attribute.getValue();
        if (name != null && !name.startsWith(prefix)) {
            String message = String.format(
                    "Resource name `%s` does not start with the project prefix `%s`",
                    name, prefix
            );
            context.report(ISSUE, context.getLocation(attribute), message);
        }
    }

    @Override
    public void visitBinaryResource(Context context) {
        File file = context.file;
        if (file == null) {
            return;
        }
        File parentDir = file.getParentFile();
        if (parentDir == null) {
            return;
        }

        String parentName = parentDir.getName();
        if (parentName.startsWith("values")) {
            return;
        }

        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;

        if (!baseName.startsWith(prefix)) {
            String message = String.format(
                    "Resource name `%s` does not start with the project prefix `%s`",
                    baseName, prefix
            );
            context.report(ISSUE, Location.create(file), message);
        }
    }
}