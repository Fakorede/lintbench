package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateDefinition",
                    "Duplicate definitions of resources",
                    "Defining the same resource more than once in the same resource folder "
                            + "is likely an error, for example attempting to add a new resource "
                            + "without realizing that the name is already used.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private Set<String> mDefinedResources;

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("name");
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mDefinedResources = new HashSet<>();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getValue();
        if (name == null || name.isEmpty()) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element.getParentNode() != element.getOwnerDocument().getDocumentElement()) {
            return;
        }

        String tagName = element.getTagName();
        String type = tagName;
        if (tagName.equals("item")) {
            type = element.getAttribute("type");
            if (type == null || type.isEmpty()) {
                return;
            }
        } else if (tagName.equals("declare-styleable")) {
            type = "styleable";
        } else if (tagName.equals("string-array") || tagName.equals("integer-array")) {
            type = "array";
        }

        String key = type + ":" + name;
        if (mDefinedResources.contains(key)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    String.format("Duplicate definition of %s '%s'", type, name));
        } else {
            mDefinedResources.add(key);
        }
    }
}