package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TextFormat;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.SdkConstants;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TextFieldDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "TextFields",
            "Missing `inputType`",
            "Providing an `inputType` attribute on a text field improves usability because " +
                    "depending on the data to be input, optimized keyboards can be shown to the " +
                    "user (such as just digits and parentheses for a phone number).\n" +
                    "\n" +
                    "This check also looks at the `id` of the view, and if the id offers a hint " +
                    "of the purpose of the field (for example, the id contains the phrase `phone` " +
                    "or `email`), then it will ensure that the `inputType` contains the " +
                    "corresponding type attributes.\n" +
                    "\n" +
                    "If you really want to keep the text field generic, you can suppress this " +
                    "warning by setting `inputType=\"text\"`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final Map<String, String[]> ID_HINTS = new HashMap<>();

    static {
        ID_HINTS.put("email", new String[]{"textemailaddress", "textemailsubject"});
        ID_HINTS.put("e-mail", new String[]{"textemailaddress"});

        ID_HINTS.put("phone", new String[]{"phone"});
        ID_HINTS.put("tel", new String[]{"phone"});
        ID_HINTS.put("mobile", new String[]{"phone"});

        ID_HINTS.put("password", new String[]{"textpassword", "textvisiblepassword"});
        ID_HINTS.put("pwd", new String[]{"textpassword", "textvisiblepassword"});
        ID_HINTS.put("passwd", new String[]{"textpassword", "textvisiblepassword"});

        ID_HINTS.put("url", new String[]{"texturi"});
        ID_HINTS.put("uri", new String[]{"texturi"});

        ID_HINTS.put("number", new String[]{"number", "numberdecimal", "numbersigned", "numberpassword"});
        ID_HINTS.put("num", new String[]{"number", "numberdecimal", "numbersigned", "numberpassword"});
        ID_HINTS.put("zip", new String[]{"number", "numberdecimal", "numbersigned", "numberpassword"});
        ID_HINTS.put("pin", new String[]{"number", "numberpassword"});

        ID_HINTS.put("name", new String[]{"textpersonname", "textpassword"});
        ID_HINTS.put("person", new String[]{"textpersonname"});
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.EDIT_TEXT,
                SdkConstants.AUTO_COMPLETE_TEXT_VIEW,
                SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW,
                SdkConstants.EXTRACT_EDIT_TEXT,
                "android.support.design.widget.TextInputEditText",
                "com.google.android.material.textfield.TextInputEditText"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr inputTypeAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE);
        String inputType = inputTypeAttr != null ? inputTypeAttr.getValue() : null;

        if (inputType == null || inputType.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element),
                    "EditText is missing an `inputType` attribute", null);
            return;
        }

        if (inputType.startsWith("@")) {
            return;
        }

        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (id == null || id.isEmpty()) {
            return;
        }

        String localId = id.substring(id.lastIndexOf('/') + 1).toLowerCase(Locale.US);
        if (localId.isEmpty()) {
            return;
        }

        Set<String> typeTokens = new HashSet<>();
        for (String token : inputType.toLowerCase(Locale.US).split("\\|")) {
            typeTokens.add(token.trim());
        }

        for (Map.Entry<String, String[]> entry : ID_HINTS.entrySet()) {
            String hint = entry.getKey();
            if (localId.contains(hint)) {
                boolean found = false;
                for (String required : entry.getValue()) {
                    if (typeTokens.contains(required)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    String expected = entry.getValue()[0];
                    String message = String.format(
                            "The id of this text field (`%s`) suggests it should accept %s input; "
                                    + "consider setting `inputType` to `%s`",
                            localId, hint, expected);
                    context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                            message, null);
                }
            }
        }
    }
}