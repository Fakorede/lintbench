package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DuplicateIdDetector extends ResourceXmlDetector {
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_ID = "id";
    private static final String ATTR_LAYOUT = "layout";
    private static final String INCLUDE_TAG = "include";
    private static final String MERGE_TAG = "merge";
    private static final String LAYOUT_PREFIX = "@layout/";

    public static final Issue ISSUE = Issue.create(
            "DuplicateIncludedIds",
            "Duplicate ids across layouts combined with include tags",
            "It's okay for two independent layouts to use the same ids. However, if "
                    + "layouts are combined with include tags, then the ids need to be unique "
                    + "within any chain of included layouts, or Activity#findViewById() can "
                    + "return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(DuplicateIdDetector.class,
                    Scope.RESOURCE_FILE_SCOPE, Scope.ALL_RESOURCE_FILES));

    private final Map<String, FileData> mFiles = new HashMap<>();
    private final Map<String, List<FileData>> mLayouts = new HashMap<>();
    private final Map<String, LayoutRecord> mCache = new HashMap<>();

    private static class FileData {
        final File file;
        final String layoutName;
        final String path;
        String rootTag;
        String rootId;
        final List<IdOccurrence> ids = new ArrayList<>();
        final List<IncludeOccurrence> includes = new ArrayList<>();
        IncludeOccurrence rootInclude;

        FileData(File file, String layoutName, String path) {
            this.file = file;
            this.layoutName = layoutName;
            this.path = path;
        }

        boolean rootIsMerge() {
            return MERGE_TAG.equals(rootTag);
        }
    }

    private static class IdOccurrence {
        final String id;
        final Location location;

        IdOccurrence(String id, Location location) {
            this.id = id;
            this.location = location;
        }
    }

    private static class IncludeOccurrence {
        final String layoutName;
        final String overrideId;
        final Location location;

        IncludeOccurrence(String layoutName, String overrideId, Location location) {
            this.layoutName = layoutName;
            this.overrideId = overrideId;
            this.location = location;
        }
    }

    private static class LayoutRecord {
        final Set<String> ids;
        final Set<String> rootIds;
        final Set<String> duplicateIds;
        final boolean rootIsMerge;

        LayoutRecord(Set<String> ids, Set<String> rootIds, Set<String> duplicateIds,
                boolean rootIsMerge) {
            this.ids = ids;
            this.rootIds = rootIds;
            this.duplicateIds = duplicateIds;
            this.rootIsMerge = rootIsMerge;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mFiles.clear();
        mLayouts.clear();
        mCache.clear();
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            FileData data = getFileData(xmlContext);
            Element root = xmlContext.getDocument().getDocumentElement();
            if (root != null) {
                data.rootTag = root.getTagName();
            }
        }
    }

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_ID, ATTR_LAYOUT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attr) {
        Element element = attr.getOwnerElement();
        String localName = attr.getLocalName();
        String namespace = attr.getNamespaceURI();

        if (ATTR_ID.equals(localName) && ANDROID_URI.equals(namespace)) {
            String id = stripId(attr.getValue());
            if (id != null) {
                FileData data = getFileData(context);
                if (INCLUDE_TAG.equals(element.getTagName())) {
                    // The id on an <include> is an override for the included root;
                    // it is recorded when we process the layout attribute.
                } else {
                    data.ids.add(new IdOccurrence(id, context.getLocation(element)));
                    if (element == element.getOwnerDocument().getDocumentElement()) {
                        data.rootId = id;
                    }
                }
            }
        } else if (ATTR_LAYOUT.equals(localName)
                && INCLUDE_TAG.equals(element.getTagName())
                && (namespace == null || ANDROID_URI.equals(namespace))) {
            String layout = attr.getValue();
            if (layout != null && layout.startsWith(LAYOUT_PREFIX)) {
                String included = layout.substring(LAYOUT_PREFIX.length());
                String override = stripId(element.getAttributeNS(ANDROID_URI, ATTR_ID));
                IncludeOccurrence include = new IncludeOccurrence(included, override,
                        context.getLocation(element));
                FileData data = getFileData(context);
                if (element == element.getOwnerDocument().getDocumentElement()) {
                    data.rootInclude = include;
                } else {
                    data.includes.add(include);
                }
            }
        }
    }

    private FileData getFileData(XmlContext context) {
        String path = context.file.getPath();
        FileData data = mFiles.get(path);
        if (data == null) {
            File file = context.file;
            String name = file.getName();
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                name = name.substring(0, dot);
            }
            data = new FileData(file, name, path);
            mFiles.put(path, data);

            List<FileData> list = mLayouts.get(name);
            if (list == null) {
                list = new ArrayList<>();
                mLayouts.put(name, list);
            }
            list.add(data);
        }
        return data;
    }

    @Nullable
    private static String stripId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        int slash = id.lastIndexOf('/');
        if (slash != -1) {
            id = id.substring(slash + 1);
        }
        return id;
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (FileData data : mFiles.values()) {
            checkFile(context, data);
        }
    }

    private void checkFile(Context context, FileData data) {
        Set<String> seen = new HashSet<>();

        for (IdOccurrence occ : data.ids) {
            if (data.rootIsMerge() && occ.id.equals(data.rootId)) {
                continue;
            }
            seen.add(occ.id);
        }

        if (data.rootInclude != null) {
            LayoutRecord child = computeLayoutRecord(data.rootInclude.layoutName,
                    new HashSet<String>());
            Set<String> childIds = applyOverride(child, data.rootInclude.overrideId,
                    child.rootIsMerge);
            Set<String> childDups = applyOverrideToDups(child, data.rootInclude.overrideId,
                    child.rootIsMerge);
            if (!childDups.isEmpty()) {
                context.report(ISSUE, data.rootInclude.location,
                        buildMessage(data.rootInclude.layoutName, childDups,
                                Collections.<String>emptySet()));
            }
            seen.addAll(childIds);
        }

        for (IncludeOccurrence include : data.includes) {
            LayoutRecord child = computeLayoutRecord(include.layoutName, new HashSet<String>());
            Set<String> childIds = applyOverride(child, include.overrideId, child.rootIsMerge);
            Set<String> childDups = applyOverrideToDups(child, include.overrideId,
                    child.rootIsMerge);
            Set<String> conflicts = new HashSet<>(seen);
            conflicts.retainAll(childIds);
            if (!childDups.isEmpty() || !conflicts.isEmpty()) {
                context.report(ISSUE, include.location,
                        buildMessage(include.layoutName, childDups, conflicts));
            }
            seen.addAll(childIds);
        }
    }

    @NonNull
    private LayoutRecord computeLayoutRecord(@NonNull String layoutName,
            @NonNull Set<String> visiting) {
        LayoutRecord cached = mCache.get(layoutName);
        if (cached != null) {
            return cached;
        }

        if (!visiting.add(layoutName)) {
            return new LayoutRecord(Collections.<String>emptySet(),
                    Collections.<String>emptySet(), Collections.<String>emptySet(), false);
        }

        List<FileData> files = mLayouts.get(layoutName);
        if (files == null) {
            visiting.remove(layoutName);
            return new LayoutRecord(Collections.<String>emptySet(),
                    Collections.<String>emptySet(), Collections.<String>emptySet(), false);
        }

        Set<String> ids = new HashSet<>();
        Set<String> rootIds = new HashSet<>();
        Set<String> duplicateIds = new HashSet<>();
        boolean rootIsMerge = false;

        for (FileData file : files) {
            Set<String> fileIds = new HashSet<>();
            Set<String> fileDups = new HashSet<>();

            if (file.rootIsMerge()) {
                rootIsMerge = true;
            }

            for (IdOccurrence occ : file.ids) {
                if (file.rootIsMerge() && occ.id.equals(file.rootId)) {
                    continue;
                }
                if (!fileIds.add(occ.id)) {
                    fileDups.add(occ.id);
                }
            }

            if (file.rootInclude != null) {
                LayoutRecord child = computeLayoutRecord(file.rootInclude.layoutName, visiting);
                Set<String> childIds = applyOverride(child, file.rootInclude.overrideId,
                        child.rootIsMerge);
                Set<String> childDups = applyOverrideToDups(child, file.rootInclude.overrideId,
                        child.rootIsMerge);
                for (String id : childIds) {
                    if (!fileIds.add(id)) {
                        fileDups.add(id);
                    }
                }
                fileDups.addAll(childDups);
                rootIds.addAll(applyOverrideToRootIds(child, file.rootInclude.overrideId,
                        child.rootIsMerge));
            } else if (!file.rootIsMerge() && file.rootId != null) {
                rootIds.add(file.rootId);
            }

            for (IncludeOccurrence include : file.includes) {
                LayoutRecord child = computeLayoutRecord(include.layoutName, visiting);
                Set<String> childIds = applyOverride(child, include.overrideId,
                        child.rootIsMerge);
                Set<String> childDups = applyOverrideToDups(child, include.overrideId,
                        child.rootIsMerge);
                fileDups.addAll(childDups);
                for (String id : childIds) {
                    if (!fileIds.add(id)) {
                        fileDups.add(id);
                    }
                }
            }

            ids.addAll(fileIds);
            duplicateIds.addAll(fileDups);
        }

        visiting.remove(layoutName);
        LayoutRecord result = new LayoutRecord(ids, rootIds, duplicateIds, rootIsMerge);
        mCache.put(layoutName, result);
        return result;
    }

    @NonNull
    private static Set<String> applyOverride(@NonNull LayoutRecord child,
            @Nullable String overrideId, boolean childIsMerge) {
        Set<String> result = new HashSet<>(child.ids);
        if (overrideId != null && !childIsMerge) {
            result.removeAll(child.rootIds);
            result.add(overrideId);
        }
        return result;
    }

    @NonNull
    private static Set<String> applyOverrideToDups(@NonNull LayoutRecord child,
            @Nullable String overrideId, boolean childIsMerge) {
        Set<String> result = new HashSet<>(child.duplicateIds);
        if (overrideId != null && !childIsMerge) {
            result.removeAll(child.rootIds);
            if (child.ids.contains(overrideId) && !child.rootIds.contains(overrideId)) {
                result.add(overrideId);
            }
        }
        return result;
    }

    @NonNull
    private static Set<String> applyOverrideToRootIds(@NonNull LayoutRecord child,
            @Nullable String overrideId, boolean childIsMerge) {
        Set<String> result = new HashSet<>();
        if (overrideId != null && !childIsMerge) {
            result.add(overrideId);
        } else {
            result.addAll(child.rootIds);
        }
        return result;
    }

    private static String buildMessage(String layoutName, Set<String> duplicateIds,
            Set<String> conflicts) {
        StringBuilder sb = new StringBuilder();
        sb.append("The included layout \"@layout/").append(layoutName).append("\"");

        boolean hasDups = !duplicateIds.isEmpty();
        boolean hasConflicts = !conflicts.isEmpty();

        if (hasDups) {
            sb.append(" has duplicate ids");
            appendIds(sb, duplicateIds);
        }

        if (hasConflicts) {
            if (hasDups) {
                sb.append(" and");
            }
            sb.append(" contains ids already used in this layout");
            appendIds(sb, conflicts);
        }

        return sb.toString();
    }

    private static void appendIds(StringBuilder sb, Collection<String> ids) {
        List<String> sorted = new ArrayList<>(ids);
        Collections.sort(sorted);
        sb.append(" (");
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(sorted.get(i));
        }
        sb.append(")");
    }
}