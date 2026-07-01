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
 * Checks for missing inputType attributes on text fields.
 */
public class TextFieldDetector extends LayoutDetector {

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

    /** Constructs a new {@link TextFieldDetector} */
    public TextFieldDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(EDIT_TEXT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if inputType attribute is present
        Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);

        if (inputTypeAttr == null) {
            // No inputType attribute at all - report missing inputType
            // But first check if there's an id that gives hints about what type to use
            String idValue = element.getAttributeNS(ANDROID_URI, ATTR_ID);
            String message = getMissingInputTypeMessage(idValue);

            LintFix fix = LintFix.create()
                    .set(ANDROID_URI, ATTR_INPUT_TYPE, "text")
                    .build();

            context.report(ISSUE, element, context.getLocation(element), message, fix);
            return;
        }

        // inputType is present - check if it matches the id hint
        String inputType = inputTypeAttr.getValue();
        String idValue = element.getAttributeNS(ANDROID_URI, ATTR_ID);

        if (idValue == null || idValue.isEmpty()) {
            return;
        }

        // Normalize the id (strip @+id/ or @id/ prefix)
        String id = idValue;
        if (id.startsWith("@+id/")) {
            id = id.substring(5);
        } else if (id.startsWith("@id/")) {
            id = id.substring(4);
        }

        String idLower = id.toLowerCase(java.util.Locale.US);

        // Check for phone-related ids
        if (containsWord(idLower, "phone")) {
            if (!inputType.contains("phone")) {
                String message = "The view's `id` (`" + id + "`) suggests this is a phone "
                        + "number, but the `inputType` does not include `phone`";
                LintFix fix = LintFix.create()
                        .set(ANDROID_URI, ATTR_INPUT_TYPE, "phone")
                        .build();
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        message, fix);
            }
            return;
        }

        // Check for email-related ids
        if (containsWord(idLower, "email")) {
            if (!inputType.contains("textEmailAddress")) {
                String message = "The view's `id` (`" + id + "`) suggests this is an e-mail "
                        + "address, but the `inputType` does not include `textEmailAddress`";
                LintFix fix = LintFix.create()
                        .set(ANDROID_URI, ATTR_INPUT_TYPE, "textEmailAddress")
                        .build();
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        message, fix);
            }
            return;
        }

        // Check for password-related ids
        if (containsWord(idLower, "password") || containsWord(idLower, "passwd")
                || containsWord(idLower, "pwd")) {
            if (!inputType.contains("textPassword")
                    && !inputType.contains("textVisiblePassword")
                    && !inputType.contains("numberPassword")) {
                String message = "The view's `id` (`" + id + "`) suggests this is a password "
                        + "field, but the `inputType` does not include `textPassword`";
                LintFix fix = LintFix.create()
                        .set(ANDROID_URI, ATTR_INPUT_TYPE, "textPassword")
                        .build();
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        message, fix);
            }
            return;
        }

        // Check for URI/URL-related ids
        if (containsWord(idLower, "url") || containsWord(idLower, "uri")
                || containsWord(idLower, "link") || containsWord(idLower, "website")) {
            if (!inputType.contains("textUri")) {
                String message = "The view's `id` (`" + id + "`) suggests this is a URI, "
                        + "but the `inputType` does not include `textUri`";
                LintFix fix = LintFix.create()
                        .set(ANDROID_URI, ATTR_INPUT_TYPE, "textUri")
                        .build();
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        message, fix);
            }
            return;
        }

        // Check for postal code / zip code related ids
        if (containsWord(idLower, "postal") || containsWord(idLower, "zip")) {
            if (!inputType.contains("number") && !inputType.contains("text")) {
                String message = "The view's `id` (`" + id + "`) suggests this is a postal "
                        + "address, but the `inputType` is not set to a number or text type";
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        message);
            }
            return;
        }

        // Check for person name related ids
        if (containsWord(idLower, "name") || containsWord(idLower, "firstname")
                || containsWord(idLower, "lastname") || containsWord(idLower, "surname")) {
            if (!inputType.contains("textPersonName") && !inputType.contains("text")) {
                String message = "The view's `id` (`" + id + "`) suggests this is a person's "
                        + "name, but the `inputType` does not include `textPersonName`";
                LintFix fix = LintFix.create()
                        .set(ANDROID_URI, ATTR_INPUT_TYPE, "textPersonName")
                        .build();
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        message, fix);
            }
        }
    }

    /**
     * Returns a message for missing inputType, potentially customized based on the id.
     */
    private static String getMissingInputTypeMessage(String idValue) {
        if (idValue != null && !idValue.isEmpty()) {
            String id = idValue;
            if (id.startsWith("@+id/")) {
                id = id.substring(5);
            } else if (id.startsWith("@id/")) {
                id = id.substring(4);
            }

            String idLower = id.toLowerCase(java.util.Locale.US);

            if (containsWord(idLower, "phone")) {
                return "This text field does not specify an `inputType`; the view's `id` (`"
                        + id + "`) suggests it should be `phone`";
            } else if (containsWord(idLower, "email")) {
                return "This text field does not specify an `inputType`; the view's `id` (`"
                        + id + "`) suggests it should be `textEmailAddress`";
            } else if (containsWord(idLower, "password") || containsWord(idLower, "passwd")
                    || containsWord(idLower, "pwd")) {
                return "This text field does not specify an `inputType`; the view's `id` (`"
                        + id + "`) suggests it should be `textPassword`";
            } else if (containsWord(idLower, "url") || containsWord(idLower, "uri")) {
                return "This text field does not specify an `inputType`; the view's `id` (`"
                        + id + "`) suggests it should be `textUri`";
            }
        }

        return "This text field does not specify an `inputType`";
    }

    /**
     * Returns true if the given string contains the given word, where a word
     * boundary is defined as either the start/end of the string, an underscore,
     * or a camelCase boundary.
     */
    private static boolean containsWord(String str, String word) {
        if (str == null || word == null) {
            return false;
        }

        // Direct contains check (for simple substring matching)
        int index = str.indexOf(word);
        while (index != -1) {
            // Check that this is a word boundary
            boolean startOk = (index == 0)
                    || !Character.isLetterOrDigit(str.charAt(index - 1))
                    || str.charAt(index - 1) == '_';
            int end = index + word.length();
            boolean endOk = (end == str.length())
                    || !Character.isLetterOrDigit(str.charAt(end))
                    || str.charAt(end) == '_'
                    || Character.isUpperCase(str.charAt(end));

            if (startOk && endOk) {
                return true;
            }

            index = str.indexOf(word, index + 1);
        }

        return false;
    }
}