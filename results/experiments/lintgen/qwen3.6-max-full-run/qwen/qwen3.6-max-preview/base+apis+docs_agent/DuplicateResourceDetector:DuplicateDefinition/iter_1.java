package com.android.tools.lint.checks;

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
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DuplicateResourceDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
        "DuplicateDefinition",
        "Duplicate definitions of resources",
        "You can define a resource multiple times in different resource folders; " +
        "that's how string translations are done, for example. However, defining the same " +
        "resource more than once in the same resource folder is likely an error, for example " +
        "attempting to add a new resource without realizing that the name is already used, and so on.",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    // Map: Resource Folder -> Normalized Resource Name -> List of Locations
    private final Map<File, Map<String, List<Location>>> folderResources = new HashMap<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!element.hasAttribute("name")) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent == null || !"resources".equals(parent.getNodeName())) {
            return;
        }

        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        // Normalize name: dots and underscores are treated as equivalent in resource names
        String key = name.replace('.', '_');

        File folder = context.file.getParentFile();
        if (folder == null) {
            return;
        }

        folderResources
            .computeIfAbsent(folder, f -> new HashMap<>())
            .computeIfAbsent(key, n -> new ArrayList<>())
            .add(context.getLocation(element));
    }

    @Override
    public void afterCheckEachProject(Context context) {
        for (Map.Entry<File, Map<String, List<Location>>> folderEntry : folderResources.entrySet()) {
            for (Map.Entry<String, List<Location>> nameEntry : folderEntry.getValue().entrySet()) {
                List<Location> locations = nameEntry.getValue();
                if (locations.size() > 1) {
                    String name = nameEntry.getKey();
                    Location primary = locations.get(0);
                    Location secondary = locations.get(1);
                    primary.setSecondary(secondary);

                    Location current = secondary;
                    for (int i = 2; i < locations.size(); i++) {
                        Location next = locations.get(i);
                        current.setSecondary(next);
                        current = next;
                    }

                    String message = String.format("Duplicate resource definition: `%s`", name);
                    context.report(ISSUE, primary, message);
                }
            }
        }
        folderResources.clear();
    }
}