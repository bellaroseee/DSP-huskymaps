package huskymaps.server.logic;

import huskymaps.params.RasterRequest;
import huskymaps.params.RasterResult;

import java.util.Objects;

import static huskymaps.Constants.*;

/** Application logic for the RasterAPIHandler. */
public class Rasterer {

    /**
     * Takes a user query and finds the grid of images that best matches the query. These
     * images will be combined into one big image (rastered) by the front end. <br>
     *
     *     The grid of images must obey the following properties, where image in the
     *     grid is referred to as a "tile".
     *     <ul>
     *         <li>The tiles collected must cover the most longitudinal distance per pixel
     *         (LonDPP) possible, while still covering less than or equal to the amount of
     *         longitudinal distance per pixel in the query box for the user viewport size. </li>
     *         <li>Contains all tiles that intersect the query bounding box that fulfill the
     *         above condition.</li>
     *         <li>The tiles must be arranged in-order to reconstruct the full image.</li>
     *     </ul>
     *
     * @param request RasterRequest
     * @return RasterResult
     */
    public static RasterResult rasterizeMap(RasterRequest request) {
        //For some queries, it may not be possible to find images to cover the entire query box.
        // This can occur under two scenarios.
        //
        //If the browser goes to the edge of the map beyond where data is available. -> ROOT_ULLAT, etc
        //        if (request.lrlat > ROOT_LRLAT || request.lrlon > ROOT_LRLON || request.ullat < ROOT_ULLAT ||
        //                request.ullon < ROOT_ULLON) {
        //            Tile[][] ret = new Tile[1][2];
        //            ret[0][0] = new Tile(0, 0, 0);
        //            ret[0][1] = new Tile(0, 1, 0);
        //            return new RasterResult(ret);
        //        }
        //If the query box is so zoomed out that it includes the entire dataset.
        //In these cases, return what data you do have available.
        //
        //You can also imagine that the browser might request a query box for a location that is completely outside
        //of the Seattle map region. RasterResult will still expect your grid to have data, so you cannot return
        //a 0-by-0 grid directly. In this case, you can return an arbitrary tile. The user should still correctly
        //not see anything on their screen, as their query box would be out of the coordinates covered by this
        //RasterResult.


        int leftEdge = (int) ((request.ullon - ROOT_ULLON) / LON_PER_TILE[request.depth]); //x
        int rightEdge = (int) ((request.lrlon - ROOT_ULLON) / LON_PER_TILE[request.depth]); //x
        int lowerEdge = (int) ((request.lrlat - ROOT_ULLAT) / LAT_PER_TILE[request.depth]) * (-1); //y
        int upperEdge = (int) ((request.ullat - ROOT_ULLAT) / LAT_PER_TILE[request.depth]) * (-1); //y

        //size of 2D array
        Tile[][] ret = new Tile[Math.abs(lowerEdge - upperEdge) + 1][Math.abs(rightEdge - leftEdge) + 1];

        //start from top left, end at bottom right
        for (int i = 0; i < ret.length; i++) {
            for (int j = 0; j < ret[0].length; j++) {
                Tile t = new Tile(request.depth, leftEdge + j, upperEdge + i);
                ret[i][j] = t;
            }
        }

        return new RasterResult(ret);
    }

    public static class Tile {
        public final int depth;
        public final int x;
        public final int y;

        public Tile(int depth, int x, int y) {
            this.depth = depth;
            this.x = x;
            this.y = y;
        }

        public Tile offset() {
            return new Tile(depth, x + 1, y + 1);
        }

        /**
         * Return the latitude of the upper-left corner of the given slippy map tile.
         * @return latitude of the upper-left corner
         * @source https://wiki.openstreetmap.org/wiki/Slippy_map_tilenames
         */
        public double lat() {
            double n = Math.pow(2.0, MIN_ZOOM_LEVEL + depth);
            int slippyY = MIN_Y_TILE_AT_DEPTH[depth] + y;
            double latRad = Math.atan(Math.sinh(Math.PI * (1 - 2 * slippyY / n)));
            return Math.toDegrees(latRad);
        }

        /**
         * Return the longitude of the upper-left corner of the given slippy map tile.
         * @return longitude of the upper-left corner
         * @source https://wiki.openstreetmap.org/wiki/Slippy_map_tilenames
         */
        public double lon() {
            double n = Math.pow(2.0, MIN_ZOOM_LEVEL + depth);
            int slippyX = MIN_X_TILE_AT_DEPTH[depth] + x;
            return slippyX / n * 360.0 - 180.0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Tile tile = (Tile) o;
            return depth == tile.depth &&
                    x == tile.x &&
                    y == tile.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(depth, x, y);
        }

        @Override
        public String toString() {
            return "d" + depth + "_x" + x + "_y" + y + ".jpg";
        }
    }
}
