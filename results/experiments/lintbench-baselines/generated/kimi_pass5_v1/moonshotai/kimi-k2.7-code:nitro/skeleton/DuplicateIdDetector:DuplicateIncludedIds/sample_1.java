package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class DuplicateIdDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIncludedIds",
                    "Duplicate ids across layouts combined with include tags",
                    "Two independent layouts may use the same android:id values without problems. "
                            + "However, when one layout is included in another with an "
                            + "`<include>` tag, all the ids from the included layout become part "
                            + "of the same view hierarchy as the including layout. If any id "
                            + "appears more than once within the transitive set of included "
                            + "layouts, calls such as `Activity#findViewById()` can return an "
                            + "unexpected view.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private Map<String, List<FileData>> mFiles;
    private FileData mCurrent;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
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
    public void beforeCheckRootProject(@NonNull Context context) {
        mFiles = new HashMap<>();
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        String name = getLayoutName(context.file.getName());
        mCurrent = new FileData(context.file, name);
        mCurrent.rootElement = context.document.getDocumentElement();
        mFiles.computeIfAbsent(name, k -> new ArrayList<>()).add(mCurrent);
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        mCurrent = null;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mFiles == null || mFiles.isEmpty()) {
            return;
        }
        for (List<FileData> files : mFiles.values()) {
            for (FileData file : files) {
                check(context, file, new HashMap<>(), null, new HashSet<>(), false);
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mCurrent == null) {
            return;
        }
        if ("include".equals(element.getTagName())) {
            String layout = element.getAttribute("layout");
            if (!layout.isEmpty()) {
                String target = getLayoutNameFromAttribute(layout);
                if (target != null) {
                    boolean hasId = !element.getAttributeNS(ANDROID_URI, "id").isEmpty();
                    mCurrent.includes.add(
                            new Include(
                                    target,
                                    Location.create(context.file, element),
                                    hasId));
                }
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (mCurrent == null) {
            return;
        }
        if (!"id".equals(attribute.getLocalName())) {
            return;
        }
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }
        String value = attribute.getValue();
        if (value == null || !value.contains("/")) {
            return;
        }
        String name = value.substring(value.lastIndexOf('/') + 1);
        if (name.isEmpty()) {
            return;
        }
        boolean isRoot = attribute.getOwnerElement() == mCurrent.rootElement;
        mCurrent.ids.add(new Id(context.file, value, attribute.getOwnerElement(), isRoot));
    }

    private void check(
            @NonNull Context context,
            @NonNull FileData data,
            @NonNull Map<String, Location> seen,
            Location includeLocation,
            @NonNull Set<String> path,
            boolean maskRoot) {
        if (!path.add(data.name)) {
            return;
        }
        for (Id id : data.ids) {
            if (id.root && maskRoot) {
                continue;
            }
            Location previous = seen.get(id.name);
            if (previous != null) {
                if (includeLocation != null) {
                    String where = previous.getFile() != null
                            ? previous.getFile().getName()
                            : "this layout";
                    String message = String.format(
                            "Duplicate id %1$s, already defined in %2$s",
                            id.value, where);
                    context.report(ISSUE, includeLocation, message);
                }
            } else {
                seen.put(id.name, Location.create(id.file, id.element));
            }
        }
        for (Include include : data.includes) {
            List<FileData> targets = mFiles.get(include.layout);
            if (targets != null) {
                for (FileData target : targets) {
                    check(context, target, seen, include.location, path, include.hasId);
                }
            }
        }
        path.remove(data.name);
    }

    private static String getLayoutName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String getLayoutNameFromAttribute(String attributeValue) {
        int slash = attributeValue.lastIndexOf('/');
        if (slash < 0 || slash == attributeValue.length() - 1) {
            return null;
        }
        return attributeValue.substring(slash + 1);
    }

    private static class FileData {
        final File file;
        final String name;
        final List<Id> ids = new ArrayList<>();
        final List<Include> includes = new ArrayList<>();
        Element rootElement;

        FileData(File file, String name) {
            this.file = file;
            this.name = name;
        }
    }

    private static class Include {
        final String layout;
        final Location location;
        final boolean hasId;

        Include(String layout, Location location, boolean hasId) {
            this.layout = layout;
            this.location = location;
            this.hasId = hasId;
        }
    }

    private static class Id implements Comparable<Id> {
        final File file;
        final String value;
        final String name;
        final Element element;
        final boolean root;

        Id(File file, String value, Element element, boolean root) {
            this.file = file;
            this.value = value;
            this.element = element;
            this.root = root;
            this.name = value.substring(value.lastIndexOf('/') + 1);
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public int compareTo(@NonNull Id other) {
            return name.compareTo(other.name);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Id)) {
                return false;
            }
            Id other = (Id) o;
            return name.equals(other.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }
}