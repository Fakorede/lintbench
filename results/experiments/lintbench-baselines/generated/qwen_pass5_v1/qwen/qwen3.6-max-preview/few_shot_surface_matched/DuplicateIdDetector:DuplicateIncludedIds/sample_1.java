package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
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

public class DuplicateIdDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIncludedIds",
            "Duplicate ids across layouts combined with include tags",
            "It's okay for two independent layouts to use the same ids. However, if layouts are "
                    + "combined with include tags, then the id's need to be unique within any chain "
                    + "of included layouts, or `Activity#findViewById()` can return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_ID = "id";
    private static final String ATTR_LAYOUT = "layout";
    private static final String TAG_INCLUDE = "include";
    private static final String NEW_ID_PREFIX = "@+id/";
    private static final String DOT_XML = ".xml";

    private Map<File, Map<String, Location>> mFileIds = new HashMap<>();
    private Map<File, List<String>> mFileIncludes = new HashMap<>();
    private Map<String, File> mLayoutFiles = new HashMap<>();
    private File mCurrentFile;

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ID);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_INCLUDE);
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        mFileIds.clear();
        mFileIncludes.clear();
        mLayoutFiles.clear();
    }

    @Override
    public void beforeCheckFile(Context context) {
        mCurrentFile = context.file;
        mFileIds.put(mCurrentFile, new HashMap<>());
        mFileIncludes.put(mCurrentFile, new ArrayList<>());
        String name = mCurrentFile.getName();
        if (name.endsWith(DOT_XML)) {
            mLayoutFiles.put(name.substring(0, name.length() - DOT_XML.length()), mCurrentFile);
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        mCurrentFile = null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (TAG_INCLUDE.equals(element.getTagName())) {
            String layout = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT);
            if (layout != null && !layout.isEmpty()) {
                int slash = layout.indexOf('/');
                if (slash != -1) {
                    layout = layout.substring(slash + 1);
                }
                List<String> includes = mFileIncludes.get(mCurrentFile);
                if (includes != null) {
                    includes.add(layout);
                }
            }
        }
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value != null && value.startsWith(NEW_ID_PREFIX)) {
            String id = value.substring(NEW_ID_PREFIX.length());
            Map<String, Location> ids = mFileIds.get(mCurrentFile);
            if (ids != null) {
                ids.put(id, context.getLocation(attribute));
            }
        }
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (Map.Entry<File, List<String>> entry : mFileIncludes.entrySet()) {
            File root = entry.getKey();
            List<String> includes = entry.getValue();
            if (includes != null && !includes.isEmpty()) {
                checkChain(context, root, new HashSet<>(), new HashMap<>());
            }
        }
    }

    private void checkChain(Context context, File file, Set<File> visited, Map<String, Location> chainIds) {
        if (visited.contains(file)) {
            return;
        }
        visited.add(file);

        Map<String, Location> ids = mFileIds.get(file);
        if (ids != null) {
            for (Map.Entry<String, Location> entry : ids.entrySet()) {
                String id = entry.getKey();
                Location location = entry.getValue();
                Location existing = chainIds.get(id);
                if (existing != null) {
                    String message = String.format(
                            "Duplicate id `%1$s`, already defined in layout `%2$s`",
                            id, existing.file.getName());
                    context.report(ISSUE, location, message);
                } else {
                    chainIds.put(id, location);
                }
            }
        }

        List<String> includes = mFileIncludes.get(file);
        if (includes != null) {
            for (String includeName : includes) {
                File includedFile = mLayoutFiles.get(includeName);
                if (includedFile != null) {
                    checkChain(context, includedFile, visited, new HashMap<>(chainIds));
                }
            }
        }
    }

    @Override
    public String toString() {
        return "DuplicateIdDetector[" + ISSUE.getId() + "]";
    }

    @Override
    public int compareTo(Detector other) {
        return getClass().getName().compareTo(other.getClass().getName());
    }
}