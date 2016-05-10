package com.jhcompany.graduation;

import org.joda.time.DateTime;

public final class TimestampUtils {

    public static String getTimestamp() {

//        TimeZone tz = TimeZone.getTimeZone("UTC");
//        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
//        df.setTimeZone(tz);
        return new DateTime().toString();
    }
}
