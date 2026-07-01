package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class DuplicateIdDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIds",
                    "Duplicate ids within a single layout",
                    "Within a layout, id's should be unique since otherwise `findViewById()` can "
                            + "return an unexpected view.",
                    Category.CORRECTNESS,
                    7,
                    Severity.FATAL,
                    IMPLEMENTATION);

    /** Map from id value to the location of the first occurrence of that id in the current file */
    private Map<String, Location> mIds;

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
        return null;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIds = new HashMap<>();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        mIds = null;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Nothing to do at root project level for this single-file check
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Nothing to do at root project level for this single-file check
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not used; attribute-based detection handles everything
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (mIds == null) {
            return;
        }

        String id = attribute.getValue();
        if (id == null || id.isEmpty()) {
            return;
        }

        if (mIds.containsKey(id)) {
            Location location = context.getLocation(attribute);
            Location previousLocation = mIds.get(id);
            location.setSecondary(previousLocation);
            context.report(
                    ISSUE,
                    attribute,
                    location,
                    String.format("Duplicate id `%1$s`, already defined earlier in this layout", id));
        } else {
            mIds.put(id, context.getLocation(attribute));
        }
    }

    public String toString(String id) {
        return "DuplicateIdDetector{id=" + id + "}";
    }

    public int compareTo(String id1, String id2) {
        if (id1 == null && id2 == null) {
            return 0;
        } else if (id1 == null) {
            return -1;
        } else if (id2 == null) {
            return 1;
        }
        return id1.compareTo(id2);
    }
}