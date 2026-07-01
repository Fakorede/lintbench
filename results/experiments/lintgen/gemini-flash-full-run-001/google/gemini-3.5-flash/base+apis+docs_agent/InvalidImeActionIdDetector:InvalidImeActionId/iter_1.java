package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class InvalidImeActionIdDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InvalidImeActionId",
            "Invalid imeActionId declaration",
            "`android:imeActionId` should not be a resource ID such as `@+id/resName`. " +
            "It must be an integer constant, or an integer resource reference, " +
            "as defined in `EditorInfo`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    InvalidImeActionIdDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_IME_ACTION_ID);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String namespace = attribute.getNamespaceURI();
        if (namespace != null) {
            if (!SdkConstants.ANDROID_URI.equals(namespace)) {
                return;
            }
        } else {
            String name = attribute.getName();
            if (!name.startsWith("android:") && !name.contains(":imeActionId")) {
                return;
            }
        }

        String value = attribute.getValue().trim();
        boolean valid = false;
        if (value.startsWith("@")) {
            if (value.startsWith("@integer/") || value.startsWith("@android:integer/")) {
                valid = true;
            }
        } else if (value.startsWith("?")) {
            valid = true;
        } else {
            try {
                Integer.decode(value);
                valid = true;
            } catch (NumberFormatException e) {
                valid = false;
            }
        }

        if (!valid) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "`android:imeActionId` should be an integer constant or an integer resource reference"
            );
        }
    }
}