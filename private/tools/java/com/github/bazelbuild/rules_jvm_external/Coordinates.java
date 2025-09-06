// Copyright 2024 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.github.bazelbuild.rules_jvm_external;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a Maven coordinate using Maven's standard schema of
 * <groupId>:<artifactId>[:<extension>[:<classifier>][:<version>].
 */
public class Coordinates implements Comparable<Coordinates> {
  private final String groupId;
  private final String artifactId;
  private final String version;
  private final String classifier;
  private final String extension;
  private static final String SNAPSHOT = "SNAPSHOT";
  private static final Pattern SNAPSHOT_TIMESTAMP = Pattern.compile("^(.*-)?([0-9]{8}\\.[0-9]{6}-[0-9]+)$");

  public Coordinates(String coordinates) {
    Objects.requireNonNull(coordinates, "Coordinates");

    String[] parts = coordinates.split(":");
    if (parts.length > 5 || parts.length < 2) {
      throw new IllegalArgumentException(
          "Bad artifact coordinates "
              + coordinates
              + ", expected format is"
              + " <groupId>:<artifactId>[:<extension>[:<classifier>][:<version>]");
    }

    groupId = Objects.requireNonNull(parts[0]);
    artifactId = Objects.requireNonNull(parts[1]);

    if (parts.length == 2) {
      extension = "jar";
      classifier = "";
      version = "";
    } else if (parts.length == 3) {
      extension = "jar";
      classifier = "";
      version = parts[2];
    } else if (parts.length == 4) {
      extension = parts[2];
      classifier = "";
      version = parts[3];
    } else {
      extension = "".equals(parts[2]) ? "jar" : parts[2];
      classifier = "jar".equals(parts[3]) ? "" : parts[3];
      version = parts[4];
    }
  }

  public Coordinates(
      String groupId, String artifactId, String extension, String classifier, String version) {
    this.groupId = Objects.requireNonNull(groupId, "Group ID");
    this.artifactId = Objects.requireNonNull(artifactId, "Artifact ID");
    this.extension = extension == null || extension.isEmpty() ? "jar" : extension;
    this.classifier =
        classifier == null || classifier.isEmpty() || "jar".equals(classifier) ? "" : classifier;
    this.version = version == null || version.isEmpty() ? "" : version;
  }

  public String getGroupId() {
    return groupId;
  }

  public String getArtifactId() {
    return artifactId;
  }

  public String getVersion() {
    return version;
  }

  public String getClassifier() {
    return classifier;
  }

  public Coordinates setClassifier(String classifier) {
    return new Coordinates(getGroupId(), getArtifactId(), getExtension(), classifier, getVersion());
  }

  public Coordinates setExtension(String extension) {
    return new Coordinates(getGroupId(), getArtifactId(), extension, getClassifier(), getVersion());
  }

  public Coordinates setVersion(String version) {
    return new Coordinates(getGroupId(), getArtifactId(), getExtension(), getClassifier(), version);
  }

  public String getExtension() {
    return extension;
  }

  public String asKey() {
    StringBuilder coords = new StringBuilder();
    coords.append(groupId).append(":").append(artifactId);

    if (getClassifier() != null && !getClassifier().isEmpty() && !"jar".equals(getClassifier())) {
      String extension = getExtension();
      if (extension == null || extension.isEmpty()) {
        extension = "jar";
      }
      coords.append(":").append(extension).append(":").append(getClassifier());
    } else {
      // Otherwise, we just check for the extension
      if (getExtension() != null && !getExtension().isEmpty() && !"jar".equals(getExtension())) {
        coords.append(":").append(getExtension());
      }
    }

    return coords.toString();
  }

  public boolean isTimestampedSnapshot() {
    return SNAPSHOT_TIMESTAMP.matcher(this.version).matches();
  }

  private static String toBaseVersion(String version) {
    String baseVersion;

    if (version == null) {
      baseVersion = null;
    } else if (version.startsWith("[") || version.startsWith("(")) {
      baseVersion = version;
    } else {
      Matcher m = SNAPSHOT_TIMESTAMP.matcher(version);
      if (m.matches()) {
        if (m.group(1) != null) {
          baseVersion = m.group(1) + SNAPSHOT;
        } else {
          baseVersion = SNAPSHOT;
        }
      } else {
        baseVersion = version;
      }
    }

    return baseVersion;
  }

  public String toRepoPath() {
    String dirVersion;
    String fileVersion;

    if (isTimestampedSnapshot()) {
      // Directory is the base SNAPSHOT
      dirVersion = toBaseVersion(getVersion());
      fileVersion = getVersion();
    } else {
      // Normal release or plain -SNAPSHOT (no timestamp)
      dirVersion = getVersion();
      fileVersion = getVersion();
    }

    StringBuilder path = new StringBuilder();
    path.append(getGroupId().replace('.', '/'))
            .append('/')
            .append(getArtifactId())
            .append('/')
            .append(dirVersion)
            .append('/')
            .append(getArtifactId())
            .append('-')
            .append(fileVersion);

    String classifier = getClassifier();
    if (classifier != null && !classifier.isEmpty() && !"jar".equals(classifier)) {
      path.append('-').append(classifier);
    }

    String ext = getExtension();
    path.append('.').append(ext == null || ext.isEmpty() ? "jar" : ext);

    return path.toString();
  }

  @Override
  public int compareTo(Coordinates o) {
    return this.toString().compareTo(o.toString());
  }

  public String toString() {
    String versionless = asKey();

    if (version != null && !version.isEmpty()) {
      return versionless + ":" + version;
    }

    return versionless;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof Coordinates)) {
      return false;
    }

    Coordinates that = (Coordinates) o;
    return getGroupId().equals(that.getGroupId())
        && getArtifactId().equals(that.getArtifactId())
        && Objects.equals(getVersion(), that.getVersion())
        && Objects.equals(getClassifier(), that.getClassifier())
        && Objects.equals(getExtension(), that.getExtension());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        getGroupId(), getArtifactId(), getVersion(), getClassifier(), getExtension());
  }

  private boolean isNullOrEmpty(String value) {
    return value == null || value.isEmpty();
  }
}
