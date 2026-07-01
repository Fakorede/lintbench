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
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;

public class InvalidImeActionIdDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InvalidImeActionId",
            "Invalid `imeActionId` declaration",
            "`android:imeActionId` should not be a resource ID such as `@+id/resName`. "
                    + "It must be an integer constant, or an integer resource reference, "
                    + "as defined in `EditorInfo`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(InvalidImeActionIdDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attribute = (Attr) attributes.item(i);
            String name = attribute.getName();
            if (!name.equals(SdkConstants.ANDROID_PREFIX + SdkConstants.ATTR_IME_ACTION_ID)
                    && !name.equals(SdkConstants.ATTR_IME_ACTION_ID)) {
                continue;
            }
            String value = attribute.getValue();
            if (value != null && (value.startsWith("@+id/") || value.startsWith("@id/"))) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getValueLocation(attribute),
                        "Invalid value for `android:imeActionId`: must be an integer, not a resource ID"
                );
            }
        }
    }
}