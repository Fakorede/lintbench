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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

    private Map<File, Set<String>> fileToIds = new HashMap<>();
    private Map<String, List<File>> layoutNameToFiles = new HashMap<>();
    private File currentFile;

    @Override
    public void beforeCheckFile(Context context) {
        File file = context.file;
        if (file == null) return;
        File parent = file.getParentFile();
        if (parent == null) return;

        ResourceFolderType type = ResourceFolderType.getFolderType(parent.getName());
        if (type == ResourceFolderType.LAYOUT) {
            currentFile = file;
            fileToIds.put(currentFile, new HashSet<>());
            String baseName = getBaseName(file.getName());
            layoutNameToFiles.computeIfAbsent(baseName, k -> new ArrayList<>()).add(currentFile);
        } else {
            currentFile = null;
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (currentFile == null) return;
        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (id != null && !id.isEmpty()) {
            String idName = stripIdPrefix(id);
            if (idName != null) {
                Set<String> ids = fileToIds.get(currentFile);
                if (ids != null) {
                    ids.add(idName);
                }
            }
        }
    }

    @Override
    public void afterCheckEachProject(Context context) {
        for (Map.Entry<String, List<File>> entry : layoutNameToFiles.entrySet()) {
            List<File> files = entry.getValue();
            if (files.size() < 2) continue;

            Set<String> allIds = new HashSet<>();
            for (File f : files) {
                Set<String> ids = fileToIds.get(f);
                if (ids != null) {
                    allIds.addAll(ids);
                }
            }

            for (File f : files) {
                Set<String> fileIds = fileToIds.getOrDefault(f, Collections.emptySet());
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
        fileToIds.clear();
        layoutNameToFiles.clear();
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