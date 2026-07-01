package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_ID;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DuplicateIdDetector extends ResourceXmlDetector {

    private Map<String, List<Attr>> mIds;
    private boolean mCheckLayout;

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        mCheckLayout = context.getResourceFolderType() == ResourceFolderType.LAYOUT;
        if (mCheckLayout) {
            mIds = new HashMap<>();
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        if (!mCheckLayout) {
            return;
        }

        for (Map.Entry<String, List<Attr>> entry : mIds.entrySet()) {
            List<Attr> attributes = entry.getValue();
            if (attributes.size() > 1) {
                String id = entry.getKey();
                for (int i = 1; i < attributes.size(); i++) {
                    Attr attr = attributes.get(i);
                    String message = String.format(
                            "Duplicate id `%1$s` in this layout; id's should be unique since otherwise `findViewById()` can return an unexpected view",
                            id);
                    Location location = context.getLocation(attr);
                    context.report(ISSUE, attr.getOwnerElement(), location, message);
                }
            }
        }

        mIds = null;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ID);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attr) {
        if (!mCheckLayout) {
            return;
        }

        String value = attr.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        String normalized = normalizeId(value);
        if (normalized == null) {
            return;
        }

        List<Attr> list = mIds.get(normalized);
        if (list == null) {
            list = new ArrayList<>();
            mIds.put(normalized, list);
        }
        list.add(attr);
    }

    private static String normalizeId(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("@+")) {
            return "@" + trimmed.substring(2);
        }
        return trimmed;
    }

    private static final Implementation IMPLEMENTATION = new Implementation(
            DuplicateIdDetector.class,
            Scope.RESOURCE_XML_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate ids within a single layout",
            "Within a layout, id's should be unique since otherwise `findViewById()` can return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION);
}