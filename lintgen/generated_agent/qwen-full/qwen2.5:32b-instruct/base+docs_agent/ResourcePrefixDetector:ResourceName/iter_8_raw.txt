package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import java.util.Arrays;
import java.util.List;

public class ResourcePrefixDetector extends Detector implements Detector.ResourceScanner {
    private static final String ISSUE_ID = "WrongResourcePrefix";
    private static final String SHORT_DESCRIPTION = "Resource with wrong prefix";
    private static final String EXPLANATION =
            "In Gradle projects, you can specify a resource prefix that all resources in the project must conform to. This makes it easier to ensure that you don't accidentally combine resources from different libraries since they all end up in the same shared app namespace.";

    public static final Issue ISSUE = Issue.create(
            ISSUE_ID,
            SHORT_DESCRIPTION,
            EXPLANATION,
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(ResourcePrefixDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private String expectedPrefix;

    @Override
    public void beforeCheck(@NonNull Context context) {
        super.beforeCheck(context);
        // Assuming the prefix is defined in a global configuration or passed as an option.
        this.expectedPrefix = "app_";  // Example default value, should be configurable.
    }

    @Override
    public List<String> getApplicableResourceTypes() {
        return Arrays.asList(
                ResourceFolderType.DRAWABLE.name().toLowerCase(),
                ResourceFolderType.LAYOUT.name().toLowerCase(),
                ResourceFolderType.MENU.name().toLowerCase(),
                ResourceFolderType.VALUES.name().toLowerCase()
        );
    }

    @Override
    public void visitResource(@NonNull Context context, @NonNull String resourceType,
                              @NonNull String resourceName) {
        if (!resourceName.startsWith(expectedPrefix)) {
            // Report the issue.
            Location location = Location.create(context.getDriver(), context.getFile());
            context.report(ISSUE, location, "Resource name '" + resourceName +
                    "' does not conform to expected prefix: " + expectedPrefix);
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType type) {
        return true;
    }
}