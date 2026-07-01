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
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DuplicateIdDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
        "DuplicateIds",
        "Duplicate ids within a single layout",
        "Within a layout, id's should be unique since otherwise `findViewById()` can return an unexpected view.",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private Map<String, Location> mIds;

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.NAVIGATION;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ID);
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        mIds = new HashMap<>();
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (mIds == null) {
            mIds = new HashMap<>();
        }
        String idValue = attribute.getValue();
        if (idValue == null || idValue.isEmpty()) {
            return;
        }

        String idName = idValue.trim();
        if (idName.startsWith("@+id/")) {
            idName = idName.substring(5);
        } else if (idName.startsWith("@id/")) {
            idName = idName.substring(4);
        } else {
            return;
        }

        if (idName.isEmpty()) {
            return;
        }

        Location existing = mIds.get(idName);
        if (existing != null) {
            Location location = context.getLocation(attribute);
            location.setSecondary(existing);
            context.report(ISSUE, location, "Duplicate id `" + idName + "`, already defined earlier in this layout");
        } else {
            mIds.put(idName, context.getLocation(attribute));
        }
    }
}