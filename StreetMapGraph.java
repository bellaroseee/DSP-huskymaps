package huskymaps;

import astar.AStarGraph;
import astar.WeightedEdge;
import autocomplete.BinaryRangeSearch;
import autocomplete.SimpleTerm;
import autocomplete.Term;
import huskymaps.params.Location;
import pointset.KDTreePointSet;
import pointset.Point;

import java.util.*;

public class StreetMapGraph implements AStarGraph<Long> {
    private Map<Long, Node> nodes = new HashMap<>(); //key: nodeID
    private Map<Long, Set<WeightedEdge<Long>>> neighbors = new HashMap<>(); //key: nodeID
    private KDTreePointSet tree; //= new KDTreePointSet(new ArrayList<>());
    private Map<Point, Long> pointIDs = new HashMap<>(); //key is point, value is nodeID
    private BinaryRangeSearch search;
    private Map<String, List<Location>> location = new HashMap<>();

    public StreetMapGraph(String filename) {
        OSMGraphHandler.initializeFromXML(this, filename);
        //this (StreetMapGraph) has all nodes and edges added from filename

        ArrayList<Point> pointList = new ArrayList<>();
        ArrayList<Term> termList = new ArrayList<>();
        ArrayList<Location> locationList = new ArrayList<>();

        Set<Long> set = nodes.keySet(); //set of all NodeIDs
        for (Long id : set) {
            Node n = nodes.get(id);
            if (n.name() != null) { //if node name is not null
                SimpleTerm t = new SimpleTerm(n.name(), n.importance());
                termList.add(n);
            }
            if (isNavigable(n)) { //if set in neighbors is not empty
                pointList.add(n.toPoint());
            }
            pointIDs.put(n.toPoint(), id);
        }
        //map of all nodes name with the list of locations?

        tree = new KDTreePointSet(pointList);
        search = new BinaryRangeSearch(termList);

        //        ArrayList<Node> val = (ArrayList<Node>) nodes.values();


        //huskymaps.StreetMapGraph is an implementation of AStarGraph<Long>.
        //Each vertex of the graph is represented as a Node corresponding to a real,
        //physical location in Seattle with a specific long id.
        //Each Node has a latitude, longitude, and, optionally, a name.
        //They represents both named places (such as the “University of Washington” light rail station)
        //as well as spots along a road (which can be unnamed). Many vertices don’t have edges,
        //specifically those that correspond to places rather than spots along a road.
        //Nodes and edges are added to the graph by the OSMGraphHandler class,
        //which reads data from an OpenStreetMap dataset.
    }

    /**
     * Returns the vertex closest to the given location.
     * @param target The target location.
     * @return The id of the node in the graph closest to the target.
     */
    public long closest(Location target) {
        Point point = target.toPoint();
        Point nearest = tree.nearest(point.x(), point.y());
        long ret = pointIDs.get(nearest);
        Node test = nodes.get(53121434);
        Node test2 = nodes.get(345817295);

        return ret;
    }

    /**
     * In linear time, collect all the names of OSM locations that prefix-match the query string.
     * @param prefix Prefix string to be searched for. Could be any case, with our without
     *               punctuation.
     * @return A <code>List</code> of full names of locations matching the <code>prefix</code>.
     */
    public List<String> getLocationsByPrefix(String prefix) {
        List<Term> l = search.allMatches(prefix);
        List<String> ret = new ArrayList<>();
        for (Term t : l) {
            ret.add(t.query());
        }
        return ret;
    }

    /**
     * Collect all locations that match a cleaned <code>locationName</code>, and return
     * information about each node that matches.
     * @param locationName A full name of a location searched for.
     * @return A list of locations whose name matches the <code>locationName</code>.
     */
    public List<Location> getLocations(String locationName) {
        List<Location> ret = new ArrayList<>();
        ArrayList<Node> val = (ArrayList<Node>) nodes.values();
        for (Node n : val) {
            if (n.query().equals(locationName)) {
                ret.add(n);
            }
        }
        return ret;
    }

    /** Returns a list of outgoing edges for V. Assumes V exists in this graph. */
    @Override
    public List<WeightedEdge<Long>> neighbors(Long v) {
        return new ArrayList<>(neighbors.get(v));
    }

    /**
     * Returns the great-circle distance between S and GOAL. xAssumes
     * S and GOAL exist in this graph.
     */
    @Override
    public double estimatedDistanceToGoal(Long s, Long goal) {
        return location(s).greatCircleDistance(location(goal));
    }

    /** Returns a set of my vertices. Altering this set does not alter this graph. */
    public Set<Long> vertices() {
        return new HashSet<>(nodes.keySet());
    }

    /** Adds an edge to this graph if it doesn't already exist, using distance as the weight. */
    public void addWeightedEdge(long from, long to, String name) {
        if (nodes.containsKey(from) && nodes.containsKey(to)) {
            double weight = location(from).greatCircleDistance(location(to));
            neighbors.get(from).add(new WeightedEdge<>(from, to, weight, name));
        }
    }

    /** Adds an edge to this graph if it doesn't already exist. */
    public void addWeightedEdge(long from, long to, double weight, String name) {
        if (nodes.containsKey(from) && nodes.containsKey(to)) {
            neighbors.get(from).add(new WeightedEdge<>(from, to, weight, name));
        }
    }

    /** Adds an edge to this graph if it doesn't already exist. */
    public void addWeightedEdge(WeightedEdge<Long> edge) {
        if (nodes.containsKey(edge.from()) && nodes.containsKey(edge.to())) {
            neighbors.get(edge.from()).add(edge);
        }
    }

    /**
     * Returns the location for the given id.
     * @param id The id of the location.
     * @return The location instance.
     */
    public Location location(long id) {
        Location location = nodes.get(id);
        if (location == null) {
            throw new IllegalArgumentException("Location not found for id: " + id);
        }
        return location;
    }

    /** Adds a node to this graph, if it doesn't yet exist. */
    void addNode(Node node) {
        if (!nodes.containsKey(node.id())) {
            nodes.put(node.id(), node);
            neighbors.put(node.id(), new HashSet<>());
        }
    }

    /** Checks if a vertex has 0 out-degree from graph. */
    boolean isNavigable(Node node) {
        return !neighbors.get(node.id()).isEmpty();
    }

    Node.Builder nodeBuilder() {
        return new Node.Builder();
    }
}
