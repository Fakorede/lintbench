package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DuplicateResourceDetector extends Detector implements XmlScanner, ResourceFolderScanner {
    public static final Issue DUPLICATE_DEFINITION = Issue.create(
            "DuplicateDefinition",
            "Duplicate resource definitions",
            "You can define a resource multiple times in different resource folders; "
                    + "that's how string translations are done, for example. However, "
                    + "defining the same resource more than once in the same resource folder "
                    + "is likely an error, for example attempting to add a new resource "
                    + "without realizing that the name is already used.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(DuplicateResourceDetector.class, Scope.ALL_RESOURCES_SCOPE));

    private Map<File, Set<String>> mReported;
    private Map<File, Map<String, Location>> mValueLocations;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mReported = new HashMap<>();
        mValueLocations = new HashMap<>();
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context, @NonNull String folderName) {
        ResourceFolderType folderType = ResourceFolderType.getFolderType(folderName);
        if (folderType == null || folderType == ResourceFolderType.VALUES) {
            return;
        }

        File[] files = context.file.listFiles();
        if (files == null) {
            return;
        }

        Map<String, List<File>> nameToFiles = new HashMap<>();
        for (File file : files) {
            if (file.isFile()) {
                String name = getResourceName(file);
                if (name != null) {
                    nameToFiles.computeIfAbsent(name, k -> new ArrayList<>()).add(file);
                }
            }
        }

        for (Map.Entry<String, List<File>> entry : nameToFiles.entrySet()) {
            List<File> list = entry.getValue();
            if (list.size() > 1) {
                for (int i = 1; i < list.size(); i++) {
                    File duplicate = list.get(i);
                    Location location = Location.create(duplicate);
                    String message = String.format(
                            "Duplicate resource `%1$s` appears more than once in `%2$s`",
                            entry.getKey(), folderName);
                    context.report(DUPLICATE_DEFINITION, location, message);
                }
            }
        }
    }

    private static String getResourceName(File file) {
        String name = file.getName();
        if (name.endsWith(".9.png")) {
            return name.substring(0, name.length() - ".9.png".length());
        }
        int dot = name.lastIndexOf('.');
        return dot == -1 ? name : name.substring(0, dot);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.VALUES) {
            return;
        }

        org.w3c.dom.Node parent = element.getParentNode();
        if (parent == null || !"resources".equals(parent.getNodeName())) {
            return;
        }

        String tag = element.getTagName();
        String name = element.getAttributeNS(null, "name");
        if (name == null || name.isEmpty()) {
            return;
        }

        if ("item".equals(tag)) {
            tag = element.getAttributeNS(null, "type");
        }

        if (tag == null || tag.isEmpty()) {
            return;
        }

        File folder = context.file.getParentFile();
        Map<String, Location> map = mValueLocations.computeIfAbsent(folder, k -> new HashMap<>());
        String key = tag + "/" + name;
        Location existing = map.get(key);
        if (existing != null) {
            String message = String.format(
                    "Duplicate resource `%1$s` appears more than once in `%2$s`",
                    name, folder.getName());
            context.report(DUPLICATE_DEFINITION, context.getLocation(element), message);
        } else {
            map.put(key, context.getLocation(element));
        }
    }
}