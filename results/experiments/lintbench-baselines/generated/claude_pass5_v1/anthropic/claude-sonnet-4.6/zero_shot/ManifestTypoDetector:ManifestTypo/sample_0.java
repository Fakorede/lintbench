package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.ResourceXmlDetector;

import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks for typos in AndroidManifest.xml tags.
 */
public class ManifestTypoDetector extends ResourceXmlDetector implements XmlScanner {

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
     * Map from known correct tag names to lists of common misspellings.
     */
    private static final Map<String, List<String>> TYPOS;

    static {
        TYPOS = new HashMap<>();

        TYPOS.put("manifest", Arrays.asList(
                "menifest", "manifets", "mainifest", "manifiest", "manifast",
                "manifset", "manifist", "manigest", "manifeest", "manifets"));

        TYPOS.put("application", Arrays.asList(
                "aplcation", "aplication", "applicaton", "applicaiton",
                "applicaton", "applcation", "applicaion", "applicatoin",
                "applicatino", "appication", "appliation", "appliaction",
                "applicaation", "applciation", "applicattion", "applicatiom"));

        TYPOS.put("activity", Arrays.asList(
                "actvity", "activty", "activiy", "acitivity", "activitiy",
                "activiity", "acivity", "actviity", "activty", "activiti",
                "actiivty", "activitty", "acticity", "activitry", "acitivity"));

        TYPOS.put("service", Arrays.asList(
                "sevice", "serivce", "srevice", "servcie", "servce",
                "serviec", "serivce", "serviice", "servive", "srvice",
                "sercive", "servicce", "servcice", "servce"));

        TYPOS.put("receiver", Arrays.asList(
                "reciver", "reciever", "receiever", "recevier", "recever",
                "receivr", "recevier", "reciver", "receviver", "receiveer",
                "recieiver", "recivier"));

        TYPOS.put("provider", Arrays.asList(
                "provder", "proivder", "proivder", "providr", "providor",
                "provideer", "provdier", "provdier", "proivider", "proveder"));

        TYPOS.put("intent-filter", Arrays.asList(
                "intent-flter", "intent-filtr", "intent-filer", "intentfilter",
                "intent-filtter", "intet-filter", "intnet-filter", "intent-fllter",
                "intent-fileter", "intent-fiilter", "itent-filter", "intetn-filter"));

        TYPOS.put("action", Arrays.asList(
                "acton", "actoin", "acion", "actiion", "actioon",
                "actionn", "aciton", "acction", "actiom", "actino"));

        TYPOS.put("category", Arrays.asList(
                "catgory", "catagory", "cateogry", "categry", "categoty",
                "catgeory", "cateory", "catgory", "categoyr", "categori",
                "cagegory", "categorry", "catgegory", "caetgory"));

        TYPOS.put("data", Arrays.asList(
                "dta", "dat", "daat", "daata", "ddata"));

        TYPOS.put("uses-permission", Arrays.asList(
                "uses-permision", "use-permission", "uses-permisson",
                "uses-permisison", "uses-permssion", "uses-permision",
                "usees-permission", "uses-permmission", "uses-permision",
                "uses-permision", "uses-permission-sdk-23"));

        TYPOS.put("uses-feature", Arrays.asList(
                "uses-feture", "use-feature", "uses-freature", "uses-feaure",
                "uses-featur", "uses-featre", "uses-fetaure", "uses-featrue",
                "uses-feeature", "uses-feautre"));

        TYPOS.put("uses-library", Arrays.asList(
                "uses-libary", "use-library", "uses-libraray", "uses-libray",
                "uses-librray", "uses-liibrary", "uses-librray", "uses-libarary"));

        TYPOS.put("uses-sdk", Arrays.asList(
                "use-sdk", "uses-dsk", "uses-skd", "uses-ssk", "uses-sdkk",
                "uses-sddk", "usessdk", "uses-sdk-version"));

        TYPOS.put("permission", Arrays.asList(
                "permision", "permssion", "permmission", "permisison",
                "permision", "permisison", "permsision", "permsision",
                "permisson", "permiission", "permision"));

        TYPOS.put("instrumentation", Arrays.asList(
                "instrumentaion", "instrumenation", "instrumantation",
                "instrumentaton", "instrumenttion", "instrumetation",
                "instrumetation", "instrumntation", "istrumentation",
                "instrumntation", "instumentation"));

        TYPOS.put("meta-data", Arrays.asList(
                "meta-dat", "metadat", "meata-data", "meta-dta",
                "meta-daata", "meda-data", "meata-data", "meta-ddata",
                "metdata", "meta-dataa", "meta-dat"));

        TYPOS.put("activity-alias", Arrays.asList(
                "activity-alais", "activity-alis", "activty-alias",
                "actiivty-alias", "activity-aliass", "activity-alais",
                "activiy-alias", "activity-laias"));

        TYPOS.put("grant-uri-permission", Arrays.asList(
                "grant-uri-permision", "grant-uri-permssion",
                "grant-uri-permisson", "grant-uri-permsision",
                "grant-uri-permiision", "grant-uri-permision"));

        TYPOS.put("path-permission", Arrays.asList(
                "path-permision", "path-permssion", "path-permisson",
                "path-permisison", "path-permiision", "path-permision"));

        TYPOS.put("compatible-screens", Arrays.asList(
                "compatable-screens", "compatible-screems", "compatible-sceens",
                "compaitble-screens", "compatible-screeens", "compatiable-screens"));

        TYPOS.put("supports-screens", Arrays.asList(
                "suports-screens", "suppports-screens", "supports-sceens",
                "suupports-screens", "supports-screems", "supportss-screens",
                "support-screens", "supports-screen"));

        TYPOS.put("supports-gl-texture", Arrays.asList(
                "supports-gl-texure", "supports-gl-textures", "suports-gl-texture",
                "supports-gl-textrue", "supports-gl-texutre"));

        TYPOS.put("queries", Arrays.asList(
                "queriess", "querries", "queires", "quaries", "quries",
                "queris", "quereis", "queeris"));

        TYPOS.put("package", Arrays.asList(
                "pakage", "pacakge", "packge", "packgae", "pakage",
                "pakcage", "pacakge", "packge", "packae"));
    }

    /** Constructs a new {@link ManifestTypoDetector} */
    public ManifestTypoDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
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

        // Check if this tag is a typo of a known correct tag
        String correctTag = findCorrectTag(tag);
        if (correctTag != null) {
            String message = String.format(
                    "Misspelled tag `<%1$s>`: Did you mean `<%2$s>`?",
                    tag, correctTag);
            context.report(ISSUE, element, context.getNameLocation(element), message);
        }
    }

    /**
     * Given a potentially misspelled tag, returns the correct tag name if this
     * tag appears to be a misspelling, or null if it's not a recognized typo.
     *
     * @param tag the tag to check
     * @return the correct tag name, or null if it's not a recognized typo
     */
    private static String findCorrectTag(String tag) {
        // First, check if the tag is itself a correct tag name
        if (TYPOS.containsKey(tag)) {
            // It's a valid tag, not a typo
            return null;
        }

        // Check if the tag is in any typo list
        for (Map.Entry<String, List<String>> entry : TYPOS.entrySet()) {
            String correctTag = entry.getKey();
            List<String> typos = entry.getValue();
            for (String typo : typos) {
                if (typo.equals(tag)) {
                    return correctTag;
                }
            }
        }

        // Also check using edit distance for close matches
        String closestTag = findClosestTag(tag);
        return closestTag;
    }

    /**
     * Finds the closest matching correct tag using edit distance,
     * but only if the edit distance is small enough to be a likely typo.
     *
     * @param tag the tag to check
     * @return the closest correct tag, or null if no close match found
     */
    private static String findClosestTag(String tag) {
        int minDistance = Integer.MAX_VALUE;
        String closestTag = null;

        for (String correctTag : TYPOS.keySet()) {
            int distance = editDistance(tag, correctTag);
            // Only consider it a typo if edit distance is 1 or 2
            // and the tag length is similar
            if (distance < minDistance && distance <= 2
                    && Math.abs(tag.length() - correctTag.length()) <= 2) {
                // Make sure the tag is actually different from the correct one
                if (!tag.equals(correctTag)) {
                    minDistance = distance;
                    closestTag = correctTag;
                }
            }
        }

        // Only return a suggestion if the edit distance is small relative to tag length
        if (closestTag != null) {
            // For very short tags, require distance of 1
            if (closestTag.length() <= 4 && minDistance > 1) {
                return null;
            }
            return closestTag;
        }

        return null;
    }

    /**
     * Computes the edit distance (Levenshtein distance) between two strings.
     *
     * @param s the first string
     * @param t the second string
     * @return the edit distance between the two strings
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