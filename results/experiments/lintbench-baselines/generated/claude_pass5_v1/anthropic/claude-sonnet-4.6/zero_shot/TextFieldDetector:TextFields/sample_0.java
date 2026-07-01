/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.ATTR_PASSWORD;
import static com.android.SdkConstants.ATTR_PHONE_NUMBER;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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

/**
 * Checks for missing inputType attributes on text fields, and also looks at the
 * id to see if the purpose of the field suggests a more specific inputType.
 */
public class TextFieldDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "TextFields",
            "Missing `inputType`",
            "Providing an `inputType` attribute on a text field improves usability " +
            "because depending on the data to be input, optimized keyboards can be shown " +
            "to the user (such as just digits and parentheses for a phone number).\n" +
            "\n" +
            "The lint detector also looks at the `id` of the view, and if the id offers a " +
            "hint of the purpose of the field (for example, the `id` contains the phrase " +
            "`phone` or `email`), then lint will also ensure that the `inputType` contains " +
            "the corresponding type attributes.\n" +
            "\n" +
            "If you really want to keep the text field generic, you can suppress this warning " +
            "by setting `inputType=\"text\"`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(
                    TextFieldDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    private static final String EDIT_TEXT = "EditText";

    // Input type constants
    private static final String TYPE_TEXT_VARIATION_EMAIL_ADDRESS = "textEmailAddress";
    private static final String TYPE_TEXT_VARIATION_URI = "textUri";
    private static final String TYPE_TEXT_VARIATION_PASSWORD = "textPassword";
    private static final String TYPE_TEXT_VARIATION_VISIBLE_PASSWORD = "textVisiblePassword";
    private static final String TYPE_TEXT_VARIATION_WEB_PASSWORD = "textWebPassword";
    private static final String TYPE_NUMBER_VARIATION_PASSWORD = "numberPassword";
    private static final String TYPE_CLASS_PHONE = "phone";
    private static final String TYPE_CLASS_NUMBER = "number";
    private static final String TYPE_NUMBER_FLAG_DECIMAL = "numberDecimal";
    private static final String TYPE_NUMBER_FLAG_SIGNED = "numberSigned";
    private static final String TYPE_TEXT_VARIATION_PERSON_NAME = "textPersonName";
    private static final String TYPE_TEXT_VARIATION_POSTAL_ADDRESS = "textPostalAddress";
    private static final String TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS = "textWebEmailAddress";

    /** Constructs a new {@link TextFieldDetector} */
    public TextFieldDetector() {
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(EDIT_TEXT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if inputType attribute is present
        Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);

        // Also check for the deprecated password and phoneNumber attributes
        String phoneNumber = element.getAttributeNS(ANDROID_URI, ATTR_PHONE_NUMBER);
        String password = element.getAttributeNS(ANDROID_URI, ATTR_PASSWORD);

        if (inputTypeAttr == null && !isTrue(phoneNumber) && !isTrue(password)) {
            // No inputType attribute found — report missing inputType
            // But first check if there's a hint via the id attribute
            String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
            context.report(ISSUE, element, context.getLocation(element),
                    "This text field does not specify an `inputType` or a `hint`");
            return;
        }

        if (inputTypeAttr == null) {
            // Has deprecated phoneNumber or password attribute
            return;
        }

        String inputType = inputTypeAttr.getValue();

        // Check if the id gives hints about what inputType should be used
        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        if (id == null || id.isEmpty()) {
            return;
        }

        // Normalize the id: strip @+id/ or @id/ prefix
        if (id.startsWith("@")) {
            int slash = id.indexOf('/');
            if (slash != -1) {
                id = id.substring(slash + 1);
            }
        }

        String idLower = id.toLowerCase(java.util.Locale.US);

        // Check for phone-related ids
        if (containsWord(idLower, "phone") && !inputType.contains(TYPE_CLASS_PHONE)) {
            // The id suggests this is a phone number field
            context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                    "The view's `id` suggests this is a phone number, but the `inputType` "
                    + "does not include `phone` or `number`; if this is not a phone field, "
                    + "consider suppressing this warning with `tools:ignore=\"TextFields\"`");
            return;
        }

        // Check for email-related ids
        if (containsWord(idLower, "email") &&
                !inputType.contains(TYPE_TEXT_VARIATION_EMAIL_ADDRESS) &&
                !inputType.contains(TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)) {
            context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                    "The view's `id` suggests this is an email address field, but the "
                    + "`inputType` does not include `textEmailAddress`; if this is not "
                    + "an email address field, consider suppressing this warning with "
                    + "`tools:ignore=\"TextFields\"`");
            return;
        }

        // Check for password-related ids
        if ((containsWord(idLower, "password") || containsWord(idLower, "passwd")
                || containsWord(idLower, "pwd")) &&
                !inputType.contains(TYPE_TEXT_VARIATION_PASSWORD) &&
                !inputType.contains(TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) &&
                !inputType.contains(TYPE_TEXT_VARIATION_WEB_PASSWORD) &&
                !inputType.contains(TYPE_NUMBER_VARIATION_PASSWORD)) {
            context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                    "The view's `id` suggests this is a password field, but the "
                    + "`inputType` does not include `textPassword`; if this is not "
                    + "a password field, consider suppressing this warning with "
                    + "`tools:ignore=\"TextFields\"`");
            return;
        }

        // Check for URI/URL-related ids
        if ((containsWord(idLower, "url") || containsWord(idLower, "uri")
                || containsWord(idLower, "link") || containsWord(idLower, "website")) &&
                !inputType.contains(TYPE_TEXT_VARIATION_URI)) {
            context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                    "The view's `id` suggests this is a URI field, but the "
                    + "`inputType` does not include `textUri`; if this is not "
                    + "a URI field, consider suppressing this warning with "
                    + "`tools:ignore=\"TextFields\"`");
            return;
        }

        // Check for postal address-related ids
        if ((containsWord(idLower, "postal") || containsWord(idLower, "address")
                || containsWord(idLower, "zip")) &&
                !inputType.contains(TYPE_TEXT_VARIATION_POSTAL_ADDRESS)) {
            context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                    "The view's `id` suggests this is a postal address field, but the "
                    + "`inputType` does not include `textPostalAddress`; if this is not "
                    + "a postal address field, consider suppressing this warning with "
                    + "`tools:ignore=\"TextFields\"`");
            return;
        }

        // Check for person name-related ids
        if ((containsWord(idLower, "name") || containsWord(idLower, "firstname")
                || containsWord(idLower, "lastname") || containsWord(idLower, "surname")) &&
                !inputType.contains(TYPE_TEXT_VARIATION_PERSON_NAME)) {
            context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                    "The view's `id` suggests this is a person name field, but the "
                    + "`inputType` does not include `textPersonName`; if this is not "
                    + "a person name field, consider suppressing this warning with "
                    + "`tools:ignore=\"TextFields\"`");
        }
    }

    /**
     * Checks whether the given string value represents a boolean true value.
     */
    private static boolean isTrue(@Nullable String value) {
        return "true".equals(value);
    }

    /**
     * Checks whether the given id string contains the given word.
     * The check is case-insensitive and treats underscores, dashes, and camelCase
     * boundaries as word separators.
     */
    private static boolean containsWord(@NonNull String id, @NonNull String word) {
        // Simple substring check first for performance
        int index = id.indexOf(word);
        if (index == -1) {
            return false;
        }

        // Make sure it's a whole word (preceded and followed by non-letter or start/end)
        // by checking boundaries
        int end = index + word.length();

        // Check left boundary
        boolean leftOk = (index == 0)
                || !Character.isLetter(id.charAt(index - 1))
                || Character.isUpperCase(id.charAt(index));

        // Check right boundary
        boolean rightOk = (end >= id.length())
                || !Character.isLetter(id.charAt(end))
                || Character.isUpperCase(id.charAt(end));

        return leftOk && rightOk;
    }
}