package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Attr;

public class DuplicateIdDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate ids within a single layout",
            "Within a layout, id's should be unique since otherwise `findViewById()` can return an unexpected view.",
            Category.CORRECTNESS,
            7,
            Severity.ERROR,
            new Implementation(
                    DuplicateIdDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private final Set<String> mIds = new HashSet<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void beforeCheckFile(Context context) {
        mIds.clear();
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

        String id = attribute.getValue();
        if (id == null || id.isEmpty()) {
            return;
        }

        String cleanId = id;
        int index = id.lastIndexOf('/');
        if (index != -1) {
            cleanId = id.substring(index + 1);
        }

        if (mIds.contains(cleanId)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    String.format("Duplicate id `%s`, already defined earlier in this layout", id)
            );
        } else {
            mIds.add(cleanId);
        }
    }
}