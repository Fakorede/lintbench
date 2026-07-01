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
import com.android.tools.lint.detector.api.LayoutDetector;
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
                    "Within a layout, id's should be unique since otherwise `findViewById()` can return an unexpected view.",
                    Category.CORRECTNESS,
                    7,
                    Severity.FATAL,
                    IMPLEMENTATION);

    private Map<String, List<IdOccurrence>> mIds;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIds = new HashMap<>();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mIds == null) {
            return;
        }

        for (List<IdOccurrence> occurrences : mIds.values()) {
            if (occurrences.size() > 1) {
                Collections.sort(occurrences);

                String idName = stripIdPrefix(occurrences.get(0).id);
                for (int i = 1; i < occurrences.size(); i++) {
                    IdOccurrence duplicate = occurrences.get(i);
                    Location location = context.getLocation(duplicate.element);
                    String message =
                            String.format(
                                    "Duplicate id @+id/%1$s, already defined earlier in this layout",
                                    idName);
                    context.report(ISSUE, location, message);
                }
            }
        }

        mIds = null;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No project-wide state is required.
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No project-wide cleanup is required.
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        if (id != null && !id.isEmpty()) {
            String canonicalId = canonicalId(id);
            int offset = context.getLocation(element).getStart().getOffset();

            List<IdOccurrence> list = mIds.get(canonicalId);
            if (list == null) {
                list = new ArrayList<>();
                mIds.put(canonicalId, list);
            }
            list.add(new IdOccurrence(element, id, offset));
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Id collection is performed in visitElement.
    }

    private static String canonicalId(String id) {
        if (id.startsWith("@+id/")) {
            return "@id/" + id.substring("@+id/".length());
        }
        return id;
    }

    private static String stripIdPrefix(String id) {
        if (id.startsWith("@+id/")) {
            return id.substring("@+id/".length());
        } else if (id.startsWith("@id/")) {
            return id.substring("@id/".length());
        } else if (id.startsWith("@android:id/")) {
            return id.substring("@android:id/".length());
        }
        return id;
    }

    private static class IdOccurrence implements Comparable<IdOccurrence> {
        final Element element;
        final String id;
        final int offset;

        IdOccurrence(Element element, String id, int offset) {
            this.element = element;
            this.id = id;
            this.offset = offset;
        }

        @Override
        public int compareTo(@NonNull IdOccurrence other) {
            return Integer.compare(this.offset, other.offset);
        }

        @Override
        public String toString() {
            return id + " at offset " + offset;
        }
    }
}