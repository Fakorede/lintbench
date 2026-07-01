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
                    new Implementation(
                            ManifestTypoDetector.class, Scope.MANIFEST_SCOPE));

    private static final Set<String> VALID_TAGS = new HashSet<>(Arrays.asList(
            "manifest", "application", "activity", "activity-alias", "service",
            "receiver", "provider", "uses-permission", "uses-permission-sdk-23",
            "permission", "permission-tree", "permission-group", "instrumentation",
            "uses-sdk", "uses-configuration", "uses-feature", "supports-screens",
            "compatible-screens", "supports-gl-texture", "meta-data", "intent-filter",
            "action", "category", "data", "grant-uri-permission", "path-permission",
            "queries", "package", "intent", "profileable", "property", "uses-library",
            "static-library", "overlay", "uses-native-library"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (tag.contains(":")) {
            tag = tag.substring(tag.indexOf(':') + 1);
        }

        if (VALID_TAGS.contains(tag)) {
            return;
        }

        String suggestion = findClosestTag(tag);
        if (suggestion != null) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    String.format("Suspicious tag name `%1$s`: did you mean `%2$s`?", tag, suggestion));
        }
    }

    private static String findClosestTag(String tag) {
        String bestMatch = null;
        int minDistance = Integer.MAX_VALUE;
        int threshold = Math.max(2, tag.length() / 3);

        for (String valid : VALID_TAGS) {
            int dist = levenshtein(tag, valid);
            if (dist < minDistance && dist <= threshold) {
                minDistance = dist;
                bestMatch = valid;
            }
        }
        return bestMatch;
    }

    private static int levenshtein(String s1, String s2) {
        int m = s1.length();
        int n = s2.length();
        int[][] dp = new int[m + 1][n + 1];

        for (int i = 0; i <= m; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= n; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }
        return dp[m][n];
    }
}