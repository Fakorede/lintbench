/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;

/**
 * Detector that checks for network security configuration files that allow user certificates
 * outside of debug-overrides, which could allow eavesdroppers to intercept app data.
 */
public class NetworkSecurityConfigDetector extends ResourceXmlDetector {

    /** Issue: app accepts user certificates outside of debug-overrides */
    public static final Issue ACCEPTS_USER_CERTIFICATES = Issue.create(
            "AcceptsUserCertificates",
            "Allowing User Certificates",
            "Allowing user certificates could allow eavesdroppers to intercept data sent by " +
            "your app, which could impact the privacy of your users. Consider nesting your " +
            "app's `trust-anchors` inside a `<debug-overrides>` element to make sure they " +
            "are only available when `android:debuggable` is set to `true`.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    NetworkSecurityConfigDetector.class,
                    Scope.RESOURCE_FILE_SCOPE))
            .addMoreInfo("https://goo.gle/AcceptsUserCertificates")
            .addMoreInfo("https://developer.android.com/training/articles/security-config#TrustingDebugCa");

    private static final String TAG_TRUST_ANCHORS = "trust-anchors";
    private static final String TAG_CERTIFICATES = "certificates";
    private static final String TAG_DEBUG_OVERRIDES = "debug-overrides";
    private static final String ATTR_SRC = "src";
    private static final String VALUE_USER = "user";

    /** Constructs a new {@link NetworkSecurityConfigDetector} */
    public NetworkSecurityConfigDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_CERTIFICATES);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Check if this is a <certificates src="user"> element
        String src = element.getAttribute(ATTR_SRC);
        if (!VALUE_USER.equals(src)) {
            return;
        }

        // Check if this certificates element is inside a trust-anchors element
        Node parent = element.getParentNode();
        if (parent == null || parent.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        Element parentElement = (Element) parent;
        if (!TAG_TRUST_ANCHORS.equals(parentElement.getTagName())) {
            return;
        }

        // Check if the trust-anchors element is inside a debug-overrides element
        if (isInsideDebugOverrides(parentElement)) {
            // This is safe - user certificates are only trusted in debug mode
            return;
        }

        // Report the issue: user certificates are trusted outside of debug-overrides
        context.report(
                ACCEPTS_USER_CERTIFICATES,
                element,
                context.getLocation(element),
                "Allowing user certificates could allow eavesdroppers to intercept data " +
                "sent by your app, which could impact the privacy of your users. Consider " +
                "nesting your app's `trust-anchors` inside a `<debug-overrides>` element " +
                "to make sure they are only available when `android:debuggable` is set to `true`."
        );
    }

    /**
     * Checks whether the given element is nested inside a {@code <debug-overrides>} element.
     *
     * @param element the element to check
     * @return true if the element is inside a debug-overrides element, false otherwise
     */
    private static boolean isInsideDebugOverrides(Element element) {
        Node current = element.getParentNode();
        while (current != null) {
            if (current.getNodeType() == Node.ELEMENT_NODE) {
                Element currentElement = (Element) current;
                if (TAG_DEBUG_OVERRIDES.equals(currentElement.getTagName())) {
                    return true;
                }
            }
            current = current.getParentNode();
        }
        return false;
    }

    @Override
    public boolean appliesTo(Context context) {
        // Only apply to XML resource files - specifically network security config files
        return true;
    }
}