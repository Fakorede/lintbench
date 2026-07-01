package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.AUTO_COMPLETE_TEXT_VIEW;
import static com.android.SdkConstants.EDIT_TEXT;
import static com.android.SdkConstants.EXTRACT_EDIT_TEXT;
import static com.android.SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW;
import static com.android.SdkConstants.VAL_TEXT;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class TextFieldDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "TextFields",
                    "Missing `inputType`",
                    "Providing an `inputType` attribute on a text field improves usability because "
                            + "depending on the data to be input, optimized keyboards can be shown "
                            + "to the user (such as just digits and parentheses for a phone number)."
                            + "\n\n"
                            + "The lint detector also looks at the `id` of the view, and if the id "
                            + "offers a hint of the purpose of the field (for example, the `id` "
                            + "contains the phrase `phone` or `email`), then lint will also ensure "
                            + "that the `inputType` contains the corresponding type attributes."
                            + "\n\n"
                            + "If you really want to keep the text field generic, you can suppress "
                            + "this warning by setting `inputType=\"text\"`.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                AUTO_COMPLETE_TEXT_VIEW,
                EDIT_TEXT,
                EXTRACT_EDIT_TEXT,
                MULTI_AUTO_COMPLETE_TEXT_VIEW);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);

        if (inputTypeAttr == null) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "This text field does not specify an inputType");
            return;
        }

        String inputType = inputTypeAttr.getValue();
        if (inputType == null) {
            return;
        }

        if (VAL_TEXT.equals(inputType.trim())) {
            return;
        }

        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        if (id == null || id.isEmpty()) {
            return;
        }

        id = stripIdPrefix(id).toLowerCase(Locale.US);

        if (id.contains("phone") && !inputType.contains("phone")) {
            context.report(
                    ISSUE,
                    inputTypeAttr,
                    context.getLocation(inputTypeAttr),
                    "The id of this text field suggests it is for a phone number, but the "
                            + "inputType does not include a phone type");
        } else if (id.contains("email") && !inputType.contains("email")) {
            context.report(
                    ISSUE,
                    inputTypeAttr,
                    context.getLocation(inputTypeAttr),
                    "The id of this text field suggests it is for an email address, but the "
                            + "inputType does not include an email type");
        }
    }

    private static String stripIdPrefix(String id) {
        int slash = id.lastIndexOf('/');
        if (slash != -1) {
            id = id.substring(slash + 1);
        }
        if (id.startsWith("@+android:id/")) {
            return id.substring("@+android:id/".length());
        } else if (id.startsWith("@android:id/")) {
            return id.substring("@android:id/".length());
        } else if (id.startsWith("@+id/")) {
            return id.substring("@+id/".length());
        } else if (id.startsWith("@id/")) {
            return id.substring("@id/".length());
        }
        return id;
    }
}