package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.utils.SdkConstants;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

public class TextFieldDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "TextFields",
            "Missing `inputType`",
            "Providing an `inputType` attribute on a text field improves usability " +
            "because depending on the data to be input, optimized keyboards can be shown " +
            "to the user (such as just digits and parentheses for a phone number). " +
            "The lint detector also looks at the `id` of the view, and if the id offers a " +
            "hint of the purpose of the field (for example, the `id` contains the phrase " +
            "`phone` or `email`), then lint will also ensure that the `inputType` contains " +
            "the corresponding type attributes. " +
            "If you really want to keep the text field generic, you can suppress this warning " +
            "by setting `inputType=\"text\"`.",
            Category.USABILITY, 5, Severity.WARNING,
            new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.EDIT_TEXT,
                "TextInputEditText",
                "AutoCompleteTextView",
                "MultiAutoCompleteTextView"
        );
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String inputType = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE);

        if (inputType == null || inputType.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Missing `inputType` attribute");
            return;
        }

        if (inputType.startsWith("@")) {
            return;
        }

        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (id == null || id.isEmpty()) {
            return;
        }

        String idName = id.substring(id.lastIndexOf('/') + 1).toLowerCase(Locale.US);
        Attr inputTypeAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE);

        if (idName.contains("phone")) {
            if (!inputType.contains("phone")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "Expected inputType to contain 'phone' based on id");
            }
        } else if (idName.contains("email")) {
            if (!inputType.contains("textEmailAddress")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "Expected inputType to contain 'textEmailAddress' based on id");
            }
        } else if (idName.contains("password")) {
            if (!inputType.contains("textPassword") && !inputType.contains("numberPassword")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "Expected inputType to contain 'textPassword' or 'numberPassword' based on id");
            }
        } else if (idName.contains("number")) {
            if (!inputType.contains("number")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "Expected inputType to contain 'number' based on id");
            }
        } else if (idName.contains("date")) {
            if (!inputType.contains("date")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "Expected inputType to contain 'date' based on id");
            }
        } else if (idName.contains("time")) {
            if (!inputType.contains("time")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "Expected inputType to contain 'time' based on id");
            }
        }
    }
}