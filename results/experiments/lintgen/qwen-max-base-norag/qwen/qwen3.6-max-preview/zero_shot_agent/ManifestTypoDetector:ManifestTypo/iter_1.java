package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ManifestTypoDetector extends Detector implements Detector.XmlScanner {

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
            "manifest", "application", "activity", "activity-alias", "service", "receiver",
            "provider", "uses-permission", "uses-permission-sdk-23", "permission", "permission-tree",
            "permission-group", "instrumentation", "uses-sdk", "uses-configuration", "uses-feature",
            "supports-screens", "compatible-screens", "supports-gl-texture", "meta-data",
            "intent-filter", "action", "category", "data", "grant-uri-permission", "path-permission",
            "layout", "queries", "package", "intent", "profileable", "uses-library", "static-library",
            "property", "protected-broadcast", "original-package", "adopt-permissions", "overlay",
            "resource-overlay", "theme"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getLocalName();
        if (tag == null) {
            tag = element.getTagName();
        }
        if (tag == null || tag.indexOf(':') != -1) {
            return;
        }

        if (VALID_TAGS.contains(tag)) {
            return;
        }

        String closest = null;
        int minDist = Integer.MAX_VALUE;

        for (String valid : VALID_TAGS) {
            int dist = editDistance(tag, valid);
            if (dist < minDist) {
                minDist = dist;
                closest = valid;
            }
        }

        if (minDist <= 2 && minDist > 0 && tag.length() > 2) {
            String message = String.format("Suspicious tag name `%1$s`: did you mean `%2$s`?", tag, closest);
            context.report(ISSUE, element, context.getNameLocation(element), message);
        }
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
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[len1][len2];
    }
}