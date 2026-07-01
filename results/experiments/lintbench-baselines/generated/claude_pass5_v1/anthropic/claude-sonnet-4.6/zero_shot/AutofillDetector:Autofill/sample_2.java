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
import static com.android.SdkConstants.ATTR_AUTOFILL_HINTS;
import static com.android.SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.VALUE_NONE;

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

/**
 * Checks that views that are likely autofillable have autofillHints set,
 * or are explicitly marked as not important for autofill.
 */
public class AutofillDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
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

    private static final String ATTR_IMPORTANT_FOR_AUTOFILL_VALUE_NO = "no";
    private static final String ATTR_IMPORTANT_FOR_AUTOFILL_VALUE_NO_EXCLUDE_DESCENDANTS =
            "noExcludeDescendants";
    private static final String ATTR_IMPORTANT_FOR_AUTOFILL_VALUE_YES = "yes";
    private static final String ATTR_IMPORTANT_FOR_AUTOFILL_VALUE_YES_EXCLUDE_DESCENDANTS =
            "yesExcludeDescendants";
    private static final String ATTR_IMPORTANT_FOR_AUTOFILL_VALUE_AUTO = "auto";

    private static final String EDIT_TEXT = "EditText";
    private static final String AUTO_COMPLETE_TEXT_VIEW = "AutoCompleteTextView";
    private static final String MULTI_AUTO_COMPLETE_TEXT_VIEW = "MultiAutoCompleteTextView";
    private static final String TEXT_INPUT_EDIT_TEXT = "TextInputEditText";
    private static final String APP_COMPAT_EDIT_TEXT = "AppCompatEditText";

    /** Constructs a new {@link AutofillDetector} */
    public AutofillDetector() {
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                EDIT_TEXT,
                AUTO_COMPLETE_TEXT_VIEW,
                MULTI_AUTO_COMPLETE_TEXT_VIEW,
                TEXT_INPUT_EDIT_TEXT,
                APP_COMPAT_EDIT_TEXT
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Only check when targeting API 26+
        if (context.getMainProject().getTargetSdk() < 26) {
            return;
        }

        // Check if the element itself has autofillHints set
        if (element.hasAttributeNS(ANDROID_URI, ATTR_AUTOFILL_HINTS)) {
            return;
        }

        // Check if the element itself or any ancestor has importantForAutofill set to "no" or
        // "noExcludeDescendants" or similar values that would suppress autofill
        if (isMarkedNotImportantForAutofill(element)) {
            return;
        }

        // Report the issue
        String message = "Missing `autofillHints` attribute. Consider adding `android:autofillHints` " +
                "or mark it as `importantForAutofill=\"no\"`";

        LintFix fix = fix().alternatives(
                fix().set(ANDROID_URI, ATTR_AUTOFILL_HINTS, "")
                        .caretEnd()
                        .build(),
                fix().set(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL, "no")
                        .build()
        );

        context.report(ISSUE, element, context.getElementLocation(element), message, fix);
    }

    /**
     * Checks whether the given element or any of its ancestors has been marked as
     * not important for autofill.
     */
    private static boolean isMarkedNotImportantForAutofill(@NonNull Element element) {
        Node current = element;
        while (current != null && current.getNodeType() == Node.ELEMENT_NODE) {
            Element currentElement = (Element) current;
            if (currentElement.hasAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL)) {
                String value = currentElement.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
                // Strip namespace prefix if present (e.g. "android:no" -> "no")
                if (value.contains(":")) {
                    value = value.substring(value.lastIndexOf(':') + 1);
                }
                if (ATTR_IMPORTANT_FOR_AUTOFILL_VALUE_NO.equals(value) ||
                        ATTR_IMPORTANT_FOR_AUTOFILL_VALUE_NO_EXCLUDE_DESCENDANTS.equals(value)) {
                    return true;
                }
                // If the current element (not a parent) is explicitly marked yes/auto,
                // stop searching ancestors — the element IS important for autofill
                if (current == element) {
                    if (ATTR_IMPORTANT_FOR_AUTOFILL_VALUE_YES.equals(value) ||
                            ATTR_IMPORTANT_FOR_AUTOFILL_VALUE_AUTO.equals(value)) {
                        return false;
                    }
                } else {
                    // For ancestors, yesExcludeDescendants means descendants are NOT important
                    if (ATTR_IMPORTANT_FOR_AUTOFILL_VALUE_YES_EXCLUDE_DESCENDANTS.equals(value)) {
                        return true;
                    }
                }
            }
            current = current.getParentNode();
        }
        return false;
    }
}