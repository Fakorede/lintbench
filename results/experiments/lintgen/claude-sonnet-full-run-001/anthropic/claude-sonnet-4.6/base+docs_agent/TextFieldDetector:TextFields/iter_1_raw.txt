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

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.EDIT_TEXT;

/**
 * Checks for missing inputType attributes on text fields, and validates that
 * the inputType is appropriate for the field's purpose based on its id.
 */
public class TextFieldDetector extends Detector implements XmlScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "TextFields",
            "Missing `inputType`",
            "Providing an `inputType` attribute on a text field improves usability "
                    + "because depending on the data to be input, optimized keyboards can be shown "
                    + "to the user (such as just digits and parentheses for a phone number).\n"
                    + "\n"
                    + "The lint detector also looks at the `id` of the view, and if the id offers a "
                    + "hint of the purpose of the field (for example, the `id` contains the phrase "
                    + "`phone` or `email`), then lint will also ensure that the `inputType` contains "
                    + "the corresponding type attributes.\n"
                    + "\n"
                    + "If you really want to keep the text field generic, you can suppress this warning "
                    + "by setting `inputType=\"text\"`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(
                    TextFieldDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    // inputType flag values
    private static final String TYPE_CLASS_PHONE = "phone";
    private static final String TYPE_CLASS_NUMBER = "number";
    private static final String TYPE_TEXT_VARIATION_EMAIL_ADDRESS = "textEmailAddress";
    private static final String TYPE_TEXT_VARIATION_PASSWORD = "textPassword";
    private static final String TYPE_TEXT_VARIATION_URI = "textUri";
    private static final String TYPE_TEXT_VARIATION_POSTAL_ADDRESS = "textPostalAddress";
    private static final String TYPE_NUMBER_VARIATION_PASSWORD = "numberPassword";
    private static final String TYPE_TEXT_VARIATION_PERSON_NAME = "textPersonName";

    /** Constructs a new {@link TextFieldDetector} */
    public TextFieldDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(EDIT_TEXT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);

        if (inputTypeAttr == null) {
            Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
            String idValue = idAttr != null ? idAttr.getValue() : null;
            String suggestion = getInputTypeSuggestion(idValue);

            if (suggestion != null) {
                String message = "This text field does not specify an `inputType`; "
                        + "based on the id (`" + stripIdPrefix(idValue) + "`), "
                        + "the `inputType` should probably be `" + suggestion + "`";
                context.report(ISSUE, element, context.getLocation(element), message);
            } else {
                context.report(ISSUE, element, context.getLocation(element),
                        "This text field does not specify an `inputType`");
            }
        } else {
            // Has inputType — check if it's appropriate for the id
            String inputType = inputTypeAttr.getValue();

            Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
            String idValue = idAttr != null ? idAttr.getValue() : null;

            if (idValue != null) {
                checkIdVsInputType(context, element, inputTypeAttr, idValue, inputType);
            }
        }
    }

    /**
     * Checks whether the inputType is consistent with what the id suggests.
     */
    private static void checkIdVsInputType(
            @NonNull XmlContext context,
            @NonNull Element element,
            @NonNull Attr inputTypeAttr,
            @NonNull String idValue,
            @NonNull String inputType) {

        String id = stripIdPrefix(idValue).toLowerCase();

        if (containsWord(id, "phone", false, false)) {
            if (!inputType.contains(TYPE_CLASS_PHONE)) {
                context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + stripIdPrefix(idValue) + "`) suggests this is a "
                                + "phone number, but the `inputType` does not include `phone`");
            }
        } else if (containsWord(id, "email", false, false)) {
            if (!inputType.contains(TYPE_TEXT_VARIATION_EMAIL_ADDRESS)) {
                context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + stripIdPrefix(idValue) + "`) suggests this is an "
                                + "e-mail address, but the `inputType` does not include "
                                + "`textEmailAddress`");
            }
        } else if (containsWord(id, "password", false, false) || containsWord(id, "passwd", false, false)
                || containsWord(id, "pwd", false, false)) {
            if (!inputType.contains(TYPE_TEXT_VARIATION_PASSWORD)
                    && !inputType.contains(TYPE_NUMBER_VARIATION_PASSWORD)) {
                context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + stripIdPrefix(idValue) + "`) suggests this is a "
                                + "password field, but the `inputType` does not include "
                                + "`textPassword`");
            }
        } else if (containsWord(id, "uri", false, false) || containsWord(id, "url", false, false)
                || containsWord(id, "link", false, false) || containsWord(id, "website", false, false)) {
            if (!inputType.contains(TYPE_TEXT_VARIATION_URI)) {
                context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + stripIdPrefix(idValue) + "`) suggests this is a "
                                + "URI, but the `inputType` does not include `textUri`");
            }
        } else if (containsWord(id, "postal", false, false) || containsWord(id, "zip", false, false)) {
            if (!inputType.contains(TYPE_TEXT_VARIATION_POSTAL_ADDRESS)
                    && !inputType.contains(TYPE_CLASS_NUMBER)) {
                context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + stripIdPrefix(idValue) + "`) suggests this is a "
                                + "postal address, but the `inputType` does not include "
                                + "`textPostalAddress`");
            }
        }
    }

    /**
     * Returns a suggested inputType based on the id, or null if no suggestion.
     */
    private static String getInputTypeSuggestion(String idValue) {
        if (idValue == null) {
            return null;
        }
        String id = stripIdPrefix(idValue).toLowerCase();

        if (containsWord(id, "phone", false, false)) {
            return TYPE_CLASS_PHONE;
        } else if (containsWord(id, "email", false, false)) {
            return TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
        } else if (containsWord(id, "password", false, false) || containsWord(id, "passwd", false, false)
                || containsWord(id, "pwd", false, false)) {
            return TYPE_TEXT_VARIATION_PASSWORD;
        } else if (containsWord(id, "uri", false, false) || containsWord(id, "url", false, false)
                || containsWord(id, "link", false, false) || containsWord(id, "website", false, false)) {
            return TYPE_TEXT_VARIATION_URI;
        } else if (containsWord(id, "postal", false, false) || containsWord(id, "zip", false, false)) {
            return TYPE_TEXT_VARIATION_POSTAL_ADDRESS;
        } else if (containsWord(id, "name", false, false) || containsWord(id, "person", false, false)) {
            return TYPE_TEXT_VARIATION_PERSON_NAME;
        }

        return null;
    }

    /**
     * Strips the @+id/ or @id/ prefix from an id value.
     */
    static String stripIdPrefix(String id) {
        if (id == null) {
            return "";
        }
        if (id.startsWith("@+id/")) {
            return id.substring(5);
        } else if (id.startsWith("@id/")) {
            return id.substring(4);
        }
        return id;
    }

    /**
     * Returns true if the given string contains the given word, with optional
     * prefix/suffix allowances for word boundary checking.
     *
     * @param text        the text to search in (should be lowercased)
     * @param word        the word to search for (should be lowercased)
     * @param allowPrefix if true, allow the word to be preceded by other letters
     * @param allowSuffix if true, allow the word to be followed by other letters
     */
    static boolean containsWord(String text, String word, boolean allowPrefix, boolean allowSuffix) {
        if (text == null || word == null) {
            return false;
        }

        int index = 0;
        while (true) {
            index = text.indexOf(word, index);
            if (index == -1) {
                return false;
            }

            boolean startOk = allowPrefix || index == 0
                    || !Character.isLetter(text.charAt(index - 1));
            boolean endOk = allowSuffix || index + word.length() >= text.length()
                    || !Character.isLetter(text.charAt(index + word.length()));

            if (startOk && endOk) {
                return true;
            }

            index += word.length();
        }
    }
}