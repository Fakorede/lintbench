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
import static com.android.SdkConstants.EDIT_TEXT;

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
            "to the user (such as just digits and parentheses for a phone number). \n" +
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
            // Check if there's an id that hints at the purpose
            Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
            String idValue = idAttr != null ? idAttr.getValue().toLowerCase() : "";

            String message = "This text field does not specify an `inputType` or a `hint`";

            if (idValue.contains("phone")) {
                message = "This text field does not specify an `inputType` or a `hint`";
                context.report(ISSUE, element, context.getLocation(element), message);
            } else if (idValue.contains("email")) {
                message = "This text field does not specify an `inputType` or a `hint`";
                context.report(ISSUE, element, context.getLocation(element), message);
            } else if (idValue.contains("password") || idValue.contains("passwd")) {
                message = "This text field does not specify an `inputType` or a `hint`";
                context.report(ISSUE, element, context.getLocation(element), message);
            } else {
                context.report(ISSUE, element, context.getLocation(element),
                        "This text field does not specify an `inputType` or a `hint`");
            }
            return;
        }

        // inputType is specified; check if it's consistent with the id
        String inputType = inputTypeAttr.getValue().toLowerCase();

        Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
        if (idAttr == null) {
            return;
        }

        String id = idAttr.getValue().toLowerCase();

        // Check phone
        if (id.contains("phone") && !inputType.contains("phone")) {
            context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                    "The view's `id` (`" + idAttr.getValue() + "`) suggests this is a phone " +
                    "number, but the `inputType` does not include `phone`");
        }

        // Check email
        if ((id.contains("email") || id.contains("e-mail")) &&
                !inputType.contains("textEmailAddress")) {
            context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                    "The view's `id` (`" + idAttr.getValue() + "`) suggests this is an e-mail " +
                    "address, but the `inputType` does not include `textEmailAddress`");
        }

        // Check password
        if ((id.contains("password") || id.contains("passwd")) &&
                !inputType.contains("textPassword") &&
                !inputType.contains("numberPassword")) {
            context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                    "The view's `id` (`" + idAttr.getValue() + "`) suggests this is a password " +
                    "field, but the `inputType` does not include `textPassword`");
        }

        // Check URI / URL
        if ((id.contains("uri") || id.contains("url")) &&
                !inputType.contains("textUri")) {
            context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                    "The view's `id` (`" + idAttr.getValue() + "`) suggests this is a URI, " +
                    "but the `inputType` does not include `textUri`");
        }

        // Check postal address
        if ((id.contains("postal") || id.contains("address")) &&
                !inputType.contains("textPostalAddress")) {
            context.report(ISSUE, element, context.getLocation(inputTypeAttr),
                    "The view's `id` (`" + idAttr.getValue() + "`) suggests this is a postal " +
                    "address, but the `inputType` does not include `textPostalAddress`");
        }
    }
}