package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

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

public class DuplicateIdDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIncludedIds",
                    "Duplicate IDs Across Included Layouts",
                    "It's okay for two independent layouts to use the same ids. However, if "
                            + "layouts are combined with include tags, then the ids need to be "
                            + "unique within any chain of included layouts, or "
                            + "`Activity#findViewById()` can return an unexpected view.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(DuplicateIdDetector.class, Scope.ALL_RESOURCE_FILES_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private Map<String, List<FileInfo>> mLayoutMap;
    private FileInfo mCurrentFile;

    @Override
    public boolean appliesTo(Context context, File file) {
        String path = file.getPath().replace('\\', '/');
        return file.isFile()
                && file.getName().endsWith(".xml")
                && path.contains("/res/layout");
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("id");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("include");
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        mLayoutMap = new HashMap<>();
        mCurrentFile = null;
    }

    @Override
    public void beforeCheckFile(Context context) {
        mCurrentFile = new FileInfo(getLayoutName(context.getFile()));
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (mCurrentFile == null) {
            return;
        }
        String value = attribute.getValue();
        String id = extractId(value);
        if (id != null) {
            mCurrentFile.ids
                    .computeIfAbsent(id, k -> new ArrayList<>())
                    .add(context.getLocation(attribute));
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (mCurrentFile == null) {
            return;
        }
        if ("include".equals(element.getLocalName())) {
            Attr layoutAttr = element.getAttributeNode("layout");
            if (layoutAttr == null) {
                layoutAttr = element.getAttributeNodeNS(ANDROID_URI, "layout");
            }
            if (layoutAttr != null) {
                String included = extractLayoutName(layoutAttr.getValue());
                if (included != null) {
                    mCurrentFile.includes.add(included);
                }
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (mCurrentFile != null) {
            mLayoutMap
                    .computeIfAbsent(mCurrentFile.name, k -> new ArrayList<>())
                    .add(mCurrentFile);
            mCurrentFile = null;
        }
    }

    @Override
    public void afterCheckRootProject(Context context) {
        if (mLayoutMap == null || mLayoutMap.isEmpty()) {
            return;
        }

        Set<String> includedNames = new HashSet<>();
        for (List<FileInfo> infos : mLayoutMap.values()) {
            for (FileInfo info : infos) {
                includedNames.addAll(info.includes);
            }
        }

        boolean hasRoot = false;
        for (List<FileInfo> infos : mLayoutMap.values()) {
            if (!infos.isEmpty() && !includedNames.contains(infos.get(0).name)) {
                hasRoot = true;
                break;
            }
        }

        for (List<FileInfo> infos : mLayoutMap.values()) {
            if (infos.isEmpty()) {
                continue;
            }
            if (!hasRoot || !includedNames.contains(infos.get(0).name)) {
                for (FileInfo info : infos) {
                    checkDuplicates(context, info);
                }
            }
        }
    }

    @Override
    public String toString() {
        return DuplicateIdDetector.class.getSimpleName();
    }

    @Override
    public int compareTo(Detector other) {
        return getClass().getName().compareTo(other.getClass().getName());
    }

    private void checkDuplicates(Context context, FileInfo root) {
        Map<String, List<Location>> allIds = new HashMap<>();
        Set<String> visited = new HashSet<>();
        collectIds(root, visited, allIds);

        for (Map.Entry<String, List<Location>> entry : allIds.entrySet()) {
            Set<File> files = new HashSet<>();
            for (Location location : entry.getValue()) {
                files.add(location.getFile());
            }
            if (files.size() > 1) {
                for (Location location : entry.getValue()) {
                    context.report(
                            ISSUE,
                            location,
                            "Duplicate id \""
                                    + entry.getKey()
                                    + "\" across layouts included from \""
                                    + root.name
                                    + "\"");
                }
            }
        }
    }

    private void collectIds(FileInfo info, Set<String> visited, Map<String, List<Location>> out) {
        if (!visited.add(info.name)) {
            return;
        }
        addIds(out, info);
        for (String include : info.includes) {
            FileInfo child = getFirstChild(include);
            if (child != null) {
                collectIds(child, visited, out);
            }
        }
    }

    private FileInfo getFirstChild(String name) {
        List<FileInfo> list = mLayoutMap.get(name);
        return list != null && !list.isEmpty() ? list.get(0) : null;
    }

    private static void addIds(Map<String, List<Location>> target, FileInfo info) {
        for (Map.Entry<String, List<Location>> entry : info.ids.entrySet()) {
            target.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                    .addAll(entry.getValue());
        }
    }

    private static String getLayoutName(File file) {
        String name = file.getName();
        int dot = name.indexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String extractId(String value) {
        if (value == null) {
            return null;
        }
        int index = value.indexOf("@+id/");
        if (index != -1) {
            return value.substring(index + "@+id/".length());
        }
        index = value.indexOf("@id/");
        if (index != -1) {
            return value.substring(index + "@id/".length());
        }
        return null;
    }

    private static String extractLayoutName(String value) {
        if (value == null) {
            return null;
        }
        int index = value.indexOf("@layout/");
        if (index == -1) {
            return null;
        }
        return value.substring(index + "@layout/".length());
    }

    private static class FileInfo {
        final String name;
        final Map<String, List<Location>> ids = new HashMap<>();
        final List<String> includes = new ArrayList<>();

        FileInfo(String name) {
            this.name = name;
        }
    }
}