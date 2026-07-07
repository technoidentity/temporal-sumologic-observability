package com.example.demo;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface HelloActivities {
  @ActivityMethod
  String composeGreeting(String name);
}
