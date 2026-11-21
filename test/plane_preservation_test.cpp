// Copyright 2026 The Manifold Authors.
// SPDX-License-Identifier: Apache-2.0

#include <gtest/gtest.h>

#include <cmath>

#include "../src/impl.h"
#include "manifold/manifold.h"
using namespace manifold;
namespace {
MeshGL64 PlateWithBevelAndBoss() {
  MeshGL64 mesh;
  const double x[] = {50, 50, 49.9, 1, 1};
  const double y[] = {10, 10, 9.9, 1, 1};
  const double z[] = {0, 2.98, 3, 3, 4};
  for (int r = 0; r < 5; ++r)
    for (int i = 0; i < 4; ++i) {
      mesh.vertProperties.push_back((i == 0 || i == 3 ? -1 : 1) * x[r]);
      mesh.vertProperties.push_back((i < 2 ? -1 : 1) * y[r]);
      mesh.vertProperties.push_back(z[r]);
    }
  for (int r = 0; r < 4; ++r)
    for (int i = 0; i < 4; ++i) {
      const int j = (i + 1) % 4;
      for (int v : {4 * r + i, 4 * r + j, 4 * (r + 1) + j, 4 * r + i,
                    4 * (r + 1) + j, 4 * (r + 1) + i})
        mesh.triVerts.push_back(v);
    }
  for (int v : {0, 2, 1, 0, 3, 2, 16, 17, 18, 16, 18, 19})
    mesh.triVerts.push_back(v);
  return mesh;
}
double HorizontalArea(const MeshGL64& mesh, double height) {
  double area = 0;
  for (size_t t = 0; t < mesh.triVerts.size(); t += 3) {
    double p[3][3];
    bool flat = true;
    for (int i = 0; i < 3; ++i)
      for (int a = 0; a < 3; ++a)
        p[i][a] = mesh.vertProperties[mesh.triVerts[t + i] * mesh.numProp + a];
    for (int i = 0; i < 3; ++i) flat &= std::abs(p[i][2] - height) < 1e-8;
    if (!flat) continue;
    const double signedArea = ((p[1][0] - p[0][0]) * (p[2][1] - p[0][1]) -
                               (p[1][1] - p[0][1]) * (p[2][0] - p[0][0])) /
                              2;
    if (signedArea > 0) area += signedArea;
  }
  return area;
}
}  // namespace
TEST(Manifold, SimplifyPreservesTheResolvedPlatePlane) {
  const Manifold source(PlateWithBevelAndBoss());
  ASSERT_EQ(source.Status(), Manifold::Error::NoError);
  const Manifold simplified = source.AsOriginal().SimplifyPreservingPlanes(.05);
  ASSERT_EQ(simplified.Status(), Manifold::Error::NoError);
  EXPECT_NEAR(HorizontalArea(source.GetMeshGL64(), 3), 1972.04, 1e-7);
  EXPECT_NEAR(HorizontalArea(simplified.GetMeshGL64(), 3), 1972.04, 1e-7);
  EXPECT_EQ(source.BoundingBox().min, simplified.BoundingBox().min);
  EXPECT_EQ(source.BoundingBox().max, simplified.BoundingBox().max);
  EXPECT_EQ(source.Genus(), simplified.Genus());
}
TEST(Manifold, PlaneConstraintsStillRemoveRedundantFlatTriangles) {
  const Manifold source(PlateWithBevelAndBoss());
  const Manifold dense = source.Refine(4).AsOriginal();
  ASSERT_GT(dense.NumTri(), source.NumTri());
  const Manifold simplified = dense.SimplifyPreservingPlanes(.05);
  ASSERT_EQ(simplified.Status(), Manifold::Error::NoError);
  EXPECT_LE(simplified.NumTri(), source.NumTri());
  EXPECT_NEAR(HorizontalArea(simplified.GetMeshGL64(), 3), 1972.04, 1e-7);
  EXPECT_NEAR(simplified.Volume(), source.Volume(), 1e-7);
}

TEST(Manifold, ResolvedPlaneKeepsItsOutlineBesideUnresolvedCurvedFacets) {
  MeshGL64 mesh;
  const int count = 1024;
  const double x[] = {10, 10, 9.9, .2, .2}, y[] = {3, 3, 2.9, .2, .2},
               z[] = {0, 2.98, 3, 3, 4};
  for (int r = 0; r < 5; ++r)
    for (int i = 0; i < count; ++i) {
      const double a = 2 * 3.141592653589793 * i / count;
      for (double v : {x[r] * std::cos(a), y[r] * std::sin(a), z[r]})
        mesh.vertProperties.push_back(v);
    }
  for (int r = 0; r < 4; ++r)
    for (int i = 0; i < count; ++i) {
      int j = (i + 1) % count;
      for (int v : {r * count + i, r * count + j, (r + 1) * count + j,
                    r * count + i, (r + 1) * count + j, (r + 1) * count + i})
        mesh.triVerts.push_back(v);
    }
  for (int i = 1; i < count - 1; ++i)
    for (int v : {0, i + 1, i, 4 * count, 4 * count + i, 4 * count + i + 1})
      mesh.triVerts.push_back(v);
  const Manifold source(mesh);
  ASSERT_EQ(source.Status(), Manifold::Error::NoError);
  Manifold result = source.AsOriginal();
  for (int pass = 0; pass < 6; ++pass)
    result = result.SimplifyPreservingPlanes(.05);
  ASSERT_EQ(result.Status(), Manifold::Error::NoError);
  // Micro-bevel cleanup may add cap area, but must not eat the resolved cap.
  EXPECT_GE(HorizontalArea(result.GetMeshGL64(), 3) + 1e-7,
            HorizontalArea(source.GetMeshGL64(), 3));
  EXPECT_LT(result.NumTri(), source.NumTri());
  EXPECT_EQ(source.Genus(), result.Genus());
}

TEST(Manifold, IncrementalBoundsFindChangedFacesWithoutMutatingTheSource) {
  Vec<Box> boxes(4);
  Vec<uint32_t> morton(4);
  const Box world(vec3(0, 0, 0), vec3(13, 1, 1));
  for (int i = 0; i < 4; ++i) {
    boxes[i] = Box(vec3(4 * i, 0, 0), vec3(4 * i + 1, 1, 1));
    morton[i] = Collider::MortonCode(boxes[i].Center(), world);
  }
  Collider current(boxes, morton);
  const Collider original = current;
  const Box moved(vec3(20, 0, 0), vec3(21, 1, 1));
  current.UpdateLeafBox(1, moved);
  auto query = [&](const Collider& tree, const Box& box) {
    std::vector<int> faces;
    auto save = [&](int, int face) { faces.push_back(face); };
    auto recorder = MakeSimpleRecorder(save);
    tree.Collisions(recorder, [&](int) { return box; }, 1, false);
    return faces;
  };
  EXPECT_EQ(query(current, moved), std::vector<int>({1}));
  EXPECT_TRUE(query(original, moved).empty());
  EXPECT_EQ(query(original, boxes[1]), std::vector<int>({1}));
  EXPECT_TRUE(query(current, boxes[1]).empty());
  current.UpdateLeafBox(1, Box());
  EXPECT_TRUE(query(current, moved).empty());
}

namespace {
// Two disjoint local surface patches. Replacing the first triangle's origin
// by vertex 6 models the swept face of a directional collapse beside a fold.
// The nearby patch is outside the changed face's topological link.
Manifold::Impl NearbyFold() {
  Manifold::Impl mesh;
  mesh.epsilon_ = mesh.tolerance_ = 1e-7;
  for (const vec3 p :
       {vec3(0, 0, 0), vec3(2, 0, 0), vec3(0, 2, 0), vec3(.5, .5, .1),
        vec3(.5, .5, .3), vec3(.6, .5, .1), vec3(0, 0, .4)})
    mesh.vertPos_.push_back(p);
  for (int v = 0; v < 6; ++v) mesh.halfedge_.push_back(v, -1, -1);
  Vec<Box> boxes(2);
  Vec<uint32_t> morton(2);
  const Box world(vec3(0, 0, 0), vec3(2, 2, .4));
  for (int f = 0; f < 2; ++f) {
    for (int j = 0; j < 3; ++j) boxes[f].Union(mesh.vertPos_[3 * f + j]);
    morton[f] = Collider::MortonCode(boxes[f].Center(), world);
  }
  mesh.collider_ = Collider(boxes, morton);
  return mesh;
}
}  // namespace

TEST(Manifold, ProposedCollapseRejectsANearbyFoldOutsideItsLink) {
  auto mesh = NearbyFold();
  EXPECT_TRUE(mesh.GeometricallyLegalChanges({{0, ivec3(0, 1, 2)}}));
  EXPECT_FALSE(mesh.GeometricallyLegalChanges({{0, ivec3(6, 1, 2)}}));
  // Properly shared boundary edges are not an interference.
  EXPECT_TRUE(mesh.GeometricallyLegalChanges({{1, ivec3(0, 1, 6)}}));
}

TEST(Manifold, ProposedChangesCompareEachOtherAndAppendedFaces) {
  auto mesh = NearbyFold();
  // Replacing both faces must compare the proposed faces, not skip both.
  EXPECT_FALSE(mesh.GeometricallyLegalChanges(
      {{0, ivec3(6, 1, 2)}, {1, ivec3(3, 4, 5)}}));
  // A face appended after the BVH was built must still be considered.
  for (int j = 0; j < 3; ++j) mesh.halfedge_.push_back(3 + j, -1, -1);
  mesh.halfedge_.SetStart(3, -1);  // original copy was removed
  EXPECT_FALSE(mesh.GeometricallyLegalChanges({{0, ivec3(6, 1, 2)}}));
}
