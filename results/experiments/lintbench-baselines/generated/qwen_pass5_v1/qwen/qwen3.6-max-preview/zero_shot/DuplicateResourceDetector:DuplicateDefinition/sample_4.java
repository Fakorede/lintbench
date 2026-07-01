package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
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

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "DuplicateDefinition",
            "Duplicate definitions of resources",
            "You can define a resource multiple times in different resource folders; that's how " +
            "string translations are done, for example. However, defining the same resource " +
            "more than once in the same resource folder is likely an error, for example " +
            "attempting to add a new resource without realizing that the name is already used, " +
            "and so on.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private final Map<File, Map<String, Location>> folderLocations = new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void beforeCheckFolder(Context context) {
        folderLocations.put(context.file, new HashMap<>());
    }

    @Override
    public void afterCheckFolder(Context context) {
        folderLocations.remove(context.file);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.VALUES) {
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

        Map<String, Location> nameMap = folderLocations.get(folder);
        if (nameMap == null) {
            nameMap = new HashMap<>();
            folderLocations.put(folder, nameMap);
        }

        Location existing = nameMap.get(name);
        if (existing != null) {
            Location location = context.getLocation(element);
            location.setSecondary(existing);
            context.report(ISSUE, element, location,
                    "Duplicate resource name \"" + name + "\"");
        } else {
            nameMap.put(name, context.getLocation(element));
        }
    }
}