package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class ResourcePrefixDetector extends Detector implements XmlScanner {
    public static final Issue ISSUE = Issue.create(
            "WrongResourcePrefix",
            "Resource with wrong prefix",
            "In Gradle projects, you can specify a resource prefix that all resources in the project must conform to. This makes it easier to ensure that you don't accidentally combine resources from different libraries.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(ResourcePrefixDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String RESOURCE_PREFIX = "myproject_"; // Example prefix

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public List<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check for resource names in XML elements
        String name = element.getAttribute("name");
        if (name != null && !name.isEmpty()) {
            checkResourceName(context, element, name);
        }
    }

    @Override
    public List<String> getApplicableAttributes() {
        return Collections.emptyList();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Check for resource names in XML attributes
        String value = attribute.getValue();
        if (value != null && !value.isEmpty()) {
            checkResourceName(context, attribute, value);
        }
    }

    private void checkResourceName(XmlContext context, org.w3c.dom.Node node, String name) {
        if (!name.startsWith(RESOURCE_PREFIX)) {
            context.report(ISSUE, node, context.getLocation(node), "Resource name does not conform to the required prefix: " + RESOURCE_PREFIX);
        }
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        // No specific action needed here.
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        // No specific action needed here.
    }
}