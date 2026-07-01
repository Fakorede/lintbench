package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

public class ResourcePrefixDetector extends Detector implements Detector.XmlScanner, Detector.BinaryResourceScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ResourcePrefixDetector.class, EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "ResourceName",
                    "Resource with Wrong Prefix",
                    "In Gradle projects you can specify a resource prefix that all resources in the project must conform to. " +
                    "This makes it easier to ensure that you don't accidentally combine resources from different libraries, " +
                    "since they all end up in the same shared app namespace.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    private String mPrefix;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        // No project-wide initialization required
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        mPrefix = null;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mPrefix = context.getProject().getResourcePrefix();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mPrefix == null || mPrefix.isEmpty()) {
            return;
        }

        String name;
        if (context.isInValues()) {
            // In values files, only check direct children of <resources> that define a name
            if (element.getParentNode() == null || !"resources".equals(element.getParentNode().getNodeName())) {
                return;
            }
            name = element.getAttribute("name");
            if (name == null || name.isEmpty()) {
                return;
            }
            if (!name.startsWith(mPrefix)) {
                String message = "Resource name `" + name + "` does not start with the project's resource prefix `" + mPrefix + "`";
                context.report(ISSUE, element, context.getLocation(element), message);
            }
        } else {
            // For other XML resources (layouts, drawables, etc.), only check the root element
            if (element != context.document.getDocumentElement()) {
                return;
            }
            name = getBaseName(context.file.getName());
            if (!name.startsWith(mPrefix)) {
                String message = "Resource name `" + name + "` does not start with the project's resource prefix `" + mPrefix + "`";
                context.report(ISSUE, element, context.getLocation(element), message);
            }
        }
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        if (mPrefix == null || mPrefix.isEmpty()) {
            return;
        }
        String name = getBaseName(context.file.getName());
        if (!name.startsWith(mPrefix)) {
            String message = "Resource name `" + name + "` does not start with the project's resource prefix `" + mPrefix + "`";
            context.report(ISSUE, context.getLocation(), message);
        }
    }

    @NonNull
    private static String getBaseName(@NonNull String filename) {
        int dot = filename.lastIndexOf('.');
        return dot == -1 ? filename : filename.substring(0, dot);
    }
}