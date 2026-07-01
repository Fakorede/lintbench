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
import org.w3c.dom.Element;
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
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void beforeCheckFile(Context context) {
        mIds = new HashMap<>();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (id == null || id.isEmpty() || !id.startsWith("@+id/")) {
            return;
        }

        String idName = id.substring(5);
        Attr attrNode = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        Location location = context.getLocation(attrNode);

        Location first = mIds.get(idName);
        if (first != null) {
            location.setSecondary(first);
            context.report(ISSUE, location, "Duplicate id `" + idName + "`, already defined earlier in this layout");
        } else {
            mIds.put(idName, location);
        }
    }
}