package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DuplicateIdDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate IDs in layout",
            "Within a layout, ids should be unique since otherwise `findViewById()` can return an unexpected view.",
            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private Map<String, List<Attr>> mIds;

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.NAVIGATION;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ID);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (mIds == null) {
            mIds = new HashMap<>();
        }

        String value = attribute.getValue();
        List<Attr> list = mIds.get(value);
        if (list == null) {
            list = new ArrayList<>();
            mIds.put(value, list);
        }
        list.add(attribute);
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        if (mIds == null) {
            return;
        }

        for (Map.Entry<String, List<Attr>> entry : mIds.entrySet()) {
            List<Attr> attributes = entry.getValue();
            if (attributes.size() <= 1) {
                continue;
            }

            String id = entry.getKey();
            Attr first = attributes.get(0);
            Location firstLocation = context.getValueLocation(first);

            for (int i = 1; i < attributes.size(); i++) {
                Attr duplicate = attributes.get(i);
                Location location = context.getValueLocation(duplicate);
                location.setSecondary(firstLocation);
                context.report(ISSUE, duplicate.getOwnerElement(), location,
                        "Duplicate id `" + id + "`, already defined in this layout");
            }
        }

        mIds = null;
    }
}