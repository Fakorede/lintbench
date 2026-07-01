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
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.EDIT_TEXT;

/**
 * Checks for missing inputType attributes on text fields, and checks that the
 * inputType is consistent with the id of the field (e.g. a field with id
 * containing "phone" should have inputType="phone").
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
            // No inputType attribute at all
            Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
            String idValue = idAttr != null ? idAttr.getValue() : null;

            // Strip the @+id/ or @id/ prefix
            if (idValue != null) {
                int slashIndex = idValue.indexOf('/');
                if (slashIndex != -1) {
                    idValue = idValue.substring(slashIndex + 1);
                }
                idValue = idValue.toLowerCase(Locale.US);
            }

            String message;

            if (idValue != null) {
                String expectedType = getExpectedType(idValue);
                if (expectedType != null) {
                    message = String.format(
                            "This text field does not specify an `inputType` or a `hint`; " +
                            "based on the id, the `inputType` should be `%1$s`",
                            expectedType);
                } else {
                    message = "This text field does not specify an `inputType` or a `hint`";
                }
            } else {
                message = "This text field does not specify an `inputType` or a `hint`";
            }

            LintFix fix = LintFix.create()
                    .set(ANDROID_URI, ATTR_INPUT_TYPE, "text")
                    .build();
            context.report(ISSUE, element, context.getLocation(element), message, fix);
        } else {
            // Has inputType - check if it's consistent with the id
            String inputType = inputTypeAttr.getValue();

            Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
            String idValue = idAttr != null ? idAttr.getValue() : null;

            if (idValue != null) {
                int slashIndex = idValue.indexOf('/');
                if (slashIndex != -1) {
                    idValue = idValue.substring(slashIndex + 1);
                }
                idValue = idValue.toLowerCase(Locale.US);

                checkInputTypeMatchesId(context, element, inputTypeAttr, inputType, idValue);
            }
        }
    }

    /**
     * Checks that the inputType is consistent with the id of the field.
     */
    private static void checkInputTypeMatchesId(
            @NonNull XmlContext context,
            @NonNull Element element,
            @NonNull Attr inputTypeAttr,
            @NonNull String inputType,
            @NonNull String idLower) {

        String inputTypeLower = inputType.toLowerCase(Locale.US);

        // Check for phone
        if (containsWord(idLower, "phone", false, false)) {
            if (!inputTypeLower.contains("phone")) {
                context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                        String.format(
                                "The view's `id` (`%1$s`) suggests this is a phone number, " +
                                "but the `inputType` does not include `phone`",
                                idLower));
            }
        }

        // Check for email
        if (containsWord(idLower, "email", false, false)) {
            if (!inputTypeLower.contains("textemailaddress")
                    && !inputTypeLower.contains("textemailsubject")) {
                context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                        String.format(
                                "The view's `id` (`%1$s`) suggests this is an e-mail address, " +
                                "but the `inputType` does not include `textEmailAddress`",
                                idLower));
            }
        }

        // Check for password
        if (containsWord(idLower, "password", false, false)
                || containsWord(idLower, "passwd", false, false)
                || containsWord(idLower, "pwd", false, false)) {
            if (!inputTypeLower.contains("textpassword")
                    && !inputTypeLower.contains("textnumericpassword")
                    && !inputTypeLower.contains("numberpassword")) {
                context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                        String.format(
                                "The view's `id` (`%1$s`) suggests this is a password field, " +
                                "but the `inputType` does not include `textPassword`",
                                idLower));
            }
        }

        // Check for URI / URL
        if (containsWord(idLower, "url", false, false)
                || containsWord(idLower, "uri", false, false)
                || containsWord(idLower, "link", false, false)) {
            if (!inputTypeLower.contains("texturl")
                    && !inputTypeLower.contains("textweburi")) {
                context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                        String.format(
                                "The view's `id` (`%1$s`) suggests this is a URI, " +
                                "but the `inputType` does not include `textUri`",
                                idLower));
            }
        }

        // Check for postal address
        if (containsWord(idLower, "postal", false, false)
                || containsWord(idLower, "address", false, false)
                || containsWord(idLower, "zip", false, false)) {
            if (!inputTypeLower.contains("textpostaladdress")) {
                context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                        String.format(
                                "The view's `id` (`%1$s`) suggests this is a postal address, " +
                                "but the `inputType` does not include `textPostalAddress`",
                                idLower));
            }
        }
    }

    /**
     * Returns the expected inputType for a given id, or null if no specific type is expected.
     */
    private static String getExpectedType(String idLower) {
        if (containsWord(idLower, "phone", false, false)) {
            return "phone";
        }
        if (containsWord(idLower, "email", false, false)) {
            return "textEmailAddress";
        }
        if (containsWord(idLower, "password", false, false)
                || containsWord(idLower, "passwd", false, false)
                || containsWord(idLower, "pwd", false, false)) {
            return "textPassword";
        }
        if (containsWord(idLower, "url", false, false)
                || containsWord(idLower, "uri", false, false)
                || containsWord(idLower, "link", false, false)) {
            return "textUri";
        }
        if (containsWord(idLower, "postal", false, false)
                || containsWord(idLower, "address", false, false)
                || containsWord(idLower, "zip", false, false)) {
            return "textPostalAddress";
        }
        return null;
    }

    /**
     * Returns true if the given string contains the given word.
     *
     * @param text the text to search in (should be lowercase)
     * @param word the word to search for (should be lowercase)
     * @param allowPrefix if true, allow the word to be preceded by other characters
     * @param allowSuffix if true, allow the word to be followed by other characters
     */
    static boolean containsWord(String text, String word, boolean allowPrefix, boolean allowSuffix) {
        int index = text.indexOf(word);
        if (index == -1) {
            return false;
        }

        if (!allowPrefix) {
            // Check that the word is at the start or preceded by a separator
            if (index > 0) {
                char before = text.charAt(index - 1);
                if (before != '_' && before != '-' && before != '.') {
                    return false;
                }
            }
        }

        if (!allowSuffix) {
            // Check that the word is at the end or followed by a separator
            int end = index + word.length();
            if (end < text.length()) {
                char after = text.charAt(end);
                if (after != '_' && after != '-' && after != '.') {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Returns true if the given string contains the given word as a whole word
     * (separated by underscores, dashes, or at start/end).
     */
    static boolean containsWord(String text, String word) {
        return containsWord(text, word, false, false);
    }
}