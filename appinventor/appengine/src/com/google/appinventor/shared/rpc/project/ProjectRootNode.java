// Copyright 2007 Google Inc. All Rights Reserved.

package com.google.appinventor.shared.rpc.project;

import java.util.ArrayList;
import java.util.List;

/**
 * All projects have a subclass of this project node at their root.
 *
 */
public abstract class ProjectRootNode extends ProjectNode {

  // For serialization
  private static final long serialVersionUID = 770074523523022847L;

  // Project id
  private long projectId;

  // Type of project
  private String type;

  /**
   * Default constructor (for serialization only). Unfortunately this will
   * prevent any fields from being marked as final!
   */
  public ProjectRootNode() {
  }

  /**
   * Creates a new project root node.
   *
   * @param name project node name (can be different from file or folder
   *        represented by this node)
   * @param projectId project ID
   * @param type project type
   */
  public ProjectRootNode(String name, long projectId, String type) {
    super(name, Long.toString(projectId));

    this.projectId = projectId;
    this.type = type;
  }

  /**
   * Returns a list of all source nodes of the project.
   *
   * @return list of source project nodes
   */
  public List<ProjectNode> getAllSourceNodes() {
    List<ProjectNode> sourceNodes = new ArrayList<ProjectNode>();
    findSourceNodes(sourceNodes);
    return sourceNodes;
  }

  @Override
  public ProjectRootNode getProjectRoot() {
    return this;
  }

  @Override
  public long getProjectId() {
    return projectId;
  }

//  /**
//   * Returns the ID for this project node.
//   *
//   * @return ID to identify the file/folder represented by the project node on
//   *         the backend (can be {@code null} for 'virtual' folders)
//   */
//  @Override
//  public String getFileId() {
//    throw new UnsupportedOperationException("getFileId is not supported for ProjectRootNode");
//  }

  @Override
  public String getProjectType() {
    return type;
  }
}
