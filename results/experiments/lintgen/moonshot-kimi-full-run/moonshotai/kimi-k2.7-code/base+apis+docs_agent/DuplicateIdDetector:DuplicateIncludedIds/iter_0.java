package com.android.tools.lint.checks;

import static com.android.tools.lint.detector.api.XmlScannerConstants.ALL;

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

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DuplicateIdDetector extends Detector implements Detector.XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private final Map<String, LayoutInfo> mLayoutInfos = new HashMap<>();

    private static class LayoutInfo {
        final String name;
        Location rootLocation;
        final List<IdOccurrence> ids = new ArrayList<>();
        final List<IncludeInfo> includes = new ArrayList<>();

        LayoutInfo(String name) {
            this.name = name;
        }
    }

    private static class IdOccurrence {
        final String id;
        final File file;
        final Location location;

        IdOccurrence(String id, File file, Location location) {
            this.id = id;
            this.file = file;
            this.location = location;
        }
    }

    private static class IncludeInfo {
        final String layout;
        final Location location;

        IncludeInfo(String layout, Location location) {
            this.layout = layout;
            this.location = location;
        }
    }

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public @NotNull Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String layoutName = getLayoutName(context.file);
        LayoutInfo info = mLayoutInfos.computeIfAbsent(layoutName, LayoutInfo::new);

        Node parent = element.getParentNode();
        if (parent != null && parent.getNodeType() == Node.DOCUMENT_NODE) {
            info.rootLocation = context.getLocation(element);
        }

        String tag = element.getTagName();
        if ("include".equals(tag)) {
            String layoutAttr = element.getAttribute("layout");
            if (layoutAttr != null && layoutAttr.startsWith("@layout/")) {
                String target = layoutAttr.substring("@layout/".length());
                info.includes.add(new IncludeInfo(target, context.getLocation(element)));
            }
        }

        String id = element.getAttributeNS(ANDROID_URI, "id");
        if (id != null && !id.isEmpty()) {
            recordId(info, id, context, element);
        }
    }

    @Override
    public void beforeCheckEachProject(@NotNull Context context) {
        mLayoutInfos.clear();
    }

    @Override
    public void afterCheckEachProject(@NotNull Context context) {
        Set<String> includedLayouts = new HashSet<>();
        for (LayoutInfo info : mLayoutInfos.values()) {
            for (IncludeInfo include : info.includes) {
                includedLayouts.add(include.layout);
            }
        }

        for (LayoutInfo info : mLayoutInfos.values()) {
            if (includedLayouts.contains(info.name) || info.rootLocation == null) {
                continue;
            }

            List<LayoutInfo> closure = new ArrayList<>();
            collectClosure(info, new HashSet<>(), closure);

            Map<String, List<IdOccurrence>> idMap = new HashMap<>();
            for (LayoutInfo layout : closure) {
                for (IdOccurrence occurrence : layout.ids) {
                    idMap.computeIfAbsent(occurrence.id, k -> new ArrayList<>()).add(occurrence);
                }
            }

            for (Map.Entry<String, List<IdOccurrence>> entry : idMap.entrySet()) {
                List<IdOccurrence> occurrences = entry.getValue();
                if (occurrences.size() < 2) {
                    continue;
                }

                Set<File> files = new HashSet<>();
                for (IdOccurrence occurrence : occurrences) {
                    files.add(occurrence.file);
                }
                if (files.size() < 2) {
                    continue;
                }

                String layouts = occurrences.stream()
                        .map(occurrence -> getLayoutName(occurrence.file))
                        .distinct()
                        .sorted()
                        .collect(Collectors.joining(", "));

                String message = String.format(
                        "Duplicate id @+id/%1$s across included layouts (%2$s); "
                                + "Activity#findViewById() may return an unexpected view",
                        entry.getKey(), layouts);

                context.report(ISSUE, info.rootLocation, message);
            }
        }
    }

    private static void recordId(
            LayoutInfo info, String idValue, XmlContext context, Element element) {
        int slash = idValue.indexOf('/');
        if (slash == -1 || slash == idValue.length() - 1) {
            return;
        }
        String idName = idValue.substring(slash + 1);
        if (idName.isEmpty()) {
            return;
        }
        info.ids.add(new IdOccurrence(idName, context.file, context.getLocation(element)));
    }

    private void collectClosure(LayoutInfo info, Set<String> visited, List<LayoutInfo> out) {
        if (!visited.add(info.name)) {
            return;
        }
        out.add(info);
        for (IncludeInfo include : info.includes) {
            LayoutInfo child = mLayoutInfos.get(include.layout);
            if (child != null) {
                collectClosure(child, visited, out);
            }
        }
    }

    private static String getLayoutName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name;
    }

    public static final Issue ISSUE = Issue.create(
            "DuplicateIncludedIds",
            "Duplicate ids across layouts combined with include tags",
            "It's okay for two independent layouts to use the same ids. However, if "
                    + "layouts are combined with include tags, then the id's need to be unique "
                    + "within any chain of included layouts, or Activity#findViewById() can "
                    + "return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(DuplicateIdDetector.class, Scope.ALL_RESOURCE_FILES)
    );
}