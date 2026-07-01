package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class DuplicateIdDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIncludedIds",
                    "Duplicate ids across layouts combined with include tags",
                    "It's okay for two independent layouts to use the same ids. However, if layouts are combined with include tags, then the id's need to be unique within any chain of included layouts, or Activity#findViewById() can return an unexpected view.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, Set<String>> mFileIds = new HashMap<>();
    private final Map<String, Set<String>> mFileIncludes = new HashMap<>();
    private final Map<String, XmlContext> mFileContexts = new HashMap<>();
    private final Map<String, Map<String, Location>> mFileIdLocations = new HashMap<>();
    private final Set<String> mReported = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("id");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("include");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (String file : mFileIds.keySet()) {
            Set<String> visited = new HashSet<>();
            Set<String> allIncluded = getTransitiveIncludes(file, visited);

            Map<String, List<String>> idToFiles = new HashMap<>();
            for (String id : mFileIds.getOrDefault(file, Collections.emptySet())) {
                idToFiles.computeIfAbsent(id, k -> new ArrayList<>()).add(file);
            }
            for (String inc : allIncluded) {
                for (String id : mFileIds.getOrDefault(inc, Collections.emptySet())) {
                    idToFiles.computeIfAbsent(id, k -> new ArrayList<>()).add(inc);
                }
            }

            for (Map.Entry<String, List<String>> entry : idToFiles.entrySet()) {
                List<String> files = entry.getValue();
                if (files.size() > 1) {
                    String id = entry.getKey();
                    Collections.sort(files);
                    String reportKey = files.get(0) + ":" + files.get(1) + ":" + id;
                    if (mReported.add(reportKey)) {
                        String definingFile = files.get(0);
                        String duplicateFile = files.get(1);
                        XmlContext ctx = mFileContexts.get(duplicateFile);
                        Map<String, Location> locs = mFileIdLocations.get(duplicateFile);
                        if (ctx != null && locs != null) {
                            Location loc = locs.get(id);
                            if (loc != null) {
                                String msg = String.format(
                                        "Duplicate id `%s`, already defined in layout `%s`",
                                        id, definingFile);
                                ctx.report(ISSUE, loc, msg);
                            }
                        }
                    }
                }
            }
        }
        mFileIds.clear();
        mFileIncludes.clear();
        mFileContexts.clear();
        mFileIdLocations.clear();
        mReported.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String layoutAttr = element.getAttributeNS("http://schemas.android.com/apk/res/android", "layout");
        if (layoutAttr != null && layoutAttr.startsWith("@layout/")) {
            String includedName = layoutAttr.substring(layoutAttr.indexOf('/') + 1);
            String fileName = getBaseName(context);
            mFileIncludes.computeIfAbsent(fileName, k -> new HashSet<>()).add(includedName);
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value != null && (value.startsWith("@+id/") || value.startsWith("@id/"))) {
            String id = value.substring(value.indexOf('/') + 1);
            String fileName = getBaseName(context);

            mFileIds.computeIfAbsent(fileName, k -> new HashSet<>()).add(id);
            mFileContexts.put(fileName, context);
            mFileIdLocations.computeIfAbsent(fileName, k -> new HashMap<>()).put(id, context.getLocation(attribute));
        }
    }

    private String getBaseName(XmlContext context) {
        String name = context.file.getName();
        int dot = name.indexOf('.');
        return dot != -1 ? name.substring(0, dot) : name;
    }

    private Set<String> getTransitiveIncludes(String file, Set<String> visited) {
        if (visited.contains(file)) {
            return Collections.emptySet();
        }
        visited.add(file);
        Set<String> result = new HashSet<>();
        Set<String> direct = mFileIncludes.get(file);
        if (direct != null) {
            for (String inc : direct) {
                result.add(inc);
                result.addAll(getTransitiveIncludes(inc, visited));
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector";
    }

    public int compareTo(DuplicateIdDetector other) {
        return 0;
    }
}