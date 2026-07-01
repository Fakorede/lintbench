/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

/**
 * Detector that checks for Network Security Config files that accept user certificates
 * outside of debug-overrides, which could allow eavesdroppers to intercept app data.
 */
public class NetworkSecurityConfigDetector extends ResourceXmlDetector {

    /** The main issue: accepting user certificates outside of debug-overrides */
    public static final Issue ACCEPTS_USER_CERTIFICATES = Issue.create(
            "AcceptsUserCertificates",
            "Allowing User Certificates",
            "Allowing user certificates could allow eavesdroppers to intercept data sent by " +
                    "your app, which could impact the privacy of your users. Consider nesting " +
                    "your app's `trust-anchors` inside a `<debug-overrides>` element to make " +
                    "sure they are only available when `android:debuggable` is set to `true`.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    NetworkSecurityConfigDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            ))
            .addMoreInfo("https://goo.gle/AcceptsUserCertificates")
            .addMoreInfo("https://developer.android.com/training/articles/security-config#TrustingDebugCa");

    private static final String TAG_NETWORK_SECURITY_CONFIG = "network-security-config";
    private static final String TAG_BASE_CONFIG = "base-config";
    private static final String TAG_DOMAIN_CONFIG = "domain-config";
    private static final String TAG_DEBUG_OVERRIDES = "debug-overrides";
    private static final String TAG_TRUST_ANCHORS = "trust-anchors";
    private static final String TAG_CERTIFICATES = "certificates";
    private static final String ATTR_SRC = "src";
    private static final String VALUE_USER = "user";

    /** Constructs a new {@link NetworkSecurityConfigDetector}. */
    public NetworkSecurityConfigDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_BASE_CONFIG,
                TAG_DOMAIN_CONFIG
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Skip if this element is nested inside a debug-overrides element
        if (isInsideDebugOverrides(element)) {
            return;
        }

        // Check if this base-config or domain-config contains trust-anchors with user certs
        checkForUserCertificates(context, element);
    }

    /**
     * Checks whether the given element (base-config or domain-config) contains
     * trust-anchors that reference user certificates.
     */
    private void checkForUserCertificates(XmlContext context, Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (TAG_TRUST_ANCHORS.equals(childElement.getTagName())) {
                    checkTrustAnchors(context, childElement);
                }
            }
        }
    }

    /**
     * Checks whether the trust-anchors element contains certificates with src="user".
     */
    private void checkTrustAnchors(XmlContext context, Element trustAnchorsElement) {
        NodeList children = trustAnchorsElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (TAG_CERTIFICATES.equals(childElement.getTagName())) {
                    String src = childElement.getAttribute(ATTR_SRC);
                    if (VALUE_USER.equals(src)) {
                        context.report(
                                ACCEPTS_USER_CERTIFICATES,
                                childElement,
                                context.getLocation(childElement),
                                "Allowing user certificates could allow eavesdroppers to " +
                                        "intercept data sent by your app, which could impact " +
                                        "the privacy of your users. Consider nesting your " +
                                        "app's `trust-anchors` inside a `<debug-overrides>` " +
                                        "element to make sure they are only available when " +
                                        "`android:debuggable` is set to `true`."
                        );
                    }
                }
            }
        }
    }

    /**
     * Returns true if the given element is nested inside a debug-overrides element.
     */
    private boolean isInsideDebugOverrides(Element element) {
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            if (TAG_DEBUG_OVERRIDES.equals(((Element) parent).getTagName())) {
                return true;
            }
            parent = parent.getParentNode();
        }
        return false;
    }
}