package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.TAG_INCLUDE;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class DuplicateIdDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIncludedIds",
            "Duplicate ids across layouts combined with include tags",
            "It's okay for two independent layouts to use the same ids. However, if "
                    + "layouts are combined with include tags, then the id's need to be unique "
                    + "within any chain of included layouts, or `Activity#findViewById()` can "
                    + "return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(DuplicateIdDetector.class, Scope.ALL_RESOURCE_FILES));

    private final Map<Project, ProjectData> mProjectData = new HashMap<>();

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_INCLUDE);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ID);
    }

    @Override
    public void visitAttribute(@NotNull XmlContext context, @NotNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || !value.startsWith("@+id/")) {
            return;
        }

        String id = value.substring("@+id/".length());
        if (id.isEmpty()) {
            return;
        }

        ProjectData data = getData(context.getProject());
        String layout = getLayoutName(context.file);
        data.layoutNames.add(layout);
        data.ids.computeIfAbsent(layout, k -> new ArrayList<>())
                .add(new IdInfo(id, context.getValueLocation(attribute)));
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        if (!TAG_INCLUDE.equals(element.getTagName())) {
            return;
        }

        String layoutAttr = element.getAttribute(ATTR_LAYOUT);
        if (layoutAttr == null || !layoutAttr.startsWith("@layout/")) {
            return;
        }

        String target = layoutAttr.substring("@layout/".length());
        if (target.isEmpty()) {
            return;
        }

        ProjectData data = getData(context.getProject());
        String layout = getLayoutName(context.file);
        data.layoutNames.add(layout);
        data.includes.computeIfAbsent(layout, k -> new ArrayList<>())
                .add(new IncludeInfo(target, context.getLocation(element)));
    }

    @Override
    public void beforeCheckRootProject(@NotNull Context context) {
        mProjectData.clear();
    }

    @Override
    public void afterCheckEachProject(@NotNull Context context) {
        ProjectData data = mProjectData.get(context.getProject());
        if (data == null) {
            return;
        }

        for (String layout : data.layoutNames) {
            Map<String, List<Location>> ids = new HashMap<>();
            collectIds(data, layout, new HashSet<>(), ids);

            for (Map.Entry<String, List<Location>> entry : ids.entrySet()) {
                List<Location> locations = entry.getValue();
                if (locations.size() > 1) {
                    String id = entry.getKey();
                    Location primary = locations.get(0);
                    for (int i = 1; i < locations.size(); i++) {
                        Location location = locations.get(i);
                        String message = "Duplicate id @+id/" + id
                                + " already defined in " + primary.getFile().getName();
                        context.report(ISSUE, location, message);
                    }
                }
            }
        }
    }

    private static void collectIds(ProjectData data, String layout, Set<String> visiting,
            Map<String, List<Location>> ids) {
        List<IdInfo> idInfos = data.ids.get(layout);
        if (idInfos != null) {
            for (IdInfo info : idInfos) {
                ids.computeIfAbsent(info.name, k -> new ArrayList<>()).add(info.location);
            }
        }

        List<IncludeInfo> includes = data.includes.get(layout);
        if (includes != null) {
            for (IncludeInfo include : includes) {
                if (visiting.add(include.target)) {
                    collectIds(data, include.target, visiting, ids);
                    visiting.remove(include.target);
                }
            }
        }
    }

    private ProjectData getData(Project project) {
        return mProjectData.computeIfAbsent(project, k -> new ProjectData());
    }

    private static String getLayoutName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name;
    }

    private static class ProjectData {
        final Map<String, List<IdInfo>> ids = new HashMap<>();
        final Map<String, List<IncludeInfo>> includes = new HashMap<>();
        final Set<String> layoutNames = new HashSet<>();
    }

    private static class IdInfo {
        final String name;
        final Location location;

        IdInfo(String name, Location location) {
            this.name = name;
            this.location = location;
        }
    }

    private static class IncludeInfo {
        final String target;
        final Location location;

        IncludeInfo(String target, Location location) {
            this.target = target;
            this.location = location;
        }
    }
}