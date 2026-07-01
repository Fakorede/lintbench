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
import static com.android.SdkConstants.EDIT_TEXT;

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

import java.util.Collection;
import java.util.Collections;

/**
 * Checks for missing inputType attributes on text fields, and validates
 * that the inputType is consistent with the field's id hint.
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
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(EDIT_TEXT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check for inputType attribute
        Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);

        // Also check for the older password/phoneNumber attributes
        boolean hasPhoneNumber = element.hasAttributeNS(ANDROID_URI, ATTR_PHONE_NUMBER);
        boolean hasPassword = element.hasAttributeNS(ANDROID_URI, ATTR_PASSWORD);

        if (inputTypeAttr == null && !hasPhoneNumber && !hasPassword) {
            // No inputType set - report the issue
            // But first check if there's an id that hints at the type
            Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
            String idValue = idAttr != null ? idAttr.getValue().toLowerCase() : "";
            // Strip @+id/ or @id/ prefix
            int slashIndex = idValue.lastIndexOf('/');
            if (slashIndex >= 0) {
                idValue = idValue.substring(slashIndex + 1);
            }

            String message = "This text field does not specify an `inputType` or a `hint`";

            if (idValue.contains("phone")) {
                message = "This text field does not specify an `inputType` of `phone`";
            } else if (idValue.contains("email")) {
                message = "This text field does not specify an `inputType` of `textEmailAddress`";
            } else if (idValue.contains("password") || idValue.contains("passwd")
                    || idValue.contains("pwd")) {
                message = "This text field does not specify an `inputType` of `textPassword`";
            } else if (idValue.contains("uri") || idValue.contains("url")
                    || idValue.contains("link") || idValue.contains("website")) {
                message = "This text field does not specify an `inputType` of `textUri`";
            } else if (idValue.contains("postal") || idValue.contains("zip")) {
                message = "This text field does not specify an `inputType` of `textPostalAddress`";
            } else if (idValue.contains("person") || idValue.contains("name")) {
                message = "This text field does not specify an `inputType` of `textPersonName`";
            } else {
                message = "This text field does not specify an `inputType`";
            }

            context.report(ISSUE, element, context.getLocation(element), message);
            return;
        }

        if (inputTypeAttr == null) {
            return;
        }

        // We have an inputType - check if it's consistent with the id hint
        String inputType = inputTypeAttr.getValue().toLowerCase();

        // Get the id
        Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
        if (idAttr == null) {
            return;
        }
        String idValue = idAttr.getValue().toLowerCase();
        // Strip @+id/ or @id/ prefix
        int slashIndex = idValue.lastIndexOf('/');
        if (slashIndex >= 0) {
            idValue = idValue.substring(slashIndex + 1);
        }

        if (idValue.contains("phone")) {
            if (!inputType.contains("phone")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + idAttr.getValue() + "`) suggests this is a phone "
                        + "number, but the `inputType` does not include `phone`");
            }
        } else if (idValue.contains("email")) {
            if (!inputType.contains("textemailaddress") && !inputType.contains("textemail")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + idAttr.getValue() + "`) suggests this is an email "
                        + "address, but the `inputType` does not include `textEmailAddress`");
            }
        } else if (idValue.contains("password") || idValue.contains("passwd")
                || idValue.contains("pwd")) {
            if (!inputType.contains("textpassword") && !inputType.contains("numberpassword")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + idAttr.getValue() + "`) suggests this is a "
                        + "password field, but the `inputType` does not include `textPassword`");
            }
        } else if (idValue.contains("uri") || idValue.contains("url")
                || idValue.contains("link") || idValue.contains("website")) {
            if (!inputType.contains("texturi")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + idAttr.getValue() + "`) suggests this is a URI, "
                        + "but the `inputType` does not include `textUri`");
            }
        } else if (idValue.contains("postal") || idValue.contains("zip")) {
            if (!inputType.contains("textpostaladdress")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + idAttr.getValue() + "`) suggests this is a postal "
                        + "address, but the `inputType` does not include `textPostalAddress`");
            }
        } else if (idValue.contains("person") || idValue.contains("name")) {
            if (!inputType.contains("textpersonname")) {
                context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr),
                        "The view's `id` (`" + idAttr.getValue() + "`) suggests this is a person "
                        + "name, but the `inputType` does not include `textPersonName`");
            }
        }
    }
}