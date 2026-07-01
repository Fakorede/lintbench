package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
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

    private static final String ANDROID_ID = "id";
    private static final String ID_PREFIX = "@id/";
    private static final String NEW_ID_PREFIX = "@+id/";
    private static final String ANDROID_ID_PREFIX = "@android:id/";

    private Map<String, List<Attr>> mIds;

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIds",
                    "Duplicate ids within a single layout",
                    "Within a layout, ids should be unique since otherwise `findViewById()` can return an unexpected view.",
                    Category.CORRECTNESS,
                    7,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ANDROID_ID);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
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

        for (Map.Entry<String, List<Attr>> entry : mIds.entrySet()) {
            List<Attr> occurrences = entry.getValue();
            if (occurrences.size() > 1) {
                String idName = entry.getKey();
                String message =
                        String.format("Duplicate id `%1$s`, already defined earlier in this layout", idName);
                for (int i = 1; i < occurrences.size(); i++) {
                    Attr attr = occurrences.get(i);
                    context.report(
                            ISSUE,
                            attr,
                            ((XmlContext) context).getLocation(attr),
                            message);
                }
            }
        }

        mIds = null;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Per-file state is reset in beforeCheckFile.
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Nothing to do here.
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not used; duplicate ids are detected by visiting attributes.
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        String idName = getIdName(value);
        if (idName == null || idName.isEmpty()) {
            return;
        }

        List<Attr> list = mIds.get(idName);
        if (list == null) {
            list = new ArrayList<>();
            mIds.put(idName, list);
        }
        list.add(attribute);
    }

    private static String getIdName(String idValue) {
        if (idValue.startsWith(NEW_ID_PREFIX)) {
            return idValue.substring(NEW_ID_PREFIX.length());
        }
        if (idValue.startsWith(ID_PREFIX)) {
            return idValue.substring(ID_PREFIX.length());
        }
        if (idValue.startsWith(ANDROID_ID_PREFIX)) {
            // Framework ids are expected to be reused.
            return null;
        }
        return null;
    }
}