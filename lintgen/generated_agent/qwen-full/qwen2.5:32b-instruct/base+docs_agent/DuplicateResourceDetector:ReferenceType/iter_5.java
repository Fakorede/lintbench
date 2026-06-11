package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Set;

public class DuplicateResourceDetector extends Detector implements XmlScanner {

    private static final String ISSUE_ID = "DuplicateResource";
    private static final String SHORT_DESCRIPTION = "Incorrect resource alias type";

    public static final Issue ISSUE = Issue.create(
            ISSUE_ID,
            SHORT_DESCRIPTION,
            "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @NonNull
    @Override
    public Set<String> getApplicableAttributes() {
        return Set.of("name", "item");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context,
                               @NonNull ResourceFolderType folderType,
                               @NonNull ResourceType resourceType,
                               @NonNull Element element,
                               @NonNull Attr attribute) {

        String attrValue = attribute.getValue();
        if (attrValue.startsWith("@")) {
            android.util.Pair<ResourceType, String> referencedResource = parseReference(attrValue);
            if (referencedResource != null && !resourceType.equals(referencedResource.first)) {
                // Report the issue
                context.report(ISSUE, element, context.getLocation(attribute),
                        "The resource alias type '%s' does not match the referenced resource type '%s'.",
                        resourceType, referencedResource.first);
            }
        }
    }

    private android.util.Pair<ResourceType, String> parseReference(String reference) {
        if (reference.startsWith("@")) {
            int colonIndex = reference.indexOf(':');
            if (colonIndex != -1) {
                String typeString = reference.substring(1, colonIndex);
                ResourceType resourceType = ResourceType.fromFolderName(typeString);
                if (resourceType != null) {
                    return android.util.Pair.create(resourceType, reference.substring(colonIndex + 1));
                }
            }
        }
        return null;
    }
}