package io.github.driftwood.model;

import java.util.List;

public final class PathMatcher {

    private PathMatcher() {
    }

    public static boolean matches(NodePath pattern, NodePath candidate) {
        return matches(pattern.getSegments(), 0, candidate.getSegments(), 0);
    }

    public static boolean matchesOrDescendant(NodePath pattern, NodePath candidate) {
        if (matches(pattern, candidate)) {
            return true;
        }
        List<NodePath.Segment> patternSegments = pattern.getSegments();
        List<NodePath.Segment> candidateSegments = candidate.getSegments();
        if (candidateSegments.size() <= patternSegments.size()) {
            return false;
        }
        return matches(patternSegments, 0, candidateSegments, 0)
                || matchesPrefix(patternSegments, candidateSegments);
    }

    private static boolean matches(
            List<NodePath.Segment> pattern,
            int patternIndex,
            List<NodePath.Segment> candidate,
            int candidateIndex) {
        if (patternIndex == pattern.size()) {
            return candidateIndex == candidate.size();
        }

        NodePath.Segment segment = pattern.get(patternIndex);
        if (segment.getType() == NodePath.Segment.Type.RECURSIVE) {
            if (patternIndex == pattern.size() - 1) {
                return true;
            }
            for (int index = candidateIndex; index <= candidate.size(); index++) {
                if (matches(pattern, patternIndex + 1, candidate, index)) {
                    return true;
                }
            }
            return false;
        }

        if (candidateIndex >= candidate.size()) {
            return false;
        }

        NodePath.Segment actual = candidate.get(candidateIndex);
        if (!matchesSegment(segment, actual)) {
            return false;
        }
        return matches(pattern, patternIndex + 1, candidate, candidateIndex + 1);
    }

    private static boolean matchesPrefix(
            List<NodePath.Segment> pattern,
            List<NodePath.Segment> candidate) {
        if (pattern.size() > candidate.size()) {
            return false;
        }
        for (int index = 0; index < pattern.size(); index++) {
            if (pattern.get(index).getType() == NodePath.Segment.Type.RECURSIVE) {
                return true;
            }
            if (!matchesSegment(pattern.get(index), candidate.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesSegment(
            NodePath.Segment pattern,
            NodePath.Segment candidate) {
        if (pattern.getType() == NodePath.Segment.Type.WILDCARD) {
            return true;
        }
        return pattern.equals(candidate);
    }
}
