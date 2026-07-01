package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.AUTO_COMPLETE_TEXT_VIEW;
import static com.android.SdkConstants.EDIT_TEXT;
import static com.android.SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW;

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

    private static final String EXTRACT_EDIT_TEXT = "ExtractEditText";

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
        Attr node = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        if (node == null) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "This text field does not specify an inputType");
            return;
        }

        String inputType = node.getValue();
        if ("text".equals(inputType)) {
            return;
        }

        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        if (id == null || id.isEmpty()) {
            return;
        }

        id = stripIdPrefix(id);
        inputType = inputType.toLowerCase(Locale.US);

        if ((containsWord(id, "phone", true, true) || containsWord(id, "fax", true, true))
                && !inputType.contains("phone")) {
            report(context, node, id, "a phone number", "a phone type");
        } else if (containsWord(id, "email", true, true)
                && !inputType.contains("email")) {
            report(context, node, id, "an email address", "an email type");
        } else if ((containsWord(id, "password", true, true)
                    || containsWord(id, "passwd", true, true)
                    || containsWord(id, "pwd", true, true))
                && !inputType.contains("password")) {
            report(context, node, id, "a password", "a password type");
        } else if (containsWord(id, "name", true, true)
                && !inputType.contains("personname")) {
            report(context, node, id, "a person's name", "a person's name type");
        } else if ((containsWord(id, "address", true, true)
                    || containsWord(id, "addr", true, true)
                    || containsWord(id, "zip", true, true)
                    || containsWord(id, "postal", true, true))
                && !inputType.contains("postaladdress")) {
            report(context, node, id, "an address", "an address type");
        } else if ((containsWord(id, "url", true, true)
                    || containsWord(id, "uri", true, true)
                    || containsWord(id, "website", true, true))
                && !inputType.contains("uri")) {
            report(context, node, id, "a URI", "a URI type");
        }
    }

    private static void report(
            XmlContext context, Attr node, String id, String type, String expected) {
        context.report(
                ISSUE,
                node,
                context.getLocation(node),
                String.format(
                        "The id of this text field (`%1$s`) suggests it is for %2$s, but the "
                                + "`inputType` does not include %3$s",
                        id, type, expected));
    }

    private static String stripIdPrefix(String id) {
        int index = id.lastIndexOf('/');
        if (index >= 0) {
            id = id.substring(index + 1);
        }
        return id;
    }

    public static boolean containsWord(
            String name, String word, boolean allowPrefix, boolean allowSuffix) {
        int wordLength = word.length();
        int nameLength = name.length();
        if (wordLength == 0) {
            return false;
        }

        for (int index = 0; index <= nameLength - wordLength; index++) {
            if (name.regionMatches(true, index, word, 0, wordLength)) {
                boolean prefixBoundary =
                        index == 0
                                || !Character.isJavaIdentifierPart(name.charAt(index - 1));
                boolean suffixBoundary =
                        index + wordLength == nameLength
                                || !Character.isJavaIdentifierPart(name.charAt(index + wordLength));
                if ((allowPrefix || prefixBoundary) && (allowSuffix || suffixBoundary)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean containsWord(String name, String word) {
        return containsWord(name, word, false, false);
    }
}