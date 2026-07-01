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
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

/**
 * Detector that checks for user certificates being trusted in network security config files.
 *
 * <p>Allowing user certificates could allow eavesdroppers to intercept data sent by your app.
 * Trust anchors with user certificates should be nested inside {@code <debug-overrides>} so they
 * are only active when {@code android:debuggable} is {@code true}.
 */
public class NetworkSecurityConfigDetector extends Detector implements XmlScanner {

    /** The main issue reported by this detector. */
    public static final Issue ACCEPTS_USER_CERTIFICATES =
            Issue.create(
                    "AcceptsUserCertificates",
                    "Allowing User Certificates",
                    "Allowing user certificates could allow eavesdroppers to intercept data sent "
                            + "by your app, which could impact the privacy of your users. "
                            + "Consider nesting your app's `trust-anchors` inside a "
                            + "`<debug-overrides>` element to make sure they are only available "
                            + "when `android:debuggable` is set to `true`.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE))
                    .addMoreInfo("https://goo.gle/AcceptsUserCertificates")
                    .addMoreInfo(
                            "https://developer.android.com/training/articles/security-config"
                                    + "#TrustingDebugCa");

    private static final String TAG_NETWORK_SECURITY_CONFIG = "network-security-config";
    private static final String TAG_BASE_CONFIG = "base-config";
    private static final String TAG_DOMAIN_CONFIG = "domain-config";
    private static final String TAG_DEBUG_OVERRIDES = "debug-overrides";
    private static final String TAG_TRUST_ANCHORS = "trust-anchors";
    private static final String TAG_CERTIFICATES = "certificates";
    private static final String ATTR_SRC = "src";
    private static final String VALUE_USER = "user";

    /** Constructs a new {@link NetworkSecurityConfigDetector}. */
    public NetworkSecurityConfigDetector() {}

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_NETWORK_SECURITY_CONFIG);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Walk through all children of <network-security-config>
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String tagName = childElement.getTagName();

            if (TAG_DEBUG_OVERRIDES.equals(tagName)) {
                // Trust anchors inside debug-overrides are intentionally allowed; skip.
                continue;
            }

            if (TAG_BASE_CONFIG.equals(tagName) || TAG_DOMAIN_CONFIG.equals(tagName)) {
                checkForUserCertificates(context, childElement);
            }
        }
    }

    /**
     * Recursively checks the given config element (and any nested domain-config elements) for
     * trust-anchors that reference user certificates outside of debug-overrides.
     */
    private void checkForUserCertificates(XmlContext context, Element configElement) {
        NodeList children = configElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String tagName = childElement.getTagName();

            if (TAG_TRUST_ANCHORS.equals(tagName)) {
                checkTrustAnchors(context, childElement);
            } else if (TAG_DOMAIN_CONFIG.equals(tagName)) {
                // Nested domain-config elements can also have trust-anchors
                checkForUserCertificates(context, childElement);
            }
        }
    }

    /**
     * Checks a {@code <trust-anchors>} element for any {@code <certificates src="user">} entries.
     */
    private void checkTrustAnchors(XmlContext context, Element trustAnchorsElement) {
        NodeList children = trustAnchorsElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (TAG_CERTIFICATES.equals(childElement.getTagName())) {
                String src = childElement.getAttribute(ATTR_SRC);
                if (VALUE_USER.equals(src)) {
                    Location location = context.getElementLocation(childElement);
                    context.report(
                            ACCEPTS_USER_CERTIFICATES,
                            childElement,
                            location,
                            "Allowing user certificates could allow eavesdroppers to intercept "
                                    + "data sent by your app, which could impact the privacy of "
                                    + "your users. Consider nesting your app's `trust-anchors` "
                                    + "inside a `<debug-overrides>` element to make sure they are "
                                    + "only available when `android:debuggable` is set to `true`.");
                }
            }
        }
    }
}