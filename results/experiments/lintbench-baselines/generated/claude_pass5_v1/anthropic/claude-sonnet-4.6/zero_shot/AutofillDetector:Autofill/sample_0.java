/*
 * Copyright (C) 2017 The Android Open Source Project
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
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.AUTO_COMPLETE_TEXT_VIEW;
import static com.android.SdkConstants.EDIT_TEXT;
import static com.android.SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW;

/**
 * Checks for views that should specify autofillHints or be marked as not important for autofill.
 */
public class AutofillDetector extends LayoutDetector {

    private static final String ATTR_AUTOFILL_HINTS = "autofillHints";
    private static final String ATTR_IMPORTANT_FOR_AUTOFILL = "importantForAutofill";

    // Values of importantForAutofill that mean "not important"
    private static final String VALUE_NO = "no";
    private static final String VALUE_NO_EXCLUDE_DESCENDANTS = "noExcludeDescendants";

    public static final Issue ISSUE = Issue.create(
            "Autofill",
            "Use Autofill",
            "Specify an `autofillHints` attribute when targeting SDK version 26 or higher or " +
            "explicitly specify that the view is not important for autofill. Your app can help " +
            "an autofill service classify the data correctly by providing the meaning of each " +
            "view that could be autofillable, such as views representing usernames, passwords, " +
            "credit card fields, email addresses, etc.\n" +
            "\n" +
            "The hints can have any value, but it is recommended to use predefined values like " +
            "'username' for a username or 'creditCardNumber' for a credit card number. For a " +
            "list of all predefined autofill hint constants, see the `AUTOFILL_HINT_` constants " +
            "in the `View` reference at " +
            "https://developer.android.com/reference/android/view/View.html.\n" +
            "\n" +
            "You can mark a view unimportant for autofill by specifying an " +
            "`importantForAutofill` attribute on that view or a parent view. See " +
            "https://developer.android.com/reference/android/view/View.html#setImportantForAutofill(int).",
            Category.USABILITY,
            3,
            Severity.WARNING,
            new Implementation(
                    AutofillDetector.class,
                    Scope.RESOURCE_FILE_SCOPE))
            .addMoreInfo("https://developer.android.com/guide/topics/text/autofill.html");

    /** Constructs a new {@link AutofillDetector} */
    public AutofillDetector() {
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                EDIT_TEXT,
                AUTO_COMPLETE_TEXT_VIEW,
                MULTI_AUTO_COMPLETE_TEXT_VIEW
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Only flag issues when targeting SDK 26 or higher
        if (context.getMainProject().getTargetSdk() < 26) {
            return;
        }

        // Check if the element itself has autofillHints
        if (element.hasAttributeNS(ANDROID_URI, ATTR_AUTOFILL_HINTS)) {
            return;
        }

        // Check if the element itself or any ancestor has importantForAutofill set to "no"
        // or "noExcludeDescendants"
        if (isNotImportantForAutofill(element)) {
            return;
        }

        // Check if the inputType is set to something that doesn't need autofill
        // (e.g., inputType="none" or no inputType at all suggesting it's not editable)
        // For EditText views without inputType or with inputType that suggests data entry,
        // we should flag them.
        Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        if (inputTypeAttr != null) {
            String inputType = inputTypeAttr.getValue();
            // If inputType is "none", it's not really an input field
            if ("none".equals(inputType)) {
                return;
            }
        }

        // Report the issue
        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `autofillHints` attribute. Consider adding `android:autofillHints` " +
                "or mark the view as `android:importantForAutofill=\"no\"`.");
    }

    /**
     * Checks whether the given element or any of its ancestors has the
     * {@code importantForAutofill} attribute set to a value that marks it as
     * not important for autofill.
     */
    private static boolean isNotImportantForAutofill(@NonNull Element element) {
        Node current = element;
        while (current != null && current.getNodeType() == Node.ELEMENT_NODE) {
            Element currentElement = (Element) current;
            if (currentElement.hasAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL)) {
                String value = currentElement.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
                // Strip any resource reference prefix if present
                if (value.startsWith("@") || value.startsWith("?")) {
                    // Can't statically determine the value; assume it might be important
                } else {
                    if (VALUE_NO.equals(value) || VALUE_NO_EXCLUDE_DESCENDANTS.equals(value)) {
                        return true;
                    }
                    // If it's set on the element itself (not an ancestor), only "no" counts.
                    // If it's set on an ancestor, "noExcludeDescendants" also counts (already handled above).
                    // "yes" or "yesExcludeDescendants" or "auto" on current element means important.
                    if (current == element) {
                        // Already checked above; any other value means it IS important for autofill
                        return false;
                    }
                }
            }
            current = current.getParentNode();
        }
        return false;
    }
}