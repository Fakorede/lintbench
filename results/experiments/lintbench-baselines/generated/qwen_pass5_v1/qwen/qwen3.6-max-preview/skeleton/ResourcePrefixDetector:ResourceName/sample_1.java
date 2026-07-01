package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
                    "In Gradle projects you can specify a resource prefix that all resources in the project must conform to. This makes it easier to ensure that you don't accidentally combine resources from different libraries, since they all end up in the same shared app namespace.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    private String prefix;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

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

        String parentName = context.file.getParentFile() != null ? context.file.getParentFile().getName() : "";
        if (parentName.startsWith("values")) {
            return;
        }

        String fileName = context.file.getName();
        if (fileName.equals("AndroidManifest.xml") || fileName.equals("public.xml") || fileName.equals("ids.xml")) {
            return;
        }

        String baseName = fileName;
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) {
            baseName = baseName.substring(0, dot);
        }

        if (!baseName.startsWith(prefix)) {
            context.report(ISSUE, Location.create(context.file),
                    "Resource file name must be prefixed with '" + prefix + "'");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        String tagName = element.getTagName();
        if ("item".equals(tagName)) {
            return;
        }

        if (!name.startsWith(prefix)) {
            Node attributeNode = element.getAttributeNode("name");
            context.report(ISSUE, context.getLocation(attributeNode),
                    "Resource name must be prefixed with '" + prefix + "'");
        }
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        String fileName = context.file.getName();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;

        if (!baseName.startsWith(prefix)) {
            context.report(ISSUE, Location.create(context.file),
                    "Resource file name must be prefixed with '" + prefix + "'");
        }
    }
}