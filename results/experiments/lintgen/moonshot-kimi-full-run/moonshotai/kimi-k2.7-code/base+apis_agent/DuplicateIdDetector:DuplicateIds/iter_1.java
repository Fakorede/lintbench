package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DuplicateIdDetector extends Detector implements XmlScanner {

    public static final Issue DUPLICATE_IDS = Issue.create(
            "DuplicateIds",
            "Duplicate ids within a single layout",
            "Within a layout, id's should be unique since otherwise `findViewById()` can return "
                    + "an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE)
    ).setAndroidSpecific(true);

    private Map<String, Location> mIds;

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ID);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || !value.startsWith(SdkConstants.NEW_ID_PREFIX)) {
            return;
        }

        String id = value.substring(SdkConstants.NEW_ID_PREFIX.length());
        if (mIds == null) {
            mIds = new HashMap<>();
        }

        Location location = context.getValueLocation(attribute);
        Location prev = mIds.put(id, location);
        if (prev != null) {
            context.report(
                    DUPLICATE_IDS,
                    location,
                    String.format("Duplicate id `%1$s`, already defined in this layout", value)
            );
        }
    }

    @Override
    public void beforeCheckFile(Context context) {
        mIds = null;
    }
}