package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.temporal.failure.ApplicationFailure;
import org.junit.jupiter.api.Test;

class HelloActivitiesImplTest {

  @Test
  void composeGreetingReturnsExpectedGreeting() {
    HelloActivitiesImpl activities = new HelloActivitiesImpl();

    String greeting = activities.composeGreeting("Sumo");

    assertThat(greeting).isEqualTo("Hello, Sumo!");
  }

  @Test
  void composeGreetingCanDriveActivityFailureScenario() {
    HelloActivitiesImpl activities = new HelloActivitiesImpl();

    assertThatThrownBy(() -> activities.composeGreeting("scenario:activity-fail"))
        .isInstanceOf(ApplicationFailure.class)
        .hasMessageContaining("Intentional activity failure");
  }
}
