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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Typos in manifest tags",
                    "This check looks through the manifest, and if it finds any tags that look "
                            + "like likely misspellings, they are flagged.",
                    Category.CORRECTNESS,
                    5,
                    Severity.FATAL,
                    IMPLEMENTATION);

    /**
     * Map from common typo/misspelling to the correct tag name.
     */
    private static final Map<String, String> TYPOS = new HashMap<>();

    static {
        // Common typos for manifest tags
        TYPOS.put("activty", "activity");
        TYPOS.put("activiy", "activity");
        TYPOS.put("activiti", "activity");
        TYPOS.put("actvity", "activity");
        TYPOS.put("activitiy", "activity");
        TYPOS.put("activty", "activity");

        TYPOS.put("sevice", "service");
        TYPOS.put("serivce", "service");
        TYPOS.put("srevice", "service");
        TYPOS.put("servce", "service");
        TYPOS.put("serviice", "service");
        TYPOS.put("servcie", "service");

        TYPOS.put("reciver", "receiver");
        TYPOS.put("reciever", "receiver");
        TYPOS.put("receiever", "receiver");
        TYPOS.put("receivr", "receiver");
        TYPOS.put("recevier", "receiver");
        TYPOS.put("reciver", "receiver");

        TYPOS.put("provder", "provider");
        TYPOS.put("providor", "provider");
        TYPOS.put("proivder", "provider");
        TYPOS.put("provdier", "provider");
        TYPOS.put("providr", "provider");

        TYPOS.put("itent-filter", "intent-filter");
        TYPOS.put("inten-filter", "intent-filter");
        TYPOS.put("intentfilter", "intent-filter");
        TYPOS.put("intent-filtr", "intent-filter");
        TYPOS.put("intent-fliter", "intent-filter");
        TYPOS.put("intent-filer", "intent-filter");
        TYPOS.put("intent-filtter", "intent-filter");

        TYPOS.put("aplication", "application");
        TYPOS.put("applicaton", "application");
        TYPOS.put("applcation", "application");
        TYPOS.put("applicaiton", "application");
        TYPOS.put("applicatoin", "application");
        TYPOS.put("appliation", "application");
        TYPOS.put("applicaion", "application");

        TYPOS.put("manfest", "manifest");
        TYPOS.put("manifset", "manifest");
        TYPOS.put("manifiest", "manifest");
        TYPOS.put("mainifest", "manifest");
        TYPOS.put("manifst", "manifest");

        TYPOS.put("permision", "permission");
        TYPOS.put("permisson", "permission");
        TYPOS.put("persmission", "permission");
        TYPOS.put("permisison", "permission");
        TYPOS.put("permssion", "permission");

        TYPOS.put("uses-permsion", "uses-permission");
        TYPOS.put("uses-permision", "uses-permission");
        TYPOS.put("uses-permisson", "uses-permission");
        TYPOS.put("usespermission", "uses-permission");
        TYPOS.put("uses-persmission", "uses-permission");
        TYPOS.put("user-permission", "uses-permission");
        TYPOS.put("uses-permisison", "uses-permission");
        TYPOS.put("uses-permssion", "uses-permission");

        TYPOS.put("uses-feture", "uses-feature");
        TYPOS.put("uses-featrue", "uses-feature");
        TYPOS.put("uses-feautre", "uses-feature");
        TYPOS.put("usesfeature", "uses-feature");
        TYPOS.put("uses-fetaure", "uses-feature");

        TYPOS.put("uses-libary", "uses-library");
        TYPOS.put("uses-libarary", "uses-library");
        TYPOS.put("uses-librray", "uses-library");
        TYPOS.put("useslibrary", "uses-library");

        TYPOS.put("uses-sdk", "uses-sdk");  // correct, but add common typos
        TYPOS.put("use-sdk", "uses-sdk");
        TYPOS.put("uses-skd", "uses-sdk");
        TYPOS.put("usessdk", "uses-sdk");

        TYPOS.put("actvity-alias", "activity-alias");
        TYPOS.put("activty-alias", "activity-alias");
        TYPOS.put("activity-alais", "activity-alias");
        TYPOS.put("activity-alis", "activity-alias");

        TYPOS.put("meta-dat", "meta-data");
        TYPOS.put("metadata", "meta-data");
        TYPOS.put("meta-dta", "meta-data");
        TYPOS.put("meta-data", "meta-data"); // correct

        TYPOS.put("catagory", "category");
        TYPOS.put("categroy", "category");
        TYPOS.put("categori", "category");
        TYPOS.put("catgory", "category");

        TYPOS.put("acton", "action");
        TYPOS.put("actoin", "action");
        TYPOS.put("actiion", "action");

        TYPOS.put("dat", "data");
        TYPOS.put("dta", "data");

        TYPOS.put("grnat-uri-permission", "grant-uri-permission");
        TYPOS.put("grant-uri-permision", "grant-uri-permission");
        TYPOS.put("grant-uri-permisson", "grant-uri-permission");
        TYPOS.put("grant-uri-persmission", "grant-uri-permission");

        TYPOS.put("instrumentation", "instrumentation"); // correct
        TYPOS.put("instrumntation", "instrumentation");
        TYPOS.put("instrumentaion", "instrumentation");
        TYPOS.put("instrumetnation", "instrumentation");

        TYPOS.put("suppots-screens", "supports-screens");
        TYPOS.put("supports-screns", "supports-screens");
        TYPOS.put("support-screens", "supports-screens");
        TYPOS.put("supports-screen", "supports-screens");

        TYPOS.put("compatible-screens", "compatible-screens"); // correct
        TYPOS.put("compatable-screens", "compatible-screens");
        TYPOS.put("compatible-screns", "compatible-screens");

        TYPOS.put("protecion-level", "protection-level");
        TYPOS.put("permision-group", "permission-group");
        TYPOS.put("permission-grup", "permission-group");
        TYPOS.put("permission-grp", "permission-group");

        TYPOS.put("permission-tre", "permission-tree");
        TYPOS.put("permission-tee", "permission-tree");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(ALL_ELEMENTS_AND_ATTRIBUTES);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getLocalName();
        if (tag == null) {
            tag = element.getTagName();
        }
        if (tag == null) {
            return;
        }

        String corrected = TYPOS.get(tag.toLowerCase());
        // Only flag it if the corrected version is different from the tag
        // (i.e., we don't want to flag correctly spelled tags that happen to be in the map)
        if (corrected != null && !corrected.equals(tag)) {
            String message = String.format(
                    "Misspelled tag `<%1$s>`: Did you mean `<%2$s>`?",
                    tag, corrected);
            context.report(ISSUE, element, context.getLocation(element), message);
        } else if (corrected == null) {
            // Check using edit distance for near-matches
            String suggestion = findSuggestion(tag);
            if (suggestion != null) {
                String message = String.format(
                        "Misspelled tag `<%1$s>`: Did you mean `<%2$s>`?",
                        tag, suggestion);
                context.report(ISSUE, element, context.getLocation(element), message);
            }
        }
    }

    /**
     * Known valid manifest tags.
     */
    private static final String[] VALID_TAGS = {
        "manifest",
        "application",
        "activity",
        "activity-alias",
        "service",
        "receiver",
        "provider",
        "intent-filter",
        "action",
        "category",
        "data",
        "meta-data",
        "uses-library",
        "uses-permission",
        "uses-permission-sdk-23",
        "permission",
        "permission-group",
        "permission-tree",
        "uses-feature",
        "uses-sdk",
        "instrumentation",
        "supports-screens",
        "compatible-screens",
        "screen",
        "grant-uri-permission",
        "path-permission",
        "queries",
        "package",
        "intent",
        "profileable",
        "uses-native-library",
        "property",
        "attribution",
    };

    /**
     * Finds a suggestion for a possible typo using edit distance.
     *
     * @param tag the tag name to check
     * @return a suggestion if the tag is close to a known valid tag, or null
     */
    private static String findSuggestion(@NonNull String tag) {
        String lowerTag = tag.toLowerCase();
        int bestDistance = Integer.MAX_VALUE;
        String bestMatch = null;

        for (String validTag : VALID_TAGS) {
            if (validTag.equals(lowerTag)) {
                // It's a valid tag, no suggestion needed
                return null;
            }
            int distance = editDistance(lowerTag, validTag);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestMatch = validTag;
            }
        }

        // Only suggest if within edit distance of 2 and lengths are similar
        if (bestDistance <= 2 && bestMatch != null) {
            int lenDiff = Math.abs(tag.length() - bestMatch.length());
            if (lenDiff <= 2) {
                return bestMatch;
            }
        }

        return null;
    }

    /**
     * Computes the Levenshtein edit distance between two strings.
     */
    private static int editDistance(@NonNull String s, @NonNull String t) {
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