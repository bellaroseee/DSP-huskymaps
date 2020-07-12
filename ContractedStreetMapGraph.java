package huskymaps;

import astar.WeightedEdge;
import edu.princeton.cs.algs4.Stopwatch;
import pq.ExtrinsicMinPQ;
import pq.TreeMapMinPQ;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ContractedStreetMapGraph extends StreetMapGraph {

    public ContractedStreetMapGraph(String filename) {
        super(filename); //construct a StreetMapGraph
        //initialize the set of uncontracted nodes
        Set<ContractableNode> uncontractedNodes = vertices().parallelStream()
                .map(this::node) //map the ContractedStreetMapGraph.node method to each element in the vertices
                .filter(this::isNavigable) //Filter each ContractableNode with the StreetMapGraph.isNavigable method
                .collect(Collectors.toUnmodifiableSet()); //Collect the navigable ContractableNode objects into an
        // unmodifiable Set
        int order = 0;
        Stopwatch timer = new Stopwatch();
        while (!uncontractedNodes.isEmpty()) {
            //System.out.println("Uncontracted: " + uncontractedNodes.size());
            // Update the priority for each node
            // sequential:
            // Map<ContractableNode, Priority> priorityUpdateMap = new HashMap<>();
            // for (ContractableNode cn : uncontractedNodes) {
            //      priorityUpdateMap.put(cn, new Priority(cn));
            // }
            // parallel:
            Map<ContractableNode, Priority> priorityUpdateMap = uncontractedNodes.parallelStream()
                    .collect(Collectors.toUnmodifiableMap(Function.identity(), Priority::new));
            // Generate an independent node set
            // sequential:
            //            Set<ContractableNode> independetNodeSet = new HashSet<>();
            //            for (ContractableNode cn: uncontractedNodes) {
            //                if (isIndependent(cn, priorityUpdateMap)) {
            //                    independetNodeSet.add(cn);
            //                }
            //            }
            // parallel:
            Set<ContractableNode> independetNodeSet = uncontractedNodes.parallelStream()
                    .filter(contractableNode -> isIndependent(contractableNode, priorityUpdateMap))
                    .collect(Collectors.toUnmodifiableSet());
            // Compute all shortcut edges in the independent node set
            // sequential:
            //            List<Shortcuts> shortcutEdgesList = new ArrayList<>();
            //            for (ContractableNode cn: independetNodeSet) {
            //                shortcutEdgesList.add(new Shortcuts(cn));
            //            }
            // parallel:
            List<Shortcuts> shortcutEdgesList = independetNodeSet.parallelStream()
                    .map(Shortcuts::new)
                    .collect(Collectors.toUnmodifiableList());
            // Contract the independent nodes by inserting the precomputed shortcut edges into the graph
            // shortcuts need to be inserted in both directions addWeightedEdge(shortcut) & (shortcut.flip())
            // contracting node requires 2 additional method calls: node.setContractionOrder(order)
            // and node.updateDepths(neighboringNodes(node));
            // sequential:
            for (Shortcuts s : shortcutEdgesList) {
                for (WeightedShortcut<Long> sc : s) {
                    addWeightedEdge(sc);
                    addWeightedEdge(sc.flip());
                }
            }
            for (ContractableNode cn : independetNodeSet) {
                cn.setContractionOrder(order);
                cn.updateDepths(neighboringNodes(cn));
            }

            order += 1;
            // Update the uncontracted nodes by removing the independent nodes
            uncontractedNodes = uncontractedNodes.parallelStream()
                    .filter(contractableNode -> !(independetNodeSet.contains(contractableNode)))
                    .collect(Collectors.toUnmodifiableSet());
            // for (ContractableNode cn: uncontractedNodes) {
            //      if (!(independetNodeSet.contains(cn))) {
            //                uncontractedNodes.add(cn);
            //      }
            // }
        }
        System.out.println("Contraction hierarchies generated in " + timer.elapsedTime() + " seconds");
    }

    /** Return true if and only if the node is independent within its 2-nearest neighborhood. */
    // implementation of "An uncontracted node x is included in the independent node set"
    // if it has the maximum priority value among all of its 2-hop nearest neighbors
    private boolean isIndependent(ContractableNode node, Map<ContractableNode, Priority> priorities) {
        for (ContractableNode immediateNeighbor : neighboringNodes(node)) {
            if (!immediateNeighbor.isContracted()) {
                int cmp = Double.compare(priorities.get(node).value, priorities.get(immediateNeighbor).value);
                // Tie-breaking: lower node id is independent
                if ((cmp > 0) || (cmp == 0 && node.id() > immediateNeighbor.id())) {
                    return false;
                }
            }
            for (ContractableNode nextNeighbor : neighboringNodes(immediateNeighbor)) {
                if (!nextNeighbor.isContracted() && !node.equals(nextNeighbor)) {
                    int cmp = Double.compare(priorities.get(node).value, priorities.get(nextNeighbor).value);
                    // Tie-breaking: lower node id is independent
                    if ((cmp > 0) || (cmp == 0 && node.id() > nextNeighbor.id())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Computes the priority value for a given node. Shortcuts are cached if computed. */
    // defines the priority value of a Node
    // also stores the shortcuts if they were computed
    private class Priority {
        final double value;
        Shortcuts shortcuts;

        Priority(ContractableNode node) {
            List<WeightedEdge<Long>> neighbors = neighbors(node.id());
            int numTrueNeighbors = neighbors.size() - numContracted(neighbors);
            if (numTrueNeighbors > 0) {
                shortcuts = new Shortcuts(neighbors);
                double edgeQuotient = shortcuts.size() / (double) numTrueNeighbors;
                value = 3 * edgeQuotient + node.getDepth();
            } else {
                shortcuts = null;
                value = node.getDepth();
            }
        }

        /** Return the number of contracted neighbors. */
        int numContracted(List<WeightedEdge<Long>> neighbors) {
            int contractedNeighbors = 0;
            for (WeightedEdge<Long> edge : neighbors) {
                ContractableNode neighbor = node(edge.to());
                if (neighbor.isContracted()) {
                    contractedNeighbors += 1;
                }
            }
            return contractedNeighbors;
        }
    }


    /** Computes the shortcut edges that could be added to the graph. */
    // models the potential shortcut edges.
    // each instance has a list of weightedShortcut edges
    // shortcuts are added to the graph via addWeightedEdge method
    private class Shortcuts implements Iterable<WeightedShortcut<Long>> {
        final List<WeightedShortcut<Long>> result = new ArrayList<>();

        Shortcuts(ContractableNode node) {
            this(neighbors(node.id()));
        }

        Shortcuts(List<WeightedEdge<Long>> neighbors) {
            int i = 1;
            for (WeightedEdge<Long> srcEdge : neighbors) {
                ContractableNode src = node(srcEdge.to());
                if (!src.isContracted()) {
                    for (WeightedEdge<Long> destEdge : neighbors.subList(i, neighbors.size())) {
                        ContractableNode dest = node(destEdge.to());
                        WeightedShortcut<Long> shortcut = new WeightedShortcut<>(
                                srcEdge.flip(), destEdge, srcEdge.weight() + destEdge.weight(), "Shortcut"
                        );
                        if (!dest.isContracted() && shortcutRequired(src.id(), dest.id(), shortcut)) {
                            result.add(shortcut);
                        }
                    }
                }
                i += 1;
            }
        }

        @Override
        public Iterator<WeightedShortcut<Long>> iterator() {
            return result.iterator();
        }

        /** Return the number of shortcuts. */
        int size() {
            return result.size();
        }

        /** Returns true if and only if the given shortcutDistance <= shortest path distance. */
        boolean shortcutRequired(long start, long end, WeightedShortcut<Long> shortcut) {
            Map<Long, Double> distTo = new HashMap<>();
            ExtrinsicMinPQ<Long> pq = new TreeMapMinPQ<>();
            pq.add(start, estimatedDistanceToGoal(start, end));
            distTo.put(start, 0.0);
            while (!pq.isEmpty() && pq.getSmallest() != end && shortcut.weight() >= distTo.get(pq.getSmallest())) {
                long v = pq.removeSmallest();
                for (WeightedEdge<Long> edge : neighbors(v)) {
                    long w = edge.to();
                    if (!node(w).isContracted() && !edge.equals(shortcut.srcEdge) && !edge.equals(shortcut.destEdge)) {
                        double bestDistance = distTo.getOrDefault(w, Double.POSITIVE_INFINITY);
                        double thisDistance = distTo.get(v) + edge.weight();
                        if (thisDistance < bestDistance) {
                            distTo.put(w, thisDistance);
                            double priority = estimatedDistanceToGoal(w, end) + thisDistance;
                            if (pq.contains(w)) {
                                pq.changePriority(w, priority);
                            } else {
                                pq.add(w, priority);
                            }
                        }
                    }
                }
            }
            return shortcut.weight() < distTo.getOrDefault(end, Double.POSITIVE_INFINITY);
        }
    }

    /** Return a list of neighboring nodes. */
    private List<ContractableNode> neighboringNodes(ContractableNode node) {
        List<WeightedEdge<Long>> neighbors = neighbors(node.id());
        List<ContractableNode> result = new ArrayList<>(neighbors.size());
        for (WeightedEdge<Long> e : neighbors) {
            result.add(node(e.to()));
        }
        return result;
    }

    /** Return the node with the given id. */
    ContractableNode node(long id) {
        return ((ContractableNode) location(id));
    }

    @Override
    Node.Builder nodeBuilder() {
        return new ContractableNode.Builder();
    }
}
