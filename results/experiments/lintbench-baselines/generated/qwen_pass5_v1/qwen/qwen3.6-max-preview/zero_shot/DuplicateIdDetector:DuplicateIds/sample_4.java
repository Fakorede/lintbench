package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DuplicateIdDetector extends LayoutDetector {
    private static final String KEY_IDS = "DuplicateIdDetector.ids";

    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate ids within a single layout",
            "Within a layout, id's should be unique since otherwise `findViewById()` can return an unexpected view.",
            Category.CORRECTNESS,
            7,
            Severity.ERROR,
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ID);
    }

    @Override
    public void visitAttribute(@NotNull XmlContext context, @NotNull Attr attribute) {
        String id = attribute.getValue();
        if (id == null || id.isEmpty()) {
            return;
        }

        String idName = id;
        if (id.startsWith("@+id/")) {
            idName = id.substring(5);
        } else if (id.startsWith("@id/")) {
            idName = id.substring(4);
        } else if (id.startsWith("@android:id/")) {
            idName = id.substring(12);
        }

        @SuppressWarnings("unchecked")
        Map<String, Location> ids = (Map<String, Location>) context.getClientProperty(KEY_IDS);
        if (ids == null) {
            ids = new HashMap<>();
            context.putClientProperty(KEY_IDS, ids);
        }

        Location firstLocation = ids.get(idName);
        if (firstLocation != null) {
            Location location = context.getLocation(attribute);
            location.setSecondary(firstLocation);
            context.report(ISSUE, attribute, location, "Duplicate id `" + idName + "`");
        } else {
            ids.put(idName, context.getLocation(attribute));
        }
    }
}