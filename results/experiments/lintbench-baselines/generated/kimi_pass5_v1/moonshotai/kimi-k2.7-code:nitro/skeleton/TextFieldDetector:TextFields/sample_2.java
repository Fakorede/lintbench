package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class TextFieldDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TextFields",
                    "Missing `inputType`",
                    "Providing an `inputType` attribute on a text field improves usability "
                            + "because the soft keyboard can be optimized for the kind of data "
                            + "the user is expected to enter (for example digits for a phone "
                            + "number). If the view's `id` suggests a specific input type, the "
                            + "`inputType` should include the corresponding type flags. To keep "
                            + "the field fully generic, set `inputType=\"text\"` to suppress "
                            + "this warning.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String TAG_EDIT_TEXT = "EditText";

    private static final class Hint {
        final String[] idPhrases;
        final String[] inputTypeFlags;
        final String description;

        Hint(String[] idPhrases, String[] inputTypeFlags, String description) {
            this.idPhrases = idPhrases;
            this.inputTypeFlags = inputTypeFlags;
            this.description = description;
        }
    }

    private static final Hint[] HINTS =
            new Hint[] {
                new Hint(new String[] {"phone", "tel"}, new String[] {"phone"}, "phone number"),
                new Hint(
                        new String[] {"email"},
                        new String[] {"textemailaddress", "textemailsubject"},
                        "email address"),
                new Hint(
                        new String[] {"password", "passwd"},
                        new String[] {"textpassword", "textvisiblepassword", "textwebpassword"},
                        "password"),
                new Hint(
                        new String[] {"url", "uri", "website", "link"},
                        new String[] {"texturi"},
                        "URL"),
                new Hint(
                        new String[] {"number", "amount", "quantity", "price"},
                        new String[] {"number", "numbersigned", "numberdecimal", "numberpassword"},
                        "number")
            };

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_EDIT_TEXT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr inputTypeNode = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE);
        if (inputTypeNode == null) {
            context.report(
                    ISSUE, element, context.getLocation(element), "Missing `inputType` attribute");
            return;
        }

        String inputType = inputTypeNode.getValue();
        if (inputType == null || inputType.isEmpty()) {
            return;
        }

        Set<String> flags = new HashSet<>();
        for (String part : inputType.split("\\|")) {
            flags.add(part.trim().toLowerCase(Locale.US));
        }

        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (id == null || id.isEmpty()) {
            return;
        }
        if (id.startsWith(SdkConstants.NEW_ID_PREFIX)) {
            id = id.substring(SdkConstants.NEW_ID_PREFIX.length());
        } else if (id.startsWith(SdkConstants.ID_PREFIX)) {
            id = id.substring(SdkConstants.ID_PREFIX.length());
        }
        String lowerId = id.toLowerCase(Locale.US);

        for (Hint hint : HINTS) {
            for (String phrase : hint.idPhrases) {
                if (lowerId.contains(phrase)) {
                    boolean found = false;
                    for (String flag : hint.inputTypeFlags) {
                        if (flags.contains(flag)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        context.report(
                                ISSUE,
                                inputTypeNode,
                                context.getLocation(inputTypeNode),
                                "The id of this text field suggests it is a "
                                        + hint.description
                                        + ", but the `inputType` does not include the corresponding "
                                        + "type attribute");
                    }
                    return;
                }
            }
        }
    }
}