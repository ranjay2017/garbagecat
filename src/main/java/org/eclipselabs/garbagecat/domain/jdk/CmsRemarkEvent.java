/**********************************************************************************************************************
 * garbagecat                                                                                                         *
 *                                                                                                                    *
 * Copyright (c) 2008-2016 Red Hat, Inc.                                                                              *
 *                                                                                                                    * 
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse *
 * Public License v1.0 which accompanies this distribution, and is available at                                       *
 * http://www.eclipse.org/legal/epl-v10.html.                                                                         *
 *                                                                                                                    *
 * Contributors:                                                                                                      *
 *    Red Hat, Inc. - initial API and implementation                                                                  *
 *********************************************************************************************************************/
package org.eclipselabs.garbagecat.domain.jdk;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipselabs.garbagecat.domain.BlockingEvent;
import org.eclipselabs.garbagecat.domain.TriggerData;
import org.eclipselabs.garbagecat.util.jdk.JdkMath;
import org.eclipselabs.garbagecat.util.jdk.JdkRegEx;
import org.eclipselabs.garbagecat.util.jdk.JdkUtil;

/**
 * <p>
 * CMS_REMARK
 * </p>
 * 
 * <p>
 * The second stop-the-world phase of the concurrent low pause collector. All live objects are marked, starting with the
 * objects identified in the {@link org.eclipselabs.garbagecat.domain.jdk.CmsInitialMarkEvent}. This event does not do
 * any garbage collection. It rescans objects directly reachable from GC roots, processes weak references, and remarks
 * objects. It is actually 3 events, but for GC analysis, it is treated as one event.
 * </p>
 * 
 * <h3>Example Logging</h3>
 * 
 * <p>
 * 1) Standard format:
 * </p>
 * 
 * <pre>
 * 253.103: [GC[YG occupancy: 16172 K (149120 K)]253.103: [Rescan (parallel) , 0.0226730 secs]253.126: [weak refs processing, 0.0624566 secs] [1 CMS-remark: 4173470K(8218240K)] 4189643K(8367360K), 0.0857010 secs]
 * </pre>
 * 
 * <p>
 * 2) JDK8 after {@link org.eclipselabs.garbagecat.preprocess.jdk.DateStampPrefixPreprocessAction} with trigger, no
 * space after trigger, and no space after "scrub symbol table":
 * </p>
 * 
 * <pre>
 * 13.749: [GC (CMS Final Remark)[YG occupancy: 149636 K (153600 K)]13.749: [Rescan (parallel) , 0.0216980 secs]13.771: [weak refs processing, 0.0005180 secs]13.772: [scrub string table, 0.0015820 secs] [1 CMS-remark: 217008K(341376K)] 366644K(494976K), 0.0239510 secs] [Times: user=0.18 sys=0.00, real=0.02 secs]
 * </pre>
 * 
 * @author <a href="mailto:mmillson@redhat.com">Mike Millson</a>
 * 
 */
public class CmsRemarkEvent implements BlockingEvent, CmsCollection, TriggerData {

    /**
     * The log entry for the event. Can be used for debugging purposes.
     */
    private String logEntry;

    /**
     * The elapsed clock time for the GC event in milliseconds (rounded).
     */
    private int duration;

    /**
     * The time when the GC event happened in milliseconds after JVM startup.
     */
    private long timestamp;

    /**
     * The trigger for the GC event.
     */
    private String trigger;

    /**
     * Regular expressions defining the logging.
     */
    private static final String REGEX = "^" + JdkRegEx.TIMESTAMP + ": \\[GC( \\((" + JdkRegEx.TRIGGER_CMS_FINAL_REMARK
            + ")\\))?\\[YG occupancy: " + JdkRegEx.SIZE + " \\(" + JdkRegEx.SIZE + "\\)\\]" + JdkRegEx.TIMESTAMP
            + ": \\[Rescan \\(parallel\\) , " + JdkRegEx.DURATION + "\\]" + JdkRegEx.TIMESTAMP
            + ": \\[weak refs processing, " + JdkRegEx.DURATION + "\\](" + JdkRegEx.TIMESTAMP
            + ": \\[scrub string table, " + JdkRegEx.DURATION + "\\])? \\[1 CMS-remark: " + JdkRegEx.SIZE + "\\("
            + JdkRegEx.SIZE + "\\)\\] " + JdkRegEx.SIZE + "\\(" + JdkRegEx.SIZE + "\\), " + JdkRegEx.DURATION + "\\]"
            + JdkRegEx.TIMES_BLOCK + "?[ ]*$";

    private static Pattern pattern = Pattern.compile(CmsRemarkEvent.REGEX);

    /**
     * Create event from log entry.
     * 
     * @param logEntry
     *            The log entry for the event.
     */
    public CmsRemarkEvent(String logEntry) {
        this.logEntry = logEntry;
        Matcher matcher = pattern.matcher(logEntry);
        if (matcher.find()) {
            timestamp = JdkMath.convertSecsToMillis(matcher.group(1)).longValue();
            trigger = matcher.group(3);
            // The last duration is the total duration for the phase.
            duration = JdkMath.convertSecsToMillis(matcher.group(17)).intValue();
        }
    }

    /**
     * Alternate constructor. Create CMS Remark logging event from values.
     * 
     * @param logEntry
     *            The log entry for the event.
     * @param timestamp
     *            The time when the GC event happened in milliseconds after JVM startup.
     * @param duration
     *            The elapsed clock time for the GC event in milliseconds.
     */
    public CmsRemarkEvent(String logEntry, long timestamp, int duration) {
        this.logEntry = logEntry;
        this.timestamp = timestamp;
        this.duration = duration;
    }

    public String getLogEntry() {
        return logEntry;
    }

    public int getDuration() {
        return duration;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getName() {
        return JdkUtil.LogEventType.CMS_REMARK.toString();
    }

    public String getTrigger() {
        return trigger;
    }

    /**
     * Determine if the logLine matches the logging pattern(s) for this event.
     * 
     * @param logLine
     *            The log line to test.
     * @return true if the log line matches the event pattern, false otherwise.
     */
    public static final boolean match(String logLine) {
        return pattern.matcher(logLine).matches();
    }
}
