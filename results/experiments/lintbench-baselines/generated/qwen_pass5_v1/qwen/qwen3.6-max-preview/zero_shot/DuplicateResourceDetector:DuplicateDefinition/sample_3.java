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

import static com.android.tools.lint.detector.api.XmlScanner.ALL;

public class DuplicateResourceDetector extends ResourceXmlDetector {
    private Map<File, Map<String, Location>> mFolderNames;

    public static final Issue ISSUE = Issue.create(
            "DuplicateDefinition",
            "Duplicate definitions of resources",
            "You can define a resource multiple times in different resource folders; " +
            "that's how string translations are done, for example. However, defining " +
            "the same resource more than once in the same resource folder is likely an error, " +
            "for example attempting to add a new resource without realizing that the name is " +
            "already used, and so on.",
            Category.CORRECTNESS,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void beforeCheckProject(Context context) {
        mFolderNames = new HashMap<>();
    }

    @Override
    public void afterCheckProject(Context context) {
        mFolderNames = null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttribute("name");
        if (name.isEmpty()) {
            return;
        }

        File folder = context.file.getParentFile();
        if (folder == null) {
            return;
        }

        Map<String, Location> names = mFolderNames.computeIfAbsent(folder, f -> new HashMap<>());
        Location existing = names.get(name);
        if (existing != null) {
            Location location = context.getLocation(element);
            location.setSecondary(existing);
            context.report(ISSUE, location, String.format("Duplicate resource name \"%s\"", name));
        } else {
            names.put(name, context.getLocation(element));
        }
    }
}