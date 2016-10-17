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
import org.eclipselabs.garbagecat.domain.OldCollection;
import org.eclipselabs.garbagecat.domain.OldData;
import org.eclipselabs.garbagecat.domain.PermCollection;
import org.eclipselabs.garbagecat.domain.PermData;
import org.eclipselabs.garbagecat.domain.YoungData;
import org.eclipselabs.garbagecat.util.jdk.JdkMath;
import org.eclipselabs.garbagecat.util.jdk.JdkRegEx;
import org.eclipselabs.garbagecat.util.jdk.JdkUtil;

/**
 * <p>
 * PAR_NEW_PROMOTION_FAILED_CMS_SERIAL_OLD_PERM_DATA
 * </p>
 * 
 * <p>
 * Combined {@link org.eclipselabs.garbagecat.domain.jdk.ParNewPromotionFailedEvent} and
 * {@link org.eclipselabs.garbagecat.domain.jdk.CmsSerialOldEvent} with perm data. Occurs when objects cannot be moved
 * from the young to the old generation due to lack of space or fragmentation. The young generation collection backs out
 * of the young collection and initiates a {@link org.eclipselabs.garbagecat.domain.jdk.CmsSerialOldEvent} full
 * collection in an attempt to free up and compact space. This is an expensive operation that typically results in large
 * pause times.
 * </p>
 * 
 * <p>
 * The CMS collector is not a compacting collector. It discovers garbage and adds the memory to free lists of available
 * space that it maintains based on popular object sizes. If many objects of varying sizes are allocated, the free lists
 * will be split. This can lead to many free lists whose total size is large enough to satisfy the calculated free space
 * needed for promotions; however, there is not enough contiguous space for one of the objects being promoted.
 * </p>
 * 
 * <p>
 * Prior to Java 5.0 the space requirement was the worst-case scenario that all young generation objects get promoted to
 * the old generation (the young generation guarantee). Starting in Java 5.0 the space requirement is an estimate based
 * on recent promotion history and is usually much less than the young generation guarantee.
 * </p>
 * 
 * <h3>Example Logging</h3>
 * 
 * <p>
 * 1) Incremental mode:
 * </p>
 * 
 * <pre>
 * 395950.370: [GC 395950.370: [ParNew (promotion failed): 53094K-&gt;53606K(59008K), 0.0510880 secs]395950.421: [CMS: 664527K-&gt;317110K(1507328K), 2.9523520 secs] 697709K-&gt;317110K(1566336K), [CMS Perm : 83780K-&gt;83711K(131072K)], 3.0039040 secs]
 * </pre>
 * 
 * <p>
 * The CMS collector run in incremental mode (icms), enabled with <code>-XX:+CMSIncrementalMode</code>. In this mode,
 * the CMS collector does not hold the processor for the entire long concurrent phases but periodically stops them and
 * yields the processor back to other threads in application. It divides the work to be done in concurrent phases into
 * small chunks called duty cycles and schedules them between minor collections. This is very useful for applications
 * that need low pause times and are run on machines with a small number of processors.
 * </p>
 * 
 * <pre>
 * 4595.651: [GC 4595.651: [ParNew (promotion failed): 1304576K-&gt;1304576K(1304576K), 1.7740754 secs]4597.425: [CMS: 967034K-&gt;684015K(4886528K), 3.2678588 secs] 2022731K-&gt;684015K(6191104K), [CMS Perm : 201541K-&gt;201494K(524288K)] icms_dc=21 , 5.0421688 secs] [Times: user=5.54 sys=0.01, real=5.04 secs]
 * </pre>
 * 
 * <p>
 * 2) Not space after GC, not incremental mode:
 * </p>
 * 
 * <pre>
 * 108537.519: [GC108537.520: [ParNew (promotion failed): 1409215K-&gt;1426861K(1567616K), 0.4259330 secs]108537.946: [CMS: 13135135K-&gt;4554003K(16914880K), 14.7637760 secs] 14542753K-&gt;4554003K(18482496K), [CMS Perm : 227503K-&gt;226115K(378908K)], 15.1927120 secs] [Times: user=16.31 sys=0.21, real=15.19 secs]
 * </pre>
 * 
 * <p>
 * 3) With <code>-XX:+PrintClassHistogram</code> after preprocessing:
 * </p>
 * 
 * <pre>
 * 182314.858: [GC 182314.859: [ParNew (promotion failed): 516864K-&gt;516864K(516864K), 2.0947428 secs]182316.954: [Class Histogram:, 41.3875632 secs]182358.342: [CMS: 3354568K-&gt;756393K(7331840K), 53.1398170 secs]182411.482: [Class Histogram, 11.0299920 secs] 3863904K-&gt;756393K(7848704K), [CMS Perm : 682507K-&gt;442221K(1048576K)], 107.6553710 secs] [Times: user=112.83 sys=0.28, real=107.66 secs]
 * </pre>
 * 
 * @author <a href="mailto:mmillson@redhat.com">Mike Millson</a>
 * @author jborelo
 */
public class ParNewPromotionFailedCmsSerialOldPermDataEvent
        implements BlockingEvent, OldCollection, PermCollection, YoungData, OldData, PermData, CmsCollection {

    /**
     * Regular expressions defining the logging.
     */
    private static final String REGEX = "^" + JdkRegEx.TIMESTAMP + ": \\[GC( )?" + JdkRegEx.TIMESTAMP
            + ": \\[ParNew( \\(promotion failed\\))?: " + JdkRegEx.SIZE + "->" + JdkRegEx.SIZE + "\\(" + JdkRegEx.SIZE
            + "\\), " + JdkRegEx.DURATION + "\\](" + JdkRegEx.TIMESTAMP + ": \\[Class Histogram: , " + JdkRegEx.DURATION
            + "\\])?" + JdkRegEx.TIMESTAMP + ": \\[CMS: " + JdkRegEx.SIZE + "->" + JdkRegEx.SIZE + "\\(" + JdkRegEx.SIZE
            + "\\), " + JdkRegEx.DURATION + "\\](" + JdkRegEx.TIMESTAMP + ": \\[Class Histogram, " + JdkRegEx.DURATION
            + "\\])? " + JdkRegEx.SIZE + "->" + JdkRegEx.SIZE + "\\(" + JdkRegEx.SIZE + "\\), \\[CMS Perm : "
            + JdkRegEx.SIZE + "->" + JdkRegEx.SIZE + "\\(" + JdkRegEx.SIZE + "\\)\\]" + JdkRegEx.ICMS_DC_BLOCK + "?, "
            + JdkRegEx.DURATION + "\\]" + JdkRegEx.TIMES_BLOCK + "?[ ]*$";
    private static Pattern pattern = Pattern.compile(ParNewPromotionFailedCmsSerialOldPermDataEvent.REGEX);

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
     * Young generation size (kilobytes) at beginning of GC event.
     */
    private int young;

    /**
     * Young generation size (kilobytes) at end of GC event.
     */
    private int youngEnd;

    /**
     * Available space in young generation (kilobytes). Equals young generation allocation minus one survivor space.
     */
    private int youngAvailable;

    /**
     * Old generation size (kilobytes) at beginning of GC event.
     */
    private int old;

    /**
     * Old generation size (kilobytes) at end of GC event.
     */
    private int oldEnd;

    /**
     * Space allocated to old generation (kilobytes).
     */
    private int oldAllocation;

    /**
     * Permanent generation size (kilobytes) at beginning of GC event.
     */
    private int permGen;

    /**
     * Permanent generation size (kilobytes) at end of GC event.
     */
    private int permGenEnd;

    /**
     * Space allocated to permanent generation (kilobytes).
     */
    private int permGenAllocation;

    /**
     * Create event from log entry.
     * 
     * @param logEntry
     *            The log entry for the event.
     */
    public ParNewPromotionFailedCmsSerialOldPermDataEvent(String logEntry) {
        this.logEntry = logEntry;
        Matcher matcher = pattern.matcher(logEntry);
        if (matcher.find()) {
            timestamp = JdkMath.convertSecsToMillis(matcher.group(1)).longValue();
            old = Integer.parseInt(matcher.group(13));
            oldEnd = Integer.parseInt(matcher.group(14));
            oldAllocation = Integer.parseInt(matcher.group(15));
            int totalBegin = Integer.parseInt(matcher.group(20));
            // Don't use ParNew values because those are presumably before the promotion failure.
            young = totalBegin - old;
            int totalEnd = Integer.parseInt(matcher.group(21));
            youngEnd = totalEnd - oldEnd;
            int totalAllocation = Integer.parseInt(matcher.group(22));
            youngAvailable = totalAllocation - oldAllocation;
            permGen = Integer.parseInt(matcher.group(23));
            permGenEnd = Integer.parseInt(matcher.group(24));
            permGenAllocation = Integer.parseInt(matcher.group(25));
            duration = JdkMath.convertSecsToMillis(matcher.group(27)).intValue();
        }
    }

    /**
     * Alternate constructor. Create ParNew detail logging event from values.
     * 
     * @param logEntry
     *            The log entry for the event.
     * @param timestamp
     *            The time when the GC event happened in milliseconds after JVM startup.
     * @param duration
     *            The elapsed clock time for the GC event in milliseconds.
     */
    public ParNewPromotionFailedCmsSerialOldPermDataEvent(String logEntry, long timestamp, int duration) {
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

    public int getYoungOccupancyInit() {
        return young;
    }

    public int getYoungOccupancyEnd() {
        return youngEnd;
    }

    public int getYoungSpace() {
        return youngAvailable;
    }

    public int getOldOccupancyInit() {
        return old;
    }

    public int getOldOccupancyEnd() {
        return oldEnd;
    }

    public int getOldSpace() {
        return oldAllocation;
    }

    public String getName() {
        return JdkUtil.LogEventType.PAR_NEW_PROMOTION_FAILED_CMS_SERIAL_OLD_PERM_DATA.toString();
    }

    public int getPermOccupancyInit() {
        return permGen;
    }

    public int getPermOccupancyEnd() {
        return permGenEnd;
    }

    public int getPermSpace() {
        return permGenAllocation;
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
