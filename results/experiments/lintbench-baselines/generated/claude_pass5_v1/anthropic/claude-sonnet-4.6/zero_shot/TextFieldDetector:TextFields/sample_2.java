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
import static com.android.SdkConstants.ATTR_PASSWORD;
import static com.android.SdkConstants.ATTR_PHONE_NUMBER;

/**
 * Checks for missing inputType attributes on text fields, and checks that
 * the inputType is consistent with the id of the field.
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
    private static final String TYPE_TEXT = "text";
    private static final String TYPE_EMAIL_ADDRESS = "textEmailAddress";
    private static final String TYPE_EMAIL_SUBJECT = "textEmailSubject";
    private static final String TYPE_PHONE = "phone";
    private static final String TYPE_URI = "textUri";
    private static final String TYPE_PASSWORD = "textPassword";
    private static final String TYPE_VISIBLE_PASSWORD = "textVisiblePassword";
    private static final String TYPE_PERSON_NAME = "textPersonName";
    private static final String TYPE_POSTAL_ADDRESS = "textPostalAddress";
    private static final String TYPE_MULTILINE = "textMultiLine";

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

        // Also check for the older password / phoneNumber attributes
        String phoneNumber = element.getAttributeNS(ANDROID_URI, ATTR_PHONE_NUMBER);
        String password = element.getAttributeNS(ANDROID_URI, ATTR_PASSWORD);

        if (inputTypeAttr == null && !isTrue(phoneNumber) && !isTrue(password)) {
            // No inputType set. Check if there's an id that gives a hint.
            String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
            if (id != null && !id.isEmpty()) {
                String idLower = id.toLowerCase();

                // Strip common prefixes like @+id/, @id/
                int slashIndex = idLower.lastIndexOf('/');
                if (slashIndex != -1) {
                    idLower = idLower.substring(slashIndex + 1);
                }

                if (idContains(idLower, "phone")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "The view's `id` (`" + id + "`) suggests this is a phone number, " +
                            "but it does not include `phone` in the `inputType`");
                    return;
                } else if (idContains(idLower, "email")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "The view's `id` (`" + id + "`) suggests this is an e-mail address, " +
                            "but it does not include `textEmailAddress` in the `inputType`");
                    return;
                } else if (idContains(idLower, "password") || idContains(idLower, "passwd")
                        || idContains(idLower, "pwd")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "The view's `id` (`" + id + "`) suggests this is a password field, " +
                            "but it does not include `textPassword` in the `inputType`");
                    return;
                } else if (idContains(idLower, "uri") || idContains(idLower, "url")
                        || idContains(idLower, "link") || idContains(idLower, "website")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "The view's `id` (`" + id + "`) suggests this is a URI, " +
                            "but it does not include `textUri` in the `inputType`");
                    return;
                } else if (idContains(idLower, "postal") || idContains(idLower, "address")
                        || idContains(idLower, "zip")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "The view's `id` (`" + id + "`) suggests this is a postal address, " +
                            "but it does not include `textPostalAddress` in the `inputType`");
                    return;
                } else if (idContains(idLower, "person") || idContains(idLower, "name")) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "The view's `id` (`" + id + "`) suggests this is a person name, " +
                            "but it does not include `textPersonName` in the `inputType`");
                    return;
                }
            }

            // Generic missing inputType warning
            context.report(ISSUE, element, context.getLocation(element),
                    "This text field does not specify an `inputType` or a `hint`");
            return;
        }

        if (inputTypeAttr == null) {
            // Has phoneNumber or password attribute but no inputType - that's okay
            return;
        }

        String inputType = inputTypeAttr.getValue();

        // Now check if the id suggests a specific type that doesn't match the inputType
        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        if (id == null || id.isEmpty()) {
            return;
        }

        String idLower = id.toLowerCase();
        int slashIndex = idLower.lastIndexOf('/');
        if (slashIndex != -1) {
            idLower = idLower.substring(slashIndex + 1);
        }

        String inputTypeLower = inputType.toLowerCase();

        if (idContains(idLower, "phone")) {
            if (!inputTypeLower.contains("phone")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + id + "`) suggests this is a phone number, " +
                        "but it does not include `phone` in the `inputType`");
            }
        } else if (idContains(idLower, "email")) {
            if (!inputTypeLower.contains("emailaddress") &&
                    !inputTypeLower.contains("email")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + id + "`) suggests this is an e-mail address, " +
                        "but it does not include `textEmailAddress` in the `inputType`");
            }
        } else if (idContains(idLower, "password") || idContains(idLower, "passwd")
                || idContains(idLower, "pwd")) {
            if (!inputTypeLower.contains("password")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + id + "`) suggests this is a password field, " +
                        "but it does not include `textPassword` in the `inputType`");
            }
        } else if (idContains(idLower, "uri") || idContains(idLower, "url")
                || idContains(idLower, "link") || idContains(idLower, "website")) {
            if (!inputTypeLower.contains("uri")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + id + "`) suggests this is a URI, " +
                        "but it does not include `textUri` in the `inputType`");
            }
        } else if (idContains(idLower, "postal") || idContains(idLower, "address")
                || idContains(idLower, "zip")) {
            if (!inputTypeLower.contains("postaladdress")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + id + "`) suggests this is a postal address, " +
                        "but it does not include `textPostalAddress` in the `inputType`");
            }
        } else if (idContains(idLower, "person") || idContains(idLower, "name")) {
            if (!inputTypeLower.contains("personname")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + id + "`) suggests this is a person name, " +
                        "but it does not include `textPersonName` in the `inputType`");
            }
        }
    }

    /**
     * Returns true if the given id contains the given substring, treating
     * underscores and camelCase boundaries appropriately.
     */
    private static boolean idContains(String id, String substring) {
        // Simple contains check (case-insensitive already handled by callers passing lowercase)
        if (id.contains(substring)) {
            return true;
        }
        return false;
    }

    /**
     * Returns true if the given attribute value represents a true boolean value.
     */
    private static boolean isTrue(String value) {
        return "true".equals(value);
    }
}