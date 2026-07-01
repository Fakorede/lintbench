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

    private static final String ATTR_HINT = "hint";
    private static final String ATTR_PASSWORD = "password";

    /** Constructs a new {@link TextFieldDetector} */
    public TextFieldDetector() {
    }

    /**
     * Checks whether the given name contains the given word, with optional prefix/suffix
     * characters allowed.
     *
     * @param name        the name to check
     * @param word        the word to look for
     * @param allowPrefix if true, allow characters before the word
     * @param allowSuffix if true, allow characters after the word
     * @return true if the name contains the word under the given constraints
     */
    public static boolean containsWord(
            @NonNull String name,
            @NonNull String word,
            boolean allowPrefix,
            boolean allowSuffix) {
        int index = 0;
        int nameLen = name.length();
        int wordLen = word.length();

        while (true) {
            int i = indexOfIgnoreCase(name, word, index);
            if (i == -1) {
                return false;
            }

            // Check prefix constraint
            if (!allowPrefix && i > 0) {
                char before = name.charAt(i - 1);
                if (Character.isLetterOrDigit(before)) {
                    index = i + 1;
                    continue;
                }
            }

            // Check suffix constraint
            int end = i + wordLen;
            if (!allowSuffix && end < nameLen) {
                char after = name.charAt(end);
                if (Character.isLetterOrDigit(after)) {
                    index = i + 1;
                    continue;
                }
            }

            return true;
        }
    }

    /**
     * Checks whether the given name contains the given word (allowing both prefix and suffix).
     *
     * @param name the name to check
     * @param word the word to look for
     * @return true if the name contains the word
     */
    public static boolean containsWord(@NonNull String name, @NonNull String word) {
        return containsWord(name, word, true, true);
    }

    private static int indexOfIgnoreCase(@NonNull String text, @NonNull String word, int fromIndex) {
        int textLen = text.length();
        int wordLen = word.length();
        if (wordLen == 0) {
            return fromIndex;
        }
        if (fromIndex + wordLen > textLen) {
            return -1;
        }
        char firstLower = Character.toLowerCase(word.charAt(0));
        char firstUpper = Character.toUpperCase(word.charAt(0));

        outer:
        for (int i = fromIndex; i <= textLen - wordLen; i++) {
            char c = text.charAt(i);
            if (c != firstLower && c != firstUpper) {
                continue;
            }
            for (int j = 1; j < wordLen; j++) {
                char tc = text.charAt(i + j);
                char wc = word.charAt(j);
                if (Character.toLowerCase(tc) != Character.toLowerCase(wc)) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(EDIT_TEXT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String inputType = element.getAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE);

        if (inputType.isEmpty()) {
            // Check for legacy password attribute
            String password = element.getAttributeNS(ANDROID_URI, ATTR_PASSWORD);
            if (password != null && !password.isEmpty()) {
                return;
            }

            // Check for phoneNumber attribute (older API)
            String phoneNumber = element.getAttributeNS(ANDROID_URI, "phoneNumber");
            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                return;
            }

            // No inputType set — report the issue
            Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
            String id = idAttr != null ? idAttr.getValue() : null;

            // Check if the id gives a hint about the expected input type
            if (id != null) {
                String idLower = id.toLowerCase();
                // Strip common prefixes like @+id/ or @id/
                int slashIndex = idLower.indexOf('/');
                if (slashIndex != -1) {
                    idLower = idLower.substring(slashIndex + 1);
                }

                if (containsWord(idLower, "phone")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "This text field does not specify an `inputType`; you should generally "
                                    + "specify an `inputType` for your text fields since the field name "
                                    + "(`" + idLower + "`) suggests this is a phone number field; use "
                                    + "`inputType=\"phone\"`");
                    return;
                }

                if (containsWord(idLower, "email")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "This text field does not specify an `inputType`; you should generally "
                                    + "specify an `inputType` for your text fields since the field name "
                                    + "(`" + idLower + "`) suggests this is an e-mail address; use "
                                    + "`inputType=\"textEmailAddress\"`");
                    return;
                }

                if (containsWord(idLower, "password") || containsWord(idLower, "passwd")
                        || containsWord(idLower, "pwd")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "This text field does not specify an `inputType`; you should generally "
                                    + "specify an `inputType` for your text fields since the field name "
                                    + "(`" + idLower + "`) suggests this is a password field; use "
                                    + "`inputType=\"textPassword\"`");
                    return;
                }

                if (containsWord(idLower, "uri") || containsWord(idLower, "url")
                        || containsWord(idLower, "link") || containsWord(idLower, "website")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "This text field does not specify an `inputType`; you should generally "
                                    + "specify an `inputType` for your text fields since the field name "
                                    + "(`" + idLower + "`) suggests this is a URI field; use "
                                    + "`inputType=\"textUri\"`");
                    return;
                }

                if (containsWord(idLower, "postal") || containsWord(idLower, "zip")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "This text field does not specify an `inputType`; you should generally "
                                    + "specify an `inputType` for your text fields since the field name "
                                    + "(`" + idLower + "`) suggests this is a postal address; use "
                                    + "`inputType=\"textPostalAddress\"`");
                    return;
                }
            }

            // Also check hint attribute for clues
            String hint = element.getAttributeNS(ANDROID_URI, ATTR_HINT);
            if (hint != null && !hint.isEmpty()) {
                String hintLower = hint.toLowerCase();
                if (containsWord(hintLower, "phone")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "This text field does not specify an `inputType`; you should generally "
                                    + "specify an `inputType` for your text fields since the hint "
                                    + "(`" + hint + "`) suggests this is a phone number field; use "
                                    + "`inputType=\"phone\"`");
                    return;
                }
                if (containsWord(hintLower, "email")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "This text field does not specify an `inputType`; you should generally "
                                    + "specify an `inputType` for your text fields since the hint "
                                    + "(`" + hint + "`) suggests this is an e-mail address; use "
                                    + "`inputType=\"textEmailAddress\"`");
                    return;
                }
                if (containsWord(hintLower, "password") || containsWord(hintLower, "passwd")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "This text field does not specify an `inputType`; you should generally "
                                    + "specify an `inputType` for your text fields since the hint "
                                    + "(`" + hint + "`) suggests this is a password field; use "
                                    + "`inputType=\"textPassword\"`");
                    return;
                }
                if (containsWord(hintLower, "uri") || containsWord(hintLower, "url")
                        || containsWord(hintLower, "website")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "This text field does not specify an `inputType`; you should generally "
                                    + "specify an `inputType` for your text fields since the hint "
                                    + "(`" + hint + "`) suggests this is a URI field; use "
                                    + "`inputType=\"textUri\"`");
                    return;
                }
            }

            // Generic missing inputType warning
            context.report(ISSUE, element, context.getLocation(element),
                    "This text field does not specify an `inputType` or a `hint`");
        } else {
            // inputType is set; check if the id suggests a specific type that doesn't match
            Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
            String id = idAttr != null ? idAttr.getValue() : null;

            if (id != null) {
                String idLower = id.toLowerCase();
                int slashIndex = idLower.indexOf('/');
                if (slashIndex != -1) {
                    idLower = idLower.substring(slashIndex + 1);
                }

                String inputTypeLower = inputType.toLowerCase();

                if (containsWord(idLower, "phone")
                        && !inputTypeLower.contains("phone")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "The view's `id` (`" + idLower + "`) suggests this is a phone number, "
                                    + "but the `inputType` does not include `phone`");
                } else if (containsWord(idLower, "email")
                        && !inputTypeLower.contains("email")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "The view's `id` (`" + idLower + "`) suggests this is an e-mail address, "
                                    + "but the `inputType` does not include `textEmailAddress`");
                } else if ((containsWord(idLower, "password") || containsWord(idLower, "passwd")
                        || containsWord(idLower, "pwd"))
                        && !inputTypeLower.contains("password")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "The view's `id` (`" + idLower + "`) suggests this is a password field, "
                                    + "but the `inputType` does not include `textPassword`");
                } else if ((containsWord(idLower, "uri") || containsWord(idLower, "url")
                        || containsWord(idLower, "website"))
                        && !inputTypeLower.contains("uri")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "The view's `id` (`" + idLower + "`) suggests this is a URI field, "
                                    + "but the `inputType` does not include `textUri`");
                }
            }
        }
    }
}