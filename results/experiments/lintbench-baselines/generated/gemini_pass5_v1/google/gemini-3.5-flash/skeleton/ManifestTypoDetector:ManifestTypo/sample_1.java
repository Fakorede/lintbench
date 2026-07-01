package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestTypoDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Typos in manifest tags",
                    "This check looks through the manifest, and if it finds any tags "
                            + "that look like likely misspellings, they are flagged.",
                    Category.CORRECTNESS,
                    5,
                    Severity.FATAL,
                    IMPLEMENTATION);

    private static final Set<String> VALID_TAGS = new HashSet<>(Arrays.asList(
            "manifest", "uses-sdk", "uses-configuration", "uses-feature", "supports-screens",
            "compatible-screens", "supports-gl-texture", "uses-permission", "uses-permission-sdk-23",
            "permission", "permission-group", "permission-tree", "instrumentation", "queries",
            "package", "intent", "provider", "application", "activity", "intent-filter",
            "action", "category", "data", "meta-data", "layout", "activity-alias", "service",
            "receiver", "grant-uri-permission", "path-permission", "uses-library", "uses-native-library",
            "uses-sdk-library", "library", "profileable", "property", "processes", "sdk-library",
            "attribution", "original-package", "protected-broadcast", "adopt-permissions"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(XmlScanner.ALL);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!"AndroidManifest.xml".equals(context.file.getName())) {
            return;
        }

        String tagName = element.getTagName();
        if (VALID_TAGS.contains(tagName)) {
            return;
        }

        String closestMatch = null;
        int minDistance = Integer.MAX_VALUE;
        for (String validTag : VALID_TAGS) {
            int distance = getLevenshteinDistance(tagName, validTag);
            if (distance < minDistance) {
                minDistance = distance;
                closestMatch = validTag;
            }
        }

        boolean isTypo = false;
        if (minDistance > 0) {
            if (minDistance <= 2) {
                isTypo = true;
            } else if (minDistance == 3 && tagName.length() >= 10) {
                isTypo = true;
            }
        }

        if (isTypo && closestMatch != null) {
            String message = String.format("Potential typo in manifest tag '%s'; did you mean '%s'?", tagName, closestMatch);
            LintFix fix = fix()
                    .replace()
                    .text(tagName)
                    .with(closestMatch)
                    .build();
            context.report(ISSUE, element, context.getNameLocation(element), message, fix);
        }
    }

    private static int getLevenshteinDistance(String s, String t) {
        if (s == null || t == null) {
            return Integer.MAX_VALUE;
        }
        int n = s.length();
        int m = t.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[] p = new int[n + 1];
        int[] d = new int[n + 1];
        for (int i = 0; i <= n; i++) {
            p[i] = i;
        }
        for (int j = 1; j <= m; j++) {
            char tj = t.charAt(j - 1);
            d[0] = j;
            for (int i = 1; i <= n; i++) {
                int cost = s.charAt(i - 1) == tj ? 0 : 1;
                d[i] = Math.min(Math.min(d[i - 1] + 1, p[i] + 1), p[i - 1] + cost);
            }
            int[] placeholder = p;
            p = d;
            d = placeholder;
        }
        return p[n];
    }
}