package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.DOT_9PNG;

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
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.io.File;
import java.util.Collection;
import java.util.EnumSet;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class ResourcePrefixDetector extends Detector
        implements Detector.XmlScanner, Detector.ResourceFolderScanner {
    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            ...,
            Category.CORRECTNESS,
            3,
            Severity.WARNING,
            new Implementation(ResourcePrefixDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.RESOURCE_FOLDER)));

    private String mResourcePrefix;

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        ...
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        ...
    }

    @Override
    public void checkFolder(
            @NonNull ResourceContext context,
            @NonNull ResourceFolderType folderType,
            @NonNull File folder) {
        ...
    }

    private static String getBaseName(String fileName) { ... }
    public static boolean libraryStyleMatches(...) ...
    public static boolean libraryPrefixMatches(...) ...
    private static String underlinesToCamelCase(...) ...
    private static String camelCaseToUnderlines(...) ...
}