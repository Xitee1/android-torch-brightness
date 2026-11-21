package de.xitee.torchbrightness.curve;

/** Shizuku user service (shell uid) that may run dumpsys. */
interface IDumpService {
    /** Required by Shizuku: called when the user service is destroyed. */
    void destroy() = 16777114;

    /** Relevant lines of `dumpsys display` (see DumpParser.relevantLines). */
    String dumpDisplay() = 1;
}
