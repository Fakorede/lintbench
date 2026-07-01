package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.*;

public class DuplicateResourceDetector extends Detector implements XmlScanner, ResourceFolderScanner {

    public static final Issue DUPLICATE_DEFINITION = Issue.create(
            "DuplicateDefinition",
            "Duplicate resource definition",
            "Defining the same resource more than once in the same resource folder is usually an error, "
                    + "for example attempting to add a new resource without realizing that the name is already used.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(DuplicateResourceDetector.class, Scope.ALL_RESOURCES_SCOPE)
    );

    private final Map<String, Set<String>> mReportedFileResources = new HashMap<>();
    private final Map<String, Map<String, Location>> mFirstValueResourceLocations = new HashMap<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mReportedFileResources.clear();
        mFirstValueResourceLocations.clear();
    }

    @Override
    public void checkResourceFolder(@NonNull ResourceFolderContext context) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null || folderType == ResourceFolderType.VALUES) {
            return;
        }

        File folder = context.getFolder();
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        Map<String, List<File>> nameToFiles = new HashMap<>();
        for (File file : files) {
            if (!file.isFile() || file.isHidden()) {
                continue;
            }
            String name = getResourceName(file);
            if (name.isEmpty()) {
                continue;
            }
            nameToFiles.computeIfAbsent(name, k -> new ArrayList<>()).add(file);
        }

        String folderPath = folder.getAbsolutePath();
        Set<String> reported = mReportedFileResources.computeIfAbsent(folderPath, k -> new HashSet<>());

        for (Map.Entry<String, List<File>> entry : nameToFiles.entrySet()) {
            List<File> duplicates = entry.getValue();
            if (duplicates.size() < 2) {
                continue;
            }
            String name = entry.getKey();
            if (!reported.add(name)) {
                continue;
            }

            String message = String.format(
                    "Resource `%1$s` is defined more than once in the `%2$s` folder",
                    name, folderType.getName());

            for (int i = 1; i < duplicates.size(); i++) {
                context.report(DUPLICATE_DEFINITION, Location.create(duplicates.get(i)), message);
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.VALUES) {
            return;
        }

        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }
        Element parent = (Element) parentNode;
        if (!"resources".equals(parent.getTagName())) {
            return;
        }

        String type = element.getTagName();
        String name = element.getAttributeNS(null, "name");

        if ("item".equals(type)) {
            type = element.getAttributeNS(null, "type");
        }

        if (type == null || type.isEmpty() || name == null || name.isEmpty()) {
            return;
        }

        File folder = context.file.getParentFile();
        if (folder == null) {
            return;
        }

        String folderPath = folder.getAbsolutePath();
        String key = type + "/" + name;

        Map<String, Location> folderMap =
                mFirstValueResourceLocations.computeIfAbsent(folderPath, k -> new HashMap<>());

        Location first = folderMap.putIfAbsent(key, context.getLocation(element));
        if (first != null) {
            String message = String.format(
                    "Duplicate resource `%1$s/%2$s` defined here; previously defined in `%3$s`",
                    type, name, first.getFile().getName());
            context.report(DUPLICATE_DEFINITION, context.getLocation(element), message);
        }
    }

    private static String getResourceName(File file) {
        String fileName = file.getName();
        int dot = fileName.indexOf('.');
        return dot == -1 ? fileName : fileName.substring(0, dot);
    }
}