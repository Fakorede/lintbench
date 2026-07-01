package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.android.tools.lint.detector.api.Detector;

/**
 * Checks for typos in AndroidManifest.xml tags.
 */
public class ManifestTypoDetector extends Detector implements XmlScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ManifestTypo",
            "Typos in manifest tags",
            "This check looks through the manifest, and if it finds any tags " +
            "that look like likely misspellings, they are flagged.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    ManifestTypoDetector.class,
                    Scope.MANIFEST_SCOPE));

    /**
     * The set of valid manifest tag names from the Android SDK.
     */
    private static final Set<String> VALID_TAGS = new HashSet<>(Arrays.asList(
            "manifest",
            "application",
            "activity",
            "activity-alias",
            "service",
            "receiver",
            "provider",
            "uses-library",
            "uses-sdk",
            "uses-permission",
            "uses-permission-sdk-23",
            "uses-permission-sdk-m",
            "uses-feature",
            "uses-configuration",
            "permission",
            "permission-group",
            "permission-tree",
            "instrumentation",
            "supports-screens",
            "compatible-screens",
            "supports-gl-texture",
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
            "overlay",
            "attribution"
    ));

    /**
     * Constructs a new {@link ManifestTypoDetector}
     */
    public ManifestTypoDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(ALL);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();

        // Skip tags that are valid
        if (VALID_TAGS.contains(tag)) {
            return;
        }

        // Check if the tag is a likely typo of a valid tag
        String suggestion = findSimilarTag(tag);
        if (suggestion != null) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    String.format("Misspelled tag `<%1$s>`: Did you mean `<%2$s>`?", tag, suggestion));
        }
    }

    /**
     * Finds a valid tag that is similar to the given tag (likely a typo).
     *
     * @param tag the tag to check
     * @return the similar valid tag, or null if none found
     */
    private static String findSimilarTag(String tag) {
        // Normalize the tag for comparison
        String normalizedTag = tag.toLowerCase();

        // First pass: check for exact case-insensitive match
        for (String validTag : VALID_TAGS) {
            if (validTag.equalsIgnoreCase(tag) && !validTag.equals(tag)) {
                return validTag;
            }
        }

        // Second pass: check for edit distance similarity
        String bestMatch = null;
        int bestDistance = Integer.MAX_VALUE;
        int threshold = getThreshold(tag);

        for (String validTag : VALID_TAGS) {
            int distance = editDistance(normalizedTag, validTag);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestMatch = validTag;
            }
        }

        if (bestDistance <= threshold && bestMatch != null) {
            return bestMatch;
        }

        return null;
    }

    /**
     * Returns the edit distance threshold for a given tag based on its length.
     *
     * @param tag the tag to check
     * @return the threshold
     */
    private static int getThreshold(String tag) {
        int length = tag.length();
        if (length <= 4) {
            return 1;
        } else if (length <= 8) {
            return 2;
        } else {
            return 3;
        }
    }

    /**
     * Computes the Levenshtein edit distance between two strings.
     *
     * @param s the first string
     * @param t the second string
     * @return the edit distance
     */
    private static int editDistance(String s, String t) {
        int m = s.length();
        int n = t.length();

        // Early exit for identical strings
        if (s.equals(t)) {
            return 0;
        }

        // Early exit if lengths differ too much
        if (Math.abs(m - n) > 4) {
            return Math.abs(m - n);
        }

        int[] dp = new int[n + 1];

        for (int j = 0; j <= n; j++) {
            dp[j] = j;
        }

        for (int i = 1; i <= m; i++) {
            int prev = dp[0];
            dp[0] = i;
            for (int j = 1; j <= n; j++) {
                int temp = dp[j];
                if (s.charAt(i - 1) == t.charAt(j - 1)) {
                    dp[j] = prev;
                } else {
                    dp[j] = 1 + Math.min(prev, Math.min(dp[j], dp[j - 1]));
                }
                prev = temp;
            }
        }

        return dp[n];
    }
}