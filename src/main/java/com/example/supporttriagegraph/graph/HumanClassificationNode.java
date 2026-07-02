package com.example.supporttriagegraph.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class HumanClassificationNode implements NodeAction {

  @Override
  public Map<String, Object> apply(OverAllState state) {
    return Map.of();
  }

}
