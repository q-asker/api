package com.icc.qasker.global.filter;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

public class NoResourceFoundSlackFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        IThrowableProxy tp = event.getThrowableProxy();
        while (tp != null) {
            String className = tp.getClassName();
            if (className != null && className.endsWith("NoResourceFoundException")) {
                return FilterReply.DENY;
            }
            tp = tp.getCause();
        }
        return FilterReply.NEUTRAL;
    }
}