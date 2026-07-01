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

import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
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

    // Map: Resource Folder -> Resource Name -> List of Locations
    private final Map<File, Map<String, List<Location>>> folderResources = new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!element.hasAttribute("name")) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.VALUES) {
            return;
        }

        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        File folder = context.file.getParentFile();
        if (folder == null) {
            return;
        }

        folderResources
            .computeIfAbsent(folder, f -> new HashMap<>())
            .computeIfAbsent(name, n -> new ArrayList<>())
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

                    Location current = secondary;
                    for (int i = 2; i < locations.size(); i++) {
                        current.setSecondary(locations.get(i));
                        current = current.getSecondary();
                    }
                    primary.setSecondary(secondary);

                    String message = String.format("Duplicate resource definition: `%s`", name);
                    context.report(ISSUE, primary, message);
                }
            }
        }
        folderResources.clear();
    }
}