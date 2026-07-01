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
import org.w3c.dom.Element;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LayoutConsistencyDetector extends Detector implements XmlScanner {
    private final Map<String, Map<File, Set<String>>> layoutIds = new HashMap<>();

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
        "this lint check for the given extra or missing views, or the whole " +
        "layout",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(LayoutConsistencyDetector.class, EnumSet.of(Scope.RESOURCE_FILE_SCOPE))
    );

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (id == null || id.isEmpty()) {
            return;
        }

        String idName = id;
        if (id.startsWith("@+id/")) {
            idName = id.substring(5);
        } else if (id.startsWith("@id/")) {
            idName = id.substring(4);
        }

        String layoutName = context.file.getName().replace(".xml", "");
        layoutIds.computeIfAbsent(layoutName, k -> new HashMap<>())
                 .computeIfAbsent(context.file, k -> new HashSet<>())
                 .add(idName);
    }

    @Override
    public void afterCheckEachProject(Context context) {
        for (Map.Entry<String, Map<File, Set<String>>> entry : layoutIds.entrySet()) {
            String layoutName = entry.getKey();
            Map<File, Set<String>> fileToIds = entry.getValue();

            if (fileToIds.size() < 2) {
                continue;
            }

            Set<String> allIds = new HashSet<>();
            for (Set<String> ids : fileToIds.values()) {
                allIds.addAll(ids);
            }

            for (Map.Entry<File, Set<String>> fileEntry : fileToIds.entrySet()) {
                File file = fileEntry.getKey();
                Set<String> idsInFile = fileEntry.getValue();
                Set<String> missing = new HashSet<>(allIds);
                missing.removeAll(idsInFile);

                if (!missing.isEmpty()) {
                    List<String> sortedMissing = new ArrayList<>(missing);
                    Collections.sort(sortedMissing);
                    String missingStr = String.join(", ", sortedMissing);
                    String message = String.format(
                        "The layout `%s` is missing the following IDs present in other configurations: `%s`",
                        layoutName, missingStr);
                    context.report(ISSUE, Location.create(file), message);
                }
            }
        }
        layoutIds.clear();
    }
}