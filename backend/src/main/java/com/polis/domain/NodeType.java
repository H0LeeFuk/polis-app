package com.polis.domain;

/** Resource-node flavours. Each yields one resource and has a base hourly production rate. */
public enum NodeType {
  SACRED_GROVE(ResourceType.WOOD),
  MARBLE_QUARRY(ResourceType.STONE),
  SILVER_VEIN(ResourceType.SILVER);

  public final ResourceType producedResource;
  NodeType(ResourceType r){ this.producedResource = r; }
}
