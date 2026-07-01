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
import java.util.Collection;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Typos in manifest tags",
                    "This check looks through the manifest, and if it finds any tags that look like likely misspellings, they are flagged.",
                    Category.CORRECTNESS,
                    5,
                    Severity.FATAL,
                    IMPLEMENTATION);

    private static final java.util.Set<String> VALID_TAGS = new java.util.HashSet<String>(java.util.Arrays.asList(
            "manifest", "application", "activity", "activity-alias", "service", "receiver",
            "provider", "uses-library", "uses-permission", "uses-permission-sdk-23",
            "permission", "permission-group", "permission-tree", "uses-sdk",
            "uses-configuration", "uses-feature", "supports-screens", "compatible-screens",
            "supports-gl-texture", "meta-data", "grant-uri-permission", "path-permission",
            "intent-filter", "action", "category", "data", "queries", "profileable",
            "property", "uses-native-library", "attribution", "sdk-library", "instrumentation",
            "original-package", "protected-broadcast", "adopt-permissions", "key-set", "public-key", "layout"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        String suggestion = findSuggestingTag(tagName);
        if (suggestion != null) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    String.format("Potential typo in manifest tag: `%s` (did you mean `%s`?)", tagName, suggestion));
        }
    }

    private static String findSuggestingTag(String tag) {
        if (VALID_TAGS.contains(tag)) {
            return null;
        }
        String closest = null;
        int minDistance = Integer.MAX_VALUE;
        for (String valid : VALID_TAGS) {
            int dist = getLevenshteinDistance(tag, valid);
            if (dist < minDistance) {
                minDistance = dist;
                closest = valid;
            }
        }
        if (closest != null) {
            int len = Math.max(tag.length(), closest.length());
            if (minDistance == 1 && len >= 3) {
                return closest;
            }
            if (minDistance == 2 && len >= 6) {
                return closest;
            }
        }
        return null;
    }

    private static int getLevenshteinDistance(String s, String t) {
        int[] d = new int[t.length() + 1];
        for (int i = 0; i <= t.length(); i++) {
            d[i] = i;
        }
        for (int i = 1; i <= s.length(); i++) {
            int prev = i;
            for (int j = 1; j <= t.length(); j++) {
                int val;
                if (s.charAt(i - 1) == t.charAt(j - 1)) {
                    val = d[j - 1];
                } else {
                    val = Math.min(d[j - 1] + 1, Math.min(d[j] + 1, prev + 1));
                }
                d[j - 1] = prev;
                prev = val;
            }
            d[t.length()] = prev;
        }
        return d[t.length()];
    }
}