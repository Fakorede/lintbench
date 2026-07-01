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

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestTypoDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Typos in manifest tags",
                    "Typos in manifest tags can cause features to be silently ignored or misinterpreted. " +
                    "This check looks through the manifest and flags tags that appear to be misspellings of valid manifest tags.",
                    Category.CORRECTNESS,
                    5,
                    Severity.FATAL,
                    IMPLEMENTATION);

    private static final Set<String> VALID_TAGS = new HashSet<>(Arrays.asList(
            "manifest", "application", "activity", "service", "receiver", "provider",
            "uses-permission", "uses-sdk", "uses-feature", "supports-screens",
            "compatible-screens", "instrumentation", "permission", "permission-group",
            "permission-tree", "uses-library", "meta-data", "intent-filter", "action",
            "category", "data", "grant-uri-permission", "path-permission", "layout",
            "activity-alias", "uses-permission-sdk-23", "queries", "package", "intent",
            "profileable", "property", "attr", "overlay", "restrict-update",
            "static-library", "library", "original-package", "adopt-permissions",
            "protected-broadcast", "eat-comment", "application-overrides",
            "config-overrides", "uses-native-library", "uses-split", "uses-configuration"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (VALID_TAGS.contains(tag)) {
            return;
        }
        // Ignore tags with namespaces, dots (often custom or fully qualified), or very short names
        if (tag.indexOf('.') != -1 || tag.indexOf(':') != -1 || tag.length() <= 2) {
            return;
        }

        String suggestion = null;
        int minDistance = Integer.MAX_VALUE;
        for (String valid : VALID_TAGS) {
            int dist = editDistance(tag, valid);
            if (dist <= 2 && dist < minDistance) {
                minDistance = dist;
                suggestion = valid;
            }
        }

        if (suggestion != null) {
            String message = String.format("Suspicious tag name `%1$s`; did you mean `%2$s`?", tag, suggestion);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    private static int editDistance(String s, String t) {
        int m = s.length();
        int n = t.length();
        int[][] dp = new int[m + 1][n + 1];

        for (int i = 0; i <= m; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= n; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int cost = s.charAt(i - 1) == t.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[m][n];
    }
}