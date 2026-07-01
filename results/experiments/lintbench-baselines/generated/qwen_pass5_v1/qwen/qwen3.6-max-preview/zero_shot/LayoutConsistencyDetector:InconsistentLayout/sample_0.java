package com.android.tools.lint.checks;

import com.android.SdkConstants;
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

public class LayoutConsistencyDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "InconsistentLayout",
            "Inconsistent Layouts",
            "This check ensures that a layout resource which is defined in multiple " +
            "resource folders, specifies the same set of widgets.\n\n" +
            "This finds cases where you have accidentally forgotten to add " +
            "a widget to all variations of the layout, which could result " +
            "in a runtime crash for some resource configurations when a " +
            "`findViewById()` fails.\n\n" +
            "There **are** cases where this is intentional. For example, you " +
            "may have a dedicated large tablet layout which adds some extra " +
            "widgets that are not present in the phone version of the layout. " +
            "As long as the code accessing the layout resource is careful to " +
            "handle this properly, it is valid. In that case, you can suppress " +
            "this lint check for the given extra or missing views, or the whole layout",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(LayoutConsistencyDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private final Map<String, Map<File, Set<String>>> layouts = new HashMap<>();

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.LAYOUT) {
            return;
        }

        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (id == null || id.isEmpty()) {
            return;
        }

        String rawId = id;
        if (rawId.startsWith("@+id/")) {
            rawId = rawId.substring(5);
        } else if (rawId.startsWith("@id/")) {
            rawId = rawId.substring(4);
        } else {
            return;
        }

        String layoutName = context.file.getName();
        layouts.computeIfAbsent(layoutName, k -> new HashMap<>())
               .computeIfAbsent(context.file, k -> new HashSet<>())
               .add(rawId);
    }

    @Override
    public void afterCheckProject(@NotNull Context context) {
        for (Map.Entry<String, Map<File, Set<String>>> entry : layouts.entrySet()) {
            Map<File, Set<String>> files = entry.getValue();
            if (files.size() <= 1) {
                continue;
            }

            Set<String> allIds = new HashSet<>();
            for (Set<String> ids : files.values()) {
                allIds.addAll(ids);
            }

            for (Map.Entry<File, Set<String>> fileEntry : files.entrySet()) {
                File file = fileEntry.getKey();
                Set<String> fileIds = fileEntry.getValue();
                Set<String> missing = new HashSet<>(allIds);
                missing.removeAll(fileIds);

                if (!missing.isEmpty()) {
                    List<String> sortedMissing = new ArrayList<>(missing);
                    Collections.sort(sortedMissing);
                    String dir = file.getParentFile() != null ? file.getParentFile().getName() : "";
                    String message = String.format(
                            "The layout `%s` in `%s` is missing the following ids: `%s`",
                            entry.getKey(), dir, String.join("`, `", sortedMissing)
                    );
                    context.report(ISSUE, Location.create(file), message);
                }
            }
        }
        layouts.clear();
    }
}