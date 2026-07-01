package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
                    "This check looks through the manifest, and if it finds any tags "
                            + "that look like likely misspellings, they are flagged.",
                    Category.CORRECTNESS,
                    10,
                    Severity.FATAL,
                    new Implementation(
                            ManifestTypoDetector.class, Scope.MANIFEST_SCOPE));

    private static final Set<String> VALID_TAGS = new HashSet<>(Arrays.asList(
            "manifest",
            "application",
            "activity",
            "activity-alias",
            "service",
            "receiver",
            "provider",
            "uses-permission",
            "uses-permission-sdk-23",
            "permission",
            "permission-group",
            "permission-tree",
            "uses-sdk",
            "instrumentation",
            "uses-library",
            "library",
            "meta-data",
            "intent-filter",
            "action",
            "category",
            "data",
            "grant-uri-permission",
            "path-permission",
            "queries",
            "package",
            "profileable",
            "uses-feature",
            "supports-screens",
            "compatible-screens",
            "supports-gl-texture",
            "uses-configuration",
            "uses-native-library",
            "property",
            "sdk-library"
    ));

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (tagName.contains(":")) {
            // Skip namespaced elements (e.g. tools:node)
            return;
        }

        if (VALID_TAGS.contains(tagName)) {
            return;
        }

        String closest = findClosestTag(tagName);
        if (closest != null) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    String.format("Suspected typo in manifest tag: \"%s\" (did you mean \"%s\"?)", tagName, closest)
            );
        }
    }

    @Nullable
    private String findClosestTag(String tag) {
        String bestMatch = null;
        int minDistance = Integer.MAX_VALUE;

        for (String validTag : VALID_TAGS) {
            int distance = getLevenshteinDistance(tag, validTag);
            if (distance < minDistance) {
                minDistance = distance;
                bestMatch = validTag;
            }
        }

        // Special cases for very common typos
        if (tag.equals("uses-permissions")) {
            return "uses-permission";
        }
        if (tag.equals("user-permission")) {
            return "uses-permission";
        }

        // Only suggest if the distance is within a reasonable threshold based on length
        int threshold = tag.length() <= 4 ? 1 : 2;
        if (minDistance <= threshold) {
            return bestMatch;
        }

        return null;
    }

    private static int getLevenshteinDistance(String s, String t) {
        if (s.isEmpty()) return t.length();
        if (t.isEmpty()) return s.length();
        int[] prev = new int[t.length() + 1];
        int[] curr = new int[t.length() + 1];
        for (int j = 0; j <= t.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= s.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= t.length(); j++) {
                int cost = (s.charAt(i - 1) == t.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            System.arraycopy(curr, 0, prev, 0, curr.length);
        }
        return prev[t.length()];
    }
}