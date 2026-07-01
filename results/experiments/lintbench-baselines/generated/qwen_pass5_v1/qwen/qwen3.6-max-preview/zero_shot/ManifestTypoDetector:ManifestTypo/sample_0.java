package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ManifestTypoDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "ManifestTypo",
            "Typos in manifest tags",
            "This check looks through the manifest, and if it finds any tags that look like likely misspellings, they are flagged.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final Set<String> VALID_TAGS = new HashSet<>(Arrays.asList(
            "action", "activity", "activity-alias", "application", "category",
            "compatible-screens", "data", "eat-comment", "grant-uri-permission",
            "instrumentation", "intent-filter", "layout", "manifest", "meta-data",
            "package", "path-permission", "permission", "permission-group",
            "permission-tree", "protected-broadcast", "provider", "receiver",
            "service", "supports-gl-texture", "supports-screens", "uses-configuration",
            "uses-feature", "uses-library", "uses-permission", "uses-permission-sdk-23",
            "uses-sdk", "original-package", "adopt-permissions", "overlay"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!context.isManifest()) {
            return;
        }

        String tag = element.getTagName();
        if (VALID_TAGS.contains(tag)) {
            return;
        }

        String suggestion = findClosestTag(tag);
        if (suggestion != null) {
            String message = String.format("Typo in tag name: should probably be `%s`", suggestion);
            context.report(ISSUE, element, context.getNameLocation(element), message);
        }
    }

    private static String findClosestTag(String tag) {
        String lowerTag = tag.toLowerCase();
        String bestMatch = null;
        int minDistance = Integer.MAX_VALUE;

        for (String valid : VALID_TAGS) {
            int dist = editDistance(lowerTag, valid);
            if (dist <= 2 && dist < minDistance && Math.abs(tag.length() - valid.length()) <= 2) {
                minDistance = dist;
                bestMatch = valid;
            }
        }
        return bestMatch;
    }

    private static int editDistance(String s1, String s2) {
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
            for (int j = 1; j <= len2; j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[len1][len2];
    }
}