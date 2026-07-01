package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Attr;

public class DuplicateIdDetector extends ResourceXmlDetector {

    private Map<String, Location> mIds;

    static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate id in a layout",
            "Within a layout, ids should be unique since otherwise `findViewById()` can return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void beforeCheckEachFile(@NonNull XmlContext context) {
        mIds = new HashMap<>();
    }

    @Override
    @NonNull
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ID);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null) {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url == null || url.type != ResourceType.ID) {
            return;
        }

        String key = (url.packageName != null ? url.packageName + "/" : "") + url.name;
        Location current = context.getValueLocation(attribute);
        Location previous = mIds.get(key);

        if (previous != null) {
            current.secondary = previous;
            context.report(
                    ISSUE,
                    current,
                    "Duplicate id " + value + " in this layout: `findViewById()` can return an unexpected view");
        } else {
            mIds.put(key, current);
        }
    }
}