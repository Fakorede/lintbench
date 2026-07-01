package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.util.*;

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

    private static final String KEY_IDS = "DuplicateIdDetector_Ids";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        Attr idAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (idAttr == null) {
            return;
        }

        String idValue = idAttr.getValue();
        if (idValue == null || idValue.isEmpty()) {
            return;
        }

        String idName = idValue;
        if (idName.startsWith(SdkConstants.VALUE_ID_PREFIX)) {
            idName = idName.substring(SdkConstants.VALUE_ID_PREFIX.length());
        } else if (idName.startsWith(SdkConstants.VALUE_ID_PREFIX_NO_PLUS)) {
            idName = idName.substring(SdkConstants.VALUE_ID_PREFIX_NO_PLUS.length());
        } else {
            return;
        }

        if (idName.isEmpty()) {
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Location> ids = (Map<String, Location>) context.getClientData(KEY_IDS);
        if (ids == null) {
            ids = new HashMap<>();
            context.putClientData(KEY_IDS, ids);
        }

        Location existing = ids.get(idName);
        if (existing != null) {
            Location location = context.getLocation(idAttr);
            location.setSecondary(existing);
            context.report(ISSUE, location, "Duplicate id `" + idName + "`, already defined earlier in this layout");
        } else {
            ids.put(idName, context.getLocation(idAttr));
        }
    }
}