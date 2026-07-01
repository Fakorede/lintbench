package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
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

public class DuplicateIdDetector extends Detector implements XmlScanner {

    private static final String LAYOUT_PREFIX = "@layout/";

    private final Map<String, Set<String>> mLayoutIds = new HashMap<>();
    private final Map<String, Set<String>> mLayoutIncludes = new HashMap<>();
    private final Map<String, File> mLayoutFiles = new HashMap<>();

    public static final Issue ISSUE = Issue.create(
            "DuplicateIncludedIds",
            "Duplicate ids across included layouts",
            "It's okay for two independent layouts to use the same ids. However, if "
                    + "layouts are combined with include tags, then the ids need to be unique "
                    + "within any chain of included layouts, or Activity#findViewById() can "
                    + "return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(DuplicateIdDetector.class, Scope.ALL_RESOURCE_FILES)
    );

    @Override
    public void beforeCheckEachProject(@NotNull Context context) {
        mLayoutIds.clear();
        mLayoutIncludes.clear();
        mLayoutFiles.clear();
    }

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    @NotNull
    public Collection<String> getApplicableAttributes() {
        return List.of(SdkConstants.ATTR_ID, SdkConstants.ATTR_LAYOUT);
    }

    @Override
    public void visitAttribute(@NotNull XmlContext context, @NotNull Attr attribute) {
        String layoutName = getLayoutName(context.file);
        mLayoutFiles.putIfAbsent(layoutName, context.file);

        String name = attribute.getLocalName();
        if (SdkConstants.ATTR_ID.equals(name)) {
            String id = stripIdPrefix(attribute.getValue());
            if (id != null && !id.isEmpty()) {
                mLayoutIds.computeIfAbsent(layoutName, k -> new HashSet<>()).add(id);
            }
        } else if (SdkConstants.ATTR_LAYOUT.equals(name)) {
            Element element = attribute.getOwnerElement();
            if (SdkConstants.VIEW_INCLUDE.equals(element.getTagName())) {
                String value = attribute.getValue();
                if (value != null && value.startsWith(LAYOUT_PREFIX)) {
                    String included = value.substring(LAYOUT_PREFIX.length());
                    if (!included.isEmpty()) {
                        mLayoutIncludes.computeIfAbsent(layoutName, k -> new HashSet<>()).add(included);
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NotNull Context context) {
        if (mLayoutIds.isEmpty() && mLayoutIncludes.isEmpty()) {
            return;
        }

        Set<String> includedLayouts = new HashSet<>();
        for (Set<String> includes : mLayoutIncludes.values()) {
            includedLayouts.addAll(includes);
        }

        Set<String> roots = new HashSet<>(mLayoutIds.keySet());
        roots.addAll(mLayoutIncludes.keySet());
        roots.removeAll(includedLayouts);
        if (roots.isEmpty()) {
            roots.addAll(mLayoutIds.keySet());
            roots.addAll(mLayoutIncludes.keySet());
        }

        for (String root : roots) {
            Set<String> closure = new HashSet<>();
            collectClosure(root, closure);

            Map<String, Set<String>> idToLayouts = new HashMap<>();
            for (String layout : closure) {
                Set<String> ids = mLayoutIds.get(layout);
                if (ids == null) {
                    continue;
                }
                for (String id : ids) {
                    idToLayouts.computeIfAbsent(id, k -> new HashSet<>()).add(layout);
                }
            }

            List<String> duplicates = new ArrayList<>();
            for (Map.Entry<String, Set<String>> entry : idToLayouts.entrySet()) {
                if (entry.getValue().size() > 1) {
                    duplicates.add(entry.getKey());
                }
            }
            if (duplicates.isEmpty()) {
                continue;
            }
            Collections.sort(duplicates);

            File file = mLayoutFiles.get(root);
            if (file == null) {
                continue;
            }

            StringBuilder message = new StringBuilder();
            message.append("The following id")
                    .append(duplicates.size() == 1 ? " is" : "s are")
                    .append(" duplicated across layouts included by '")
                    .append(root)
                    .append("': ");
            for (int i = 0; i < duplicates.size(); i++) {
                if (i > 0) {
                    message.append("; ");
                }
                String id = duplicates.get(i);
                List<String> layouts = new ArrayList<>(idToLayouts.get(id));
                Collections.sort(layouts);
                message.append("'").append(id).append("' in ")
                        .append(String.join(", ", layouts));
            }
            message.append(". Activity#findViewById() may return an unexpected view.");

            context.report(ISSUE, Location.create(file), message.toString());
        }
    }

    private void collectClosure(@NotNull String layout, @NotNull Set<String> closure) {
        if (!closure.add(layout)) {
            return;
        }

        Set<String> includes = mLayoutIncludes.get(layout);
        if (includes == null) {
            return;
        }

        for (String included : includes) {
            if (mLayoutIds.containsKey(included) || mLayoutIncludes.containsKey(included)) {
                collectClosure(included, closure);
            }
        }
    }

    @NotNull
    private static String getLayoutName(@NotNull File file) {
        String name = file.getName();
        if (name.endsWith(SdkConstants.DOT_XML)) {
            return name.substring(0, name.length() - SdkConstants.DOT_XML.length());
        }
        return name;
    }

    @NotNull
    private static String stripIdPrefix(@NotNull String value) {
        int index = value.lastIndexOf('/');
        if (index != -1 && index + 1 < value.length()) {
            return value.substring(index + 1);
        }
        return value;
    }
}