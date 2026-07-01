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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LayoutConsistencyDetector extends Detector implements XmlScanner {

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
        new Implementation(LayoutConsistencyDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private final Map<String, Map<File, Set<String>>> layoutNameToFileIds = new HashMap<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (id != null && !id.isEmpty()) {
            String idName = stripIdPrefix(id);
            if (idName != null) {
                File file = context.file;
                String baseName = getBaseName(file.getName());
                layoutNameToFileIds
                    .computeIfAbsent(baseName, k -> new HashMap<>())
                    .computeIfAbsent(file, k -> new HashSet<>())
                    .add(idName);
            }
        }
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (Map.Entry<String, Map<File, Set<String>>> entry : layoutNameToFileIds.entrySet()) {
            Map<File, Set<String>> fileToIds = entry.getValue();
            if (fileToIds.size() < 2) continue;

            Set<String> allIds = new HashSet<>();
            for (Set<String> ids : fileToIds.values()) {
                allIds.addAll(ids);
            }

            for (Map.Entry<File, Set<String>> fileEntry : fileToIds.entrySet()) {
                File f = fileEntry.getKey();
                Set<String> fileIds = fileEntry.getValue();
                Set<String> missing = new HashSet<>(allIds);
                missing.removeAll(fileIds);
                if (!missing.isEmpty()) {
                    String missingList = String.join(", ", missing);
                    String qualifier = getQualifier(f);
                    String message = String.format(
                        "The layout `%s` in configuration `%s` is missing the following IDs found in other configurations: %s",
                        entry.getKey(), qualifier, missingList);
                    context.report(ISSUE, Location.create(f), message);
                }
            }
        }
        layoutNameToFileIds.clear();
    }

    private static String getBaseName(String fileName) {
        int dot = fileName.indexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String stripIdPrefix(String id) {
        if (id.startsWith("@+id/")) return id.substring(5);
        if (id.startsWith("@id/")) return id.substring(4);
        return null;
    }

    private static String getQualifier(File file) {
        File parent = file.getParentFile();
        if (parent == null) return "default";
        String folderName = parent.getName();
        int dash = folderName.indexOf('-');
        return dash > 0 ? folderName.substring(dash + 1) : "default";
    }
}