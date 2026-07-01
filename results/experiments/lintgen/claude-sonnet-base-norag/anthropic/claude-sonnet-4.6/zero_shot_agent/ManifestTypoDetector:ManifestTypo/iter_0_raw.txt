package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;

public class ManifestTypoDetector extends Detector implements Detector.XmlScanner {

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
                    Scope.MANIFEST_SCOPE
            )
    );

    /**
     * Known valid manifest tags (top-level and nested).
     */
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
            "uses-feature",
            "uses-library",
            "uses-sdk",
            "uses-configuration",
            "instrumentation",
            "supports-screens",
            "compatible-screens",
            "supports-gl-texture",
            "grant-uri-permission",
            "path-permission",
            "intent-filter",
            "action",
            "category",
            "data",
            "meta-data",
            "queries",
            "package",
            "protected-broadcast",
            "adopt-permissions",
            "original-package",
            "overlay",
            "profileable"
    ));

    /**
     * Map from known typos to their correct spellings.
     */
    private static final Map<String, String> TYPO_MAP = new HashMap<>();

    static {
        // Common typos for manifest tags
        TYPO_MAP.put("activty", "activity");
        TYPO_MAP.put("actvity", "activity");
        TYPO_MAP.put("activiy", "activity");
        TYPO_MAP.put("activiti", "activity");
        TYPO_MAP.put("activitiy", "activity");
        TYPO_MAP.put("activty-alias", "activity-alias");
        TYPO_MAP.put("activity-alais", "activity-alias");
        TYPO_MAP.put("activity-alis", "activity-alias");
        TYPO_MAP.put("activty-alias", "activity-alias");
        TYPO_MAP.put("sevice", "service");
        TYPO_MAP.put("serivce", "service");
        TYPO_MAP.put("servce", "service");
        TYPO_MAP.put("servcie", "service");
        TYPO_MAP.put("reciver", "receiver");
        TYPO_MAP.put("reciever", "receiver");
        TYPO_MAP.put("recever", "receiver");
        TYPO_MAP.put("recevier", "receiver");
        TYPO_MAP.put("provder", "provider");
        TYPO_MAP.put("providor", "provider");
        TYPO_MAP.put("proivder", "provider");
        TYPO_MAP.put("uses-premission", "uses-permission");
        TYPO_MAP.put("uses-permision", "uses-permission");
        TYPO_MAP.put("uses-permisson", "uses-permission");
        TYPO_MAP.put("uses-persmission", "uses-permission");
        TYPO_MAP.put("use-permission", "uses-permission");
        TYPO_MAP.put("uses-permisison", "uses-permission");
        TYPO_MAP.put("uses-feture", "uses-feature");
        TYPO_MAP.put("uses-featrue", "uses-feature");
        TYPO_MAP.put("uses-freature", "uses-feature");
        TYPO_MAP.put("use-feature", "uses-feature");
        TYPO_MAP.put("uses-libraray", "uses-library");
        TYPO_MAP.put("uses-libary", "uses-library");
        TYPO_MAP.put("uses-librray", "uses-library");
        TYPO_MAP.put("use-library", "uses-library");
        TYPO_MAP.put("uses-skd", "uses-sdk");
        TYPO_MAP.put("uses-dsk", "uses-sdk");
        TYPO_MAP.put("use-sdk", "uses-sdk");
        TYPO_MAP.put("instrumenation", "instrumentation");
        TYPO_MAP.put("instrumentaion", "instrumentation");
        TYPO_MAP.put("instumenation", "instrumentation");
        TYPO_MAP.put("instrumetation", "instrumentation");
        TYPO_MAP.put("intent-fliter", "intent-filter");
        TYPO_MAP.put("intent-filtr", "intent-filter");
        TYPO_MAP.put("intent-filer", "intent-filter");
        TYPO_MAP.put("intnet-filter", "intent-filter");
        TYPO_MAP.put("intet-filter", "intent-filter");
        TYPO_MAP.put("meta-dat", "meta-data");
        TYPO_MAP.put("meta-dta", "meta-data");
        TYPO_MAP.put("meta-dtaa", "meta-data");
        TYPO_MAP.put("permision", "permission");
        TYPO_MAP.put("premission", "permission");
        TYPO_MAP.put("permisson", "permission");
        TYPO_MAP.put("persmission", "permission");
        TYPO_MAP.put("applicaton", "application");
        TYPO_MAP.put("applicaiton", "application");
        TYPO_MAP.put("applcation", "application");
        TYPO_MAP.put("aplication", "application");
        TYPO_MAP.put("appliation", "application");
        TYPO_MAP.put("suppots-screens", "supports-screens");
        TYPO_MAP.put("supports-screns", "supports-screens");
        TYPO_MAP.put("suports-screens", "supports-screens");
        TYPO_MAP.put("grant-uri-permision", "grant-uri-permission");
        TYPO_MAP.put("grant-uri-premission", "grant-uri-permission");
        TYPO_MAP.put("catagory", "category");
        TYPO_MAP.put("categroy", "category");
        TYPO_MAP.put("categoy", "category");
        TYPO_MAP.put("acton", "action");
        TYPO_MAP.put("actiion", "action");
    }

    public ManifestTypoDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getLocalName();
        if (tag == null) {
            tag = element.getTagName();
        }

        if (tag == null) {
            return;
        }

        // Check direct typo map first
        String correct = TYPO_MAP.get(tag);
        if (correct != null) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    String.format("Misspelled tag `<%1$s>`: Did you mean `<%2$s>`?", tag, correct)
            );
            return;
        }

        // Check if the tag is a known valid tag
        if (VALID_TAGS.contains(tag)) {
            return;
        }

        // For unknown tags, try to find close matches using edit distance
        String suggestion = findClosestTag(tag);
        if (suggestion != null) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    String.format("Misspelled tag `<%1$s>`: Did you mean `<%2$s>`?", tag, suggestion)
            );
        }
    }

    /**
     * Finds the closest known valid tag to the given tag using edit distance,
     * but only if the distance is small enough to be a likely typo.
     */
    private static String findClosestTag(String tag) {
        int minDistance = Integer.MAX_VALUE;
        String closest = null;

        for (String validTag : VALID_TAGS) {
            int distance = editDistance(tag, validTag);
            if (distance < minDistance) {
                minDistance = distance;
                closest = validTag;
            }
        }

        // Only suggest if the edit distance is small relative to the tag length
        // This avoids false positives for completely unrelated tags
        if (closest != null) {
            int threshold = Math.max(2, tag.length() / 4);
            if (minDistance <= threshold) {
                return closest;
            }
        }

        return null;
    }

    /**
     * Computes the Levenshtein edit distance between two strings.
     */
    private static int editDistance(String s, String t) {
        int m = s.length();
        int n = t.length();

        int[][] dp = new int[m + 1][n + 1];

        for (int i = 0; i <= m; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= n; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (s.charAt(i - 1) == t.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j - 1],
                            Math.min(dp[i - 1][j], dp[i][j - 1]));
                }
            }
        }

        return dp[m][n];
    }
}