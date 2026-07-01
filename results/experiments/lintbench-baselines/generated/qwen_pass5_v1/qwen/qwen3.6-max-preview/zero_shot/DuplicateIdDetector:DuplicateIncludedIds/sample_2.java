package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.SdkConstants;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.*;

public class DuplicateIdDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "DuplicateIncludedIds",
            "Duplicate ids across layouts combined with include tags",
            "It's okay for two independent layouts to use the same ids. However, if layouts are combined with include tags, then the id's need to be unique within any chain of included layouts, or `Activity#findViewById()` can return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private final Map<String, List<IdInfo>> fileToIds = new HashMap<>();
    private final Map<String, List<String>> fileToIncludes = new HashMap<>();
    private final Map<String, String> resourceNameToFile = new HashMap<>();
    private final Set<String> reportedDuplicates = new HashSet<>();

    private static class IdInfo {
        final String idName;
        final Location location;
        IdInfo(String idName, Location location) {
            this.idName = idName;
            this.location = location;
        }
    }

    @Override
    public Collection<ResourceFolderType> getApplicableFolderTypes() {
        return EnumSet.of(ResourceFolderType.LAYOUT);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String filePath = context.file.getPath();
        String fileName = context.file.getName();
        String baseName = LintUtils.getBaseName(fileName);
        int dash = baseName.indexOf('-');
        String resName = dash != -1 ? baseName.substring(0, dash) : baseName;
        resourceNameToFile.put(resName, filePath);

        Attr idAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (idAttr != null) {
            String idValue = idAttr.getValue();
            String idName = idValue;
            if (idValue.startsWith("@+id/")) {
                idName = idValue.substring(5);
            } else if (idValue.startsWith("@id/")) {
                idName = idValue.substring(4);
            }
            if (!idName.isEmpty()) {
                fileToIds.computeIfAbsent(filePath, k -> new ArrayList<>())
                        .add(new IdInfo(idName, context.getLocation(idAttr)));
            }
        }

        if (SdkConstants.TAG_INCLUDE.equals(element.getTagName())) {
            Attr layoutAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT);
            if (layoutAttr != null) {
                String layoutValue = layoutAttr.getValue();
                if (layoutValue.startsWith("@layout/")) {
                    String includedRes = layoutValue.substring(8);
                    fileToIncludes.computeIfAbsent(filePath, k -> new ArrayList<>()).add(includedRes);
                }
            }
        }
    }

    @Override
    public void afterCheckProject(Context context) {
        for (String rootFile : fileToIds.keySet()) {
            checkFileChain(context, rootFile);
        }
        fileToIds.clear();
        fileToIncludes.clear();
        resourceNameToFile.clear();
        reportedDuplicates.clear();
    }

    private void checkFileChain(Context context, String rootFile) {
        Set<String> visited = new HashSet<>();
        List<String> chainFiles = new ArrayList<>();
        collectChain(rootFile, visited, chainFiles);

        if (chainFiles.size() <= 1) {
            return;
        }

        Map<String, List<IdInfo>> idToLocations = new HashMap<>();
        for (String file : chainFiles) {
            List<IdInfo> ids = fileToIds.get(file);
            if (ids != null) {
                for (IdInfo info : ids) {
                    idToLocations.computeIfAbsent(info.idName, k -> new ArrayList<>()).add(info);
                }
            }
        }

        for (Map.Entry<String, List<IdInfo>> entry : idToLocations.entrySet()) {
            List<IdInfo> locations = entry.getValue();
            Set<String> filesWithId = new HashSet<>();
            for (IdInfo info : locations) {
                filesWithId.add(info.location.getFile().getPath());
            }

            if (filesWithId.size() > 1) {
                String reportKey = entry.getKey() + chainFiles.hashCode();
                if (reportedDuplicates.add(reportKey)) {
                    IdInfo first = locations.get(0);
                    IdInfo second = locations.get(1);
                    Location location = first.location;
                    location.setSecondary(second.location);
                    String message = String.format(
                            "Duplicate id `%1$s`, already defined in layout `%2$s`",
                            first.idName, second.location.getFile().getName());
                    context.report(ISSUE, location, message);
                }
            }
        }
    }

    private void collectChain(String file, Set<String> visited, List<String> chain) {
        if (!visited.add(file)) {
            return;
        }
        chain.add(file);
        List<String> includes = fileToIncludes.get(file);
        if (includes != null) {
            for (String resName : includes) {
                String includedFile = resourceNameToFile.get(resName);
                if (includedFile != null) {
                    collectChain(includedFile, visited, chain);
                }
            }
        }
    }
}