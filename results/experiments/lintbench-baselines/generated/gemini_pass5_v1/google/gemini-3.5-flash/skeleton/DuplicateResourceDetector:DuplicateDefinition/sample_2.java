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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

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

    private final Map<String, Set<String>> folderToResources = new HashMap<>();

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
        // No-op
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        Node parent = element.getParentNode();
        if (parent == null || !"resources".equals(parent.getNodeName())) {
            return;
        }

        String type = element.getTagName();
        if ("item".equals(type)) {
            String typeAttr = element.getAttribute("type");
            if (typeAttr != null && !typeAttr.isEmpty()) {
                type = typeAttr;
            }
        }

        String name = attribute.getValue();
        if (name == null || name.isEmpty()) {
            return;
        }

        String folderName = "";
        if (context.file != null && context.file.getParentFile() != null) {
            folderName = context.file.getParentFile().getName();
        }
        String projectKey = context.getProject().getName() + "/" + folderName;

        String resourceKey = type + "/" + name;
        Set<String> defined = folderToResources.get(projectKey);
        if (defined == null) {
            defined = new HashSet<>();
            folderToResources.put(projectKey, defined);
        }

        if (defined.contains(resourceKey)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    String.format("Duplicate definition of resource %s/%s", type, name));
        } else {
            defined.add(resourceKey);
        }
    }
}