package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class TextFieldDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "TextFields",
            "Missing inputType or useful inputType",
            "Providing an `inputType` attribute on a text field improves usability "
                    + "because depending on the data to be input, optimized keyboards can be shown "
                    + "to the user (such as just digits and parentheses for a phone number).\n\n"
                    + "The lint detector also looks at the `id` of the view, and if the id offers a "
                    + "hint of the purpose of the field (for example, the `id` contains the phrase "
                    + "`phone` or `email`), then lint will also ensure that the `inputType` contains "
                    + "the corresponding type attributes.\n\n"
                    + "If you really want to keep the text field generic, you can suppress this warning "
                    + "by setting `inputType=\"text\"`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.EDIT_TEXT,
                "com.google.android.material.textfield.TextInputEditText"
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String inputType = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE);
        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);

        if (inputType == null || inputType.isEmpty()) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `inputType` attribute"
            );
            return;
        }

        if (id != null && !id.isEmpty()) {
            String idValue = id.substring(id.indexOf('/') + 1).toLowerCase(Locale.US);
            Attr inputTypeAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE);
            Location location = inputTypeAttr != null ? context.getLocation(inputTypeAttr) : context.getNameLocation(element);

            if (idValue.contains("email") && !inputType.contains("textEmailAddress")) {
                context.report(
                        ISSUE,
                        element,
                        location,
                        "This text field is named like an email field but does not specify `textEmailAddress` as an `inputType`"
                );
            } else if (idValue.contains("phone") && !inputType.contains("phone")) {
                context.report(
                        ISSUE,
                        element,
                        location,
                        "This text field is named like a phone number field but does not specify `phone` as an `inputType`"
                );
            } else if (idValue.contains("password") && !inputType.contains("password")) {
                context.report(
                        ISSUE,
                        element,
                        location,
                        "This text field is named like a password field but does not specify a password `inputType`"
                );
            } else if ((idValue.contains("number") || idValue.contains("numeric"))
                    && !(inputType.contains("number") || inputType.contains("phone"))) {
                context.report(
                        ISSUE,
                        element,
                        location,
                        "This text field is named like a numeric field but does not specify a numeric `inputType`"
                );
            }
        }
    }
}