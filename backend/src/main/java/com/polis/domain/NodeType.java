package com.polis.domain;

/** Resource-node flavours. Each yields one BASE resource and has a base hourly production rate. */
public enum NodeType {
  SACRED_GROVE(ResourceType.WOOD),
  MARBLE_QUARRY(ResourceType.STONE),
  WHEAT_FIELD(ResourceType.WHEAT);

  public final ResourceType producedResource;
  NodeType(ResourceType r){ this.producedResource = r; }
}
