package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Typos in manifest tags",
                    "This check looks through the manifest, and if it finds any tags that look like "
                            + "likely misspellings, they are flagged.",
                    Category.CORRECTNESS,
                    6,
                    Severity.FATAL,
                    new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE));

    private static final Set<String> VALID_TAGS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "manifest", "application", "activity", "activity-alias", "service", "receiver", "provider",
            "uses-permission", "uses-permission-sdk-23", "uses-sdk", "intent-filter", "action", "category",
            "data", "meta-data", "uses-feature", "supports-screens", "permission", "permission-group",
            "permission-tree", "instrumentation", "path-permission", "grant-uri-permission", "queries",
            "package", "intent", "profileable", "uses-configuration", "uses-library", "supports-gl-texture",
            "compatible-screens", "screen", "eat-comment", "protected-broadcast", "overlay", "static-library",
            "library", "original-package", "adopt-permissions", "keyset", "public-key", "upgrade-keyset",
            "signing-keyset", "domain", "debuggable", "restrict-update", "extract-native-libs",
            "uses-native-library", "property", "config-file", "resource-overlay", "capability", "shortcuts",
            "shortcut", "layout", "account-authenticator", "sync-adapter", "device-admin",
            "accessibility-service", "input-method", "spell-checker", "voice-interaction-service",
            "autofill-service", "content-preloader", "app-widget-provider", "dream", "print-service",
            "trust-agent", "wallpaper", "payment", "slice", "deny-permission"
    )));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (tagName.contains(":")) {
            tagName = tagName.substring(tagName.indexOf(':') + 1);
        }

        if (!VALID_TAGS.contains(tagName)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Suspicious tag name `" + element.getTagName() + "`: likely a typo in the manifest");
        }
    }
}