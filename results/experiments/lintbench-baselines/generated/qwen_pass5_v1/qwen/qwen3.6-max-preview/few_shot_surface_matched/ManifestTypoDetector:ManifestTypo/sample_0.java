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
                    5,
                    Severity.FATAL,
                    new Implementation(
                            ManifestTypoDetector.class, Scope.MANIFEST_SCOPE));

    private static final Set<String> VALID_TAGS = new HashSet<>(Arrays.asList(
            "manifest", "application", "activity", "service", "receiver", "provider",
            "uses-permission", "uses-sdk", "uses-feature", "supports-screens",
            "compatible-screens", "instrumentation", "permission", "permission-group",
            "permission-tree", "meta-data", "intent-filter", "action", "category",
            "data", "grant-uri-permission", "path-permission", "activity-alias",
            "uses-library", "queries", "package", "intent", "profileable", "property",
            "overlay", "restrict-update", "static-library", "uses-native-library",
            "config-parameter", "uses-permission-sdk-23", "uses-permission-sdk-m"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (VALID_TAGS.contains(tagName)) {
            return;
        }

        String suggestion = findClosestMatch(tagName);
        if (suggestion != null) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Possible typo in manifest tag: did you mean `" + suggestion + "`?");
        }
    }

    private static String findClosestMatch(String typo) {
        String bestMatch = null;
        int minDist = Integer.MAX_VALUE;
        String lowerTypo = typo.toLowerCase();

        for (String valid : VALID_TAGS) {
            if (Math.abs(typo.length() - valid.length()) > 2) {
                continue;
            }
            int dist = levenshteinDistance(lowerTypo, valid);
            if (dist <= 2 && dist < minDist) {
                minDist = dist;
                bestMatch = valid;
                if (dist == 1) {
                    break;
                }
            }
        }
        return bestMatch;
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