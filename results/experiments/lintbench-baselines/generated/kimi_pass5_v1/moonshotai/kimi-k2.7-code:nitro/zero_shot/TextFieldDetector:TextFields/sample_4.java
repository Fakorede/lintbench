package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TextFieldDetector extends LayoutDetector {

    private static final Map<String, String> ID_TO_INPUT_TYPE = new HashMap<String, String>();
    static {
        ID_TO_INPUT_TYPE.put("phone", "phone");
        ID_TO_INPUT_TYPE.put("email", "textEmailAddress");
        ID_TO_INPUT_TYPE.put("e_mail", "textEmailAddress");
        ID_TO_INPUT_TYPE.put("mail", "textEmailAddress");
        ID_TO_INPUT_TYPE.put("password", "textPassword");
        ID_TO_INPUT_TYPE.put("pwd", "textPassword");
        ID_TO_INPUT_TYPE.put("name", "textPersonName");
        ID_TO_INPUT_TYPE.put("address", "textPostalAddress");
        ID_TO_INPUT_TYPE.put("street", "textPostalAddress");
        ID_TO_INPUT_TYPE.put("zip", "textPostalAddress");
        ID_TO_INPUT_TYPE.put("number", "number");
        ID_TO_INPUT_TYPE.put("date", "date");
        ID_TO_INPUT_TYPE.put("time", "time");
        ID_TO_INPUT_TYPE.put("url", "textUri");
        ID_TO_INPUT_TYPE.put("uri", "textUri");
    }

    public static final Issue ISSUE = Issue.create(
        "TextFields",
        "Missing inputType",
        "Providing an `inputType` attribute on a text field improves usability because depending "
            + "on the data to be input, optimized keyboards can be shown to the user (such as "
            + "just digits and parentheses for a phone number).\\n\\n"
            + "The lint detector also looks at the `id` of the view, and if the `id` offers a hint "
            + "of the purpose of the field (for example, the `id` contains the phrase `phone` or "
            + "`email`), then lint will also ensure that the `inputType` contains the corresponding "
            + "type attributes.\\n\\n"
            + "If you really want to keep the text field generic, you can suppress this warning "
            + "by setting `inputType=\\\"text\\\"`.",
        Category.USABILITY,
        5,
        Severity.WARNING,
        new Implementation(TextFieldDetector.class, EnumSet.of(Scope.RESOURCE_FILE_SCOPE))
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public @NonNull Collection<String> getApplicableElements() {
        return Arrays.asList(
            SdkConstants.EDIT_TEXT,
            SdkConstants.AUTO_COMPLETE_TEXT_VIEW,
            SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW,
            SdkConstants.TEXT_INPUT_EDIT_TEXT
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String inputType = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE);
        if (inputType.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Provide an `android:inputType` attribute for this text field; use "
                    + "`android:inputType=\"text\"` if it is intentionally generic"
            );
            return;
        }

        if ("text".equals(inputType)) {
            return;
        }

        if (inputType.startsWith("@")) {
            return;
        }

        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        String localId = getLocalId(id);
        if (localId == null || localId.isEmpty()) {
            return;
        }

        String lowerId = localId.toLowerCase(Locale.ROOT);
        Set<String> types = new HashSet<String>();
        for (String token : inputType.split("\\|")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                types.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }

        for (Map.Entry<String, String> entry : ID_TO_INPUT_TYPE.entrySet()) {
            if (lowerId.contains(entry.getKey())) {
                String expected = entry.getValue();
                if (!types.contains(expected.toLowerCase(Locale.ROOT))) {
                    Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INPUT_TYPE);
                    context.report(
                        ISSUE,
                        attr != null ? attr : element,
                        attr != null ? context.getLocation(attr) : context.getLocation(element),
                        "The `id` of this field suggests it is used for " + entry.getKey()
                            + ", so `android:inputType` should include `" + expected + "`"
                    );
                }
            }
        }
    }

    private static String getLocalId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        int slash = id.indexOf('/');
        if (slash != -1 && slash + 1 < id.length()) {
            id = id.substring(slash + 1);
        }
        return id.replace("_", "").replace("+", "");
    }
}