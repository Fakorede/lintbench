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

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(EDIT_TEXT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String inputType = element.getAttributeNS(ANDROID_URI, ATTR_INPUT_TYPE);

        // Also check for the older textPassword / phoneNumber etc. attributes
        // (pre-inputType)
        if (inputType.isEmpty()) {
            // Check for legacy password attribute
            String password = element.getAttributeNS(ANDROID_URI, ATTR_PASSWORD);
            if (password != null && !password.isEmpty()) {
                // Has legacy password attribute; not flagging this
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

                if (idLower.contains("phone")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "This text field does not specify an `inputType`; you should generally "
                                    + "specify an `inputType` for your text fields since the field name "
                                    + "(`" + idLower + "`) suggests this is a phone number field; use "
                                    + "`inputType=\"phone\"`");
                    return;
                }

                if (idLower.contains("email")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "This text field does not specify an `inputType`; you should generally "
                                    + "specify an `inputType` for your text fields since the field name "
                                    + "(`" + idLower + "`) suggests this is an e-mail address; use "
                                    + "`inputType=\"textEmailAddress\"`");
                    return;
                }

                if (idLower.contains("password") || idLower.contains("passwd")
                        || idLower.contains("pwd")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "This text field does not specify an `inputType`; you should generally "
                                    + "specify an `inputType` for your text fields since the field name "
                                    + "(`" + idLower + "`) suggests this is a password field; use "
                                    + "`inputType=\"textPassword\"`");
                    return;
                }

                if (idLower.contains("uri") || idLower.contains("url")
                        || idLower.contains("link") || idLower.contains("website")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "This text field does not specify an `inputType`; you should generally "
                                    + "specify an `inputType` for your text fields since the field name "
                                    + "(`" + idLower + "`) suggests this is a URI field; use "
                                    + "`inputType=\"textUri\"`");
                    return;
                }

                if (idLower.contains("postal") || idLower.contains("zip")) {
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
                if (hintLower.contains("phone")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "This text field does not specify an `inputType`; you should generally "
                                    + "specify an `inputType` for your text fields since the hint "
                                    + "(`" + hint + "`) suggests this is a phone number field; use "
                                    + "`inputType=\"phone\"`");
                    return;
                }
                if (hintLower.contains("email")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "This text field does not specify an `inputType`; you should generally "
                                    + "specify an `inputType` for your text fields since the hint "
                                    + "(`" + hint + "`) suggests this is an e-mail address; use "
                                    + "`inputType=\"textEmailAddress\"`");
                    return;
                }
                if (hintLower.contains("password") || hintLower.contains("passwd")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "This text field does not specify an `inputType`; you should generally "
                                    + "specify an `inputType` for your text fields since the hint "
                                    + "(`" + hint + "`) suggests this is a password field; use "
                                    + "`inputType=\"textPassword\"`");
                    return;
                }
                if (hintLower.contains("uri") || hintLower.contains("url")
                        || hintLower.contains("website")) {
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

                if (idLower.contains("phone")
                        && !inputTypeLower.contains("phone")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "The view's `id` (`" + idLower + "`) suggests this is a phone number, "
                                    + "but the `inputType` does not include `phone`");
                } else if (idLower.contains("email")
                        && !inputTypeLower.contains("email")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "The view's `id` (`" + idLower + "`) suggests this is an e-mail address, "
                                    + "but the `inputType` does not include `textEmailAddress`");
                } else if ((idLower.contains("password") || idLower.contains("passwd")
                        || idLower.contains("pwd"))
                        && !inputTypeLower.contains("password")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "The view's `id` (`" + idLower + "`) suggests this is a password field, "
                                    + "but the `inputType` does not include `textPassword`");
                } else if ((idLower.contains("uri") || idLower.contains("url")
                        || idLower.contains("website"))
                        && !inputTypeLower.contains("uri")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "The view's `id` (`" + idLower + "`) suggests this is a URI field, "
                                    + "but the `inputType` does not include `textUri`");
                }
            }
        }
    }
}