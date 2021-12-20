package io.opentelemetry.instrumentation.logback.v1_0;

import static ch.qos.logback.classic.Level.ALL_INT;
import static ch.qos.logback.classic.Level.DEBUG_INT;
import static ch.qos.logback.classic.Level.ERROR_INT;
import static ch.qos.logback.classic.Level.INFO_INT;
import static ch.qos.logback.classic.Level.OFF_INT;
import static ch.qos.logback.classic.Level.TRACE_INT;
import static ch.qos.logback.classic.Level.WARN_INT;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.logs.LogBuilder;
import io.opentelemetry.sdk.logs.SdkLogEmitterProvider;
import io.opentelemetry.sdk.logs.data.Severity;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class OtlpAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
  private static final AtomicReference<SdkLogEmitterProvider> logEmitterProviderRef = new AtomicReference<>();

  // Visible for testing
  static final AttributeKey<String> ATTR_THROWABLE_MESSAGE =
      AttributeKey.stringKey("throwable.message");

  public static void setLogEmitterProvider(SdkLogEmitterProvider logEmitterProvider) {
    final boolean wasSet = logEmitterProviderRef.compareAndSet(null, logEmitterProvider);
    if (!wasSet) {
      throw new IllegalStateException(
          "OtlpAppender.setLogEmitterProvider has already been called. OpenTelemetryLog4j.initialize "
              + "must be called only once. Previous invocation set to cause of this exception.");
    }
  }

  @Override
  protected void append(ILoggingEvent eventObject) {
    final SdkLogEmitterProvider sdkLogEmitterProvider = logEmitterProviderRef.get();
    if (sdkLogEmitterProvider == null) {
      return;
    }

    final LogBuilder logBuilder = sdkLogEmitterProvider.logEmitterBuilder(
            eventObject.getLoggerName())
        .build()
        .logBuilder()
        .setEpoch(eventObject.getTimeStamp(), TimeUnit.MILLISECONDS)
        .setBody(eventObject.getFormattedMessage())
        .setSeverity(levelToSeverity(eventObject.getLevel()))
        .setSeverityText(eventObject.getLevel().toString())
        .setContext(Context.current());

    final AttributesBuilder attributes = Attributes.builder();

    final IThrowableProxy throwableProxy = eventObject.getThrowableProxy();
    if (throwableProxy != null) {
      attributes.put(ATTR_THROWABLE_MESSAGE, throwableProxy.getMessage());
    }

    logBuilder.setAttributes(attributes.build());
    logBuilder.emit();
  }

  private static Severity levelToSeverity(Level level) {
    switch (level.toInt()) {
      case ALL_INT:
        return Severity.TRACE;
      case TRACE_INT:
        return Severity.TRACE2;
      case DEBUG_INT:
        return Severity.DEBUG;
      case INFO_INT:
        return Severity.INFO;
      case WARN_INT:
        return Severity.WARN;
      case ERROR_INT:
        return Severity.ERROR;
      case OFF_INT:
      default:
        return Severity.UNDEFINED_SEVERITY_NUMBER;
    }
  }
}
