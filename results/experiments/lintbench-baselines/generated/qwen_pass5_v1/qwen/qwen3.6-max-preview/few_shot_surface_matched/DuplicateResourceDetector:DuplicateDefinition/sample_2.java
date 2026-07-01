package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
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

    public static final Issue ISSUE = Issue.create(
            "DuplicateDefinition",
            "Duplicate Resource Definition",
            "You can define a resource multiple times in different resource folders; that's how " +
            "string translations are done, for example. However, defining the same resource more " +
            "than once in the same resource folder is likely an error, for example attempting to " +
            "add a new resource without realizing that the name is already used, and so on.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private Set<String> seenNames;

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("name");
    }

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        seenNames = new HashSet<>();
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String name = attribute.getValue();
        if (name != null && !name.isEmpty()) {
            if (!seenNames.add(name)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "Duplicate resource name `" + name + "`");
            }
        }
    }
}