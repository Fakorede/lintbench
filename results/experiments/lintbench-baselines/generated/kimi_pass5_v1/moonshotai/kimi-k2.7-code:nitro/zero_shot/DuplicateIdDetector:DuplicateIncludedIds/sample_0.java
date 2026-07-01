package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.ID_PREFIX;
import static com.android.SdkConstants.NEW_ID_PREFIX;

import com.android.annotations.NonNull;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DuplicateIdDetector extends ResourceXmlDetector {

    private static final String ISSUE_SUMMARY =
            "Duplicate ids across layouts combined with include tags";

    public static final Issue ISSUE = Issue.create(
            "DuplicateIncludedIds",
            ISSUE_SUMMARY,
            "It's okay for two independent layouts to use the same ids. However, if "
                    + "layouts are combined with include tags, then the ids need to be unique "
                    + "within any chain of included layouts, or Activity#findViewById() can "
                    + "return an unexpected view.",
            Category.CORRECTNESS,
            7,
            Severity.ERROR,
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private final Map<String, Map<String, Location>> mIds = new HashMap<>();
    private final Map<String, List<IncludeReference>> mIncludes = new HashMap<>();

    private static final class IncludeReference {
        final String layout;
        final Location location;

        IncludeReference(String layout, Location location) {
            this.layout = layout;
            this.location = location;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @NonNull
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        String currentLayout = getLayoutName(context.file);

        Attr layoutAttr = null;
        if ("include".equals(tag)) {
            layoutAttr = element.getAttributeNode(ATTR_LAYOUT);
            if (layoutAttr == null) {
                layoutAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT);
            }
            if (layoutAttr != null) {
                String value = layoutAttr.getValue();
                if (value.startsWith("@layout/")) {
                    String included = value.substring("@layout/".length());
                    mIncludes.computeIfAbsent(currentLayout, k -> new ArrayList<>())
                            .add(new IncludeReference(included, context.getLocation(layoutAttr)));
                }
            }
        }

        Attr idAttr = element.getAttributeNode(ATTR_ID);
        if (idAttr == null) {
            idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
        }
        if (idAttr != null) {
            recordId(currentLayout, idAttr, context);
        }
    }

    private void recordId(String layoutName, Attr idAttr, XmlContext context) {
        String id = getIdName(idAttr.getValue());
        if (id == null) {
            return;
        }
        Map<String, Location> map = mIds.computeIfAbsent(layoutName, k -> new HashMap<>());
        map.putIfAbsent(id, context.getLocation(idAttr));
    }

    private String getIdName(String value) {
        if (value.startsWith(NEW_ID_PREFIX)) {
            return value.substring(NEW_ID_PREFIX.length());
        }
        if (value.startsWith(ID_PREFIX)) {
            return value.substring(ID_PREFIX.length());
        }
        int slash = value.lastIndexOf('/');
        if (slash != -1) {
            return value.substring(slash + 1);
        }
        return null;
    }

    private String getLayoutName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name;
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        Set<String> reported = new HashSet<>();

        for (String layout : new ArrayList<>(mIncludes.keySet())) {
            Set<String> visited = new HashSet<>();
            visited.add(layout);

            List<String> path = new ArrayList<>();
            path.add(layout);

            check(context, layout, path, visited, reported);
        }

        mIds.clear();
        mIncludes.clear();
    }

    private void check(@NonNull Context context, @NonNull String root,
            @NonNull List<String> path, @NonNull Set<String> visited,
            @NonNull Set<String> reported) {
        String current = path.get(path.size() - 1);
        List<IncludeReference> refs = mIncludes.get(current);
        if (refs == null) {
            return;
        }

        for (IncludeReference ref : refs) {
            if (visited.contains(ref.layout)) {
                continue;
            }

            Map<String, Location> includedIds = mIds.get(ref.layout);
            if (includedIds != null) {
                for (String ancestor : path) {
                    Map<String, Location> ancestorIds = mIds.get(ancestor);
                    if (ancestorIds == null) {
                        continue;
                    }
                    for (Map.Entry<String, Location> entry : ancestorIds.entrySet()) {
                        String id = entry.getKey();
                        if (includedIds.containsKey(id)) {
                            String key = root + "|" + ancestor + "|" + ref.layout + "|" + id;
                            if (reported.add(key)) {
                                String message = String.format(
                                        "Duplicate id `@id/%1$s` found in `%2$s` and included "
                                                + "layout `%3$s`",
                                        id, ancestor, ref.layout);
                                context.report(ISSUE, entry.getValue(), message);
                            }
                        }
                    }
                }
            }

            visited.add(ref.layout);
            path.add(ref.layout);
            check(context, root, path, visited, reported);
            path.remove(path.size() - 1);
            visited.remove(ref.layout);
        }
    }
}