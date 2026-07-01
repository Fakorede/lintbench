package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.*;

import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ANDROID_URI;

public class DuplicateIdDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate ids within a single layout",
            "Within a layout, id's should be unique since otherwise `findViewById()` can " +
            "return an unexpected view.",
            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            new Implementation(
                    DuplicateIdDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private final Map<String, Location> mIds = new HashMap<>();

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIds.clear();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ID);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String id = attribute.getValue();
        if (id == null || id.isEmpty()) {
            return;
        }

        if (mIds.containsKey(id)) {
            Location location = context.getLocation(attribute);
            Location previousLocation = mIds.get(id);
            location.setSecondary(previousLocation);
            context.report(
                    ISSUE,
                    attribute,
                    location,
                    String.format("Duplicate id `%1$s`, already defined earlier in this layout", id)
            );
        } else {
            mIds.put(id, context.getLocation(attribute));
        }
    }
}