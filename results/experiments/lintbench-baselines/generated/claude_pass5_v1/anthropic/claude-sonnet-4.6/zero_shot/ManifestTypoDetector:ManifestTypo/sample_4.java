package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.Detector;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Checks for typos in AndroidManifest.xml tags.
 */
public class ManifestTypoDetector extends Detector implements XmlScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ManifestTypo",
            "Typo in manifest tag",
            "This check looks through the manifest, and if it finds any tags that look like " +
            "likely misspellings, they are flagged.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    ManifestTypoDetector.class,
                    Scope.MANIFEST_SCOPE));

    /**
     * Set of valid manifest tag names as per the Android documentation.
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
            "uses-permission",
            "uses-permission-sdk-23",
            "permission",
            "permission-tree",
            "permission-group",
            "instrumentation",
            "uses-sdk",
            "uses-configuration",
            "uses-feature",
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
            "uses-native-library"
    ));

    /**
     * Constructs a new {@link ManifestTypoDetector}.
     */
    public ManifestTypoDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root != null) {
            checkElement(context, root);
        }
    }

    private void checkElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        // Only check tags that don't contain dots (custom tags / class names are okay)
        if (!tag.contains(".") && !VALID_TAGS.contains(tag)) {
            // Check if it looks like a typo of a known tag
            String suggestion = findSimilarTag(tag);
            if (suggestion != null) {
                String message = String.format(
                        "Misspelled tag `<%1$s>`: Did you mean `<%2$s>`?",
                        tag, suggestion);
                context.report(ISSUE, element, context.getLocation(element), message);
            }
        }

        // Recurse into children
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement(context, (Element) child);
            }
        }
    }

    /**
     * Finds a valid tag that is similar to the given tag (potential typo).
     *
     * @param tag the tag name to check
     * @return a similar valid tag name, or null if no close match is found
     */
    private static String findSimilarTag(String tag) {
        // Threshold: if edit distance <= this value, consider it a likely typo
        int threshold = computeThreshold(tag);

        String bestMatch = null;
        int bestDistance = Integer.MAX_VALUE;

        for (String validTag : VALID_TAGS) {
            int distance = editDistance(tag, validTag);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestMatch = validTag;
            }
        }

        if (bestDistance <= threshold) {
            return bestMatch;
        }

        return null;
    }

    /**
     * Computes the edit distance threshold for a given tag length.
     * Shorter tags require a smaller edit distance to be considered a typo.
     */
    private static int computeThreshold(String tag) {
        int len = tag.length();
        if (len <= 4) {
            return 1;
        } else if (len <= 8) {
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

        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];

        for (int j = 0; j <= n; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            for (int j = 1; j <= n; j++) {
                int cost = s.charAt(i - 1) == t.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }

        return prev[n];
    }
}