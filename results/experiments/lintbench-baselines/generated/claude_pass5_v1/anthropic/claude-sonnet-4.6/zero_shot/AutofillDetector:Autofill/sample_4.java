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
import com.android.tools.lint.detector.api.LintFix;
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
 * Checks for missing autofillHints attribute on views that could be autofillable.
 */
public class AutofillDetector extends LayoutDetector {

    private static final String ATTR_AUTOFILL_HINTS = "autofillHints";
    private static final String ATTR_IMPORTANT_FOR_AUTOFILL = "importantForAutofill";

    private static final String VALUE_NO = "no";
    private static final String VALUE_NO_EXCLUDE_DESCENDANTS = "noExcludeDescendants";

    public static final Issue ISSUE = Issue.create(
            "Autofill",
            "Use Autofill",
            "Specify an `autofillHints` attribute when targeting SDK version 26 or higher or " +
            "explicitly specify that the view is not important for autofill. " +
            "Your app can help an autofill service classify the data correctly by providing " +
            "the meaning of each view that could be autofillable, such as views representing " +
            "usernames, passwords, credit card fields, email addresses, etc.\n" +
            "\n" +
            "The hints can have any value, but it is recommended to use predefined values " +
            "like 'username' for a username or 'creditCardNumber' for a credit card number. " +
            "For a list of all predefined autofill hint constants, see the `AUTOFILL_HINT_` " +
            "constants in the `View` reference at " +
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
        // Only check if targeting SDK 26 or higher
        if (context.getMainProject().getTargetSdk() < 26) {
            return;
        }

        // Check if the element itself has autofillHints
        if (element.hasAttributeNS(ANDROID_URI, ATTR_AUTOFILL_HINTS)) {
            return;
        }

        // Check if the element itself is marked as not important for autofill
        if (isMarkedNotImportantForAutofill(element)) {
            return;
        }

        // Check if any ancestor is marked as not important for autofill
        // (noExcludeDescendants would exclude this view)
        if (hasAncestorMarkedNotImportantForAutofill(element)) {
            return;
        }

        // Check if inputType is set (indicates it's an input field that should have autofill hints)
        // We report the issue on the element
        String message = "Missing `autofillHints` attribute";

        // Check if there's an inputType attribute to make the warning more targeted
        Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);

        LintFix fix = LintFix.create()
                .alternatives(
                        LintFix.create()
                                .set(ANDROID_URI, ATTR_AUTOFILL_HINTS, "")
                                .caretEnd()
                                .name("Set autofillHints")
                                .build(),
                        LintFix.create()
                                .set(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL, "no")
                                .name("Set importantForAutofill=\"no\"")
                                .build()
                );

        if (inputTypeAttr != null) {
            context.report(ISSUE, inputTypeAttr, context.getLocation(inputTypeAttr), message, fix);
        } else {
            context.report(ISSUE, element, context.getLocation(element), message, fix);
        }
    }

    /**
     * Checks whether the given element has importantForAutofill set to "no" or
     * "noExcludeDescendants".
     */
    private static boolean isMarkedNotImportantForAutofill(@NonNull Element element) {
        if (!element.hasAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL)) {
            return false;
        }
        String value = element.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
        return VALUE_NO.equals(value) || VALUE_NO_EXCLUDE_DESCENDANTS.equals(value);
    }

    /**
     * Checks whether any ancestor element has importantForAutofill set to "noExcludeDescendants".
     */
    private static boolean hasAncestorMarkedNotImportantForAutofill(@NonNull Element element) {
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            if (parentElement.hasAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL)) {
                String value = parentElement.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
                if (VALUE_NO_EXCLUDE_DESCENDANTS.equals(value)) {
                    return true;
                }
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}