package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;

import com.android.annotations.NonNull;
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
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class LayoutConsistencyDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InconsistentLayout",
            "Inconsistent layout",
            "This layout is defined in multiple resource folders but does not define the same "
                    + "set of widgets in each folder. Code that calls findViewById(...) for a "
                    + "widget that is missing in one configuration may crash at runtime.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(LayoutConsistencyDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private final Map<String, Map<String, List<LayoutVersion>>> mProjectLayouts = new HashMap<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mProjectLayouts.clear();
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        File file = context.file;
        String layoutName = getBaseName(file.getName());
        String folderName = file.getParentFile().getName();
        String projectPath = context.getProject().getDir().getPath();

        Set<String> widgets = new HashSet<>();
        collectWidgets(root, widgets);

        mProjectLayouts
                .computeIfAbsent(projectPath, k -> new HashMap<>())
                .computeIfAbsent(layoutName, k -> new ArrayList<>())
                .add(new LayoutVersion(folderName, file, widgets));
    }

    private void collectWidgets(@NonNull Element element, @NonNull Set<String> widgets) {
        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        if (id != null && !id.isEmpty()) {
            String idName = stripIdPrefix(id);
            widgets.add(element.getTagName() + " [" + idName + "]");
        }

        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectWidgets((Element) child, widgets);
            }
            child = child.getNextSibling();
        }
    }

    @NonNull
    private String stripIdPrefix(@NonNull String id) {
        if (id.startsWith("@+id/")) {
            return id.substring("@+id/".length());
        }
        if (id.startsWith("@id/")) {
            return id.substring("@id/".length());
        }
        return id;
    }

    @NonNull
    private String getBaseName(@NonNull String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot == -1 ? fileName : fileName.substring(0, dot);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map<String, List<LayoutVersion>> layouts : mProjectLayouts.values()) {
            for (Map.Entry<String, List<LayoutVersion>> layoutEntry : layouts.entrySet()) {
                String layoutName = layoutEntry.getKey();
                List<LayoutVersion> versions = layoutEntry.getValue();
                if (versions.size() < 2) {
                    continue;
                }

                Set<String> union = new HashSet<>();
                for (LayoutVersion version : versions) {
                    union.addAll(version.widgets);
                }
                if (union.isEmpty()) {
                    continue;
                }

                for (LayoutVersion version : versions) {
                    Set<String> missing = new HashSet<>(union);
                    missing.removeAll(version.widgets);
                    if (!missing.isEmpty()) {
                        Location location = Location.create(version.file);
                        String message = String.format(
                                "The layout '%1$s' has a different set of views in `%2$s`; "
                                        + "the following views are present in other configurations but not here: %3$s",
                                layoutName, version.folderName, sorted(missing));
                        context.report(ISSUE, location, message);
                    }
                }
            }
        }
    }

    @NonNull
    private String sorted(@NonNull Set<String> set) {
        List<String> list = new ArrayList<>(set);
        Collections.sort(list);
        return list.toString();
    }

    private static final class LayoutVersion {
        final String folderName;
        final File file;
        final Set<String> widgets;

        LayoutVersion(String folderName, File file, Set<String> widgets) {
            this.folderName = folderName;
            this.file = file;
            this.widgets = widgets;
        }
    }
}