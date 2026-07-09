package com.example.demo;

import io.temporal.failure.ApplicationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HelloActivitiesImpl implements HelloActivities {

  private static final Logger log = LoggerFactory.getLogger(HelloActivitiesImpl.class);

  @Override
  public String composeGreeting(String name) {
    log.info("HelloActivity composing greeting for name={}", name);
    if ("scenario:activity-fail".equals(name)) {
      throw ApplicationFailure.newNonRetryableFailure(
          "Intentional activity failure for dashboard validation", "DashboardActivityFailure");
    }

    String greeting = "Hello, " + name + "!";
    log.info("HelloActivity produced greeting={}", greeting);
    return greeting;
  }
}
