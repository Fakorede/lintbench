package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderType;
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

    public static final Issue ISSUE = Issue.create(
            "DuplicateIncludedIds",
            "Duplicate ids across included layouts",
            "If layouts are combined with <include> tags, ids must be unique within the "
                    + "entire included hierarchy. Otherwise Activity#findViewById() can return "
                    + "an unexpected view because the search order depends on the view hierarchy.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private final Map<File, Set<String>> mIds = new HashMap<>();
    private final Map<File, List<Include>> mIncludes = new HashMap<>();
    private final Map<String, List<File>> mLayoutFiles = new HashMap<>();
    private final Set<File> mKnownFiles = new HashSet<>();

    private static class Include {
        final String layoutName;
        final Location location;
        List<File> targets = Collections.emptyList();

        Include(String layoutName, Location location) {
            this.layoutName = layoutName;
            this.location = location;
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_INCLUDE);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ID);
    }

    @Override
    public void beforeCheckProject(Context context) {
        mIds.clear();
        mIncludes.clear();
        mLayoutFiles.clear();
        mKnownFiles.clear();
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        rememberFile(context.file);

        String value = attribute.getValue();
        if (value == null) {
            return;
        }

        int slash = value.lastIndexOf('/');
        if (slash == -1) {
            return;
        }

        String id = value.substring(slash + 1);
        int question = id.indexOf('?');
        if (question != -1) {
            id = id.substring(0, question);
        }

        if (!id.isEmpty()) {
            mIds.computeIfAbsent(context.file, k -> new HashSet<>()).add(id);
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!SdkConstants.TAG_INCLUDE.equals(element.getTagName())) {
            return;
        }

        rememberFile(context.file);

        String layout = element.getAttribute(SdkConstants.ATTR_LAYOUT);
        if (layout == null || layout.isEmpty()) {
            layout = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT);
        }

        if (layout == null || !layout.startsWith(SdkConstants.LAYOUT_RESOURCE_PREFIX)) {
            return;
        }

        int slash = layout.lastIndexOf('/');
        if (slash == -1) {
            return;
        }

        String name = layout.substring(slash + 1);
        int question = name.indexOf('?');
        if (question != -1) {
            name = name.substring(0, question);
        }

        if (!name.isEmpty()) {
            Location location = context.getLocation(element);
            mIncludes.computeIfAbsent(context.file, k -> new ArrayList<>())
                    .add(new Include(name, location));
        }
    }

    @Override
    public void afterCheckProject(Context context) {
        for (File file : mIds.keySet()) {
            rememberFile(file);
        }
        for (File file : mIncludes.keySet()) {
            rememberFile(file);
        }

        for (List<Include> includes : mIncludes.values()) {
            for (Include include : includes) {
                List<File> targets = mLayoutFiles.get(include.layoutName);
                if (targets != null) {
                    include.targets = new ArrayList<>(targets);
                }
            }
        }

        Set<String> reported = new HashSet<>();

        for (File root : mIds.keySet()) {
            Set<String> seen = new HashSet<>(mIds.getOrDefault(root, Collections.emptySet()));
            Set<File> visited = new HashSet<>();
            List<Include> includes = mIncludes.getOrDefault(root, Collections.emptyList());
            for (Include include : includes) {
                checkInclude(include, seen, visited, reported, context);
            }
        }
    }

    private void checkInclude(Include include, Set<String> seen, Set<File> visited,
            Set<String> reported, Context context) {
        for (File target : include.targets) {
            if (!visited.add(target)) {
                continue;
            }

            Set<String> targetIds = mIds.getOrDefault(target, Collections.emptySet());
            for (String id : targetIds) {
                if (seen.contains(id)) {
                    String key = id + "\u0001" + include.location.getFile().getPath()
                            + "\u0001" + include.location.getStart().getOffset();
                    if (reported.add(key)) {
                        String message = String.format(
                                "Duplicate id `%1$s`: this id is already used in the "
                                        + "current layout or in a previously included layout. "
                                        + "Because this layout is included with `<include>`, "
                                        + "Activity#findViewById() may return the wrong view.",
                                id);
                        context.report(ISSUE, include.location, message);
                    }
                }
            }
            seen.addAll(targetIds);

            List<Include> childIncludes = mIncludes.getOrDefault(target, Collections.emptyList());
            for (Include child : childIncludes) {
                checkInclude(child, seen, visited, reported, context);
            }
        }
    }

    private void rememberFile(File file) {
        if (!mKnownFiles.add(file)) {
            return;
        }

        String name = file.getName();
        if (!name.endsWith(SdkConstants.DOT_XML)) {
            return;
        }

        String layoutName = name.substring(0, name.length() - SdkConstants.DOT_XML.length());
        mLayoutFiles.computeIfAbsent(layoutName, k -> new ArrayList<>()).add(file);
    }
}