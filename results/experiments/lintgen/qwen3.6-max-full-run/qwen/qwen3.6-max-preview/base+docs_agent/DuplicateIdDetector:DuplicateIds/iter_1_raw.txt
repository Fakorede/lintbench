package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Attr;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DuplicateIdDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate ids within a single layout",
            "Within a layout, id's should be unique since otherwise `findViewById()` can return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private Map<String, Attr> seenIds;

    @Override
    public void beforeCheckFile(Context context) {
        seenIds = new HashMap<>();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ID);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null) {
            return;
        }

        String idName = null;
        if (value.startsWith(SdkConstants.NEW_ID_PREFIX)) {
            idName = value.substring(SdkConstants.NEW_ID_PREFIX.length());
        } else if (value.startsWith(SdkConstants.ID_PREFIX)) {
            idName = value.substring(SdkConstants.ID_PREFIX.length());
        }

        if (idName == null || idName.isEmpty()) {
            return;
        }

        Attr first = seenIds.putIfAbsent(idName, attribute);
        if (first != null) {
            Location location = context.getLocation(attribute);
            Location secondary = context.getLocation(first);
            secondary.setMessage("Original id defined here");
            location.setSecondary(secondary);
            context.report(ISSUE, location, "Duplicate id `" + idName + "`");
        }
    }
}