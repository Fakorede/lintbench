package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DuplicateIdDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
        "DuplicateIds",
        "Duplicate ids within a single layout",
        "Within a layout, id's should be unique since otherwise `findViewById()` can return an unexpected view.",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private Map<String, Location> mIds;

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ID);
    }

    @Override
    public void beforeCheckFile(Context context) {
        mIds = new HashMap<>();
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String id = attribute.getValue();
        if (id == null || id.isEmpty()) {
            return;
        }

        int slash = id.lastIndexOf('/');
        if (slash == -1) {
            return;
        }

        String idName = id.substring(slash + 1);
        Location location = context.getLocation(attribute);

        Location first = mIds.get(idName);
        if (first != null) {
            location.setSecondary(first);
            context.report(ISSUE, location, "Duplicate id `" + idName + "`, already defined earlier in this layout");
        } else {
            mIds.put(idName, location);
        }
    }
}