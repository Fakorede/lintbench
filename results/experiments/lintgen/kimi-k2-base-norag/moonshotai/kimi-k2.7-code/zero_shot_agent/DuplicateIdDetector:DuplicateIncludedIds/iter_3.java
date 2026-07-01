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
import java.util.Arrays;
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
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private final Map<String, FileData> mFiles = new HashMap<>();
    private final Map<String, List<FileData>> mLayouts = new HashMap<>();
    private final Map<String, LayoutInfo> mCache = new HashMap<>();

    private static class FileData {
        final File file;
        final String layoutName;
        final List<IdOccurrence> ids = new ArrayList<>();
        final List<IncludeOccurrence> includes = new ArrayList<>();
        final Set<String> rootIds = new HashSet<>();
        IncludeOccurrence rootInclude;

        FileData(File file, String layoutName) {
            this.file = file;
            this.layoutName = layoutName;
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

    private static class LayoutInfo {
        final Set<String> ids;
        final Set<String> rootIds;
        final Set<String> duplicateIds;

        LayoutInfo(Set<String> ids, Set<String> rootIds, Set<String> duplicateIds) {
            this.ids = ids;
            this.rootIds = rootIds;
            this.duplicateIds = duplicateIds;
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

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_ID, ATTR_LAYOUT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attr) {
        Element element = attr.getOwnerElement();
        String localName = attr.getLocalName();

        if (ATTR_ID.equals(localName) && ANDROID_URI.equals(attr.getNamespaceURI())) {
            if (!INCLUDE_TAG.equals(element.getTagName())) {
                String id = stripId(attr.getValue());
                if (id != null) {
                    FileData data = getFileData(context);
                    data.ids.add(new IdOccurrence(id, context.getLocation(element)));
                    if (element == element.getOwnerDocument().getDocumentElement()) {
                        data.rootIds.add(id);
                    }
                }
            }
        } else if (ATTR_LAYOUT.equals(localName) && INCLUDE_TAG.equals(element.getTagName())) {
            String layout = attr.getValue();
            if (layout.startsWith(LAYOUT_PREFIX)) {
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
            data = new FileData(file, name);
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
            seen.add(occ.id);
        }

        if (data.rootInclude != null) {
            LayoutInfo child = computeLayoutInfo(data.rootInclude.layoutName, new HashSet<String>());
            seen.addAll(applyOverride(child, data.rootInclude.overrideId));
        }

        for (IncludeOccurrence include : data.includes) {
            LayoutInfo child = computeLayoutInfo(include.layoutName, new HashSet<String>());
            Set<String> effectiveIds = applyOverride(child, include.overrideId);
            Set<String> effectiveDups = applyOverrideToDups(child, include.overrideId);

            Set<String> conflicts = new HashSet<>(seen);
            conflicts.retainAll(effectiveIds);

            if (!conflicts.isEmpty() || !effectiveDups.isEmpty()) {
                String message = buildMessage(include.layoutName, effectiveDups, conflicts);
                context.report(ISSUE, include.location, message);
            }

            seen.addAll(effectiveIds);
        }
    }

    @NonNull
    private LayoutInfo computeLayoutInfo(@NonNull String layoutName,
            @NonNull Set<String> visiting) {
        LayoutInfo cached = mCache.get(layoutName);
        if (cached != null) {
            return cached;
        }

        if (!visiting.add(layoutName)) {
            return new LayoutInfo(Collections.<String>emptySet(),
                    Collections.<String>emptySet(), Collections.<String>emptySet());
        }

        List<FileData> files = mLayouts.get(layoutName);
        if (files == null) {
            visiting.remove(layoutName);
            return new LayoutInfo(Collections.<String>emptySet(),
                    Collections.<String>emptySet(), Collections.<String>emptySet());
        }

        Set<String> ids = new HashSet<>();
        Set<String> rootIds = new HashSet<>();
        Set<String> duplicateIds = new HashSet<>();

        for (FileData file : files) {
            Set<String> fileIds = new HashSet<>();
            Set<String> fileDups = new HashSet<>();

            if (file.rootInclude != null) {
                LayoutInfo child = computeLayoutInfo(file.rootInclude.layoutName, visiting);
                Set<String> childIds = applyOverride(child, file.rootInclude.overrideId);
                Set<String> childDups = applyOverrideToDups(child, file.rootInclude.overrideId);
                for (String id : childIds) {
                    if (!fileIds.add(id)) {
                        fileDups.add(id);
                    }
                }
                fileDups.addAll(childDups);
                rootIds.addAll(applyOverrideToRootIds(child, file.rootInclude.overrideId));
            } else {
                for (IdOccurrence occ : file.ids) {
                    if (!fileIds.add(occ.id)) {
                        fileDups.add(occ.id);
                    }
                }
                rootIds.addAll(file.rootIds);
            }

            for (IncludeOccurrence include : file.includes) {
                LayoutInfo child = computeLayoutInfo(include.layoutName, visiting);
                Set<String> childIds = applyOverride(child, include.overrideId);
                Set<String> childDups = applyOverrideToDups(child, include.overrideId);
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
        LayoutInfo result = new LayoutInfo(ids, rootIds, duplicateIds);
        mCache.put(layoutName, result);
        return result;
    }

    @NonNull
    private static Set<String> applyOverride(@NonNull LayoutInfo info, @Nullable String overrideId) {
        Set<String> result = new HashSet<>(info.ids);
        if (overrideId != null) {
            result.removeAll(info.rootIds);
            result.add(overrideId);
        }
        return result;
    }

    @NonNull
    private static Set<String> applyOverrideToDups(@NonNull LayoutInfo info,
            @Nullable String overrideId) {
        Set<String> result = new HashSet<>(info.duplicateIds);
        if (overrideId != null) {
            result.removeAll(info.rootIds);
            if (info.ids.contains(overrideId) && !info.rootIds.contains(overrideId)) {
                result.add(overrideId);
            }
        }
        return result;
    }

    @NonNull
    private static Set<String> applyOverrideToRootIds(@NonNull LayoutInfo info,
            @Nullable String overrideId) {
        Set<String> result = new HashSet<>();
        if (overrideId != null) {
            result.add(overrideId);
        } else {
            result.addAll(info.rootIds);
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