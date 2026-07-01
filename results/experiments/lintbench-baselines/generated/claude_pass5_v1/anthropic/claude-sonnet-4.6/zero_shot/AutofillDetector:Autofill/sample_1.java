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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL;
import static com.android.SdkConstants.AUTO_COMPLETE_TEXT_VIEW;
import static com.android.SdkConstants.EDIT_TEXT;
import static com.android.SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW;

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
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Checks for missing autofillHints attribute on views that could be autofillable.
 */
public class AutofillDetector extends LayoutDetector {

    private static final String ATTR_AUTOFILL_HINTS = "autofillHints";
    private static final String ATTR_INPUT_TYPE = "inputType";

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "Autofill",
            "Use Autofill",
            "Specify an `autofillHints` attribute when targeting SDK version 26 or higher or " +
            "explicitly specify that the view is not important for autofill. " +
            "Your app can help an autofill service classify the data correctly by " +
            "providing the meaning of each view that could be autofillable, such as " +
            "views representing usernames, passwords, credit card fields, email " +
            "addresses, etc.\n" +
            "\n" +
            "The hints can have any value, but it is recommended to use predefined " +
            "values like 'username' for a username or 'creditCardNumber' for a credit " +
            "card number. For a list of all predefined autofill hint constants, see the " +
            "`AUTOFILL_HINT_` constants in the `View` reference at " +
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
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                EDIT_TEXT,
                AUTO_COMPLETE_TEXT_VIEW,
                MULTI_AUTO_COMPLETE_TEXT_VIEW
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Only check when targeting API 26+
        if (context.getMainProject().getTargetSdk() < 26) {
            return;
        }

        // Check if autofillHints is set on the element itself
        if (element.hasAttributeNS(ANDROID_URI, ATTR_AUTOFILL_HINTS)) {
            return;
        }

        // Check if importantForAutofill is set on the element or any ancestor
        if (hasImportantForAutofillSet(element)) {
            return;
        }

        // Report the issue
        String message = "Missing `autofillHints` attribute. Consider adding `android:autofillHints` " +
                "or marking it as `importantForAutofill=\"no\"` to suppress this warning";

        LintFix fix = LintFix.create()
                .set(ANDROID_URI, ATTR_AUTOFILL_HINTS, "")
                .build();

        Attr inputTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        context.report(
                ISSUE,
                element,
                context.getLocation(inputTypeAttr != null ? (Node) inputTypeAttr : (Node) element),
                message,
                fix);
    }

    /**
     * Checks whether the element or any of its ancestors has importantForAutofill set
     * to a value that means autofill is not needed (no, noExcludeDescendants).
     */
    private static boolean hasImportantForAutofillSet(@NonNull Element element) {
        Node current = element;
        while (current != null && current.getNodeType() == Node.ELEMENT_NODE) {
            Element el = (Element) current;
            if (el.hasAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL)) {
                String value = el.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
                // If the value indicates the view (or its descendants) are not important
                // for autofill, suppress the warning
                if ("no".equals(value)
                        || "noExcludeDescendants".equals(value)) {
                    return true;
                }
                // If it's set on the element itself (even to "yes" or "auto"), we only
                // care about parent values of "noExcludeDescendants"
                if (current == element) {
                    // The element itself has the attribute set, but not to "no" or
                    // "noExcludeDescendants", so continue checking parents
                } else {
                    // For ancestors, "noExcludeDescendants" already handled above
                    // Other values on ancestors don't suppress the warning for descendants
                }
            }
            current = current.getParentNode();
        }
        return false;
    }
}