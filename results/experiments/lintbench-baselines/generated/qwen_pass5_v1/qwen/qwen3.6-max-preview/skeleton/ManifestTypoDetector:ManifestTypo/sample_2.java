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
import java.util.Collections;
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
            "action", "activity", "activity-alias", "application", "category",
            "compatible-screens", "data", "grant-uri-permission", "instrumentation",
            "intent-filter", "manifest", "meta-data", "path-permission", "permission",
            "permission-group", "permission-tree", "provider", "receiver", "service",
            "supports-gl-texture", "supports-screens", "uses-configuration", "uses-feature",
            "uses-library", "uses-permission", "uses-permission-sdk-23", "uses-sdk",
            "queries", "package", "overlay"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!context.isManifestFile()) {
            return;
        }

        String tag = element.getTagName();
        if (VALID_TAGS.contains(tag)) {
            return;
        }

        String suggestion = findClosestTag(tag);
        if (suggestion != null) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Suspicious tag name `" + tag + "`; did you mean `" + suggestion + "`?");
        }
    }

    private static String findClosestTag(String tag) {
        String bestMatch = null;
        int bestDistance = Integer.MAX_VALUE;

        for (String valid : VALID_TAGS) {
            int lenDiff = Math.abs(tag.length() - valid.length());
            if (lenDiff > 2) {
                continue;
            }
            int dist = levenshteinDistance(tag, valid);
            if (dist <= 2 && dist < bestDistance) {
                bestDistance = dist;
                bestMatch = valid;
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