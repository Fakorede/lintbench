package com.android.tools.lint.checks;

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
            new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final Set<String> VALID_TAGS = new HashSet<>(Arrays.asList(
            "manifest", "application", "activity", "activity-alias", "service",
            "receiver", "provider", "uses-permission", "uses-permission-sdk-23",
            "uses-sdk", "uses-feature", "uses-configuration", "supports-screens",
            "compatible-screens", "instrumentation", "permission", "permission-tree",
            "permission-group", "meta-data", "intent-filter", "action", "category",
            "data", "grant-uri-permission", "path-permission", "queries", "package",
            "intent", "profileable", "uses-library", "static-library", "overlay",
            "eat-comment", "protected-broadcast", "original-package", "adopt-permissions"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!context.isManifestFile()) {
            return;
        }

        String tag = element.getTagName();
        if (VALID_TAGS.contains(tag)) {
            return;
        }

        String suggestion = findClosestTag(tag);
        if (suggestion != null) {
            String message = String.format("Suspicious tag name `%1$s`: did you mean `%2$s`?", tag, suggestion);
            context.report(ISSUE, element, context.getLocation(element), message,
                    fix().replace().text(tag).with(suggestion).build());
        }
    }

    private static String findClosestTag(String typo) {
        String bestMatch = null;
        int minDistance = Integer.MAX_VALUE;
        int threshold = getThreshold(typo.length());

        for (String valid : VALID_TAGS) {
            int distance = levenshteinDistance(typo, valid);
            if (distance <= threshold && distance < minDistance) {
                minDistance = distance;
                bestMatch = valid;
            }
        }
        return bestMatch;
    }

    private static int getThreshold(int length) {
        if (length < 4) return 1;
        if (length < 8) return 2;
        return 3;
    }

    private static int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        int[] prev = new int[len2 + 1];
        int[] curr = new int[len2 + 1];

        for (int j = 0; j <= len2; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            curr[0] = i;
            for (int j = 1; j <= len2; j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }
        return prev[len2];
    }
}