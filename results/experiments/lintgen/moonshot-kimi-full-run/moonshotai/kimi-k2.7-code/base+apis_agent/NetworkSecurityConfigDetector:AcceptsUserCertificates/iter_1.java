package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class NetworkSecurityConfigDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "AcceptsUserCertificates",
            "Allowing user certificates",
            "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, " +
                    "which could impact the privacy of your users. Consider nesting your app's " +
                    "<code>trust-anchors</code> inside a <code>&lt;debug-overrides&gt;</code> element " +
                    "to make sure they are only available when <code>android:debuggable</code> is " +
                    "set to <code>true</code>.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String TAG_CERTIFICATES = "certificates";
    private static final String ATTR_SRC = "src";
    private static final String SRC_USER = "user";
    private static final String TAG_DEBUG_OVERRIDES = "debug-overrides";

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_CERTIFICATES);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!SRC_USER.equals(element.getAttribute(ATTR_SRC))) {
            return;
        }

        Node parent = element.getParentNode();
        while (parent != null) {
            if (parent instanceof Element
                    && TAG_DEBUG_OVERRIDES.equals(((Element) parent).getTagName())) {
                return;
            }
            parent = parent.getParentNode();
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Allowing user certificates could allow eavesdroppers to intercept data sent by your app. " +
                        "Consider nesting your app's trust-anchors inside a <debug-overrides> element " +
                        "so they are only available when android:debuggable is true."
        );
    }
}