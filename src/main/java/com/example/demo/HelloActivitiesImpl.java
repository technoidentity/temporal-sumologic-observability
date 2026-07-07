package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HelloActivitiesImpl implements HelloActivities {

  private static final Logger log = LoggerFactory.getLogger(HelloActivitiesImpl.class);

  @Override
  public String composeGreeting(String name) {
    log.info("HelloActivity composing greeting for name={}", name);
    String greeting = "Hello, " + name + "!";
    log.info("HelloActivity produced greeting={}", greeting);
    return greeting;
  }
}
