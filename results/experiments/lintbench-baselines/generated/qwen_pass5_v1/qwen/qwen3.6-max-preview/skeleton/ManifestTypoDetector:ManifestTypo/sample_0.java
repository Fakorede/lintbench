package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestTypoDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Typos in manifest tags",
                    "This check looks through the manifest, and if it finds any tags that look like likely misspellings, they are flagged.",
                    Category.CORRECTNESS,
                    5,
                    Severity.FATAL,
                    IMPLEMENTATION);

    private static final Set<String> VALID_TAGS = new HashSet<>(Arrays.asList(
            "manifest", "application", "activity", "service", "receiver", "provider",
            "uses-permission", "uses-sdk", "uses-feature", "uses-library", "uses-configuration",
            "supports-screens", "compatible-screens", "permission", "permission-group",
            "permission-tree", "instrumentation", "intent-filter", "action", "category",
            "data", "meta-data", "grant-uri-permission", "path-permission", "queries",
            "package", "intent", "profileable", "restrict-update", "overlay", "static-library"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (VALID_TAGS.contains(tagName)) {
            return;
        }

        String suggestion = findClosestTag(tagName);
        if (suggestion != null) {
            String message = String.format("Suspicious tag name `%1$s`: did you mean `%2$s`?", tagName, suggestion);
            context.report(ISSUE, element, context.getLocation(element), message,
                    fix().replace().text(tagName).with(suggestion).build());
        }
    }

    private static String findClosestTag(String typo) {
        String bestMatch = null;
        int minDistance = Integer.MAX_VALUE;
        int threshold = 2;

        for (String valid : VALID_TAGS) {
            int dist = getLevenshteinDistance(typo, valid);
            if (dist <= threshold && dist < minDistance) {
                minDistance = dist;
                bestMatch = valid;
            }
        }
        return bestMatch;
    }

    private static int getLevenshteinDistance(String s1, String s2) {
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