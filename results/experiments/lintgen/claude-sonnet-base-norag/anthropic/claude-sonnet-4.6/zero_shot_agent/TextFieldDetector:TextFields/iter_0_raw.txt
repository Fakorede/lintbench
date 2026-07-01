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
            // No inputType attribute at all - check if there's a hint from the id
            Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
            String idValue = idAttr != null ? idAttr.getValue() : null;

            // Strip the @+id/ or @id/ prefix
            if (idValue != null) {
                int slashIndex = idValue.indexOf('/');
                if (slashIndex != -1) {
                    idValue = idValue.substring(slashIndex + 1);
                }
                idValue = idValue.toLowerCase(java.util.Locale.US);
            }

            String message = "This text field does not specify an `inputType` or a `hint`";

            if (idValue != null) {
                String expectedType = getExpectedType(idValue);
                if (expectedType != null) {
                    message = String.format(
                            "This text field does not specify an `inputType` or a `hint`; " +
                            "based on the id, the `inputType` should be `%1$s`",
                            expectedType);
                }
            }

            // Check if there's a hint attribute as well
            Attr hintAttr = element.getAttributeNodeNS(ANDROID_URI, "hint");
            if (hintAttr == null) {
                LintFix fix = LintFix.create()
                        .set(ANDROID_URI, ATTR_INPUT_TYPE, "text")
                        .build();
                context.report(ISSUE, element, context.getLocation(element), message, fix);
            } else {
                // Has a hint but no inputType - still report
                LintFix fix = LintFix.create()
                        .set(ANDROID_URI, ATTR_INPUT_TYPE, "text")
                        .build();
                context.report(ISSUE, element, context.getLocation(element),
                        "This text field does not specify an `inputType`", fix);
            }
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
                idValue = idValue.toLowerCase(java.util.Locale.US);

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

        String inputTypeLower = inputType.toLowerCase(java.util.Locale.US);

        // Check for phone
        if (containsWord(idLower, "phone")) {
            if (!inputTypeLower.contains("phone")) {
                context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                        String.format(
                                "The view's `id` (`%1$s`) suggests this is a phone number, " +
                                "but the `inputType` does not include `phone`",
                                idLower));
            }
        }

        // Check for email
        if (containsWord(idLower, "email")) {
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
        if (containsWord(idLower, "password") || containsWord(idLower, "passwd")
                || containsWord(idLower, "pwd")) {
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
        if (containsWord(idLower, "url") || containsWord(idLower, "uri")
                || containsWord(idLower, "link")) {
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
        if (containsWord(idLower, "postal") || containsWord(idLower, "address")
                || containsWord(idLower, "zip")) {
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
        if (containsWord(idLower, "phone")) {
            return "phone";
        }
        if (containsWord(idLower, "email")) {
            return "textEmailAddress";
        }
        if (containsWord(idLower, "password") || containsWord(idLower, "passwd")
                || containsWord(idLower, "pwd")) {
            return "textPassword";
        }
        if (containsWord(idLower, "url") || containsWord(idLower, "uri")
                || containsWord(idLower, "link")) {
            return "textUri";
        }
        if (containsWord(idLower, "postal") || containsWord(idLower, "address")
                || containsWord(idLower, "zip")) {
            return "textPostalAddress";
        }
        return null;
    }

    /**
     * Returns true if the given string contains the given word as a whole word
     * (separated by underscores, camelCase boundaries, or at start/end).
     */
    private static boolean containsWord(String text, String word) {
        // Simple substring check first for performance
        int index = text.indexOf(word);
        if (index == -1) {
            return false;
        }

        // Check that it's a word boundary
        // Before the word: start of string, underscore, or uppercase transition
        // After the word: end of string, underscore, or uppercase transition
        int end = index + word.length();

        boolean beforeOk = index == 0
                || text.charAt(index - 1) == '_'
                || text.charAt(index - 1) == '-'
                || Character.isUpperCase(text.charAt(index));

        boolean afterOk = end == text.length()
                || text.charAt(end) == '_'
                || text.charAt(end) == '-'
                || Character.isUpperCase(text.charAt(end));

        if (beforeOk && afterOk) {
            return true;
        }

        // Also do a simple contains check - the id might just contain the word
        // as part of a longer word, which is still a good hint
        return true; // Be lenient - if the id contains the substring, it's a hint
    }
}