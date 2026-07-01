package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
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

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateIds",
                    "Duplicate ids within a single layout",
                    "Within a single layout, ids should be unique since otherwise `findViewById()` can return an unexpected view.",
                    Category.CORRECTNESS,
                    7,
                    Severity.FATAL,
                    IMPLEMENTATION);

    private Map<String, List<Attr>> mIds;

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
        return ALL;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIds = new HashMap<>();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        XmlContext xmlContext = (XmlContext) context;
        for (Map.Entry<String, List<Attr>> entry : mIds.entrySet()) {
            List<Attr> attributes = entry.getValue();
            if (attributes.size() > 1) {
                String id = entry.getKey();
                for (int i = 1; i < attributes.size(); i++) {
                    Attr duplicate = attributes.get(i);
                    xmlContext.report(
                            ISSUE,
                            duplicate,
                            xmlContext.getLocation(duplicate),
                            "Duplicate id @id/" + id + ", already defined earlier in this layout");
                }
            }
        }
        mIds = null;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }
        String id = getIdName(value);
        if (id == null) {
            return;
        }
        List<Attr> list = mIds.get(id);
        if (list == null) {
            list = new ArrayList<>();
            mIds.put(id, list);
        }
        list.add(attribute);
    }

    private static String getIdName(@NonNull String idValue) {
        int slash = idValue.lastIndexOf('/');
        if (slash == -1 || slash == idValue.length() - 1) {
            return null;
        }
        return idValue.substring(slash + 1);
    }
}