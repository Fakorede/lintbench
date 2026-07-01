package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ManifestTypo",
            "Typos in manifest tags",
            "This check looks through the manifest, and if it finds any tags that look like likely misspellings, they are flagged.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE));

    private static final Set<String> VALID_TAGS = new HashSet<>(Arrays.asList(
            "manifest", "application", "activity", "activity-alias", "service",
            "receiver", "provider", "uses-permission", "uses-sdk", "uses-feature",
            "uses-configuration", "uses-library", "uses-native-library", "supports-screens",
            "compatible-screens", "supports-gl-texture", "instrumentation", "permission",
            "permission-tree", "permission-group", "queries", "package", "intent",
            "intent-filter", "action", "category", "data", "meta-data",
            "grant-uri-permission", "path-permission", "layout", "fragment", "profileable",
            "property", "apex-contributor", "static-library", "overlay", "resource-overlay",
            "theme", "adopt-permissions", "original-package", "package-restrictions",
            "protected-broadcast", "manifest-package", "eat-comment", "feature-group",
            "uses-permission-sdk-23", "uses-permission-sdk-m", "key-sets", "key-set",
            "public-key", "upgrade-key-set", "restrict-update", "split-permission",
            "capability", "apex-system-service", "uses-split", "config-parameter",
            "overlayable", "policy", "sdk-library", "bootclasspath", "classpath",
            "library", "native-library", "uses-native", "uses", "uses-static-library",
            "uses-sdk-extension", "feature", "restriction", "account-authenticator",
            "sync-adapter", "device-admin", "input-method", "wallpaper",
            "accessibility-service", "print-service", "host-apdu-service",
            "offhost-apdu-service", "nfc-host", "dream", "trust-agent",
            "voice-interaction-service", "external-service", "shortcut",
            "attributable", "config-override"
    ));

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (tag == null || tag.isEmpty() || tag.contains(":")) {
            return;
        }

        if (VALID_TAGS.contains(tag)) {
            return;
        }

        String closest = findClosestTag(tag);
        if (closest != null) {
            String message = String.format("Possible typo: `%1$s`. Did you mean `%2$s`?", tag, closest);
            LintFix fix = fix().replace().text(tag).with(closest).autoFix().build();
            context.report(ISSUE, element, context.getNameLocation(element), message, fix);
        }
    }

    private String findClosestTag(String tag) {
        String bestMatch = null;
        int bestDistance = Integer.MAX_VALUE;
        int threshold = tag.length() <= 4 ? 1 : 2;

        for (String valid : VALID_TAGS) {
            int dist = getEditDistance(tag, valid);
            if (dist <= threshold && dist < bestDistance) {
                bestDistance = dist;
                bestMatch = valid;
            }
        }
        return bestMatch;
    }

    private static int getEditDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            char c1 = s1.charAt(i - 1);
            for (int j = 1; j <= len2; j++) {
                char c2 = s2.charAt(j - 1);
                int cost = (c1 == c2) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[len1][len2];
    }
}