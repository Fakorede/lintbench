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

import java.util.Arrays;
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
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                SdkConstants.ATTR_IME_ACTION_ID,
                SdkConstants.ANDROID_PREFIX + SdkConstants.ATTR_IME_ACTION_ID
        );
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        if (isValidImeActionId(value)) {
            return;
        }

        Location location = context.getValueLocation(attribute);
        if (location == null) {
            location = context.getLocation(attribute);
        }

        context.report(
                ISSUE,
                attribute,
                location,
                "Invalid value for `android:imeActionId`: must be an integer constant or an integer resource reference, not `" + value + "`"
        );
    }

    private static boolean isValidImeActionId(String value) {
        if (isInteger(value)) {
            return true;
        }

        if (value.startsWith("@")) {
            String ref = value;
            if (ref.startsWith("@+")) {
                ref = ref.substring(2);
            } else if (ref.startsWith("@*")) {
                ref = ref.substring(2);
            } else if (ref.startsWith("@")) {
                ref = ref.substring(1);
            }

            int colon = ref.indexOf(':');
            if (colon != -1) {
                ref = ref.substring(colon + 1);
            }

            int slash = ref.indexOf('/');
            String type = slash != -1 ? ref.substring(0, slash) : ref;
            return "integer".equals(type);
        }

        return false;
    }

    private static boolean isInteger(String value) {
        try {
            if (value.startsWith("0x") || value.startsWith("0X")) {
                Integer.parseInt(value.substring(2), 16);
            } else {
                Integer.parseInt(value);
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}