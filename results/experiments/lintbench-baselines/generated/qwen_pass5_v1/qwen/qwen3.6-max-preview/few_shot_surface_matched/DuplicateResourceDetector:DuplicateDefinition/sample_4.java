package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_NAME;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class DuplicateResourceDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateDefinition",
                    "Duplicate Resource Definition",
                    "Defining the same resource more than once in the same resource folder is likely an error. "
                            + "Resource names must be unique within a given folder.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private Set<String> mNames;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_NAME);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mNames = new HashSet<>();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getValue();
        if (name != null && !name.isEmpty()) {
            if (!mNames.add(name)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "Duplicate resource name \"" + name + "\"");
            }
        }
    }
}