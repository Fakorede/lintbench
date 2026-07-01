package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;

public class InvalidImeActionIdDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "InvalidImeActionId",
            "Invalid imeActionId declaration",
            "`android:imeActionId` should not be a resource ID such as `@+id/resName`. " +
            "It must be an integer constant, or an integer resource reference, as defined in `EditorInfo`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    InvalidImeActionIdDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_IME_ACTION_ID);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
            int index = name.indexOf(':');
            if (index != -1) {
                name = name.substring(index + 1);
            }
        }
        if (!SdkConstants.ATTR_IME_ACTION_ID.equals(name)) {
            return;
        }

        String namespace = attribute.getNamespaceURI();
        if (namespace != null) {
            if (!SdkConstants.ANDROID_URI.equals(namespace)) {
                return;
            }
        } else {
            String prefix = attribute.getPrefix();
            if (prefix != null && !prefix.equals("android")) {
                return;
            }
        }

        String value = attribute.getValue();
        if (value == null) {
            return;
        }
        value = value.trim();
        if (value.isEmpty()) {
            return;
        }

        boolean isInvalid = false;
        if (value.startsWith("@")) {
            if (!value.contains("integer/")) {
                isInvalid = true;
            }
        } else if (value.startsWith("?")) {
            isInvalid = false;
        } else {
            if (!isValidInteger(value)) {
                isInvalid = true;
            }
        }

        if (isInvalid) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "`android:imeActionId` should be an integer constant or an integer resource reference, not a resource ID"
            );
        }
    }

    private static boolean isValidInteger(String value) {
        try {
            if (value.startsWith("0x") || value.startsWith("0X")) {
                Long.parseLong(value.substring(2), 16);
            } else {
                Long.parseLong(value);
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}