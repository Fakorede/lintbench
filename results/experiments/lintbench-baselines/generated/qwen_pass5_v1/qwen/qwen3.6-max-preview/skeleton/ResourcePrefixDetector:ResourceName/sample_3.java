package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import org.w3c.dom.Element;

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

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        // No initialization needed
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No cleanup needed
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No per-file initialization needed
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (element.getParentNode() != null) {
            return;
        }
        if (context.getResourceFolderType() == ResourceFolderType.VALUES) {
            return;
        }
        checkPrefix(context, context.file, element);
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        if (context.getResourceFolderType() == ResourceFolderType.VALUES) {
            return;
        }
        checkPrefix(context, context.file, null);
    }

    private void checkPrefix(@NonNull Context context, @NonNull File file, @Nullable Element element) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        String name = file.getName();
        int dot = name.indexOf('.');
        if (dot != -1) {
            name = name.substring(0, dot);
        }

        if (!name.startsWith(prefix)) {
            String message = "Resource names in this project must start with the prefix `" + prefix + "`";
            if (element != null && context instanceof XmlContext) {
                XmlContext xmlContext = (XmlContext) context;
                xmlContext.report(ISSUE, element, xmlContext.getLocation(element), message);
            } else {
                context.report(ISSUE, context.getLocation(file), message);
            }
        }
    }
}