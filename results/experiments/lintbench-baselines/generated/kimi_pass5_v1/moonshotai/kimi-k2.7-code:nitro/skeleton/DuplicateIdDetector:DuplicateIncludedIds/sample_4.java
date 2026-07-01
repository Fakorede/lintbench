package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class DuplicateIdDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_ID = "id";
    private static final String ATTR_LAYOUT = "layout";
    private static final String VIEW_INCLUDE = "include";
    private static final String LAYOUT_PREFIX = "@layout/";
    private static final String NEW_ID_PREFIX = "@+id/";
    private static final String ID_REF_PREFIX = "@id/";

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIncludedIds",
                    "Duplicate ids across layouts combined with include tags",
                    "When one layout is included into another via an <include> tag, the two "
                            + "layouts share the same View hierarchy at runtime. If any of the "
                            + "combined layouts define the same android:id, "
                            + "Activity#findViewById() can return an unexpected view. "
                            + "Ids only need to be unique within a chain of included layouts; it is "
                            + "fine for two unrelated layouts to reuse the same ids.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, LayoutFile> mLayouts = new HashMap<>();
    private final Set<String> mReported = new HashSet<>();
    private LayoutFile mCurrentLayout;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ID);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(VIEW_INCLUDE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        String fileName = context.file.getName();
        if (fileName.endsWith(".xml")) {
            fileName = fileName.substring(0, fileName.length() - ".xml".length());
        }
        mCurrentLayout = new LayoutFile(fileName, context.file.getPath());
        mLayouts.put(fileName, mCurrentLayout);
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        mCurrentLayout = null;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mLayouts.clear();
        mReported.clear();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        List<LayoutFile> roots = new ArrayList<>(mLayouts.values());
        roots.sort(this::compareTo);
        for (LayoutFile root : roots) {
            check(root, context);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mCurrentLayout == null || !VIEW_INCLUDE.equals(element.getLocalName())) {
            return;
        }
        Attr layoutAttr = element.getAttributeNode(ATTR_LAYOUT);
        if (layoutAttr != null) {
            String target = layoutAttr.getValue();
            if (target.startsWith(LAYOUT_PREFIX)) {
                String name = target.substring(LAYOUT_PREFIX.length());
                mCurrentLayout.includes.add(new Include(name, context.getLocation(layoutAttr)));
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (mCurrentLayout == null) {
            return;
        }
        String id = getIdName(attribute.getValue());
        if (id != null) {
            mCurrentLayout.ids.add(new IdUsage(id, context.getLocation(attribute)));
        }
    }

    private void check(LayoutFile root, Context context) {
        List<LayoutFile> chain = new ArrayList<>();
        Map<String, List<IdUsage>> seen = new HashMap<>();
        Set<LayoutFile> path = new HashSet<>();
        collect(root, chain, seen, path, context);
    }

    private void collect(
            LayoutFile current,
            List<LayoutFile> chain,
            Map<String, List<IdUsage>> seen,
            Set<LayoutFile> path,
            Context context) {
        if (!path.add(current)) {
            return;
        }
        chain.add(current);
        for (IdUsage usage : current.ids) {
            List<IdUsage> list = seen.get(usage.id);
            if (list == null) {
                list = new ArrayList<>();
                seen.put(usage.id, list);
            } else {
                reportDuplicate(context, usage, list.get(0), chain);
            }
            list.add(usage);
        }
        for (Include include : current.includes) {
            LayoutFile target = mLayouts.get(include.target);
            if (target != null) {
                collect(target, chain, seen, path, context);
            }
        }
        chain.remove(chain.size() - 1);
        path.remove(current);
    }

    private void reportDuplicate(
            Context context, IdUsage duplicate, IdUsage original, List<LayoutFile> chain) {
        String id = duplicate.id;
        StringBuilder sb = new StringBuilder();
        toString(sb, chain);
        String message =
                "Duplicate id \"@+id/"
                        + id
                        + "\" detected in layout chain ("
                        + sb.toString()
                        + "); first defined in "
                        + original.location.getFile().getName();
        String key = canonicalKey(id, duplicate.location, original.location);
        if (mReported.add(key)) {
            context.report(ISSUE, duplicate.location, message);
        }
    }

    private String canonicalKey(String id, Location a, Location b) {
        String ka = a.getFile().getPath() + ":" + a.getStart().getOffset() + ":" + id;
        String kb = b.getFile().getPath() + ":" + b.getStart().getOffset() + ":" + id;
        if (ka.compareTo(kb) <= 0) {
            return ka + "|" + kb;
        } else {
            return kb + "|" + ka;
        }
    }

    private String getIdName(String value) {
        if (value == null) {
            return null;
        }
        String id;
        if (value.startsWith(NEW_ID_PREFIX)) {
            id = value.substring(NEW_ID_PREFIX.length());
        } else if (value.startsWith(ID_REF_PREFIX)) {
            id = value.substring(ID_REF_PREFIX.length());
        } else {
            return null;
        }
        return id.isEmpty() ? null : id;
    }

    public void toString(@NonNull StringBuilder sb, @NonNull List<LayoutFile> chain) {
        for (int i = 0; i < chain.size(); i++) {
            if (i > 0) {
                sb.append(" -> ");
            }
            sb.append(chain.get(i).name);
        }
    }

    public int compareTo(@NonNull LayoutFile a, @NonNull LayoutFile b) {
        return a.name.compareTo(b.name);
    }

    private static class LayoutFile {
        final String name;
        final String path;
        final List<IdUsage> ids = new ArrayList<>();
        final List<Include> includes = new ArrayList<>();

        LayoutFile(String name, String path) {
            this.name = name;
            this.path = path;
        }
    }

    private static class IdUsage {
        final String id;
        final Location location;

        IdUsage(String id, Location location) {
            this.id = id;
            this.location = location;
        }
    }

    private static class Include {
        final String target;
        final Location location;

        Include(String target, Location location) {
            this.target = target;
            this.location = location;
        }
    }
}