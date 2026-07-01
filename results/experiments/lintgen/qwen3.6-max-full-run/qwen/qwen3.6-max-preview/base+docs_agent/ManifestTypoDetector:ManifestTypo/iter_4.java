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
            new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE));

    private static final Set<String> VALID_TAGS = new HashSet<>(Arrays.asList(
            "manifest", "application", "activity", "activity-alias", "service",
            "receiver", "provider", "uses-permission", "uses-permission-sdk-23",
            "permission", "permission-tree", "permission-group", "instrumentation",
            "uses-sdk", "uses-configuration", "uses-feature", "supports-screens",
            "compatible-screens", "screen", "intent-filter", "action", "category", "data",
            "meta-data", "library", "uses-library", "queries", "package", "intent",
            "layout", "grant-uri-permission", "path-permission", "static-library",
            "overlay", "profileable", "eats-touch", "restrict-update", "original-package",
            "uses-native-library", "property", "attributable", "extension", "uses-split",
            "applies-to", "protected-broadcast", "adopt-permissions", "feature-group"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (tag.indexOf('.') != -1) {
            return;
        }
        int colon = tag.indexOf(':');
        if (colon != -1) {
            tag = tag.substring(colon + 1);
        }
        if (VALID_TAGS.contains(tag)) {
            return;
        }

        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String valid : VALID_TAGS) {
            int distance = levenshtein(tag, valid);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = valid;
            }
        }

        if (best != null && bestDistance <= 2) {
            String message = String.format("Suspicious tag name `%1$s`; did you mean `%2$s`?", tag, best);
            context.report(ISSUE, context.getLocation(element), message);
        }
    }

    private static int levenshtein(String s1, String s2) {
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