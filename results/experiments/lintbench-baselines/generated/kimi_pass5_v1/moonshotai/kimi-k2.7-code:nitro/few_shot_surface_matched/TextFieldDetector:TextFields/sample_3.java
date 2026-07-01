package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class TextFieldDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "TextFields",
                    "Missing inputType",
                    "Providing an `inputType` attribute on a text field improves usability because"
                            + " depending on the data to be input, optimized keyboards can be shown"
                            + " to the user (such as just digits and parentheses for a phone number)."
                            + " If you really want to keep the text field generic, you can suppress"
                            + " this warning by setting `inputType=\"text\"`.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            TextFieldDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_INPUT_TYPE = "inputType";
    private static final String ATTR_ID = "id";
    private static final String TAG_EDIT_TEXT = "EditText";
    private static final String TAG_AUTO_COMPLETE_TEXT_VIEW = "AutoCompleteTextView";
    private static final String TAG_MULTI_AUTO_COMPLETE_TEXT_VIEW = "MultiAutoCompleteTextView";
    private static final String TAG_EXTRACT_EDIT_TEXT = "ExtractEditText";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_EDIT_TEXT,
                TAG_AUTO_COMPLETE_TEXT_VIEW,
                TAG_MULTI_AUTO_COMPLETE_TEXT_VIEW,
                TAG_EXTRACT_EDIT_TEXT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        if (inputTypeAttr == null) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "This text field is missing an `inputType` attribute; add one to improve usability");
            return;
        }

        String inputType = inputTypeAttr.getValue();
        if ("text".equals(inputType)) {
            return;
        }

        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        if (id == null || id.isEmpty()) {
            return;
        }

        String idLower = id.toLowerCase(Locale.ROOT);
        String inputTypeLower = inputType.toLowerCase(Locale.ROOT);

        if (idLower.contains("email") && !inputTypeLower.contains("email")) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "The id of this text field suggests it is an email field, but the `inputType`"
                            + " does not include an email type");
        }

        if (idLower.contains("phone") && !inputTypeLower.contains("phone")) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "The id of this text field suggests it is a phone field, but the `inputType`"
                            + " does not include a phone type");
        }
    }
}