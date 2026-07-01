package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DuplicateResourceDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "DuplicateDefinition",
            "Duplicate definitions of resources",
            "You can define a resource multiple times in different resource folders; " +
            "that's how string translations are done, for example. However, defining the same " +
            "resource more than once in the same resource folder is likely an error, for example " +
            "attempting to add a new resource without realizing that the name is already used, " +
            "and so on.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private Map<File, Map<String, Location>> folderToNames = new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parent = element.getParentNode();
        if (parent instanceof Element && "resources".equals(((Element) parent).getTagName())) {
            String name = element.getAttribute("name");
            if (name != null && !name.isEmpty()) {
                File folder = context.file.getParentFile();
                if (folder == null) {
                    return;
                }

                Map<String, Location> names = folderToNames.computeIfAbsent(folder, f -> new HashMap<>());
                Location location = context.getLocation(element.getAttributeNode("name"));
                Location existing = names.put(name, location);
                if (existing != null) {
                    String message = String.format("Duplicate resource name \"%s\"", name);
                    location.setSecondary(existing);
                    context.report(ISSUE, location, message);
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(Context context) {
        folderToNames.clear();
    }
}