package com.example.demo;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface HelloWorkflow {
  @WorkflowMethod
  String sayHello(String name);
}
