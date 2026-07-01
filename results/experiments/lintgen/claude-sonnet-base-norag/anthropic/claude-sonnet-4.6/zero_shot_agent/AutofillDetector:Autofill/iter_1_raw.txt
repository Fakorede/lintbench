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
 * Checks that views that accept text input specify autofill hints or are marked as not important
 * for autofill when targeting SDK 26+.
 */
public class AutofillDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE =
            Issue.create(
                    "Autofill",
                    "Use Autofill",
                    "Specify an `autofillHints` attribute when targeting SDK version 26 or higher "
                            + "or explicitly specify that the view is not important for autofill. "
                            + "Your app can help an autofill service classify the data correctly by "
                            + "providing the meaning of each view that could be autofillable, such as "
                            + "views representing usernames, passwords, credit card fields, email "
                            + "addresses, etc.\n"
                            + "\n"
                            + "The hints can have any value, but it is recommended to use predefined "
                            + "values like 'username' for a username or 'creditCardNumber' for a credit "
                            + "card number. For a list of all predefined autofill hint constants, see the "
                            + "`AUTOFILL_HINT_` constants in the `View` reference at "
                            + "https://developer.android.com/reference/android/view/View.html.\n"
                            + "\n"
                            + "You can mark a view unimportant for autofill by specifying an "
                            + "`importantForAutofill` attribute on that view or a parent view. See "
                            + "https://developer.android.com/reference/android/view/View.html"
                            + "#setImportantForAutofill(int).",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE))
                    .addMoreInfo("https://developer.android.com/guide/topics/text/autofill.html");

    private static final String EDIT_TEXT = "EditText";
    private static final String AUTO_COMPLETE_TEXT_VIEW = "AutoCompleteTextView";
    private static final String MULTI_AUTO_COMPLETE_TEXT_VIEW = "MultiAutoCompleteTextView";
    private static final String TEXT_INPUT_EDIT_TEXT = "TextInputEditText";

    // Values for importantForAutofill that indicate the view is NOT important for autofill
    private static final String VALUE_NO = "no";
    private static final String VALUE_NO_EXCLUDE_DESCENDANTS = "noExcludeDescendants";
    // yesExcludeDescendants means the view itself is important but its children are not
    private static final String VALUE_YES_EXCLUDE_DESCENDANTS = "yesExcludeDescendants";

    public AutofillDetector() {}

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                EDIT_TEXT,
                AUTO_COMPLETE_TEXT_VIEW,
                MULTI_AUTO_COMPLETE_TEXT_VIEW,
                TEXT_INPUT_EDIT_TEXT
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Only check when targeting SDK 26+
        if (context.getMainProject().getTargetSdk() < 26) {
            return;
        }

        // Check if autofillHints is specified on this element
        if (element.hasAttributeNS(ANDROID_URI, ATTR_AUTOFILL_HINTS)) {
            return;
        }

        // Check if importantForAutofill is set to "no" or "noExcludeDescendants" on this element
        // Note: "yes" means it IS important for autofill, so we should still warn
        if (isMarkedNotImportantForAutofill(element)) {
            return;
        }

        // Check parent elements for importantForAutofill that excludes descendants
        if (isAncestorMarkedNotImportantForAutofill(element)) {
            return;
        }

        // Report the issue
        LintFix fix = LintFix.create()
                .group()
                .add(LintFix.create()
                        .set(ANDROID_URI, ATTR_AUTOFILL_HINTS, "")
                        .build())
                .add(LintFix.create()
                        .set(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL, "no")
                        .build())
                .build();

        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `autofillHints` attribute. Consider adding `android:autofillHints` "
                        + "or mark it as `importantForAutofill=\"no\"`",
                fix);
    }

    /**
     * Returns true if the element itself is marked as not important for autofill.
     * "no" and "noExcludeDescendants" mean the view is not important for autofill.
     * "yes" and "yesExcludeDescendants" mean the view IS important for autofill (still warn).
     */
    private static boolean isMarkedNotImportantForAutofill(@NonNull Element element) {
        Attr attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
        if (attr == null) {
            return false;
        }
        String value = attr.getValue();
        return VALUE_NO.equals(value) || VALUE_NO_EXCLUDE_DESCENDANTS.equals(value);
    }

    /**
     * Returns true if any ancestor element is marked with importantForAutofill in a way
     * that excludes descendants from autofill:
     * - "noExcludeDescendants": parent is not important and neither are descendants
     * - "yesExcludeDescendants": parent is important but descendants are excluded
     *
     * Note: "no" on a parent only applies to that parent view, not its descendants,
     * so we do NOT include "no" here.
     */
    private static boolean isAncestorMarkedNotImportantForAutofill(@NonNull Element element) {
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            Attr attr = parentElement.getAttributeNodeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
            if (attr != null) {
                String value = attr.getValue();
                if (VALUE_NO_EXCLUDE_DESCENDANTS.equals(value)
                        || VALUE_YES_EXCLUDE_DESCENDANTS.equals(value)) {
                    return true;
                }
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}